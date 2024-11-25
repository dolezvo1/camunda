/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.usermanagement.EntityJoin;
import io.camunda.webapps.schema.entities.usermanagement.GroupEntity;
import io.camunda.webapps.schema.entities.usermanagement.GroupEntity.Relation;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import java.util.List;
import java.util.Set;

public class GroupCreatedUpdatedHandler implements ExportHandler<GroupEntity, GroupRecordValue> {

  private static final Set<Intent> SUPPORTED_INTENTS =
      Set.of(GroupIntent.CREATED, GroupIntent.UPDATED);
  private final String indexName;

  public GroupCreatedUpdatedHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.GROUP;
  }

  @Override
  public Class<GroupEntity> getEntityType() {
    return GroupEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<GroupRecordValue> record) {
    return getHandledValueType().equals(record.getValueType())
        && SUPPORTED_INTENTS.contains(record.getIntent());
  }

  @Override
  public List<String> generateIds(final Record<GroupRecordValue> record) {
    return List.of(String.valueOf(record.getKey()));
  }

  @Override
  public GroupEntity createNewEntity(final String id) {
    return new GroupEntity().setId(id).setJoin(new EntityJoin<>(Relation.GROUP, null));
  }

  @Override
  public void updateEntity(final Record<GroupRecordValue> record, final GroupEntity entity) {
    final GroupRecordValue value = record.getValue();
    entity.setGroupKey(value.getGroupKey()).setName(value.getName());
  }

  @Override
  public void flush(final GroupEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.add(indexName, entity);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
