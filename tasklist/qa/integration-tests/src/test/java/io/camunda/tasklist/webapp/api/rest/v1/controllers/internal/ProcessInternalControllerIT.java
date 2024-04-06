/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers.internal;

import static io.camunda.tasklist.util.TestCheck.PROCESS_IS_DEPLOYED_CHECK;
import static io.camunda.tasklist.util.assertions.CustomAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.MockMvcHelper;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.util.TestCheck;
import io.camunda.tasklist.util.ZeebeTestUtil;
import io.camunda.tasklist.webapp.api.rest.v1.entities.ProcessPublicEndpointsResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.ProcessResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.StartProcessRequest;
import io.camunda.tasklist.webapp.graphql.entity.ProcessInstanceDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableInputDTO;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

public class ProcessInternalControllerIT extends TasklistZeebeIntegrationTest {

  @Autowired
  @Qualifier(PROCESS_IS_DEPLOYED_CHECK)
  private TestCheck processIsDeployedCheck;

  @Autowired private WebApplicationContext context;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private TasklistProperties tasklistProperties;

  private MockMvcHelper mockMvcHelper;

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("camunda.tasklist.cloud.clusterId", () -> "449ac2ad-d3c6-4c73-9c68-7752e39ae616");
    registry.add("camunda.tasklist.client.clusterId", () -> "449ac2ad-d3c6-4c73-9c68-7752e39ae616");
    registry.add("camunda.tasklist.featureFlag.processPublicEndpoints", () -> true);
  }

  @BeforeEach
  public void setUp() {
    mockMvcHelper =
        new MockMvcHelper(MockMvcBuilders.webAppContextSetup(context).build(), objectMapper);
  }

  private MockHttpServletResponse startProcessDeployInvokeAndReturn(
      final String pathProcess, final String bpmnProcessId) throws Exception {
    final List<VariableInputDTO> variables = new ArrayList<VariableInputDTO>();
    variables.add(new VariableInputDTO().setName("testVar").setValue("\"testValue\""));
    variables.add(new VariableInputDTO().setName("testVar2").setValue("\"testValue2\""));

    final StartProcessRequest startProcessRequest =
        new StartProcessRequest().setVariables(variables);

    final String processId1 = ZeebeTestUtil.deployProcess(zeebeClient, pathProcess);

    databaseTestExtension.processAllRecordsAndWait(processIsDeployedCheck, processId1);

    // when
    final var result =
        mockMvcHelper.doRequest(
            patch(
                    TasklistURIs.PROCESSES_URL_V1.concat("/{processDefinitionKey}/start"),
                    bpmnProcessId)
                .content(objectMapper.writeValueAsString(startProcessRequest))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8.name()));

    return result;
  }

  @Nested
  class SearchProcessTests {
    @Test
    public void searchProcessesByProcessIdWithAndWithoutQuery() {
      tasklistProperties.setVersion(TasklistProperties.ALPHA_RELEASES_SUFIX);
      // given
      final String processId1 = ZeebeTestUtil.deployProcess(zeebeClient, "simple_process.bpmn");
      final String processId2 = ZeebeTestUtil.deployProcess(zeebeClient, "simple_process_2.bpmn");
      final String processId3 = ZeebeTestUtil.deployProcess(zeebeClient, "userTaskForm.bpmn");
      final String processId4 =
          ZeebeTestUtil.deployProcess(zeebeClient, "subscribeFormProcess.bpmn");

      databaseTestExtension.processAllRecordsAndWait(processIsDeployedCheck, processId1);
      databaseTestExtension.processAllRecordsAndWait(processIsDeployedCheck, processId2);
      databaseTestExtension.processAllRecordsAndWait(processIsDeployedCheck, processId3);
      databaseTestExtension.processAllRecordsAndWait(processIsDeployedCheck, processId4);

      // when
      final var resultWithQuery =
          mockMvcHelper.doRequest(get(TasklistURIs.PROCESSES_URL_V1).param("query", processId2));

      final var resultWithQueryByStartedByForm =
          mockMvcHelper.doRequest(
              get(TasklistURIs.PROCESSES_URL_V1).param("isStartedByForm", String.valueOf(true)));

      final var resultEmptyQuery = mockMvcHelper.doRequest(get(TasklistURIs.PROCESSES_URL_V1));

      // then
      assertThat(resultWithQuery)
          .hasOkHttpStatus()
          .hasApplicationJsonContentType()
          .extractingListContent(objectMapper, ProcessResponse.class)
          .singleElement()
          .satisfies(
              process -> {
                assertThat(process.getId()).isEqualTo(processId2);
                assertThat(process.getBpmnProcessId()).isEqualTo("testProcess2");
                assertThat(process.getVersion()).isEqualTo(1);
              });

      // test query for start by forms
      assertThat(resultWithQueryByStartedByForm)
          .hasOkHttpStatus()
          .hasApplicationJsonContentType()
          .extractingListContent(objectMapper, ProcessResponse.class)
          .singleElement()
          .satisfies(
              process -> {
                assertThat(process.getId()).isEqualTo(processId4);
                assertThat(process.getBpmnProcessId()).isEqualTo("subscribeFormProcess");
                assertThat(process.getVersion()).isEqualTo(1);
              });

      assertThat(resultEmptyQuery)
          .hasOkHttpStatus()
          .hasApplicationJsonContentType()
          .extractingListContent(objectMapper, ProcessResponse.class)
          .extracting("id", "bpmnProcessId", "version")
          .containsExactlyInAnyOrder(
              tuple(processId1, "Process_1g4wt4m", 1),
              tuple(processId2, "testProcess2", 1),
              tuple(processId3, "userTaskFormProcess", 1),
              tuple(processId4, "subscribeFormProcess", 1));
    }

    @Test
    public void searchProcessesWhenMoreThan10ProcessesAreDeployedThenAllProcessesReturned() {
      tasklistProperties.setVersion(tasklistProperties.ALPHA_RELEASES_SUFIX);
      // given
      final int processesCount = 15;
      for (int i = 0; i < processesCount; i++) {
        tester
            .createAndDeploySimpleProcess("process_" + i, "task_" + i)
            .waitUntil()
            .processIsDeployed();
      }

      // when
      final var result = mockMvcHelper.doRequest(get(TasklistURIs.PROCESSES_URL_V1));

      // then
      assertThat(result)
          .hasOkHttpStatus()
          .hasApplicationJsonContentType()
          .extractingListContent(objectMapper, ProcessResponse.class)
          .extracting("bpmnProcessId", "version")
          .containsExactlyInAnyOrderElementsOf(
              IntStream.range(0, processesCount)
                  .mapToObj(i -> tuple("process_" + i, 1))
                  .collect(Collectors.toSet()));
    }

    @Test
    public void searchProcessesWhenWrongQueryProvidedThenEmptyResultReturned() {
      // given
      final String query = "WRONG QUERY";
      final String processId1 = ZeebeTestUtil.deployProcess(zeebeClient, "simple_process.bpmn");
      final String processId2 = ZeebeTestUtil.deployProcess(zeebeClient, "simple_process_2.bpmn");
      final String processId3 = ZeebeTestUtil.deployProcess(zeebeClient, "userTaskForm.bpmn");

      databaseTestExtension.processAllRecordsAndWait(processIsDeployedCheck, processId1);
      databaseTestExtension.processAllRecordsAndWait(processIsDeployedCheck, processId2);
      databaseTestExtension.processAllRecordsAndWait(processIsDeployedCheck, processId3);

      // when
      final var result =
          mockMvcHelper.doRequest(get(TasklistURIs.PROCESSES_URL_V1).param("query", query));

      // then
      assertThat(result)
          .hasOkHttpStatus()
          .hasApplicationJsonContentType()
          .extractingListContent(objectMapper, ProcessResponse.class)
          .isEmpty();
    }
  }

  @Nested
  class StartAndDeleteProcessInstanceTests {
    @Test
    public void startProcessInstance() throws Exception {
      final var result =
          startProcessDeployInvokeAndReturn("startedByFormProcess.bpmn", "startedByForm");
      assertThat(result)
          .hasHttpStatus(HttpStatus.OK)
          .extractingContent(objectMapper, ProcessInstanceDTO.class)
          .satisfies(
              processInstanceDTO -> {
                Assertions.assertThat(processInstanceDTO.getId()).isNotNull();
              });
    }

    @Test
    public void deleteProcessInstance() {
      final String bpmnProcessId = "testProcess";
      final String flowNodeBpmnId = "taskA";
      final String processInstanceId =
          tester
              .createAndDeploySimpleProcess(bpmnProcessId, flowNodeBpmnId)
              .waitUntil()
              .processIsDeployed()
              .and()
              .startProcessInstance(bpmnProcessId)
              .waitUntil()
              .taskIsCreated(flowNodeBpmnId)
              .claimAndCompleteHumanTask(
                  flowNodeBpmnId,
                  "delete",
                  "\"me\"",
                  "by",
                  "\"REST API\"",
                  "when",
                  "\"processInstance is completed\"")
              .then()
              .waitUntil()
              .processInstanceIsCompleted()
              .getProcessInstanceId();

      // when
      final var result =
          mockMvcHelper.doRequest(
              delete(
                  TasklistURIs.PROCESSES_URL_V1.concat("/{processInstanceId}"), processInstanceId));

      // then
      assertThat(result).hasHttpStatus(HttpStatus.NO_CONTENT).hasNoContent();
    }
  }

  @Nested
  class PublicEndPointTests {
    @Test
    public void shouldReturnPublicEndpointJustForLatestVersions() {
      tasklistProperties.getFeatureFlag().setProcessPublicEndpoints(true);
      // given
      final String processId1 =
          ZeebeTestUtil.deployProcess(zeebeClient, "subscribeFormProcess.bpmn");
      final String processId2 =
          ZeebeTestUtil.deployProcess(zeebeClient, "travelSearchProcess.bpmn");
      final String processId3 =
          ZeebeTestUtil.deployProcess(zeebeClient, "travelSearchProcess_v2.bpmn");

      databaseTestExtension.processAllRecordsAndWait(processIsDeployedCheck, processId1);
      databaseTestExtension.processAllRecordsAndWait(processIsDeployedCheck, processId2);
      databaseTestExtension.processAllRecordsAndWait(processIsDeployedCheck, processId3);

      // when
      final var result =
          mockMvcHelper.doRequest(get(TasklistURIs.PROCESSES_URL_V1.concat("/publicEndpoints")));

      // then
      assertThat(result)
          .hasOkHttpStatus()
          .hasApplicationJsonContentType()
          .extractingListContent(objectMapper, ProcessPublicEndpointsResponse.class)
          .singleElement()
          .satisfies(
              process -> {
                assertThat(process.getProcessDefinitionKey()).isEqualTo(processId1);
                assertThat(process.getEndpoint())
                    .isEqualTo(TasklistURIs.START_PUBLIC_PROCESS.concat("subscribeFormProcess"));
              });
    }

    @Test
    public void shouldNotReturnPublicEndpoints() {
      // given
      final String processId1 = ZeebeTestUtil.deployProcess(zeebeClient, "simple_process.bpmn");
      databaseTestExtension.processAllRecordsAndWait(processIsDeployedCheck, processId1);

      // when
      final var result =
          mockMvcHelper.doRequest(get(TasklistURIs.PROCESSES_URL_V1.concat("/publicEndpoints")));

      // then
      assertThat(result)
          .hasOkHttpStatus()
          .hasApplicationJsonContentType()
          .extractingListContent(objectMapper, ProcessPublicEndpointsResponse.class)
          .isEmpty();
    }

    @Test
    public void shouldNotReturnPublicEndPointsAsFeatureFlagIsFalse() {
      tasklistProperties.getFeatureFlag().setProcessPublicEndpoints(false);
      // given
      final String processId1 =
          ZeebeTestUtil.deployProcess(zeebeClient, "subscribeFormProcess.bpmn");
      final String processId2 =
          ZeebeTestUtil.deployProcess(zeebeClient, "travelSearchProcess.bpmn");
      final String processId3 =
          ZeebeTestUtil.deployProcess(zeebeClient, "travelSearchProcess_v2.bpmn");

      databaseTestExtension.processAllRecordsAndWait(processIsDeployedCheck, processId1);
      databaseTestExtension.processAllRecordsAndWait(processIsDeployedCheck, processId2);
      databaseTestExtension.processAllRecordsAndWait(processIsDeployedCheck, processId3);

      // when
      final var result =
          mockMvcHelper.doRequest(get(TasklistURIs.PROCESSES_URL_V1.concat("/publicEndpoints")));

      // then
      assertThat(result)
          .hasOkHttpStatus()
          .hasApplicationJsonContentType()
          .extractingListContent(objectMapper, ProcessPublicEndpointsResponse.class)
          .isEmpty();
    }

    @Test
    public void shouldReturnPublicEndpointByBpmnProcessId() {
      tasklistProperties.getFeatureFlag().setProcessPublicEndpoints(true);

      final String bpmnProcessId = "subscribeFormProcess";

      // given
      final String processId1 =
          ZeebeTestUtil.deployProcess(zeebeClient, "subscribeFormProcess.bpmn");
      databaseTestExtension.processAllRecordsAndWait(processIsDeployedCheck, processId1);

      // when
      final var result =
          mockMvcHelper.doRequest(
              get(
                  TasklistURIs.PROCESSES_URL_V1.concat("/{bpmnProcessId}/publicEndpoint"),
                  bpmnProcessId));

      // then
      assertThat(result)
          .hasOkHttpStatus()
          .hasApplicationJsonContentType()
          .extractingContent(objectMapper, ProcessPublicEndpointsResponse.class)
          .satisfies(
              process -> {
                assertThat(process.getProcessDefinitionKey()).isEqualTo(processId1);
                assertThat(process.getEndpoint())
                    .isEqualTo(TasklistURIs.START_PUBLIC_PROCESS.concat("subscribeFormProcess"));
              });
    }

    @Test
    public void shouldNotReturnPublicEndpointByBpmnProcessId() {
      tasklistProperties.getFeatureFlag().setProcessPublicEndpoints(true);

      final String bpmnProcessId = "travelSearchProcess";

      // given
      final String processId1 =
          ZeebeTestUtil.deployProcess(zeebeClient, "travelSearchProcess.bpmn");
      final String processId2 =
          ZeebeTestUtil.deployProcess(zeebeClient, "travelSearchProcess_v2.bpmn");
      databaseTestExtension.processAllRecordsAndWait(processIsDeployedCheck, processId1);
      databaseTestExtension.processAllRecordsAndWait(processIsDeployedCheck, processId2);

      // when
      final var result =
          mockMvcHelper.doRequest(
              get(
                  TasklistURIs.PROCESSES_URL_V1.concat("/{bpmnProcessId}/publicEndpoint"),
                  bpmnProcessId));

      // then
      assertThat(result)
          .hasHttpStatus(HttpStatus.NOT_FOUND)
          .hasApplicationProblemJsonContentType()
          .extractingErrorContent(objectMapper)
          .hasStatus(HttpStatus.NOT_FOUND)
          .hasInstanceId()
          .hasMessage("The public endpoint for bpmnProcessId: '%s' is not found", bpmnProcessId);
    }

    @Test
    public void getProcessWithFormWithoutPublic() {
      final String bpmnProcessId = "startedByFormWithoutPublic";

      // given
      final String processId1 =
          ZeebeTestUtil.deployProcess(zeebeClient, "startedByFormProcessWithoutPublic.bpmn");
      databaseTestExtension.processAllRecordsAndWait(processIsDeployedCheck, processId1);

      final var result =
          mockMvcHelper.doRequest(get(TasklistURIs.PROCESSES_URL_V1).param("query", bpmnProcessId));

      assertThat(result)
          .hasOkHttpStatus()
          .hasApplicationJsonContentType()
          .extractingListContent(objectMapper, ProcessResponse.class)
          .singleElement()
          .satisfies(
              process -> {
                assertThat(process.getId()).isEqualTo(processId1);
                assertThat(process.getBpmnProcessId()).isEqualTo("startedByFormWithoutPublic");
                assertThat(process.getStartEventFormId()).isEqualTo("testForm");
                assertThat(process.getVersion()).isEqualTo(1);
              });
    }
  }
}
