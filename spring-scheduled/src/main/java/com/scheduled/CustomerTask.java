package com.scheduled;

import com.scheduled.annotation.JobScheduled;
import com.scheduled.config.DataConfig;
import org.springframework.stereotype.Component;

/**
 * @author Feinik
 * @Discription
 * @Data 2018/12/30
 * @Version 1.0.0
 */
@Component
public class CustomerTask {

    @JobScheduled(cron = DataConfig.JOB1_CRON, enable = DataConfig.JOB1_ENABLE)
    public void job1() {
        System.out.println("调度任务1执行");
        try {
            Thread.sleep(60000);
            System.out.println("调度任务1执行完成");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @JobScheduled(cron = DataConfig.JOB2_CRON, enable = DataConfig.JOB2_ENABLE)
    public void job2() {
        System.out.println("调度任务2执行");
    }
}
