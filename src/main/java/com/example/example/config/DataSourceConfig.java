package com.example.example.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

  @Bean("masterDataSource")
  @ConfigurationProperties(prefix = "spring.datasource.master")
  public DataSource masterDataSource() {
    return DataSourceBuilder.create()
        .type(HikariDataSource.class)
        .build();
  }

  @Primary
  @Bean
  public DataSource dataSource(@Qualifier("masterDataSource") DataSource masterDataSource) {
    // LazyConnectionDataSourceProxy를 사용하여 트랜잭션 시작 후 실제 커넥션을 가져오도록 설정
    return new LazyConnectionDataSourceProxy(masterDataSource);
  }
}
