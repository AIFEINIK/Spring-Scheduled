package com.scheduled.annotation;

import com.scheduled.config.DataConfig;
import org.apache.commons.lang3.BooleanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Schedules;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.FixedRateTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.config.Task;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @author Feinik
 * @Discription 注册被JobScheduled注解的任务
 * @Data 2018/12/30
 * @Version 1.0.0
 */
@Component
public class JobScheduledAnnotationBeanPostProcessor
        implements BeanPostProcessor, Ordered, EmbeddedValueResolverAware, BeanNameAware,
        BeanFactoryAware, ApplicationContextAware, SmartInitializingSingleton, ApplicationListener<ContextRefreshedEvent>, DisposableBean {

    private Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private DataConfig config;

    @Nullable
    private Object scheduler;

    @Nullable
    private StringValueResolver embeddedValueResolver;

    @Nullable
    private BeanFactory beanFactory;

    @Nullable
    private String beanName;

    private boolean init = true;

    @Nullable
    private ApplicationContext applicationContext;

    public Map<String, Object> beanMap = new HashMap<>();

    private final ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();

    private final Set<Class<?>> nonAnnotatedClasses = Collections.newSetFromMap(new ConcurrentHashMap<>(64));

    @Override
    public void setEmbeddedValueResolver(StringValueResolver resolver) {
        this.embeddedValueResolver = resolver;
    }

    @Override
    public int getOrder() {
        return LOWEST_PRECEDENCE;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    @Override
    public void setBeanName(String name) {
        this.beanName = name;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        if (this.beanFactory == null) {
            this.beanFactory = applicationContext;
        }
    }

    @Nullable
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        return getObject(bean);
    }

    public Object getObject(Object bean) {
        Class<?> targetClass = AopProxyUtils.ultimateTargetClass(bean);
        if (!this.nonAnnotatedClasses.contains(targetClass)) {
            final Set<Method> annotatedMethods = new LinkedHashSet<>(1);
            ReflectionUtils.doWithMethods(targetClass, method -> {
                Set<JobScheduled> schedules = AnnotationUtils.getRepeatableAnnotations(method, JobScheduled.class, Schedules.class);
                for (JobScheduled jobScheduled : schedules) {
                    processScheduled(jobScheduled, method, bean);
                    annotatedMethods.add(method);
                }
            });

            if (annotatedMethods.isEmpty()) {
                this.nonAnnotatedClasses.add(targetClass);
                if (logger.isTraceEnabled()) {
                    logger.trace("No @JobScheduled annotations found on bean class: " + bean.getClass());
                }
            }
        }
        return bean;
    }

    protected void processScheduled(JobScheduled scheduled, Method method, Object bean) {
        try {
            Assert.isTrue(method.getParameterCount() == 0,
                    "Only no-arg methods may be annotated with @JobScheduled");
            if (init) {
                //将bean先保存到内存，等重新注册任务时使用
                beanMap.put(bean.getClass().getName(), bean);
            }
            Method invocableMethod = AopUtils.selectInvocableMethod(method, bean.getClass());
            Runnable runnable = new ScheduledMethodRunnable(bean, invocableMethod);
            boolean processedSchedule = false;
            String errorMessage =
                    "Exactly one of the 'cron', 'fixedDelay(String)', or 'fixedRate(String)' attributes is required";

            // Determine initial delay
            long initialDelay = scheduled.initialDelay();
            String initialDelayString = config.getString(scheduled.initialDelayString());
            if (StringUtils.hasText(initialDelayString)) {
                Assert.isTrue(initialDelay < 0, "Specify 'initialDelay' or 'initialDelayString', not both");
                if (this.embeddedValueResolver != null) {
                    initialDelayString = this.embeddedValueResolver.resolveStringValue(initialDelayString);
                }
                if (StringUtils.hasLength(initialDelayString)) {
                    try {
                        initialDelay = Long.valueOf(initialDelayString);
                    }
                    catch (RuntimeException ex) {
                        throw new IllegalArgumentException(
                                "Invalid initialDelayString value \"" + initialDelayString + "\" - cannot parse into long");
                    }
                }
            }

            // Check cron expression
            String cron = config.getString(scheduled.cron());
            if (StringUtils.isEmpty(cron)) {
                cron = scheduled.cron();
            }

            // 检查是否开启任务
            String enableStr = config.getString(scheduled.enable());
            boolean enable;
            if (StringUtils.isEmpty(enableStr)) {
                enable = BooleanUtils.toBoolean(scheduled.enable());
            } else {
                enable = BooleanUtils.toBoolean(enableStr);
            }
            if (StringUtils.hasText(cron)) {
                String zone = scheduled.zone();
                if (this.embeddedValueResolver != null) {
                    cron = this.embeddedValueResolver.resolveStringValue(cron);
                    zone = this.embeddedValueResolver.resolveStringValue(zone);
                }
                if (StringUtils.hasLength(cron)) {
                    Assert.isTrue(initialDelay == -1, "'initialDelay' not supported for cron triggers");
                    processedSchedule = true;
                    TimeZone timeZone;
                    if (StringUtils.hasText(zone)) {
                        timeZone = StringUtils.parseTimeZoneString(zone);
                    }
                    else {
                        timeZone = TimeZone.getDefault();
                    }

                    registerScheduledTask(method, bean, new CronTask(runnable, new CronTrigger(cron, timeZone)), enable);
                }
            }

            // At this point we don't need to differentiate between initial delay set or not anymore
            if (initialDelay < 0) {
                initialDelay = 0;
            }

            // Check fixed delay
            long fixedDelay = scheduled.fixedDelay();
            if (fixedDelay >= 0) {
                Assert.isTrue(!processedSchedule, errorMessage);
                processedSchedule = true;
                registerScheduledTask(method, bean, new FixedDelayTask(runnable, fixedDelay, initialDelay), enable);
            }
            String fixedDelayString = config.getString(scheduled.fixedDelayString());
            if (StringUtils.hasText(fixedDelayString)) {
                if (this.embeddedValueResolver != null) {
                    fixedDelayString = this.embeddedValueResolver.resolveStringValue(fixedDelayString);
                }
                if (StringUtils.hasLength(fixedDelayString)) {
                    Assert.isTrue(!processedSchedule, errorMessage);
                    processedSchedule = true;
                    try {
                        fixedDelay = Long.valueOf(fixedDelayString);
                    }
                    catch (RuntimeException ex) {
                        throw new IllegalArgumentException(
                                "Invalid fixedDelayString value \"" + fixedDelayString + "\" - cannot parse into long");
                    }
                    registerScheduledTask(method, bean, new FixedDelayTask(runnable, fixedDelay, initialDelay), enable);
                }
            }

            // Check fixed rate
            long fixedRate = scheduled.fixedRate();
            if (fixedRate >= 0) {
                Assert.isTrue(!processedSchedule, errorMessage);
                processedSchedule = true;
                registerScheduledTask(method, bean, new FixedRateTask(runnable, fixedRate, initialDelay), enable);
            }
            String fixedRateString = config.getString(scheduled.fixedRateString());
            if (StringUtils.hasText(fixedRateString)) {
                if (this.embeddedValueResolver != null) {
                    fixedRateString = this.embeddedValueResolver.resolveStringValue(fixedRateString);
                }
                if (StringUtils.hasLength(fixedRateString)) {
                    Assert.isTrue(!processedSchedule, errorMessage);
                    processedSchedule = true;
                    try {
                        fixedRate = Long.valueOf(fixedRateString);
                    }
                    catch (RuntimeException ex) {
                        throw new IllegalArgumentException(
                                "Invalid fixedRateString value \"" + fixedRateString + "\" - cannot parse into long");
                    }
                    registerScheduledTask(method, bean, new FixedRateTask(runnable, fixedRate, initialDelay), enable);

                }
            }

            // Check whether we had any attribute set
            Assert.isTrue(processedSchedule, errorMessage);
        }
        catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "Encountered invalid @JobScheduled method '" + method.getName() + "': " + ex.getMessage());
        }
    }

    private void registerScheduledTask(Method method, Object bean, Task task, boolean enable) {
        if (enable) {
            try {
                if (task instanceof CronTask) {
                    this.registrar.scheduleCronTask((CronTask) task);
                } else if (task instanceof FixedDelayTask) {
                    this.registrar.scheduleFixedDelayTask((FixedDelayTask) task);
                } else if (task instanceof FixedRateTask) {
                    this.registrar.scheduleFixedRateTask((FixedRateTask) task);
                }
                logger.info("------>类：" + bean.getClass().getName() + "，方法:" + method.getName() + "的调度任务已注册完成");
            } catch (Exception e) {
                logger.error("------>类：" + bean.getClass().getName() + "，方法:" + method.getName() + "的调度任务注册失败");
            }
        } else {
            logger.warn("------>类：" + bean.getClass().getName() + "，方法:" + method.getName() + "的调度任务未开启");
        }
    }

    @Override
    public void afterSingletonsInstantiated() {
        // Remove resolved singleton classes from cache
        this.nonAnnotatedClasses.clear();

        if (this.applicationContext == null) {
            // Not running in an ApplicationContext -> register tasks early...
            finishRegistration();
        }
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (event.getApplicationContext() == this.applicationContext) {
            // Running in an ApplicationContext -> register tasks this late...
            // giving other ContextRefreshedEvent listeners a chance to perform
            // their work at the same time (e.g. Spring Batch's job registration).
            finishRegistration();
        }
    }

    private void finishRegistration() {
        if (this.scheduler != null) {
            this.registrar.setScheduler(this.scheduler);
        }

        if (this.beanFactory instanceof ListableBeanFactory) {
            Map<String, SchedulingConfigurer> beans =
                    ((ListableBeanFactory) this.beanFactory).getBeansOfType(SchedulingConfigurer.class);
            List<SchedulingConfigurer> configurers = new ArrayList<>(beans.values());
            AnnotationAwareOrderComparator.sort(configurers);
            for (SchedulingConfigurer configurer : configurers) {
                configurer.configureTasks(this.registrar);
            }
        }

        if (this.registrar.hasTasks() && this.registrar.getScheduler() == null) {
            Assert.state(this.beanFactory != null, "BeanFactory must be set to find scheduler by type");
            try {
                // 使用自定义的ThreadPoolTaskScheduler
                this.registrar.setScheduler(this.beanFactory.getBean("customerThreadPoolTaskScheduler"));
            }
            catch (NoUniqueBeanDefinitionException ex) {
                logger.debug("Could not find unique TaskScheduler bean", ex);
                try {
                    this.registrar.setScheduler(this.beanFactory.getBean(TaskScheduler.class));
                }
                catch (NoSuchBeanDefinitionException ex2) {
                    if (logger.isInfoEnabled()) {
                        logger.info("More than one TaskScheduler bean exists within the context, and " +
                                "none is named 'taskScheduler'. Mark one of them as primary or name it 'taskScheduler' " +
                                "(possibly as an alias); or implement the SchedulingConfigurer interface and call " +
                                "ScheduledTaskRegistrar#setScheduler explicitly within the configureTasks() callback: " +
                                ex.getBeanNamesFound());
                    }
                }
            }
            catch (NoSuchBeanDefinitionException ex) {
                logger.debug("Could not find default TaskScheduler bean", ex);
                // Search for ScheduledExecutorService bean next...
                try {
                    this.registrar.setScheduler(this.beanFactory.getBean(ScheduledExecutorService.class));
                }
                catch (NoUniqueBeanDefinitionException ex2) {
                    logger.debug("Could not find unique ScheduledExecutorService bean", ex2);
                    try {
                        this.registrar.setScheduler(this.beanFactory.getBean(ScheduledExecutorService.class));
                    }
                    catch (NoSuchBeanDefinitionException ex3) {
                        if (logger.isInfoEnabled()) {
                            logger.info("More than one ScheduledExecutorService bean exists within the context, and " +
                                    "none is named 'taskScheduler'. Mark one of them as primary or name it 'taskScheduler' " +
                                    "(possibly as an alias); or implement the SchedulingConfigurer interface and call " +
                                    "ScheduledTaskRegistrar#setScheduler explicitly within the configureTasks() callback: " +
                                    ex2.getBeanNamesFound());
                        }
                    }
                }
                catch (NoSuchBeanDefinitionException ex2) {
                    logger.debug("Could not find default ScheduledExecutorService bean", ex2);
                    // Giving up -> falling back to default scheduler within the registrar...
                    logger.info("No TaskScheduler/ScheduledExecutorService bean found for scheduled processing");
                }
            }
        }

        this.registrar.afterPropertiesSet();
    }

    @Override
    public void destroy() throws Exception {
        this.registrar.destroy();
    }

    /**
     * 重新注册任务
     */
    public void reRegister(){
        try {
            //注销之前注册的任务
            this.registrar.destroy();
            this.registrar.setCronTasksList(new ArrayList<>());
            this.init = false;

            for (Object o : this.beanMap.values()) {
                this.getObject(o);
            }

            //设置自定义任务线程池
            this.registrar.setScheduler(this.beanFactory.getBean("customerThreadPoolTaskScheduler"));
            this.registrar.afterPropertiesSet();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
