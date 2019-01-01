package com.scheduled.datasource;

import com.alibaba.druid.pool.DruidDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * @author Feinik
 * @Discription 数据源配置
 * @Data 2018/12/31
 * @Version 1.0.0
 */
@Configuration
public class DataSourceConfig {
    //数据源配置
    @Bean
    public DataSource dataSource() throws SQLException {
        DruidDataSource source = new DruidDataSource();
        source.setUrl("jdbc:mysql://localhost:3306/test");
        source.setUsername("root");
        source.setPassword("root");
        source.setDriverClassName("com.mysql.jdbc.Driver");
        source.init();
        return source;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource source) {
        return new JdbcTemplate(source);
    }

    //事务管理配置
    @Bean
    public PlatformTransactionManager transactionManager(DataSource source) {
        PlatformTransactionManager transactionManager = new DataSourceTransactionManager(source);
        return transactionManager;
    }
}
