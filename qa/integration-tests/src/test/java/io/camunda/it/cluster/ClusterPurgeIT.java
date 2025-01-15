/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.cluster;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.response.SearchQueryResponse;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.zeebe.management.cluster.PlannedOperationsResponse;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractUserTaskBuilder;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
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
    // GIVEN
    final var processModel =
        Bpmn.createExecutableProcess("test-process").startEvent().endEvent().done();
    client = cluster.newClientBuilder().build();
    final var processDefinitionKey = deployProcessModel(processModel);
    final var actuator = ClusterActuator.of(cluster.availableGateway());

    // WHEN
    final var planChangeResponse = actuator.purge(false);

    // THEN
    assertThatChangesAreApplied(planChangeResponse);

    assertThatEntityNotFound(client.newProcessDefinitionGetRequest(processDefinitionKey).send());
  }

  @Test
  void shouldPurgeProcessInstances() {
    // GIVEN
    client = cluster.newClientBuilder().build();
    final var processModel =
        Bpmn.createExecutableProcess("test-process").startEvent().endEvent().done();
    final var processDefinitionKey = deployProcessModel(processModel);
    final var processInstanceKey = startProcess(processDefinitionKey);

    final var actuator = ClusterActuator.of(cluster.availableGateway());

    // WHEN
    final var planChangeResponse = actuator.purge(false);

    // THEN
    assertThatChangesAreApplied(planChangeResponse);

    assertThatEntityNotFound(client.newProcessInstanceGetRequest(processInstanceKey).send());
  }

  @Test
  void shouldPurgeServiceTask() {
    // GIVEN
    client = cluster.newClientBuilder().build();
    final var processModel =
        Bpmn.createExecutableProcess("test-process")
            .startEvent()
            .serviceTask("service-task-1")
            .zeebeJobType("test")
            .endEvent()
            .done();
    final var processDefinitionKey = deployProcessModel(processModel);
    final var processInstanceKey = startProcess(processDefinitionKey);

    final var activeJob =
        client
            .newActivateJobsCommand()
            .jobType("test")
            .maxJobsToActivate(1)
            .send()
            .join()
            .getJobs()
            .getFirst();

    final var actuator = ClusterActuator.of(cluster.availableGateway());

    // WHEN
    final var planChangeResponse = actuator.purge(false);

    // THEN
    assertThatChangesAreApplied(planChangeResponse);

    assertThatJobNotFound(client.newCompleteCommand(activeJob).send());
  }

  @Test
  void shouldPurgeUserTask() throws InterruptedException {
    // GIVEN
    client = cluster.newClientBuilder().build();
    final var processModel =
        Bpmn.createExecutableProcess("test-process")
            .startEvent()
            .userTask("user-task-1", AbstractUserTaskBuilder::zeebeUserTask)
            .endEvent()
            .done();
    final var processDefinitionKey = deployProcessModel(processModel);
    final var processInstanceKey = startProcess(processDefinitionKey);

    Awaitility.await("until user task is active")
        .ignoreExceptions()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              final Future<SearchQueryResponse<UserTask>> userTaskFuture =
                  client.newUserTaskQuery().send();
              Assertions.assertThat(userTaskFuture)
                  .succeedsWithin(Duration.ofSeconds(10))
                  .extracting(SearchQueryResponse::items)
                  .satisfies(items -> Assertions.assertThat(items).hasSize(1));
            });

    final var actuator = ClusterActuator.of(cluster.availableGateway());

    // WHEN
    final var planChangeResponse = actuator.purge(false);

    // THEN
    assertThatChangesAreApplied(planChangeResponse);

    final Future<SearchQueryResponse<UserTask>> userTaskFuture = client.newUserTaskQuery().send();
    Assertions.assertThat(userTaskFuture)
        .succeedsWithin(Duration.ofSeconds(10))
        .extracting(SearchQueryResponse::items)
        .satisfies(items -> Assertions.assertThat(items).isEmpty());
  }

  private long startProcess(final long processDefinitionKey) {
    final var processInstanceKey =
        client
            .newCreateInstanceCommand()
            .processDefinitionKey(processDefinitionKey)
            .send()
            .join()
            .getProcessInstanceKey();
    Awaitility.await("until process instance is created")
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              final Future<?> futureRequest =
                  client.newProcessInstanceGetRequest(processInstanceKey).send();
              Assertions.assertThat(futureRequest).succeedsWithin(Duration.ofSeconds(10));
            });
    return processInstanceKey;
  }

  private void assertThatEntityNotFound(final Future<?> future) {
    Assertions.assertThat(future)
        .failsWithin(Duration.ofSeconds(10))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(ProblemException.class)
        .withMessageContaining("NOT_FOUND");
  }

  private void assertThatJobNotFound(final Future<?> future) {
    Assertions.assertThat(future)
        .failsWithin(Duration.ofSeconds(10))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(StatusRuntimeException.class)
        .withMessageContaining("NOT_FOUND");
  }

  private void assertThatChangesAreApplied(final PlannedOperationsResponse planChangeResponse) {
    Assertions.assertThat(planChangeResponse.getPlannedChanges()).isNotEmpty();

    Awaitility.await("until cluster purge completes")
        .untilAsserted(
            () -> {
              ClusterActuatorAssert.assertThat(cluster).hasCompletedChanges(planChangeResponse);
            });
  }

  /**
   * Deploys a process model and waits until it is accessible via the API.
   *
   * @return the process definition key
   */
  private long deployProcessModel(final BpmnModelInstance processModel) {
    final var deploymentEvent =
        client
            .newDeployResourceCommand()
            .addProcessModel(processModel, "test-process.bpmn")
            .send()
            .join();
    final var processDefinitionKey =
        deploymentEvent.getProcesses().getFirst().getProcessDefinitionKey();
    Awaitility.await("until process model is accessible via API")
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              final Future<?> futureRequest =
                  client.newProcessDefinitionGetRequest(processDefinitionKey).send();
              Assertions.assertThat(futureRequest).succeedsWithin(Duration.ofSeconds(10));
            });
    return processDefinitionKey;
  }
}
