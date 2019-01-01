package com.scheduled.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.DefaultServletHandlerConfigurer;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author Feinik
 * @Discription
 * @Data 2018/12/18
 * @Version 1.0.0
 */
@Configuration
@EnableWebMvc
@EnableScheduling
@ComponentScan("com.scheduled")
@Import(DataConfig.class)
public class MvcConfig implements WebMvcConfigurer {

    /**
     * 开启默认的Servlet支持，使其可以访问静态资源
     * @param configurer
     */
    @Override
    public void configureDefaultServletHandling(DefaultServletHandlerConfigurer configurer) {
        configurer.enable();
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(20);
        executor.setKeepAliveSeconds(10);
        executor.setMaxPoolSize(200);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("callable-");
        executor.initialize();
        //注册自定义线程池
        configurer.setTaskExecutor(executor);
    }

    @Bean
    public ThreadPoolTaskScheduler customerThreadPoolTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(200);
        return scheduler;
    }

}
