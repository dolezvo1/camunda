/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.AuthorizationSearchClient;
import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authentication;
import io.camunda.security.auth.Authorization;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerAuthorizationPatchRequest;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionAction;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class AuthorizationServices
    extends SearchQueryService<AuthorizationServices, AuthorizationQuery, AuthorizationEntity> {

  private final AuthorizationSearchClient authorizationSearchClient;

  public AuthorizationServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final AuthorizationSearchClient authorizationSearchClient,
      final Authentication authentication) {
    super(brokerClient, securityContextProvider, authentication);
    this.authorizationSearchClient = authorizationSearchClient;
  }

  @Override
  public AuthorizationServices withAuthentication(final Authentication authentication) {
    return new AuthorizationServices(
        brokerClient, securityContextProvider, authorizationSearchClient, authentication);
  }

  @Override
  public SearchQueryResult<AuthorizationEntity> search(final AuthorizationQuery query) {
    return authorizationSearchClient
        .withSecurityContext(
            securityContextProvider.provideSecurityContext(
                authentication, Authorization.of(a -> a.authorization().read())))
        .searchAuthorizations(query);
  }

  public List<AuthorizationEntity> findAll(final AuthorizationQuery query) {
    return authorizationSearchClient
        .withSecurityContext(
            securityContextProvider.provideSecurityContext(
                authentication, Authorization.of(a -> a.authorization().read())))
        .findAllAuthorizations(query);
  }

  public List<String> getAuthorizedResources(
      final Set<Long> ownerKeys,
      final PermissionType permissionType,
      final AuthorizationResourceType resourceType) {
    final var authorizationQuery =
        SearchQueryBuilders.authorizationSearchQuery(
            fn ->
                fn.filter(
                    f ->
                        f.ownerKeys(ownerKeys.stream().toList())
                            .permissionType(permissionType)
                            .resourceType(resourceType.name())));
    return findAll(authorizationQuery).stream()
        .map(AuthorizationEntity::resourceId)
        .collect(Collectors.toList());
  }

  public List<String> getAuthorizedApplications(final Set<Long> ownerKeys) {
    return getAuthorizedResources(
        ownerKeys, PermissionType.READ, AuthorizationResourceType.APPLICATION);
  }

  public Set<String> fetchAssignedPermissions(
      final Long ownerKey, final AuthorizationResourceType resourceType, final String resourceId) {
    final SearchQueryResult<AuthorizationEntity> result =
        search(
            SearchQueryBuilders.authorizationSearchQuery(
                fn ->
                    fn.filter(
                            f ->
                                f.resourceType(resourceType.name())
                                    .resourceIds(
                                        resourceId != null && !resourceId.isEmpty()
                                            ? resourceId
                                            : null)
                                    .ownerKeys(ownerKey))
                        .page(p -> p.size(1))));
    // TODO logic to fetch indirect authorizations via roles/groups should be added later
    return result.items().stream()
        .filter(entity -> entity.resourceId().equals(resourceId))
        .map(entity -> entity.permissionType().name())
        .collect(Collectors.toSet());
  }

  public CompletableFuture<AuthorizationRecord> patchAuthorization(
      final PatchAuthorizationRequest request) {
    final var futures =
        request.permissions().entrySet().stream()
            .flatMap(
                entry ->
                    entry.getValue().stream()
                        .map(
                            resourceId -> {
                              final var intent =
                                  request.action() == PermissionAction.ADD
                                      ? AuthorizationIntent.ADD_PERMISSION
                                      : AuthorizationIntent.REMOVE_PERMISSION;

                              final var brokerRequest =
                                  new BrokerAuthorizationPatchRequest(intent)
                                      .setOwnerKey(request.ownerKey())
                                      .setResourceType(request.resourceType())
                                      .setPermission(entry.getKey(), resourceId);

                              return sendBrokerRequest(brokerRequest);
                            }))
            .toList();

    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
        .thenApply(
            v -> {
              final var aggregatedRecord =
                  new AuthorizationRecord()
                      .setOwnerKey(request.ownerKey())
                      .setResourceType(request.resourceType());

              futures.forEach(
                  future -> {
                    final AuthorizationRecord record = future.join();
                    record.getPermissions().forEach(aggregatedRecord::addPermission);
                  });

              return aggregatedRecord;
            });
  }

  public record PatchAuthorizationRequest(
      long ownerKey,
      PermissionAction action,
      AuthorizationResourceType resourceType,
      Map<PermissionType, Set<String>> permissions) {}
}
