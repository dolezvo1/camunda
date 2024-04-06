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
package io.camunda.tasklist.qa.backup;

import static io.camunda.tasklist.qa.backup.BackupRestoreTest.BACKUP_ID;
import static io.camunda.tasklist.qa.backup.BackupRestoreTest.ZEEBE_INDEX_PREFIX;
import static io.camunda.tasklist.webapp.management.dto.BackupStateDto.COMPLETED;
import static io.camunda.tasklist.webapp.management.dto.BackupStateDto.IN_PROGRESS;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphql.spring.boot.test.GraphQLResponse;
import com.graphql.spring.boot.test.GraphQLTestTemplate;
import com.jayway.jsonpath.PathNotFoundException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.qa.util.TestContext;
import io.camunda.tasklist.qa.util.rest.StatefulRestTemplate;
import io.camunda.tasklist.webapp.api.rest.v1.entities.SaveVariablesRequest;
import io.camunda.tasklist.webapp.graphql.entity.TaskDTO;
import io.camunda.tasklist.webapp.management.dto.GetBackupStateResponseDto;
import io.camunda.tasklist.webapp.management.dto.TakeBackupRequestDto;
import io.camunda.tasklist.webapp.management.dto.TakeBackupResponseDto;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

@Component
@Configuration
@EnableRetry
public class TasklistAPICaller {

  private static final Logger LOGGER = LoggerFactory.getLogger(TasklistAPICaller.class);
  private static final String COMPLETE_TASK_MUTATION_PATTERN =
      "mutation {completeTask(taskId: \"%s\", variables: [%s]){id name assignee taskState completionTime}}";
  private static final String USERNAME = "demo";
  private static final String PASSWORD = "demo";

  @Autowired private ObjectMapper objectMapper;

  @Autowired private ResourceLoader resourceLoader;

  @Autowired private BiFunction<String, Integer, StatefulRestTemplate> statefulRestTemplateFactory;

  private GraphQLTestTemplate graphQLTestTemplate;
  private StatefulRestTemplate statefulRestTemplate;

  public GraphQLResponse getAllTasks() throws IOException {
    final GraphQLResponse graphQLResponse =
        graphQLTestTemplate.postForResource("get-all-tasks.graphql");
    try {
      final Object errors = graphQLResponse.getRaw("$.errors");
      if (errors != null && ((List) errors).size() > 0) {
        throw new TasklistRuntimeException("Error occurred when getting the tasks: " + errors);
      }
    } catch (PathNotFoundException ex) {
      // ignore
    }
    return graphQLResponse;
  }

  public List<TaskDTO> getTasks(String taskBpmnId) throws IOException {
    final ObjectNode query = objectMapper.createObjectNode();
    query.putObject("query").put("taskDefinitionId", taskBpmnId);

    final GraphQLResponse graphQLResponse =
        graphQLTestTemplate.perform("get-tasks-by-query.graphql", query, new ArrayList<>());
    return graphQLResponse.getList("$.data.tasks", TaskDTO.class);
  }

  public void completeTask(String id, String variablesJson) {
    final GraphQLResponse response =
        graphQLTestTemplate.postMultipart(
            String.format(COMPLETE_TASK_MUTATION_PATTERN, id, variablesJson), "{}");
    assertThat(response.isOk()).isTrue();
  }

  public List<TaskDTO> getTasksByPath(GraphQLResponse graphQLResponse, String path) {
    return graphQLResponse.getList(path, TaskDTO.class);
  }

  public GraphQLTestTemplate createGraphQLTestTemplate(TestContext testContext) {
    final RestTemplateBuilder restTemplateBuilder = new RestTemplateBuilder();
    final TestRestTemplate testRestTemplate = new TestRestTemplate(restTemplateBuilder);
    final Field restTemplateField;
    try {
      statefulRestTemplate =
          statefulRestTemplateFactory.apply(
              testContext.getExternalTasklistHost(), testContext.getExternalTasklistPort());
      statefulRestTemplate.loginWhenNeeded(USERNAME, PASSWORD);
      restTemplateField = testRestTemplate.getClass().getDeclaredField("restTemplate");
      restTemplateField.setAccessible(true);
      restTemplateField.set(testRestTemplate, statefulRestTemplate);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
    graphQLTestTemplate =
        new GraphQLTestTemplate(
            resourceLoader,
            testRestTemplate,
            String.format(
                "http://%s:%s/graphql",
                testContext.getExternalTasklistHost(), testContext.getExternalTasklistPort()),
            objectMapper);
    return graphQLTestTemplate;
  }

  public void saveDraftTaskVariables(String taskId, SaveVariablesRequest saveVariablesRequest) {
    final var response =
        statefulRestTemplate.postForEntity(
            statefulRestTemplate.getURL(String.format("v1/tasks/%s/variables", taskId)),
            saveVariablesRequest,
            Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  public TakeBackupResponseDto backup(Long backupId) {
    final TakeBackupRequestDto takeBackupRequest = new TakeBackupRequestDto().setBackupId(backupId);
    return statefulRestTemplate.postForObject(
        statefulRestTemplate.getURL("actuator/backups"),
        takeBackupRequest,
        TakeBackupResponseDto.class);
  }

  public GetBackupStateResponseDto getBackupState(Long backupId) {
    return statefulRestTemplate.getForObject(
        statefulRestTemplate.getURL("actuator/backups/" + backupId),
        GetBackupStateResponseDto.class);
  }

  @Retryable(
      retryFor = {TasklistRuntimeException.class},
      maxAttempts = 10,
      backoff = @Backoff(delay = 2000))
  public void checkIndicesAreDeleted(RestHighLevelClient esClient) throws IOException {
    final int count =
        esClient
            .indices()
            .get(new GetIndexRequest("tasklist*", ZEEBE_INDEX_PREFIX + "*"), RequestOptions.DEFAULT)
            .getIndices()
            .length;
    if (count > 0) {
      LOGGER.warn("ElasticSearch indices are not yet removed.");
      throw new TasklistRuntimeException("Indices are not yet deleted.");
    }
  }

  @Retryable(
      retryFor = {TasklistRuntimeException.class},
      maxAttempts = 10,
      backoff = @Backoff(delay = 2000))
  public void checkIndicesAreDeleted(OpenSearchClient osClient) throws IOException {
    final int count =
        osClient
            .indices()
            .get(gir -> gir.index("tasklist*", ZEEBE_INDEX_PREFIX + "*"))
            .result()
            .size();
    if (count > 0) {
      LOGGER.warn("OpenSearch indices are not yet removed.");
      throw new TasklistRuntimeException("Indices are not yet deleted.");
    }
  }

  @Bean
  public ObjectMapper objectMapper() {
    return Jackson2ObjectMapperBuilder.json()
        .featuresToDisable(
            SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
            DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE,
            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .featuresToEnable(JsonParser.Feature.ALLOW_COMMENTS, SerializationFeature.INDENT_OUTPUT)
        .build();
  }

  @Retryable(
      value = {AssertionError.class, HttpClientErrorException.NotFound.class},
      maxAttempts = 10,
      backoff = @Backoff(delay = 2000))
  public void assertBackupState() {
    try {
      final GetBackupStateResponseDto backupState = getBackupState(BACKUP_ID);
      assertThat(backupState.getState()).isIn(IN_PROGRESS, COMPLETED);
      // to retry
      assertThat(backupState.getState()).isEqualTo(COMPLETED);
    } catch (AssertionError | HttpClientErrorException.NotFound er) {
      LOGGER.warn("Error when checking backup state: " + er.getMessage());
      throw er;
    }
  }
}
