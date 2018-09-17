package org.camunda.optimize.service.es.report.util.creator.median;

import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.service.es.report.util.creator.ReportDataCreator;

import static org.camunda.optimize.test.util.ReportDataHelper.createMedianProcessInstanceDurationGroupByVariableWithProcessPart;

public class MedianProcessInstanceDurationGroupByVariableWithProcessPartReportDataCreator implements ReportDataCreator {

  @Override
  public SingleReportDataDto create(String processDefinitionKey, String processDefinitionVersion,
                                    String... additional) {
    if (additional.length == 2) {
      String startFlowNodeId = additional[0];
      String endFlowNodeId = additional[1];
      return createMedianProcessInstanceDurationGroupByVariableWithProcessPart(
        processDefinitionKey,
        processDefinitionVersion,
        "foo",
        "bar",
        startFlowNodeId,
        endFlowNodeId
      );
    } else if (additional.length == 3) {
      String variableName = additional[0];
      String startFlowNodeId = additional[1];
      String endFlowNodeId = additional[2];
      return createMedianProcessInstanceDurationGroupByVariableWithProcessPart(
        processDefinitionKey,
        processDefinitionVersion,
        variableName,
        "bar",
        startFlowNodeId,
        endFlowNodeId
      );
    } else {
      String variableName = additional[0];
      String variableType = additional[1];
      String startFlowNodeId = additional[2];
      String endFlowNodeId = additional[3];
      return createMedianProcessInstanceDurationGroupByVariableWithProcessPart(
        processDefinitionKey,
        processDefinitionVersion,
        variableName,
        variableType,
        startFlowNodeId,
        endFlowNodeId
      );
    }
  }
}
