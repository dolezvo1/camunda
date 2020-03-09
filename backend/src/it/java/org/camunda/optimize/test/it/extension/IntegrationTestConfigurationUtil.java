/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.it.extension;

import lombok.experimental.UtilityClass;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import org.camunda.optimize.test.util.PropertyUtil;

import java.util.Properties;

@UtilityClass
public class IntegrationTestConfigurationUtil {
  public static final String DEFAULT_PROPERTIES_PATH = "integration-rules.properties";
  private static final Properties PROPERTIES = PropertyUtil.loadProperties(DEFAULT_PROPERTIES_PATH);

  public static String getDefaultEngineName() {
    return PROPERTIES.getProperty("camunda.optimize.engine.default.name");
  }

  public static String resolveFullDefaultEngineName() {
    return System.getProperty("prefixedDefaultEngineName", resolveFullEngineName(getDefaultEngineName()));
  }

  public static String resolveFullEngineName(final String engineName) {
    return System.getProperty("enginePrefix", "") + engineName;
  }

  public static String getEngineVersion() {
    return PROPERTIES.getProperty("camunda.engine.version");
  }

  public static String getEngineDateFormat() {
    return PROPERTIES.getProperty("camunda.engine.serialization.date.format");
  }

  public static String getEngineItPluginEndpoint() {
    return PROPERTIES.getProperty("camunda.engine.it.plugin.endpoint");
  }

  public static String getEnginesRestEndpoint() {
    return PROPERTIES.getProperty("camunda.engine.rest.engines.endpoint");
  }

  public static String getEmbeddedOptimizeEndpoint() {
    return "http://localhost:" + System.getProperty("optimizeHttpPort", "8090");
  }

  public static String getEmbeddedOptimizeRestApiEndpoint() {
    return getEmbeddedOptimizeEndpoint() + "/api";
  }

  public static ConfigurationService createItConfigurationService() {
    return ConfigurationServiceBuilder.createConfiguration()
      .loadConfigurationFrom("service-config.yaml", "it/it-config.yaml")
      .build();
  }

  public static int getSmtpPort() {
    return Integer.parseInt(System.getProperty("smtpTestPort", "6666"));
  }

  public static int getHttpTimeoutMillis() {
    return Integer.parseInt(System.getProperty("httpTestTimeout", "10000"));
  }
}
