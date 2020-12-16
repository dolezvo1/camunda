/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.persistence.incident.IncidentStatus;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.WithResolvedIncidentsOnlyFilterDataDto;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.List;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.INCIDENTS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.INCIDENT_STATUS;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
public class WithResolvedIncidentsOnlyQueryFilter implements QueryFilter<WithResolvedIncidentsOnlyFilterDataDto> {

  public void addFilters(final BoolQueryBuilder query,
                         final List<WithResolvedIncidentsOnlyFilterDataDto> withResolvedIncidentsOnly,
                         final ZoneId timezone) {
    if (!CollectionUtils.isEmpty(withResolvedIncidentsOnly)) {
      List<QueryBuilder> filters = query.filter();
      final NestedQueryBuilder onlyProcessInstancesWithResolvedIncidents = nestedQuery(
        INCIDENTS,
        boolQuery()
          .must(termQuery(INCIDENTS + "." + INCIDENT_STATUS, IncidentStatus.RESOLVED.getId())),
        ScoreMode.None
      );
      filters.add(onlyProcessInstancesWithResolvedIncidents);
    }
  }

}
