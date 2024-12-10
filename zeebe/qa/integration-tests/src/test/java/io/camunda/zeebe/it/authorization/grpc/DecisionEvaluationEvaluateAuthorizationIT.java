/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.authorization.grpc;

import static io.camunda.zeebe.it.util.AuthorizationsUtil.createClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.application.Profile;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ClientStatusException;
import io.camunda.zeebe.client.protocol.rest.PermissionTypeEnum;
import io.camunda.zeebe.client.protocol.rest.ResourceTypeEnum;
import io.camunda.zeebe.gateway.impl.configuration.AuthenticationCfg.AuthMode;
import io.camunda.zeebe.it.util.AuthorizationsUtil;
import io.camunda.zeebe.it.util.AuthorizationsUtil.Permissions;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@AutoCloseResources
@Testcontainers
@ZeebeIntegration
public class DecisionEvaluationEvaluateAuthorizationIT {

  @Container
  private static final ElasticsearchContainer CONTAINER =
      TestSearchContainers.createDefeaultElasticsearchContainer();

  private static final String DECISION_ID = "jedi_or_sith";
  private static AuthorizationsUtil authUtil;
  @AutoCloseResource private static ZeebeClient defaultUserClient;

  @TestZeebe(autoStart = false)
  private TestStandaloneBroker broker =
      new TestStandaloneBroker()
          .withRecordingExporter(true)
          .withSecurityConfig(c -> c.getAuthorizations().setEnabled(true))
          .withGatewayConfig(c -> c.getSecurity().getAuthentication().setMode(AuthMode.IDENTITY))
          .withAdditionalProfile(Profile.AUTH_BASIC);

  @BeforeEach
  void beforeEach() {
    broker.withCamundaExporter("http://" + CONTAINER.getHttpHostAddress());
    broker.start();

    final var defaultUsername = "demo";
    defaultUserClient = createClient(broker, defaultUsername);
    final var clientForPermissions = createClient(broker, defaultUsername, "demo");
    authUtil = new AuthorizationsUtil(broker, clientForPermissions, CONTAINER.getHttpHostAddress());

    authUtil.awaitUserExistsInElasticsearch(defaultUsername);
    defaultUserClient
        .newDeployResourceCommand()
        .addResourceFromClasspath("dmn/drg-force-user.dmn")
        .send()
        .join();
  }

  @Test
  void shouldBeAuthorizedToEvaluateDecisionWithDefaultUser() {
    // when
    final var response =
        defaultUserClient
            .newEvaluateDecisionCommand()
            .decisionId(DECISION_ID)
            .variables(Map.of("lightsaberColor", "red"))
            .send()
            .join();

    // then
    assertThat(response.getDecisionOutput()).isEqualTo("\"Sith\"");
  }

  @Test
  void shouldBeAuthorizedToEvaluateDecisionWithUser() {
    // given
    final var username = UUID.randomUUID().toString();
    final var password = "password";
    authUtil.createUserWithPermissions(
        username,
        password,
        new Permissions(
            ResourceTypeEnum.DECISION_DEFINITION,
            PermissionTypeEnum.CREATE_DECISION_INSTANCE,
            List.of(DECISION_ID)));

    try (final var client = authUtil.createClient(username)) {
      // when
      final var response =
          client
              .newEvaluateDecisionCommand()
              .decisionId(DECISION_ID)
              .variables(Map.of("lightsaberColor", "red"))
              .send()
              .join();

      // then
      assertThat(response.getDecisionOutput()).isEqualTo("\"Sith\"");
    }
  }

  @Test
  void shouldBeUnauthorizedToEvaluateDecisionIfNoPermissions() {
    // given
    final var username = UUID.randomUUID().toString();
    final var password = "password";
    authUtil.createUser(username, password);

    try (final var client = authUtil.createClient(username)) {
      // when
      final var response =
          client
              .newEvaluateDecisionCommand()
              .decisionId(DECISION_ID)
              .variables(Map.of("lightsaberColor", "red"))
              .send();

      // then
      assertThatThrownBy(response::join)
          .isInstanceOf(ClientStatusException.class)
          .hasMessageContaining("UNAUTHORIZED")
          .hasMessageContaining(
              "Unauthorized to perform operation 'CREATE_DECISION_INSTANCE' on resource 'DECISION_DEFINITION' with decision id '%s'",
              DECISION_ID);
    }
  }
}
