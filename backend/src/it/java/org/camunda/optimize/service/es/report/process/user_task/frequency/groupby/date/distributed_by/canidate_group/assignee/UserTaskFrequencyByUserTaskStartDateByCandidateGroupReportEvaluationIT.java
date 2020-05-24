/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.user_task.frequency.groupby.date.distributed_by.canidate_group.assignee;

import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.FlowNodeExecutionState;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.ReportHyperMapResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.util.HyperMapAsserter;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.stream.Stream;

public class UserTaskFrequencyByUserTaskStartDateByCandidateGroupReportEvaluationIT
  extends UserTaskFrequencyByUserTaskDateByCandidateGroupReportEvaluationIT {

  @Test
  public void reportEvaluationForOneProcessWithUnassignedTasks() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineIntegrationExtension.finishAllRunningUserTasks();

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .groupByContains(groupedByDayDateAsString(referenceDate))
      .distributedByContains(getLocalisedUnassignedLabel(), 1L)
      .distributedByContains(FIRST_CANDIDATE_GROUP, 1L)
      .doAssert(result);
    // @formatter:on
  }

  @ParameterizedTest
  @MethodSource("getExecutionStateExpectedValues")
  public void evaluateReportWithExecutionState(final FlowNodeExecutionState executionState,
                                               final Long assignee1Count,
                                               final Long assignee2Count) {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishTwoUserTasksWithDifferentCandidateGroups();

    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReportData(processDefinition, GroupByDateUnit.DAY);
    reportData.getConfiguration().setFlowNodeExecutionState(executionState);
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(groupedByDayDateAsString(OffsetDateTime.now()))
        .distributedByContains(SECOND_CANDIDATE_GROUP, assignee2Count)
        .distributedByContains(FIRST_CANDIDATE_GROUP, assignee1Count)
      .doAssert(result);
    // @formatter:on
  }

  protected static Stream<Arguments> getExecutionStateExpectedValues() {
    return Stream.of(
      Arguments.of(FlowNodeExecutionState.RUNNING, 1L, null),
      Arguments.of(FlowNodeExecutionState.COMPLETED, 1L, 1L),
      Arguments.of(FlowNodeExecutionState.ALL, 2L, 1L)
    );
  }

  private String getLocalisedUnassignedLabel() {
    return embeddedOptimizeExtension.getLocalizationService()
      .getDefaultLocaleMessageForMissingAssigneeLabel();
  }

  @Override
  protected ProcessReportDataType getReportDataType() {
    return ProcessReportDataType.USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_START_DATE_BY_CANDIDATE_GROUP;
  }

  @Override
  protected ProcessGroupByType getGroupByType() {
    return ProcessGroupByType.START_DATE;
  }

  @Override
  protected void changeUserTaskDates(final Map<String, OffsetDateTime> updates) {
    engineDatabaseExtension.changeUserTaskStartDates(updates);
  }

  @Override
  protected void changeUserTaskDate(final ProcessInstanceEngineDto processInstance,
                                    final String userTaskKey,
                                    final OffsetDateTime dateToChangeTo) {
    engineDatabaseExtension.changeUserTaskStartDate(processInstance.getId(), userTaskKey, dateToChangeTo);
  }
}
