package com.scheduled.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Feinik
 * @Discription 自定义schedule注解，支持定时任务开启，关闭
 * @Data 2018/12/30
 * @Version 1.0.0
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface JobScheduled {
    /**
     * cron 表达式
     * @return
     */
    String cron() default "";

    /**
     * 时区
     * @return
     */
    String zone() default "";

    /**
     * 是否开启任务，默认开启
     * @return
     */
    String enable() default "true";

    /**
     * 下次任务执行时间与上次执行任务之间固定间隔毫秒数
     * @return
     */
    long fixedDelay() default -1;

    String fixedDelayString() default "";

    /**
     * 以固定速率执行任务
     * @return
     */
    long fixedRate() default -1;

    String fixedRateString() default "";

    /**
     * 初始延迟时间，毫秒
     * @return
     */
    long initialDelay() default -1;

    String initialDelayString() default "";
}
