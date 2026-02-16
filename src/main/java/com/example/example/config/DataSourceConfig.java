package com.example.example.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ConnectionBuilder;
import java.sql.SQLException;
import java.sql.ShardingKeyBuilder;
import java.util.logging.Logger;

@Slf4j
@Configuration
public class DataSourceConfig {

  @Bean("masterDataSource")
  @ConfigurationProperties(prefix = "spring.datasource.master")
  public DataSource masterDataSource() {
    return DataSourceBuilder.create()
        .type(HikariDataSource.class)
        .build();
  }

//  @Primary
  @Bean("loggingMasterDataSource")
  public DataSource loggingMasterDataSource(@Qualifier("masterDataSource") DataSource masterDataSource) {
    return new DataSource() {
      @Override
      public Connection getConnection() throws SQLException {
        log.info("========[실제 DB 커넥션 획득 시점!]========");
        return masterDataSource.getConnection();
      }

      @Override
      public Connection getConnection(String username, String password) throws SQLException {
        log.info("========실제 DB 커넥션 획득 시점 (with credentials)!========");
        return masterDataSource.getConnection(username, password);
      }

      @Override
      public PrintWriter getLogWriter() throws SQLException {
        return masterDataSource.getLogWriter();
      }

      @Override
      public void setLogWriter(PrintWriter out) throws SQLException {
        masterDataSource.setLogWriter(out);
      }

      @Override
      public void setLoginTimeout(int seconds) throws SQLException {
        masterDataSource.setLoginTimeout(seconds);
      }

      @Override
      public int getLoginTimeout() throws SQLException {
        return masterDataSource.getLoginTimeout();
      }

      @Override
      public ConnectionBuilder createConnectionBuilder() throws SQLException {
        return masterDataSource.createConnectionBuilder();
      }

      @Override
      public Logger getParentLogger() {
        return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
      }

      @Override
      public ShardingKeyBuilder createShardingKeyBuilder() throws SQLException {
        return masterDataSource.createShardingKeyBuilder();
      }

      @Override
      public <T> T unwrap(Class<T> iface) throws SQLException {
        return masterDataSource.unwrap(iface);
      }

      @Override
      public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return masterDataSource.isWrapperFor(iface);
      }
    };
  }

  @Primary
  @Bean
  public DataSource dataSource(@Qualifier("loggingMasterDataSource") DataSource loggingMasterDataSource) {
    // LazyConnectionDataSourceProxy를 사용하여 트랜잭션 시작 후 실제 커넥션을 가져오도록 설정
    return new LazyConnectionDataSourceProxy(loggingMasterDataSource);
  }
}
