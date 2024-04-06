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
package io.camunda.tasklist.store.opensearch;

import static io.camunda.tasklist.schema.indices.VariableIndex.ID;
import static io.camunda.tasklist.schema.indices.VariableIndex.NAME;
import static io.camunda.tasklist.schema.indices.VariableIndex.SCOPE_FLOW_NODE_ID;
import static io.camunda.tasklist.util.CollectionUtil.isNotEmpty;
import static io.camunda.tasklist.util.OpenSearchUtil.SCROLL_KEEP_ALIVE_MS;
import static io.camunda.tasklist.util.OpenSearchUtil.createSearchRequest;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.entities.FlowNodeInstanceEntity;
import io.camunda.tasklist.entities.TaskVariableEntity;
import io.camunda.tasklist.entities.VariableEntity;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.indices.FlowNodeInstanceIndex;
import io.camunda.tasklist.schema.indices.VariableIndex;
import io.camunda.tasklist.schema.templates.TaskVariableTemplate;
import io.camunda.tasklist.store.VariableStore;
import io.camunda.tasklist.tenant.TenantAwareOpenSearchClient;
import io.camunda.tasklist.util.OpenSearchUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch._types.query_dsl.ConstantScoreQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.*;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.UpdateOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class VariableStoreOpenSearch implements VariableStore {
  private static final Logger LOGGER = LoggerFactory.getLogger(VariableStoreOpenSearch.class);

  @Autowired
  @Qualifier("openSearchClient")
  private OpenSearchClient osClient;

  @Autowired private TenantAwareOpenSearchClient tenantAwareClient;
  @Autowired private FlowNodeInstanceIndex flowNodeInstanceIndex;
  @Autowired private VariableIndex variableIndex;
  @Autowired private TaskVariableTemplate taskVariableTemplate;
  @Autowired private TasklistProperties tasklistProperties;

  public List<VariableEntity> getVariablesByFlowNodeInstanceIds(
      List<String> flowNodeInstanceIds, List<String> varNames, final Set<String> fieldNames) {

    final Query.Builder flowNodeInstanceKeyQ = new Query.Builder();
    flowNodeInstanceKeyQ.terms(
        terms ->
            terms
                .field(SCOPE_FLOW_NODE_ID)
                .terms(
                    t ->
                        t.value(
                            flowNodeInstanceIds.stream()
                                .map(m -> FieldValue.of(m))
                                .collect(toList()))));

    Query.Builder varNamesQ = null;
    if (isNotEmpty(varNames)) {
      varNamesQ = new Query.Builder();
      varNamesQ.terms(
          terms ->
              terms
                  .field(VariableIndex.NAME)
                  .terms(
                      t ->
                          t.value(varNames.stream().map(m -> FieldValue.of(m)).collect(toList()))));
    }
    final Query.Builder query = new Query.Builder();
    query.constantScore(
        new ConstantScoreQuery.Builder()
            .filter(OpenSearchUtil.joinWithAnd(flowNodeInstanceKeyQ, varNamesQ))
            .build());
    final SearchRequest.Builder searchRequest = new SearchRequest.Builder();
    searchRequest.index(variableIndex.getAlias()).query(query.build());
    applyFetchSourceForVariableIndex(searchRequest, fieldNames);

    try {
      return OpenSearchUtil.scroll(searchRequest, VariableEntity.class, osClient);
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining all variables: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  public Map<String, List<TaskVariableEntity>> getTaskVariablesPerTaskId(
      final List<GetVariablesRequest> requests) {

    if (requests == null || requests.size() == 0) {
      return new HashMap<>();
    }
    final Query.Builder taskIdsQ = new Query.Builder();
    final List<String> ids =
        requests.stream().map(GetVariablesRequest::getTaskId).collect(toList());
    taskIdsQ.terms(
        terms ->
            terms
                .field(TaskVariableTemplate.TASK_ID)
                .terms(t -> t.value(ids.stream().map(m -> FieldValue.of(m)).collect(toList()))));

    final List<String> varNames =
        requests.stream()
            .map(GetVariablesRequest::getVarNames)
            .filter(Objects::nonNull)
            .flatMap(List::stream)
            .distinct()
            .collect(toList());
    Query.Builder varNamesQ = null;
    if (isNotEmpty(varNames)) {
      varNamesQ = new Query.Builder();
      varNamesQ.terms(
          terms ->
              terms
                  .field(NAME)
                  .terms(
                      t ->
                          t.value(varNames.stream().map(m -> FieldValue.of(m)).collect(toList()))));
    }

    final SearchRequest.Builder searchRequestBuilder = new SearchRequest.Builder();

    final Query joins = OpenSearchUtil.joinWithAnd(taskIdsQ, varNamesQ);
    searchRequestBuilder.query(q -> q.constantScore(cs -> cs.filter(joins)));
    searchRequestBuilder.index(taskVariableTemplate.getAlias());
    applyFetchSourceForTaskVariableTemplate(
        searchRequestBuilder,
        requests
            .get(0)
            .getFieldNames()); // we assume here that all requests has the same list of fields

    try {
      final List<TaskVariableEntity> entities =
          OpenSearchUtil.scroll(searchRequestBuilder, TaskVariableEntity.class, osClient);
      return entities.stream()
          .collect(
              groupingBy(TaskVariableEntity::getTaskId, mapping(Function.identity(), toList())));
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining all variables: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  @Override
  public Map<String, String> getTaskVariablesIdsWithIndexByTaskIds(List<String> taskIds) {
    final SearchRequest.Builder searchRequest =
        OpenSearchUtil.createSearchRequest(taskVariableTemplate)
            .query(
                q ->
                    q.terms(
                        terms ->
                            terms
                                .field(TaskVariableTemplate.TASK_ID)
                                .terms(
                                    t ->
                                        t.value(
                                            taskIds.stream()
                                                .map(FieldValue::of)
                                                .collect(Collectors.toList())))))
            .fields(f -> f.field(TaskVariableTemplate.ID));

    try {
      return OpenSearchUtil.scrollIdsWithIndexToMap(searchRequest, osClient);
    } catch (IOException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
  }

  public void persistTaskVariables(final Collection<TaskVariableEntity> finalVariables) {
    final BulkRequest.Builder bulkRequest = new BulkRequest.Builder();
    final List<BulkOperation> operations = new ArrayList<BulkOperation>();
    for (TaskVariableEntity variableEntity : finalVariables) {
      operations.add(createUpsertRequest(variableEntity));
    }
    bulkRequest.operations(operations);
    bulkRequest.refresh(Refresh.WaitFor);
    try {
      OpenSearchUtil.processBulkRequest(osClient, bulkRequest.build());
    } catch (PersistenceException ex) {
      throw new TasklistRuntimeException(ex);
    }
  }

  private BulkOperation createUpsertRequest(TaskVariableEntity variableEntity) {
    return new BulkOperation.Builder()
        .update(
            UpdateOperation.of(
                i ->
                    i.index(taskVariableTemplate.getFullQualifiedName())
                        .id(variableEntity.getId())
                        .docAsUpsert(true)
                        .document(CommonUtils.getJsonObjectFromEntity(variableEntity))
                        .retryOnConflict(OpenSearchUtil.UPDATE_RETRY_COUNT)))
        .build();
  }

  public List<FlowNodeInstanceEntity> getFlowNodeInstances(final List<String> processInstanceIds) {

    final SearchRequest.Builder searchRequestBuilder = new SearchRequest.Builder();
    searchRequestBuilder
        .index(flowNodeInstanceIndex.getAlias())
        .query(
            q ->
                q.constantScore(
                    cs ->
                        cs.filter(
                            f ->
                                f.terms(
                                    terms ->
                                        terms
                                            .field(FlowNodeInstanceIndex.PROCESS_INSTANCE_ID)
                                            .terms(
                                                t ->
                                                    t.value(
                                                        processInstanceIds.stream()
                                                            .map(m -> FieldValue.of(m))
                                                            .collect(toList())))))))
        .sort(sort -> sort.field(f -> f.field(FlowNodeInstanceIndex.POSITION).order(SortOrder.Asc)))
        .size(tasklistProperties.getOpenSearch().getBatchSize());

    try {
      return OpenSearchUtil.scroll(searchRequestBuilder, FlowNodeInstanceEntity.class, osClient);
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining all flow nodes: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  public VariableEntity getRuntimeVariable(final String variableId, Set<String> fieldNames) {

    final SearchRequest.Builder request = new SearchRequest.Builder();
    request.index(variableIndex.getAlias()).query(q -> q.ids(ids -> ids.values(variableId)));
    applyFetchSourceForVariableIndex(request, fieldNames);

    try {
      final SearchResponse<VariableEntity> response =
          tenantAwareClient.search(request, VariableEntity.class);
      if (response.hits().total().value() == 1L) {
        return response.hits().hits().get(0).source();
      } else if (response.hits().total().value() > 1L) {
        throw new NotFoundException(
            String.format("Unique variable with id %s was not found", variableId));
      } else {
        throw new NotFoundException(String.format("Variable with id %s was not found", variableId));
      }
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining variable: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  public TaskVariableEntity getTaskVariable(final String variableId, Set<String> fieldNames) {

    final SearchRequest.Builder request = createSearchRequest(taskVariableTemplate);
    request.query(q -> q.ids(ids -> ids.values(variableId)));
    applyFetchSourceForTaskVariableTemplate(request, fieldNames);
    try {
      final SearchResponse<TaskVariableEntity> response =
          tenantAwareClient.search(request, TaskVariableEntity.class);
      if (response.hits().total().value() == 1L) {
        return response.hits().hits().get(0).source();
      } else if (response.hits().total().value() > 1L) {
        throw new NotFoundException(
            String.format("Unique task variable with id %s was not found", variableId));
      } else {
        throw new NotFoundException(
            String.format("Task variable with id %s was not found", variableId));
      }
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining task variable: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  @Override
  public List<String> getProcessInstanceIdsWithMatchingVars(
      List<String> varNames, List<String> varValues) {
    final List<Set<String>> listProcessIdsMatchingVars = new ArrayList<>();

    for (int i = 0; i < varNames.size(); i++) {
      final Query.Builder nameQ = new Query.Builder();
      final int finalI = i;
      nameQ.terms(
          terms ->
              terms
                  .field(VariableIndex.NAME)
                  .terms(
                      t ->
                          t.value(Collections.singletonList(FieldValue.of(varNames.get(finalI))))));

      final Query.Builder valueQ = new Query.Builder();
      valueQ.terms(
          terms ->
              terms
                  .field(VariableIndex.VALUE)
                  .terms(
                      t ->
                          t.value(
                              Collections.singletonList(FieldValue.of(varValues.get(finalI))))));
      final Query boolQuery = OpenSearchUtil.joinWithAnd(nameQ, valueQ);
      final SearchRequest.Builder searchRequestBuilder = new SearchRequest.Builder();
      searchRequestBuilder
          .index(variableIndex.getAlias())
          .query(q -> q.constantScore(cs -> cs.filter(boolQuery)))
          .scroll(timeBuilder -> timeBuilder.time(SCROLL_KEEP_ALIVE_MS));

      final Set<String> processInstanceIds = new HashSet<>();

      try {
        SearchResponse<VariableEntity> response =
            osClient.search(searchRequestBuilder.build(), VariableEntity.class);

        List<String> scrollProcessIds =
            response.hits().hits().stream()
                .map(hit -> hit.source().getProcessInstanceId())
                .collect(Collectors.toList());

        processInstanceIds.addAll(scrollProcessIds);

        final String scrollId = response.scrollId();

        while (!scrollProcessIds.isEmpty()) {
          final ScrollRequest scrollRequest =
              ScrollRequest.of(
                  builder ->
                      builder
                          .scrollId(scrollId)
                          .scroll(new Time.Builder().time(SCROLL_KEEP_ALIVE_MS).build()));

          response = osClient.scroll(scrollRequest, VariableEntity.class);
          scrollProcessIds =
              response.hits().hits().stream()
                  .map(hit -> hit.source().getProcessInstanceId())
                  .collect(Collectors.toList());

          processInstanceIds.addAll(scrollProcessIds);
        }

        OpenSearchUtil.clearScroll(scrollId, osClient);

        listProcessIdsMatchingVars.add(processInstanceIds);

      } catch (IOException e) {
        final String message =
            String.format("Exception occurred while obtaining flowInstanceIds: %s", e.getMessage());
        throw new TasklistRuntimeException(message, e);
      }
    }

    // now find the intersection of all sets
    return new ArrayList<>(
        listProcessIdsMatchingVars.stream()
            .reduce(
                (set1, set2) -> {
                  set1.retainAll(set2);
                  return set1;
                })
            .orElse(Collections.emptySet()));
  }

  private void applyFetchSourceForVariableIndex(
      SearchRequest.Builder searchSourceBuilder, final Set<String> fieldNames) {
    final String[] includesFields;
    if (isNotEmpty(fieldNames)) {
      final Set<String> elsFieldNames = VariableIndex.getElsFieldsByGraphqlFields(fieldNames);
      elsFieldNames.add(ID);
      elsFieldNames.add(NAME);
      elsFieldNames.add(SCOPE_FLOW_NODE_ID);
      includesFields = elsFieldNames.toArray(new String[elsFieldNames.size()]);
      searchSourceBuilder.source(s -> s.filter(f -> f.includes(Arrays.asList(includesFields))));
    }
  }

  private void applyFetchSourceForTaskVariableTemplate(
      SearchRequest.Builder searchRequestBuilder, final Set<String> fieldNames) {
    final String[] includesFields;
    if (isNotEmpty(fieldNames)) {
      final Set<String> elsFieldNames =
          TaskVariableTemplate.getElsFieldsByGraphqlFields(fieldNames);
      elsFieldNames.add(TaskVariableTemplate.ID);
      elsFieldNames.add(TaskVariableTemplate.NAME);
      elsFieldNames.add(TaskVariableTemplate.TASK_ID);
      includesFields = elsFieldNames.toArray(new String[elsFieldNames.size()]);
      searchRequestBuilder.source(s -> s.filter(f -> f.includes(Arrays.asList(includesFields))));
    }
  }
}
