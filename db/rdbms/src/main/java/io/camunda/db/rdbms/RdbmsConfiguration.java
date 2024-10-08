/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import io.camunda.db.rdbms.queue.ExecutionQueue;
import io.camunda.db.rdbms.service.ExporterPositionRdbmsService;
import io.camunda.db.rdbms.service.ProcessInstanceRdbmsService;
import io.camunda.db.rdbms.service.ProcessRdbmsService;
import io.camunda.db.rdbms.service.VariableRdbmsService;
import io.camunda.db.rdbms.sql.ExporterPositionMapper;
import io.camunda.db.rdbms.sql.ProcessDefinitionMapper;
import io.camunda.db.rdbms.sql.ProcessInstanceMapper;
import io.camunda.db.rdbms.sql.VariableMapper;
import io.camunda.zeebe.scheduler.ActorScheduler;
import java.io.IOException;
import java.util.Properties;
import javax.sql.DataSource;
import liquibase.integration.spring.MultiTenantSpringLiquibase;
import org.apache.commons.logging.Log;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.JdbcType;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

@Configuration
public class RdbmsConfiguration {

  private static Logger LOGGER = LoggerFactory.getLogger(RdbmsConfiguration.class);

  @Bean
  public MultiTenantSpringLiquibase customerLiquibase(final DataSource dataSource) {
    LOGGER.info("Initializing Liquibase for RDBMS.");
    final var moduleConfig = new MultiTenantSpringLiquibase();
    moduleConfig.setDataSource(dataSource);
    // changelog file located in src/main/resources directly in the module
    moduleConfig.setChangeLog("db/changelog/rdbms-support/changelog-master.xml");
    return moduleConfig;
  }

  @Bean
  public SqlSessionFactory sqlSessionFactory(final DataSource dataSource) throws Exception {
    final var vendorProperties = new Properties();
    vendorProperties.put("H2", "h2");
    vendorProperties.put("PostgreSQL", "postgresql");
    vendorProperties.put("Oracle", "oracle");
    vendorProperties.put("MariaDB", "mariadb");
    vendorProperties.put("MySQL", "mariadb");
    vendorProperties.put("SQL Server", "sqlserver");
    final var databaseIdProvider = new VendorDatabaseIdProvider();
    databaseIdProvider.setProperties(vendorProperties);

    final var configuration = new org.apache.ibatis.session.Configuration();
    configuration.setJdbcTypeForNull(JdbcType.NULL);

    final SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
    factoryBean.setConfiguration(configuration);
    factoryBean.setDataSource(dataSource);
    factoryBean.setDatabaseIdProvider(databaseIdProvider);
    factoryBean.addMapperLocations(
        new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/*.xml"));

    // load vendor specific template variables
    final var databaseId = databaseIdProvider.getDatabaseId(dataSource);
    LOGGER.info("Detected databaseId: {}", databaseId);
    final Properties p = getVendorProperties(databaseIdProvider.getDatabaseId(dataSource));

    factoryBean.setConfigurationProperties(p);
    return factoryBean.getObject();
  }

  @Bean
  public MapperFactoryBean<ProcessInstanceMapper> processInstanceMapper(
      final SqlSessionFactory sqlSessionFactory) throws Exception {
    final MapperFactoryBean<ProcessInstanceMapper> factoryBean =
        new MapperFactoryBean<>(ProcessInstanceMapper.class);
    factoryBean.setSqlSessionFactory(sqlSessionFactory);
    return factoryBean;
  }

  @Bean
  public MapperFactoryBean<ProcessDefinitionMapper> processDeploymentMapper(
      final SqlSessionFactory sqlSessionFactory) throws Exception {
    final MapperFactoryBean<ProcessDefinitionMapper> factoryBean =
        new MapperFactoryBean<>(ProcessDefinitionMapper.class);
    factoryBean.setSqlSessionFactory(sqlSessionFactory);
    return factoryBean;
  }

  @Bean
  public MapperFactoryBean<VariableMapper> variableMapper(final SqlSessionFactory sqlSessionFactory)
      throws Exception {
    final MapperFactoryBean<VariableMapper> factoryBean =
        new MapperFactoryBean<>(VariableMapper.class);
    factoryBean.setSqlSessionFactory(sqlSessionFactory);
    return factoryBean;
  }

  @Bean
  public MapperFactoryBean<ExporterPositionMapper> exporterPosition(
      final SqlSessionFactory sqlSessionFactory) throws Exception {
    final MapperFactoryBean<ExporterPositionMapper> factoryBean =
        new MapperFactoryBean<>(ExporterPositionMapper.class);
    factoryBean.setSqlSessionFactory(sqlSessionFactory);
    return factoryBean;
  }

  @Bean
  public ExecutionQueue executionQueue(
      final ActorScheduler actorScheduler, final SqlSessionFactory sqlSessionFactory) {
    return new ExecutionQueue(actorScheduler, sqlSessionFactory);
  }

  @Bean
  public VariableRdbmsService variableRdbmsService(
      final ExecutionQueue executionQueue, final VariableMapper variableMapper) {
    return new VariableRdbmsService(executionQueue, variableMapper);
  }

  @Bean
  public ProcessRdbmsService processDeploymentRdbmsService(
      final ExecutionQueue executionQueue, final ProcessDefinitionMapper processDefinitionMapper) {
    return new ProcessRdbmsService(executionQueue, processDefinitionMapper);
  }

  @Bean
  public ProcessInstanceRdbmsService processRdbmsService(
      final ExecutionQueue executionQueue, final ProcessInstanceMapper processInstanceMapper) {
    return new ProcessInstanceRdbmsService(executionQueue, processInstanceMapper);
  }

  @Bean
  public ExporterPositionRdbmsService exporterPositionRdbmsService(
      final ExecutionQueue executionQueue, final ExporterPositionMapper exporterPositionMapper) {
    return new ExporterPositionRdbmsService(executionQueue, exporterPositionMapper);
  }

  @Bean
  public RdbmsService rdbmsService(
      final ExecutionQueue executionQueue,
      final ExporterPositionRdbmsService exporterPositionRdbmsService,
      final VariableRdbmsService variableRdbmsService,
      final ProcessRdbmsService processRdbmsService,
      final ProcessInstanceRdbmsService processInstanceRdbmsService) {
    return new RdbmsService(
        executionQueue,
        exporterPositionRdbmsService,
        processRdbmsService,
        processInstanceRdbmsService,
        variableRdbmsService);
  }

  private Properties getVendorProperties(String vendorId) throws IOException {
    final Properties properties = new Properties();
    final var file = "db/vendor-properties/" + vendorId + ".properties";
    final var propertiesInputStream = this.getClass().getClassLoader().getResourceAsStream(file);
    if (propertiesInputStream != null) {
      properties.load(this.getClass().getClassLoader().getResourceAsStream(file));
    } else {
      LOGGER.debug("No vendor properties found for databaseId {}", vendorId);
    }
    return properties;
  }
}
