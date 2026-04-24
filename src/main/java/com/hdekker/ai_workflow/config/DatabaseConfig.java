package com.hdekker.ai_workflow.config;

import java.util.Properties;

import javax.sql.DataSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.zaxxer.hikari.HikariDataSource;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
    basePackages = "com.hdekker.ai_workflow.database",
    entityManagerFactoryRef = "entityManagerFactory",
    transactionManagerRef = "transactionManager"
)
public class DatabaseConfig {

    @Autowired
    private DataSourceProperties dataSourceProperties;

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean
    @Primary
    public DataSource dataSource() {
        DataSourceBuilder<?> builder = DataSourceBuilder.create();
        builder.type(HikariDataSource.class);
        builder.driverClassName("org.sqlite.JDBC");
        builder.url(dataSourceProperties.getDatabase().getUrl());
        return builder.build();
    }

    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean entityManagerFactory() {
        LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(dataSource());
        emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        emf.setPackagesToScan("com.hdekker.ai_workflow.database");
        emf.setJpaProperties(jpaProperties());
        return emf;
    }

    @Bean
    @Primary
    public JpaTransactionManager transactionManager() {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(entityManagerFactory().getObject());
        return transactionManager;
    }

    private Properties jpaProperties() {
        Properties props = new Properties();
        props.put("hibernate.id.new_generator_mappings", "true");
        props.put("hibernate.connection.release_mode", "after_transaction");
        props.put("jakarta.persistence.sharedCache.mode", "NONE");
        props.put("hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect");
        props.put("hibernate.hbm2ddl.auto", "update");
        props.put("spring.jpa.hibernate.naming.physical-strategy", PhysicalNamingStrategyStandardImpl.class.getName());
        return props;
    }
}
