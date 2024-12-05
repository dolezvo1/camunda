/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.tenant;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.TenantState;
import io.camunda.zeebe.engine.state.tenant.PersistedTenant;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.apache.commons.lang3.StringUtils;

public class TenantUpdateProcessor implements DistributedTypedRecordProcessor<TenantRecord> {

  private final TenantState tenantState;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public TenantUpdateProcessor(
      final TenantState tenantState,
      final AuthorizationCheckBehavior authCheckBehavior,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior) {
    this.tenantState = tenantState;
    this.authCheckBehavior = authCheckBehavior;
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.commandDistributionBehavior = commandDistributionBehavior;
  }

  @Override
  public void processNewCommand(final TypedRecord<TenantRecord> command) {

    final var record = command.getValue();
    final var tenantKey = record.getTenantKey();

    final var persistedTenant = tenantState.getTenantByKey(tenantKey);
    if (persistedTenant.isEmpty()) {
      rejectCommand(
          command,
          RejectionType.NOT_FOUND,
          "Expected to update tenant with key '%s', but no tenant with this key exists."
              .formatted(tenantKey));
      return;
    }

    if (!StringUtils.isEmpty(record.getTenantId())) {
      rejectCommand(
          command,
          RejectionType.INVALID_ARGUMENT,
          "Tenant ID cannot be updated. Expected no tenant ID, but received '%s'."
              .formatted(record.getTenantId()));
      return;
    }

    if (!isAuthorizedToUpdate(command, persistedTenant.get())) {
      return;
    }

    updateExistingTenant(persistedTenant.get(), record);
    updateStateAndDistribute(command, persistedTenant.get());
  }

  @Override
  public void processDistributedCommand(final TypedRecord<TenantRecord> command) {
    stateWriter.appendFollowUpEvent(
        command.getValue().getTenantKey(), TenantIntent.UPDATED, command.getValue());
    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private boolean isAuthorizedToUpdate(
      final TypedRecord<TenantRecord> command, final PersistedTenant persistedTenant) {
    final var authorizationRequest =
        new AuthorizationRequest(command, AuthorizationResourceType.TENANT, PermissionType.UPDATE)
            .addResourceId(persistedTenant.getTenantId());
    if (!authCheckBehavior.isAuthorized(authorizationRequest)) {
      rejectCommandWithUnauthorizedError(
          command, authorizationRequest, persistedTenant.getTenantId());
      return false;
    }
    return true;
  }

  private void updateExistingTenant(
      final PersistedTenant existingTenant, final TenantRecord updateRecord) {
    final var updatedName = updateRecord.getName();
    if (!updatedName.isEmpty()) {
      existingTenant.setName(updatedName);
    }
  }

  private void updateStateAndDistribute(
      final TypedRecord<TenantRecord> command, final PersistedTenant persistedTenant) {
    final var updatedRecord =
        new TenantRecord()
            .setTenantKey(persistedTenant.getTenantKey())
            .setTenantId(persistedTenant.getTenantId())
            .setName(persistedTenant.getName());

    stateWriter.appendFollowUpEvent(
        persistedTenant.getTenantKey(), TenantIntent.UPDATED, updatedRecord);
    responseWriter.writeEventOnCommand(
        persistedTenant.getTenantKey(), TenantIntent.UPDATED, updatedRecord, command);
    distributeCommand(command);
  }

  private void distributeCommand(final TypedRecord<TenantRecord> command) {
    final long distributionKey = keyGenerator.nextKey();
    commandDistributionBehavior
        .withKey(distributionKey)
        .inQueue(DistributionQueue.IDENTITY.getQueueId())
        .distribute(command);
  }

  private void rejectCommandWithUnauthorizedError(
      final TypedRecord<TenantRecord> command,
      final AuthorizationRequest authorizationRequest,
      final String tenantId) {
    final var errorMessage =
        AuthorizationCheckBehavior.UNAUTHORIZED_ERROR_MESSAGE_WITH_RESOURCE.formatted(
            authorizationRequest.getPermissionType(),
            authorizationRequest.getResourceType(),
            "tenant id '%s'".formatted(tenantId));
    rejectCommand(command, RejectionType.UNAUTHORIZED, errorMessage);
  }

  private void rejectCommand(
      final TypedRecord<TenantRecord> command,
      final RejectionType type,
      final String errorMessage) {
    rejectionWriter.appendRejection(command, type, errorMessage);
    responseWriter.writeRejectionOnCommand(command, type, errorMessage);
  }
}
