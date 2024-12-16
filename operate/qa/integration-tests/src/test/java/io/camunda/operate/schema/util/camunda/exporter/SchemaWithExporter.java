/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.util.camunda.exporter;

import io.camunda.exporter.CamundaExporter;
import io.camunda.exporter.config.ConnectionTypes;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;

public class SchemaWithExporter {
  private final ExporterConfiguration config = new ExporterConfiguration();

  public SchemaWithExporter(final String prefix, final boolean isElasticsearch) {
    config.getIndex().setPrefix(prefix);
    config
        .getConnect()
        .setType(
            isElasticsearch
                ? ConnectionTypes.ELASTICSEARCH.getType()
                : ConnectionTypes.OPENSEARCH.getType());
  }

  public void createSchema() {
    final var exporter = new CamundaExporter();

    final var context =
        new ExporterTestContext()
            .setConfiguration(
                new ExporterTestConfiguration<>(config.getConnect().getType(), config));

    exporter.configure(context);
    exporter.open(new ExporterTestController());
  }
}
