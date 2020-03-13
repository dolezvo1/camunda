/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security.collection;

import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDefinitionDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.Collections;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class CollectionManageAuthorizationIT extends AbstractCollectionRoleIT {

  private static final String USER_ID_JOHN = "John";

  @ParameterizedTest
  @MethodSource(MANAGER_IDENTITY_ROLES)
  public void managerIdentityCanUpdateNameByCollectionRole(final IdentityAndRole identityAndRole) {
    //given
    final String collectionId = collectionClient.createNewCollection();
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    addRoleToCollectionAsDefaultUser(
      identityAndRole.roleType,
      identityAndRole.identityDto,
      collectionId
    );

    // when
    final PartialCollectionDefinitionDto collectionRenameDto = new PartialCollectionDefinitionDto("Test");

    //then
    collectionClient.updateCollection(collectionId, collectionRenameDto);
  }

  @ParameterizedTest
  @MethodSource(NON_MANAGER_IDENTITY_ROLES)
  public void nonManagerIdentityIsRejectedToUpdateNameByCollectionRole(final IdentityAndRole identityAndRole) {
    //given
    final String collectionId = collectionClient.createNewCollection();

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final PartialCollectionDefinitionDto collectionRenameDto = new PartialCollectionDefinitionDto("Test");
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildUpdatePartialCollectionRequest(collectionId, collectionRenameDto)
      .execute();

    // then
    assertThat(response.getStatus(), is(Response.Status.FORBIDDEN.getStatusCode()));
  }

  @Test
  public void superUserIdentityCanUpdateNameByCollectionRole() {
    //given
    final String collectionId = collectionClient.createNewCollection();
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtension.getConfigurationService().getSuperUserIds().add(KERMIT_USER);

    // when
    final PartialCollectionDefinitionDto collectionRenameDto = new PartialCollectionDefinitionDto("Test");

    //then
    collectionClient.updateCollection(collectionId, collectionRenameDto);
  }

  @Test
  public void noRoleUserIsRejectedToUpdateName() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();

    final String collectionId = collectionClient.createNewCollection();

    // when
    final PartialCollectionDefinitionDto collectionRenameDto = new PartialCollectionDefinitionDto("Test");
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildUpdatePartialCollectionRequest(collectionId, collectionRenameDto)
      .execute();

    // then
    assertThat(response.getStatus(), is(Response.Status.FORBIDDEN.getStatusCode()));
  }

  @ParameterizedTest
  @MethodSource(MANAGER_IDENTITY_ROLES)
  public void managerIdentityCanCreateRoleByCollectionRole(final IdentityAndRole identityAndRole) {
    //given
    final String collectionId = collectionClient.createNewCollection();

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    final CollectionRoleDto collectionRoleDto = createJohnEditorRoleDto();
    authorizationClient.addUserAndGrantOptimizeAccess(USER_ID_JOHN);

    // when
    collectionClient.addRoleToCollectionAsUser(collectionId, collectionRoleDto, KERMIT_USER, KERMIT_USER);
  }

  @ParameterizedTest
  @MethodSource(NON_MANAGER_IDENTITY_ROLES)
  public void nonManagerIdentityRejectedToCreateRoleByCollectionRole(final IdentityAndRole identityAndRole) {
    //given
    final String collectionId = collectionClient.createNewCollection();

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    final CollectionRoleDto collectionRoleDto = createJohnEditorRoleDto();
    authorizationClient.addUserAndGrantOptimizeAccess(USER_ID_JOHN);

    // when
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildAddRoleToCollectionRequest(collectionId, collectionRoleDto)
      .execute();

    // then
    assertThat(response.getStatus(), is(Response.Status.FORBIDDEN.getStatusCode()));
  }

  @Test
  public void superUserIdentityCanCreateRoleByCollectionRole() {
    //given
    final String collectionId = collectionClient.createNewCollection();

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtension.getConfigurationService().getSuperUserIds().add(KERMIT_USER);

    final CollectionRoleDto collectionRoleDto = createJohnEditorRoleDto();
    authorizationClient.addUserAndGrantOptimizeAccess(USER_ID_JOHN);

    // when
    collectionClient.addRoleToCollectionAsUser(collectionId, collectionRoleDto, KERMIT_USER, KERMIT_USER);
  }

  @ParameterizedTest
  @MethodSource(MANAGER_IDENTITY_ROLES)
  public void managerIdentityCanUpdateRoleByCollectionRole(final IdentityAndRole identityAndRole) {
    //given
    final String collectionId = collectionClient.createNewCollection();
    authorizationClient.addUserAndGrantOptimizeAccess(USER_ID_JOHN);
    final String roleId = collectionClient.addRoleToCollection(collectionId, createJohnEditorRoleDto()).getId();

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when + then
    collectionClient.updateCollectionRoleAsUser(
      collectionId,
      roleId,
      new CollectionRoleUpdateDto(RoleType.MANAGER),
      KERMIT_USER,
      KERMIT_USER
    );
  }

  @ParameterizedTest
  @MethodSource(NON_MANAGER_IDENTITY_ROLES)
  public void nonManagerIdentityRejectedToUpdateRoleByCollectionRole(final IdentityAndRole identityAndRole) {
    //given
    final String collectionId = collectionClient.createNewCollection();
    authorizationClient.addUserAndGrantOptimizeAccess(USER_ID_JOHN);
    final CollectionRoleDto johnEditorRoleDto = createJohnEditorRoleDto();
    final String roleId = collectionClient.addRoleToCollection(collectionId, johnEditorRoleDto).getId();

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildUpdateRoleToCollectionRequest(collectionId, roleId, new CollectionRoleUpdateDto(RoleType.MANAGER))
      .execute();
    // then
    assertThat(response.getStatus(), is(Response.Status.FORBIDDEN.getStatusCode()));
  }

  @Test
  public void superUserIdentityCanUpdateRoleByCollectionRole() {
    //given
    final String collectionId = collectionClient.createNewCollection();
    authorizationClient.addUserAndGrantOptimizeAccess(USER_ID_JOHN);
    final String roleId = collectionClient.addRoleToCollection(collectionId, createJohnEditorRoleDto()).getId();

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtension.getConfigurationService().getSuperUserIds().add(KERMIT_USER);

    // when
    collectionClient.updateCollectionRoleAsUser(
      collectionId,
      roleId,
      new CollectionRoleUpdateDto(RoleType.MANAGER),
      KERMIT_USER,
      KERMIT_USER
    );
  }

  @ParameterizedTest
  @MethodSource(MANAGER_IDENTITY_ROLES)
  public void managerIdentityCanDeleteRoleByCollectionRole(final IdentityAndRole identityAndRole) {
    //given
    final String collectionId = collectionClient.createNewCollection();
    authorizationClient.addUserAndGrantOptimizeAccess(USER_ID_JOHN);
    final String roleId = collectionClient.addRoleToCollection(collectionId, createJohnEditorRoleDto()).getId();

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    collectionClient.deleteCollectionRoleAsUser(collectionId, roleId, KERMIT_USER, KERMIT_USER);
  }

  @ParameterizedTest
  @MethodSource(NON_MANAGER_IDENTITY_ROLES)
  public void nonManagerIdentityRejectedToDeleteRoleByCollectionRole(final IdentityAndRole identityAndRole) {
    //given
    final String collectionId = collectionClient.createNewCollection();
    authorizationClient.addUserAndGrantOptimizeAccess(USER_ID_JOHN);
    final String roleId = collectionClient.addRoleToCollection(collectionId, createJohnEditorRoleDto()).getId();

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildDeleteRoleToCollectionRequest(collectionId, roleId)
      .execute();

    // then
    assertThat(response.getStatus(), is(Response.Status.FORBIDDEN.getStatusCode()));
  }

  @Test
  public void superUserIdentityCanDeleteRoleByCollectionRole() {
    //given
    final String collectionId = collectionClient.createNewCollection();
    authorizationClient.addUserAndGrantOptimizeAccess(USER_ID_JOHN);
    final String roleId = collectionClient.addRoleToCollection(collectionId, createJohnEditorRoleDto()).getId();

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtension.getConfigurationService().getSuperUserIds().add(KERMIT_USER);

    // when
    collectionClient.deleteCollectionRoleAsUser(collectionId, roleId, KERMIT_USER, KERMIT_USER);
  }

  @ParameterizedTest
  @MethodSource(MANAGER_IDENTITY_ROLES)
  public void managerIdentityCanCreateScopeByCollectionRole(final IdentityAndRole identityAndRole) {
    //given
    final String collectionId = collectionClient.createNewCollection();

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantAllResourceAuthorizationsForKermit(RESOURCE_TYPE_PROCESS_DEFINITION);
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    collectionClient.addScopeEntryToCollectionAsUser(collectionId, createProcessScope(), KERMIT_USER, KERMIT_USER);
  }

  @ParameterizedTest
  @MethodSource(NON_MANAGER_IDENTITY_ROLES)
  public void nonManagerIdentityRejectedToCreateScopeByCollectionRole(final IdentityAndRole identityAndRole) {
    //given
    final String collectionId = collectionClient.createNewCollection();

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantAllResourceAuthorizationsForKermit(RESOURCE_TYPE_PROCESS_DEFINITION);
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildAddScopeEntryToCollectionRequest(collectionId, createProcessScope())
      .execute();

    // then
    assertThat(response.getStatus(), is(Response.Status.FORBIDDEN.getStatusCode()));
  }

  @Test
  public void superUserIdentityCanCreateScopeByCollectionRole() {
    //given
    final String collectionId = collectionClient.createNewCollection();

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantAllResourceAuthorizationsForKermit(RESOURCE_TYPE_PROCESS_DEFINITION);
    embeddedOptimizeExtension.getConfigurationService().getSuperUserIds().add(KERMIT_USER);

    // when
    collectionClient.addScopeEntryToCollectionAsUser(collectionId, createProcessScope(), KERMIT_USER, KERMIT_USER);
  }

  @ParameterizedTest
  @MethodSource(MANAGER_IDENTITY_ROLES)
  public void managerIdentityCanUpdateScopeByCollectionRole(final IdentityAndRole identityAndRole) {
    //given
    final String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto scopeEntry = createProcessScope();
    collectionClient.addScopeEntryToCollection(collectionId, scopeEntry);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    collectionClient.updateCollectionScopeAsKermit(collectionId, scopeEntry.getId(), Collections.singletonList("tenant1"));
  }

  @ParameterizedTest
  @MethodSource(NON_MANAGER_IDENTITY_ROLES)
  public void nonManagerIdentityRejectedToUpdateScopeByCollectionRole(final IdentityAndRole identityAndRole) {
    //given
    final String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto scopeEntry = createProcessScope();
    collectionClient.addScopeEntryToCollection(collectionId, scopeEntry);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildUpdateCollectionScopeEntryRequest(collectionId, scopeEntry.getId(), createScopeUpdate())
      .execute();
    // then
    assertThat(response.getStatus(), is(Response.Status.FORBIDDEN.getStatusCode()));
  }

  @Test
  public void superUserIdentityCanUpdateScopeByCollectionRole() {
    //given
    final String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto scopeEntry = createProcessScope();
    collectionClient.addScopeEntryToCollection(collectionId, scopeEntry);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtension.getConfigurationService().getSuperUserIds().add(KERMIT_USER);

    // when
    collectionClient.updateCollectionScopeAsKermit(collectionId, scopeEntry.getId(), Collections.singletonList("tenant1"));
  }

  @ParameterizedTest
  @MethodSource(MANAGER_IDENTITY_ROLES)
  public void managerIdentityCanDeleteScopeByCollectionRole(final IdentityAndRole identityAndRole) {
    //given
    final String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto scopeEntry = createProcessScope();
    collectionClient.addScopeEntryToCollection(collectionId, scopeEntry);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildDeleteScopeEntryFromCollectionRequest(collectionId, scopeEntry.getId())
      .execute();

    // then
    assertThat(response.getStatus(), is(Response.Status.NO_CONTENT.getStatusCode()));
  }

  @ParameterizedTest
  @MethodSource(NON_MANAGER_IDENTITY_ROLES)
  public void nonManagerIdentityRejectedToDeleteScopeByCollectionRole(final IdentityAndRole identityAndRole) {
    //given
    final String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto scopeEntry = createProcessScope();
    collectionClient.addScopeEntryToCollection(collectionId, scopeEntry);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildDeleteScopeEntryFromCollectionRequest(collectionId, scopeEntry.getId())
      .execute();

    // then
    assertThat(response.getStatus(), is(Response.Status.FORBIDDEN.getStatusCode()));
  }

  @Test
  public void superUserIdentityCanDeleteScopeByCollectionRole() {
    //given
    final String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto scopeEntry = createProcessScope();
    collectionClient.addScopeEntryToCollection(collectionId, scopeEntry);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtension.getConfigurationService().getSuperUserIds().add(KERMIT_USER);

    // when
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildDeleteScopeEntryFromCollectionRequest(collectionId, scopeEntry.getId())
      .execute();

    // then
    assertThat(response.getStatus(), is(Response.Status.NO_CONTENT.getStatusCode()));
  }

  @Test
  public void onlyManagerCanCopyACollection() {
    final String collectionId = collectionClient.createNewCollection();
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    collectionClient.addRoleToCollection(collectionId, new CollectionRoleDto(
        new IdentityDto("kermit", IdentityType.USER),
        RoleType.VIEWER
      )).getId();

    authorizationClient.addUserAndGrantOptimizeAccess("gonzo");
    collectionClient.addRoleToCollection(collectionId, new CollectionRoleDto(
        new IdentityDto("gonzo", IdentityType.USER),
        RoleType.MANAGER
      )).getId();

    embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCopyCollectionRequest(collectionId)
      .withUserAuthentication("gonzo", "gonzo")
      .execute(Response.Status.OK.getStatusCode());

    embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCopyCollectionRequest(collectionId)
      .withUserAuthentication("kermit", "kermit")
      .execute(Response.Status.FORBIDDEN.getStatusCode());
  }

  private CollectionScopeEntryUpdateDto createScopeUpdate() {
    return new CollectionScopeEntryUpdateDto(Collections.singletonList("tenant1"));
  }

  private CollectionScopeEntryDto createProcessScope() {
    return new CollectionScopeEntryDto(DefinitionType.PROCESS, "KEY");
  }

  private CollectionRoleDto createJohnEditorRoleDto() {
    return new CollectionRoleDto(new IdentityDto(USER_ID_JOHN, IdentityType.USER), RoleType.EDITOR);
  }

}
