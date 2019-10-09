/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

public class DashboardRestServiceIT {

  public EngineIntegrationRule engineIntegrationRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineIntegrationRule).around(embeddedOptimizeRule);

  @Test
  public void createNewDashboardWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withoutAuthentication()
      .buildCreateDashboardRequestWithDefinition(generateDashboardDefinitionDto())
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void createNewDashboard() {
    // when
    IdDto idDto = embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateDashboardRequest()
      .execute(IdDto.class, 200);

    // then the status code is okay
    assertThat(idDto, is(notNullValue()));
  }

  @Test
  public void createNewDashboardWithDefinition() {
    // when
    IdDto idDto = embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateDashboardRequestWithDefinition(generateDashboardDefinitionDto())
      .execute(IdDto.class, 200);

    // then the status code is okay
    assertThat(idDto, is(notNullValue()));
  }

  @Test
  public void createNewDashboardWithCollectionIdAndDefinitionRespectsTheCollectionIdParameter() {
    // when
    final String collectionId = createEmptyCollectionToOptimize();
    DashboardDefinitionDto dashboardDefinitionDto = generateDashboardDefinitionDto();
    dashboardDefinitionDto.setCollectionId(UUID.randomUUID().toString());
    String id = createDashboardToCollectionAsDefaultUserWithDefinition(collectionId, dashboardDefinitionDto);

    // then the status code is okay
    assertThat(id, is(notNullValue()));
    // and the stored dashboard has the expected collectionId
    DashboardDefinitionDto returnedDashboard = getDashboardWithId(id);
    assertThat(returnedDashboard.getCollectionId(), is(collectionId));
  }

  @Test
  public void copyPrivateDashboard() {
    // given
    String dashboardId = createEmptyPrivateDashboard();
    createEmptyReportToDashboard(dashboardId);

    // when
    IdDto copyId = embeddedOptimizeRule.getRequestExecutor()
      .buildCopyDashboardRequest(dashboardId)
      .execute(IdDto.class, 200);

    // then
    DashboardDefinitionDto oldDashboard = getDashboard(dashboardId);
    DashboardDefinitionDto dashboard = getDashboard(copyId.getId());
    assertThat(dashboard.toString(), is(oldDashboard.toString()));
    assertThat(dashboard.getName(), is(oldDashboard.getName() + " – Copy"));

    final List<String> newReportIds = dashboard.getReports()
      .stream()
      .map(ReportLocationDto::getId)
      .collect(Collectors.toList());

    assertThat(newReportIds.isEmpty(), is(false));
    assertThat(
      newReportIds,
      containsInAnyOrder(oldDashboard.getReports().stream().map(ReportLocationDto::getId).toArray())
    );
  }

  @Test
  public void copyPrivateDashboardWithNameParameter() {
    // given
    final String dashboardId = createEmptyPrivateDashboard();
    createEmptyReportToDashboard(dashboardId);

    final String testDashboardCopyName = "This is my new report copy! ;-)";

    // when
    IdDto copyId = embeddedOptimizeRule.getRequestExecutor()
      .buildCopyDashboardRequest(dashboardId)
      .addSingleQueryParam("name", testDashboardCopyName)
      .execute(IdDto.class, 200);

    // then
    DashboardDefinitionDto oldDashboard = getDashboard(dashboardId);
    DashboardDefinitionDto dashboard = getDashboard(copyId.getId());
    assertThat(dashboard.toString(), is(oldDashboard.toString()));
    assertThat(dashboard.getName(), is(testDashboardCopyName));
  }

  @Test
  public void getDashboardWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withoutAuthentication()
      .buildGetDashboardRequest("asdf")
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getDashboard() {
    //given
    DashboardDefinitionDto definitionDto = generateDashboardDefinitionDto();
    String id = createPrivateDashboardWithDefinition(generateDashboardDefinitionDto());

    // when
    DashboardDefinitionDto returnedDashboard = getDashboardWithId(id);

    // then
    assertThat(returnedDashboard, is(notNullValue()));
    assertThat(returnedDashboard.getId(), is(id));
    assertThat(returnedDashboard.getName(), is(definitionDto.getName()));
  }

  @Test
  public void getDashboardForNonExistingIdThrowsError() {
    // when
    String response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetDashboardRequest("fooid")
      .execute(String.class, 404);

    // then the status code is okay
    assertThat(response.contains("Dashboard does not exist!"), is(true));
  }

  @Test
  public void updateDashboardWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withoutAuthentication()
      .buildUpdateDashboardRequest("1", null)
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void updateNonExistingDashboard() {
    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateDashboardRequest("nonExistingId", new DashboardDefinitionDto())
      .execute();

    // then
    assertThat(response.getStatus(), is(404));
  }

  @Test
  public void updateDashboard() {
    //given
    String id = createEmptyPrivateDashboard();

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateDashboardRequest(id, new DashboardDefinitionDto())
      .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
  }

  @Test
  public void updateDashboardDoesNotChangeCollectionId() {
    //given
    final String collectionId = createEmptyCollectionToOptimize();
    String id = createDashboardToCollectionAsDefaultUserWithDefinition(collectionId, new DashboardDefinitionDto());

    // when
    embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateDashboardRequest(id, new DashboardDefinitionDto())
      .execute();

    // then
    final DashboardDefinitionDto dashboard = getDashboard(id);
    assertThat(dashboard.getCollectionId(), is(collectionId));
  }

  @Test
  public void deleteDashboardWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withoutAuthentication()
      .buildDeleteDashboardRequest("1124")
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void deleteNewDashboard() {
    //given
    String id = createEmptyPrivateDashboard();

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildDeleteDashboardRequest(id)
      .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
  }

  @Test
  public void deleteNonExistingDashboard() {
    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildDeleteDashboardRequest("nonExistingId")
      .execute();

    // then
    assertThat(response.getStatus(), is(404));
  }

  private String createEmptyPrivateDashboard() {
    return createDashboardToCollectionAsDefaultUserWithDefinition(null, null);
  }

  private String createPrivateDashboardWithDefinition(DashboardDefinitionDto dashboardDefinitionDto) {
    return createDashboardToCollectionAsDefaultUserWithDefinition(null, dashboardDefinitionDto);
  }

  private String createDashboardToCollectionAsDefaultUserWithDefinition(final String collectionId,
                                                                        final DashboardDefinitionDto dashboardDefinitionDto) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateDashboardRequestWithDefinition(collectionId, dashboardDefinitionDto)
      .execute(IdDto.class, 200)
      .getId();
  }

  private DashboardDefinitionDto getDashboard(String dashboardId) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetDashboardRequest(dashboardId)
      .execute(DashboardDefinitionDto.class, 200);
  }

  private String createEmptyCollectionToOptimize() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateCollectionRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  private void updateDashboardRequest(final String dashboardId, final List<ReportLocationDto> reports) {
    final DashboardDefinitionDto dashboard = getDashboardWithId(dashboardId);

    if (reports != null) {
      dashboard.setReports(reports);
    }

    embeddedOptimizeRule.getRequestExecutor()
      .buildUpdateDashboardRequest(dashboardId, dashboard)
      .execute(204);
  }

  private DashboardDefinitionDto getDashboardWithId(final String id) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetDashboardRequest(id)
      .execute(DashboardDefinitionDto.class, 200);
  }

  private void createEmptyReportToDashboard(final String dashboardId) {
    final String reportId = createEmptySingleProcessReportToCollection(null);
    final ReportLocationDto reportLocationDto = new ReportLocationDto();
    reportLocationDto.setId(reportId);
    updateDashboardRequest(dashboardId, Collections.singletonList(reportLocationDto));
  }

  private String createEmptySingleProcessReportToCollection(final String collectionId) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest(collectionId)
      .execute(IdDto.class, 200)
      .getId();
  }

  private DashboardDefinitionDto generateDashboardDefinitionDto() {
    DashboardDefinitionDto dashboardDefinitionDto = new DashboardDefinitionDto();
    dashboardDefinitionDto.setName("Dashboard name");
    return dashboardDefinitionDto;
  }

}
