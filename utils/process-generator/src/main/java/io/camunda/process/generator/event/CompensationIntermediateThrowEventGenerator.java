/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.generator.event;

import io.camunda.process.generator.GeneratorContext;
import io.camunda.zeebe.model.bpmn.builder.AbstractCatchEventBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CompensationIntermediateThrowEventGenerator implements BpmnCatchEventGenerator {

  private static final Logger LOG = LoggerFactory.getLogger(
      CompensationIntermediateThrowEventGenerator.class);

  @Override
  public void addEventDefinition(final String elementId,
      final AbstractCatchEventBuilder<?, ?> catchEventBuilder,
      final GeneratorContext generatorContext, final boolean generateExecutionPath) {

    LOG.debug("Turning catch event with id {} into a compensation catch event", elementId);

    final var compensationThrowEventId = "compensation_throw_" + generatorContext.createNewId();

     catchEventBuilder
        .intermediateThrowEvent(compensationThrowEventId)
        .compensateEventDefinition()
        .compensateEventDefinitionDone();
  }
}
