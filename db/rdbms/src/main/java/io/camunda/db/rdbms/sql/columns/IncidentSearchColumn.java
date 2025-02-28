/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.IncidentEntity;
import io.camunda.zeebe.util.DateUtil;
import java.util.function.Function;

public enum IncidentSearchColumn implements SearchColumn<IncidentEntity> {
  INCIDENT_KEY("incidentKey", IncidentEntity::incidentKey),
  PROCESS_DEFINITION_KEY("processDefinitionKey", IncidentEntity::processDefinitionKey),
  PROCESS_DEFINITION_ID("processDefinitionId", IncidentEntity::processDefinitionId),
  PROCESS_INSTANCE_KEY("processInstanceKey", IncidentEntity::processInstanceKey),
  FLOW_NODE_INSTANCE_KEY("flowNodeInstanceKey", IncidentEntity::flowNodeInstanceKey),
  FLOW_NODE_ID("flowNodeId", IncidentEntity::flowNodeId),
  CREATION_DATE("creationTime", IncidentEntity::creationTime, DateUtil::fuzzyToOffsetDateTime),
  ERROR_TYPE("errorType", IncidentEntity::errorType),
  ERROR_MESSAGE("errorMessage", IncidentEntity::errorMessage),
  STATE("state", IncidentEntity::state),
  JOB_KEY("jobKey", IncidentEntity::jobKey),
  TENANT_ID("tenantId", IncidentEntity::tenantId);

  private final String property;
  private final Function<IncidentEntity, Object> propertyReader;
  private final Function<Object, Object> sortOptionConverter;

  IncidentSearchColumn(
      final String property, final Function<IncidentEntity, Object> propertyReader) {
    this(property, propertyReader, Function.identity());
  }

  IncidentSearchColumn(
      final String property,
      final Function<IncidentEntity, Object> propertyReader,
      final Function<Object, Object> sortOptionConverter) {
    this.property = property;
    this.propertyReader = propertyReader;
    this.sortOptionConverter = sortOptionConverter;
  }

  @Override
  public Object getPropertyValue(final IncidentEntity entity) {
    return propertyReader.apply(entity);
  }

  @Override
  public Object convertSortOption(final Object object) {
    if (object == null) {
      return null;
    }

    return sortOptionConverter.apply(object);
  }

  public static IncidentSearchColumn findByProperty(final String property) {
    for (final IncidentSearchColumn column : IncidentSearchColumn.values()) {
      if (column.property.equals(property)) {
        return column;
      }
    }

    return null;
  }
}
