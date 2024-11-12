/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.tasklist.adapter.es;

import static java.util.stream.Collectors.toList;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import io.camunda.migration.api.MigrationException;
import io.camunda.migration.tasklist.MigrationRepositoryIndex;
import io.camunda.migration.tasklist.TasklistMigrationProperties;
import io.camunda.migration.tasklist.adapter.Adapter;
import io.camunda.operate.schema.migration.AbstractStep;
import io.camunda.operate.schema.migration.ProcessorStep;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.webapps.schema.descriptors.operate.index.ProcessIndex;
import io.camunda.webapps.schema.entities.operate.ProcessEntity;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticsearchAdapter implements Adapter {

  private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchAdapter.class);
  private final ElasticsearchClient client;
  private final TasklistMigrationProperties properties;
  private final MigrationRepositoryIndex migrationRepositoryIndex;
  private final ProcessIndex processIndex;

  public ElasticsearchAdapter(
      final TasklistMigrationProperties properties, final ElasticsearchConnector connector) {
    this.properties = properties;
    migrationRepositoryIndex =
        new MigrationRepositoryIndex(properties.getConnect().getIndexPrefix(), true);
    processIndex = new ProcessIndex(properties.getConnect().getIndexPrefix(), true);
    client = connector.createClient();
  }

  @Override
  public String migrate(final List<ProcessEntity> entities) throws MigrationException {
    try {
      final BulkRequest.Builder bulkRequest = new BulkRequest.Builder();
      entities.forEach(entity -> migrateEntity(entity, bulkRequest));
      final BulkResponse response = client.bulk(bulkRequest.build());
      return lastUpdatedProcessDefinition(response.items());

    } catch (final IOException e) {
      throw new MigrationException("Tasklist migration step failed", e);
    }
  }

  @Override
  public List<ProcessEntity> nextBatch(final String lastMigratedEntity) {
    final SearchRequest searchRequest =
        new SearchRequest.Builder()
            .index(processIndex.getFullQualifiedName())
            .size(properties.getBatchSize())
            .sort(s -> s.field(f -> f.field(PROCESS_DEFINITION_KEY).order(SortOrder.Asc)))
            .query(
                q ->
                    q.range(
                        m ->
                            m.field(PROCESS_DEFINITION_KEY)
                                .gt(
                                    JsonData.of(
                                        lastMigratedEntity == null ? "" : lastMigratedEntity))))
            .build();
    final SearchResponse<ProcessEntity> searchResponse;
    try {
      searchResponse = client.search(searchRequest, ProcessEntity.class);
    } catch (final IOException | ElasticsearchException e) {
      LOG.error("Failed to acquire new migration batch", e);
      return List.of();
    }

    return searchResponse.hits().hits().stream().map(Hit::source).collect(toList());
  }

  @Override
  public String readLastMigratedEntity() throws MigrationException {
    final SearchRequest searchRequest =
        new SearchRequest.Builder()
            .index(migrationRepositoryIndex.getFullQualifiedName())
            .size(1)
            .query(
                q ->
                    q.bool(
                        b ->
                            b.must(
                                    m ->
                                        m.match(
                                            t ->
                                                t.field(MigrationRepositoryIndex.TYPE)
                                                    .query(PROCESSOR_STEP_TYPE)))
                                .must(
                                    m ->
                                        m.term(
                                            t ->
                                                t.field(MigrationRepositoryIndex.ID)
                                                    .value(PROCESSOR_STEP_ID)))))
            .build();
    final SearchResponse<ProcessorStep> searchResponse;
    try {
      searchResponse = client.search(searchRequest, ProcessorStep.class);
      return searchResponse.hits().hits().stream()
          .map(Hit::source)
          .filter(Objects::nonNull)
          .map(AbstractStep::getContent)
          .findFirst()
          .orElse(null);
    } catch (final IOException | ElasticsearchException e) {
      throw new MigrationException("Failed to fetch next batch", e);
    }
  }

  @Override
  public void writeLastMigratedEntity(final String processDefinitionKey) throws MigrationException {
    final UpdateRequest<ProcessorStep, ProcessorStep> updateRequest =
        new UpdateRequest.Builder<ProcessorStep, ProcessorStep>()
            .index(migrationRepositoryIndex.getFullQualifiedName())
            .id(PROCESSOR_STEP_ID)
            .docAsUpsert(true)
            .doc(upsertProcessorStep(processDefinitionKey))
            .refresh(Refresh.True)
            .upsert(upsertProcessorStep(processDefinitionKey))
            .build();
    try {
      client.update(updateRequest, ProcessorStep.class);
    } catch (final IOException e) {
      throw new MigrationException("Failed to write last migrated entity", e);
    }
  }

  @Override
  public void close() throws IOException {
    client._transport().close();
  }

  private void migrateEntity(final ProcessEntity entity, final BulkRequest.Builder bulkRequest) {
    bulkRequest.operations(
        op ->
            op.update(
                e ->
                    e.index(processIndex.getFullQualifiedName())
                        .id(entity.getId())
                        .action(act -> act.doc(getUpdateMap(entity)))));
  }

  private String lastUpdatedProcessDefinition(final List<BulkResponseItem> items) {
    final var sorted = items.stream().sorted(Comparator.comparing(BulkResponseItem::id)).toList();
    for (int i = 0; i < sorted.size(); i++) {
      if (sorted.get(i).error() != null) {
        return i > 0
            ? Objects.requireNonNull(sorted.get(i - 1).id())
            : Objects.requireNonNull(sorted.get(i).id());
      }
    }

    return Objects.requireNonNull(sorted.getLast().id());
  }
}
