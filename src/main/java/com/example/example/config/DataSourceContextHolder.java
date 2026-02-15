package com.example.example.config;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class DataSourceContextHolder {

  private static final ThreadLocal<DataSourceType> contextHolder = new ThreadLocal<>();

  private DataSourceContextHolder() {
    throw new IllegalStateException("is not instantiable");
  }

  public static void setDataSourceType(DataSourceType dataSourceType) {
    if (dataSourceType == null) {
      throw new IllegalArgumentException("DataSourceType cannot be null");
    }
    log.debug("Setting DataSource type to: {}", dataSourceType);
    contextHolder.set(dataSourceType);
  }

  public static DataSourceType getDataSourceType() {
    DataSourceType dataSourceType = contextHolder.get();
    log.debug("Getting DataSource type: {}", dataSourceType);
    return dataSourceType != null ? dataSourceType : DataSourceType.MASTER;
  }

  public static void clearDataSourceType() {
    log.debug("Clearing DataSource type");
    contextHolder.remove();
  }
}
