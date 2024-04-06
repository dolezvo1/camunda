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
package io.camunda.tasklist.webapp.security;

import static io.camunda.tasklist.schema.indices.MetricIndex.VALUE;

import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.indices.MetricIndex;
import java.io.IOException;
import java.util.Collections;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class AssigneeMigratorElasticSearch implements AssigneeMigrator {

  private static final Logger LOGGER = LoggerFactory.getLogger(AssigneeMigratorElasticSearch.class);
  @Autowired private RestHighLevelClient esClient;

  @Autowired private MetricIndex metricIndex;
  @Autowired private TasklistProperties tasklistProperties;

  @Override
  public void migrateUsageMetrics(String newAssignee) {
    if (!tasklistProperties.isFixUsernames()) {
      LOGGER.debug("Migration of usernames is disabled.");
      return;
    }
    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof OldUsernameAware)) {
      LOGGER.debug("No migration of usernames possible.");
      return;
    }
    final OldUsernameAware oldUsernameAware = (OldUsernameAware) authentication;
    final String oldAssignee = oldUsernameAware.getOldName();
    LOGGER.debug("Migrate old assignee {} to new assignee {}", oldAssignee, newAssignee);
    final QueryBuilder oldAssigneeQuery = QueryBuilders.termsQuery(VALUE, oldAssignee);
    final Script updateScript =
        new Script(
            ScriptType.INLINE,
            "painless",
            "ctx._source." + VALUE + " = '" + newAssignee + "'",
            Collections.emptyMap());
    final long migrated =
        updateByQuery(metricIndex.getFullQualifiedName(), oldAssigneeQuery, updateScript);
    if (migrated > 0) {
      LOGGER.info(
          "Migrated {} usage metric entries from old assignee {} to new assignee {}.",
          migrated,
          oldAssignee,
          newAssignee);
    }
  }

  public long updateByQuery(
      final String indexPattern, final QueryBuilder query, final Script updateScript) {
    try {
      final UpdateByQueryRequest request =
          new UpdateByQueryRequest(indexPattern).setQuery(query).setScript(updateScript);
      final BulkByScrollResponse response = esClient.updateByQuery(request, RequestOptions.DEFAULT);
      return response.getTotal();
    } catch (IOException e) {
      throw new TasklistRuntimeException(
          "Error while trying to update entities for query " + query);
    }
  }
}
