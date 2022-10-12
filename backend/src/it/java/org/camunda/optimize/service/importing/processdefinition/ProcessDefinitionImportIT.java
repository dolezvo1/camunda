/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.processdefinition;

import org.assertj.core.groups.Tuple;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.FlowNodeDataDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.importing.AbstractImportIT;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.util.BpmnModels.END_EVENT;
import static org.camunda.optimize.util.BpmnModels.START_EVENT;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;

public class ProcessDefinitionImportIT extends AbstractImportIT {
  private static final String START = "aStart";
  private static final String END = "anEnd";

  @Test
  public void getProcessDefinitionFields() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(
      "aProcess",
      START,
      END
    ));

    // when
    importAllEngineEntitiesFromScratch();
    List<ProcessDefinitionOptimizeDto> processDefinitions = elasticSearchIntegrationTestExtension.getAllProcessDefinitions();

    // then
    assertThat(processDefinitions)
      .singleElement()
      .satisfies(definition -> assertThat(definition.getFlowNodeData())
        .containsExactly(
          new FlowNodeDataDto(END, END, END_EVENT),
          new FlowNodeDataDto(START, START, START_EVENT)
        ));
  }

  @Test
  public void processImportedForFirstTimeMarkedAsNotOnboarded() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(
      "aProcess",
      START,
      END
    ));

    // when
    importAllEngineEntitiesFromScratch();
    List<ProcessDefinitionOptimizeDto> processDefinitions = elasticSearchIntegrationTestExtension.getAllProcessDefinitions();

    // then
    assertThat(processDefinitions)
      .singleElement()
      .satisfies(definition -> assertThat(definition.isOnboarded()).isFalse());
  }

  @Test
  public void secondVersionOfProcessDefinitionImportedDoesNotOverrideOnboardedStateOfFirst() {
    // given
    final BpmnModelInstance process = getSimpleBpmnDiagram("aProcess", START, END);
    engineIntegrationExtension.deployAndStartProcess(process);

    // when
    importAllEngineEntitiesFromScratch();
    List<ProcessDefinitionOptimizeDto> processDefinitions = elasticSearchIntegrationTestExtension.getAllProcessDefinitions();

    // then
    assertThat(processDefinitions)
      .singleElement()
      .satisfies(definition -> {
        assertThat(definition.getVersion()).isEqualTo("1");
        assertThat(definition.isOnboarded()).isFalse();
      });

    engineIntegrationExtension.deployAndStartProcess(process);

    // when
    importAllEngineEntitiesFromScratch();
    processDefinitions = elasticSearchIntegrationTestExtension.getAllProcessDefinitions();

    // then
    assertThat(processDefinitions)
      .hasSize(2)
      .extracting(DefinitionOptimizeResponseDto::getVersion, ProcessDefinitionOptimizeDto::isOnboarded)
      .containsExactlyInAnyOrder(Tuple.tuple("1", false), Tuple.tuple("2", true));
  }

}
