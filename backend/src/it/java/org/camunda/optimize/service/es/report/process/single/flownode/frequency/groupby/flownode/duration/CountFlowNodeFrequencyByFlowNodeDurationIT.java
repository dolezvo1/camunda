/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.flownode.frequency.groupby.flownode.duration;

import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.single.ModelElementFrequencyByModelElementDurationIT;
import org.camunda.optimize.test.util.ProcessReportDataType;

import static org.camunda.optimize.test.util.ProcessReportDataType.FLOW_NODE_FREQUENCY_GROUP_BY_FLOW_NODE_DURATION;

public class CountFlowNodeFrequencyByFlowNodeDurationIT extends ModelElementFrequencyByModelElementDurationIT {
  @Override
  protected void startProcessInstanceCompleteUserTaskAndModifyModelElementDuration(final String definitionId, final long durationInMillis) {
    final ProcessInstanceEngineDto processInstance = engineIntegrationExtension.startProcessInstance(definitionId);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance.getId());
    engineDatabaseExtension.changeAllActivityDurations(processInstance.getId(), durationInMillis);
  }

  @Override
  protected ProcessViewEntity getModelElementView() {
    return ProcessViewEntity.FLOW_NODE;
  }

  @Override
  protected int getNumberOfModelElementsPerInstance() {
    return 3;
  }

  @Override
  protected ProcessReportDataType getReportDataType() {
    return FLOW_NODE_FREQUENCY_GROUP_BY_FLOW_NODE_DURATION;
  }
}
