/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.cluster;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import java.time.Duration;
import java.util.concurrent.Future;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
public class ClusterPurgeIT {
  @TestZeebe
  final TestCluster cluster =
      TestCluster.builder()
          .withBrokersCount(1)
          .withEmbeddedGateway(true)
          .withBrokerConfig(TestStandaloneBroker::withRdbmsExporter)
          .build();

  @AutoClose CamundaClient client;

  @Test
  void shouldPurgeProcessDefinitions() {
    final var processModel =
        Bpmn.createExecutableProcess("test-process").startEvent().endEvent().done();
    final var clientBuilder = cluster.newClientBuilder();
    client = clientBuilder.build();
    final var result =
        client
            .newDeployResourceCommand()
            .addProcessModel(processModel, "test-process.bpmn")
            .send()
            .join();
    final var processDefinitionKey = result.getProcesses().getFirst().getProcessDefinitionKey();
    Awaitility.await("until process model is accessible via API")
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              final Future<?> futureRequest =
                  client.newProcessDefinitionGetRequest(processDefinitionKey).send();
              Assertions.assertThat(futureRequest).succeedsWithin(Duration.ofSeconds(10));
            });
    final var actuator = ClusterActuator.of(cluster.availableGateway());
    final var planChangeResponse = actuator.purge(false);

    Assertions.assertThat(planChangeResponse.getPlannedChanges()).isNotEmpty();

    Awaitility.await("until cluster purge completes")
        .untilAsserted(
            () -> {
              ClusterActuatorAssert.assertThat(cluster).hasCompletedChanges(planChangeResponse);
            });

    final Future<?> futureRequest =
        client.newProcessDefinitionGetRequest(processDefinitionKey).send();
    Assertions.assertThat(futureRequest).failsWithin(Duration.ofSeconds(10));
  }
}
