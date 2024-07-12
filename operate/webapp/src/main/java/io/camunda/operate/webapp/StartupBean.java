/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.camunda.operate.conditions.DatabaseInfoProvider;
import io.camunda.operate.connect.ElasticsearchConnectorHelper;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.SchemaWithMigrationStartup;
import io.camunda.operate.webapp.security.auth.OperateUserDetailsService;
import io.camunda.operate.webapp.zeebe.operation.OperationExecutor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@DependsOn("schemaStartup")
@Profile("!test")
public class StartupBean {

  private static final Logger LOGGER = LoggerFactory.getLogger(StartupBean.class);

  @Autowired(required = false)
  private RestHighLevelClient esClient;

  @Autowired(required = false)
  private RestHighLevelClient zeebeEsClient;

  @Autowired(required = false)
  private ElasticsearchClient elasticsearchClient;

  @Autowired(required = false)
  private OperateUserDetailsService operateUserDetailsService;

  @Autowired private OperateProperties operateProperties;

  @Autowired private OperationExecutor operationExecutor;

  @Autowired private DatabaseInfoProvider databaseInfoProvider;

  @Autowired private SchemaWithMigrationStartup schemaStartup;

  @PostConstruct
  public void initApplication() {
    schemaStartup.initializeSchema();
    if (operateUserDetailsService != null) {
      LOGGER.info(
          "INIT: Create users in {} if not exists ...",
          databaseInfoProvider.getCurrent().getCode());
      operateUserDetailsService.initializeUsers();
    }

    LOGGER.info("INIT: Start operation executor...");
    operationExecutor.startExecuting();
    LOGGER.info("INIT: DONE");
  }

  @PreDestroy
  public void shutdown() {
    if (databaseInfoProvider.isElasticsearch()) {
      LOGGER.info("Shutdown elasticsearch clients.");
      ElasticsearchConnectorHelper.closeEsClient(esClient);
      ElasticsearchConnectorHelper.closeEsClient(zeebeEsClient);
    }
  }
}
