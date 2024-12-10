/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.longTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.webapps.schema.descriptors.usermanagement.index.UserIndex.EMAIL;
import static io.camunda.webapps.schema.descriptors.usermanagement.index.UserIndex.KEY;
import static io.camunda.webapps.schema.descriptors.usermanagement.index.UserIndex.NAME;
import static io.camunda.webapps.schema.descriptors.usermanagement.index.UserIndex.USERNAME;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.UserFilter;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public class UserFilterTransformer extends IndexFilterTransformer<UserFilter> {

  public UserFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final UserFilter filter) {

    return and(
        filter.keys() == null ? null : longTerms(KEY, filter.keys()),
        filter.username() == null ? null : term(USERNAME, filter.username()),
        filter.email() == null ? null : term(EMAIL, filter.email()),
        filter.name() == null ? null : term(NAME, filter.name()));
  }
}
