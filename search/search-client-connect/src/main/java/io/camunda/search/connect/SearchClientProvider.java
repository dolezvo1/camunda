/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect;

import io.camunda.search.clients.CamundaSearchClient;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.search.es.clients.ElasticsearchProcessInstanceSearchClient;
import io.camunda.search.es.clients.ElasticsearchSearchClient;
import io.camunda.search.os.clients.OpensearchSearchClient;

public final class SearchClientProvider {

  // Create two providers? One for ES, one for OS?

  public static CamundaSearchClient createElasticsearchProvider(
      final ConnectConfiguration configuration) {
    final var connector = new ElasticsearchConnector(configuration);
    final var elasticsearch = connector.createClient();
    return new ElasticsearchSearchClient(elasticsearch);
  }

  public static ElasticsearchProcessInstanceSearchClient createElasticsearchProcessInstanceSearchClient(
      final ConnectConfiguration configuration) {
    final var connector = new ElasticsearchConnector(configuration);
    final var elasticsearch = connector.createClient();
    return new ElasticsearchProcessInstanceSearchClient(elasticsearch);
  }

  public static CamundaSearchClient createOpensearchProvider(
      final ConnectConfiguration configuration) {
    final var connector = new OpensearchConnector(configuration);
    final var opensearch = connector.createClient();
    return new OpensearchSearchClient(opensearch);
  }
}
