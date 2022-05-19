package com.solfini.util;

import java.util.Properties;

public class PoolSize {

  private PoolSize() {
    // hidden default constructor
  }

  public static void minimize(final Properties properties) {
    properties.setProperty("INITIAL_USER_CACHE_SIZE", "128");
    properties.setProperty("INITIAL_ORDER_BOOK_SIZE", "4096");
    properties.setProperty("BALANCE_ADMIN_POOL_QUEUE_CAPACITY", "128");
    properties.setProperty("BALANCE_ADMIN_POOL_START_CAPACITY", "128");
    properties.setProperty("BUSINESS_REJECT_POOL_QUEUE_CAPACITY", "128");
    properties.setProperty("BUSINESS_REJECT_POOL_START_CAPACITY", "128");
    properties.setProperty("BYTE_BUFFER_POOL_QUEUE_CAPACITY", "128");
    properties.setProperty("BYTE_BUFFER_POOL_START_CAPACITY", "128");
    properties.setProperty("CANCEL_ORDER_POOL_QUEUE_CAPACITY", "128");
    properties.setProperty("CANCEL_ORDER_POOL_START_CAPACITY", "128");
    properties.setProperty("CANCEL_REJECT_POOL_QUEUE_CAPACITY", "128");
    properties.setProperty("CANCEL_REJECT_POOL_START_CAPACITY", "128");
    properties.setProperty("EXECUTION_REPORT_POOL_QUEUE_CAPACITY", "128");
    properties.setProperty("EXECUTION_REPORT_POOL_START_CAPACITY", "128");
    properties.setProperty("KAFKA_PRODUCER_POOL_QUEUE_CAPACITY", "128");
    properties.setProperty("KAFKA_PRODUCER_POOL_START_CAPACITY", "128");
    properties.setProperty("LIQUIDATION_ORDER_POOL_QUEUE_CAPACITY", "128");
    properties.setProperty("LIQUIDATION_ORDER_POOL_START_CAPACITY", "128");
    properties.setProperty("LOG_POOL_QUEUE_CAPACITY", "128");
    properties.setProperty("LOG_POOL_START_CAPACITY", "128");
    properties.setProperty("ORDER_POOL_QUEUE_CAPACITY", "128");
    properties.setProperty("ORDER_POOL_START_CAPACITY", "128");
    properties.setProperty("POSITION_POOL_QUEUE_CAPACITY", "128");
    properties.setProperty("POSITION_POOL_START_CAPACITY", "128");
    properties.setProperty("POSITION_REPORT_PARSER_START_CAPACITY", "128");
    properties.setProperty("POSITION_REPORT_POOL_QUEUE_CAPACITY", "128");
    properties.setProperty("POSITION_REPORT_POOL_START_CAPACITY", "128");
    properties.setProperty("RecieverData_POOL_QUEUE_CAPACITY", "128");
    properties.setProperty("RecieverData_POOL_START_CAPACITY", "128");
    properties.setProperty("STRING_BUILDER_POOL_QUEUE_CAPACITY", "128");
    properties.setProperty("STRING_BUILDER_POOL_START_CAPACITY", "128");
    properties.setProperty("USER_OPEN_ORDERS_POOL_QUEUE_CAPACITY", "128");
    properties.setProperty("USER_OPEN_ORDERS_POOL_START_CAPACITY", "128");
  }
}
