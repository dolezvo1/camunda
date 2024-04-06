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
package io.camunda.tasklist.store.elasticsearch;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.enums.DeletionStatus;
import io.camunda.tasklist.es.RetryElasticsearchClient;
import io.camunda.tasklist.property.MultiTenancyProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.indices.ProcessInstanceDependant;
import io.camunda.tasklist.schema.indices.ProcessInstanceIndex;
import io.camunda.tasklist.schema.templates.TaskVariableTemplate;
import io.camunda.tasklist.store.TaskStore;
import io.camunda.tasklist.tenant.TenantAwareElasticsearchClient;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.apache.lucene.search.TotalHits;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHits;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessInstanceStoreElasticSearchTest {

  @Mock private ProcessInstanceIndex processInstanceIndex;
  @Mock private TaskStore taskStore;
  @Spy private List<ProcessInstanceDependant> processInstanceDependants = Collections.emptyList();
  @Spy private TaskVariableTemplate taskVariableTemplate = new TaskVariableTemplate();
  @Mock private RetryElasticsearchClient retryElasticsearchClient;
  @Mock private TenantAwareElasticsearchClient tenantAwareClient;
  @Mock private TasklistProperties tasklistProperties;
  @Spy private ObjectMapper objectMapper = CommonUtils.OBJECT_MAPPER;

  @InjectMocks private ProcessInstanceStoreElasticSearch instance;

  @BeforeEach
  void setUp() {
    when(processInstanceIndex.getFullQualifiedName()).thenReturn("tasklist-process-instance-x.0.0");
  }

  @Test
  void deleteProcessInstanceWhenMultiTenancyEnabledAndProcessInstanceIsNotAccessible()
      throws IOException {
    // given
    final var processInstanceId = "2220123";
    when(tasklistProperties.getMultiTenancy())
        .thenReturn(new MultiTenancyProperties().setEnabled(true));
    final var searchResponse = mock(SearchResponse.class);
    when(tenantAwareClient.search(any())).thenReturn(searchResponse);
    final var searchHits = mock(SearchHits.class);
    when(searchResponse.getHits()).thenReturn(searchHits);
    when(searchHits.getTotalHits()).thenReturn(new TotalHits(0L, TotalHits.Relation.EQUAL_TO));

    // when
    final var result = instance.deleteProcessInstance(processInstanceId);

    // then
    assertThat(result).isEqualTo(DeletionStatus.NOT_FOUND);
    verifyNoInteractions(
        retryElasticsearchClient,
        objectMapper,
        taskStore,
        taskVariableTemplate,
        processInstanceDependants);
  }
}
