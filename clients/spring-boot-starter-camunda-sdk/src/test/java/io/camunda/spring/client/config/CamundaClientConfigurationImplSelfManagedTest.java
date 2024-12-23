/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.config;

import static io.camunda.spring.client.configuration.CamundaClientConfigurationImpl.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.impl.oauth.OAuthCredentialsProvider;
import io.camunda.spring.client.configuration.CamundaClientAllAutoConfiguration;
import io.camunda.spring.client.configuration.CamundaClientProdAutoConfiguration;
import io.camunda.spring.client.jobhandling.CamundaClientExecutorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.OutputCaptureExtension;

@SpringBootTest(
    classes = {CamundaClientAllAutoConfiguration.class, CamundaClientProdAutoConfiguration.class},
    properties = {
      "camunda.client.mode=self-managed",
      "camunda.client.auth.client-id=my-client-id",
      "camunda.client.auth.client-secret=my-client-secret"
    })
@ExtendWith(OutputCaptureExtension.class)
public class CamundaClientConfigurationImplSelfManagedTest {
  @Autowired CamundaClientConfiguration camundaClientConfiguration;
  @Autowired JsonMapper jsonMapper;
  @Autowired CamundaClientExecutorService zeebeClientExecutorService;

  @Test
  void shouldContainsZeebeClientConfiguration() {
    assertThat(camundaClientConfiguration).isNotNull();
  }

  @Test
  void shouldNotHaveCredentialsProvider() {
    assertThat(camundaClientConfiguration.getCredentialsProvider())
        .isInstanceOf(OAuthCredentialsProvider.class);
  }

  @Test
  void shouldHaveGatewayAddress() {
    assertThat(camundaClientConfiguration.getGatewayAddress()).isEqualTo("localhost:26500");
  }

  @Test
  void shouldHaveDefaultTenantId() {
    assertThat(camundaClientConfiguration.getDefaultTenantId())
        .isEqualTo(DEFAULT.getDefaultTenantId());
  }

  @Test
  void shouldHaveDefaultJobWorkerTenantIds() {
    assertThat(camundaClientConfiguration.getDefaultJobWorkerTenantIds())
        .isEqualTo(DEFAULT.getDefaultJobWorkerTenantIds());
  }

  @Test
  void shouldHaveNumJobWorkerExecutionThreads() {
    assertThat(camundaClientConfiguration.getNumJobWorkerExecutionThreads())
        .isEqualTo(DEFAULT.getNumJobWorkerExecutionThreads());
  }

  @Test
  void shouldHaveDefaultJobWorkerMaxJobsActive() {
    assertThat(camundaClientConfiguration.getDefaultJobWorkerMaxJobsActive())
        .isEqualTo(DEFAULT.getDefaultJobWorkerMaxJobsActive());
  }

  @Test
  void shouldHaveDefaultJobWorkerName() {
    assertThat(camundaClientConfiguration.getDefaultJobWorkerName())
        .isEqualTo(DEFAULT.getDefaultJobWorkerName());
  }

  @Test
  void shouldHaveDefaultJobTimeout() {
    assertThat(camundaClientConfiguration.getDefaultJobTimeout())
        .isEqualTo(DEFAULT.getDefaultJobTimeout());
  }

  @Test
  void shouldHaveDefaultJobPollInterval() {
    assertThat(camundaClientConfiguration.getDefaultJobPollInterval())
        .isEqualTo(DEFAULT.getDefaultJobPollInterval());
  }

  @Test
  void shouldHaveDefaultMessageTimeToLive() {
    assertThat(camundaClientConfiguration.getDefaultMessageTimeToLive())
        .isEqualTo(DEFAULT.getDefaultMessageTimeToLive());
  }

  @Test
  void shouldHaveDefaultRequestTimeout() {
    assertThat(camundaClientConfiguration.getDefaultRequestTimeout())
        .isEqualTo(DEFAULT.getDefaultRequestTimeout());
  }

  @Test
  void shouldHavePlaintextConnectionEnabled() {
    assertThat(camundaClientConfiguration.isPlaintextConnectionEnabled()).isEqualTo(true);
  }

  @Test
  void shouldHaveCaCertificatePath() {
    assertThat(camundaClientConfiguration.getCaCertificatePath())
        .isEqualTo(DEFAULT.getCaCertificatePath());
  }

  @Test
  void shouldHaveKeepAlive() {
    assertThat(camundaClientConfiguration.getKeepAlive()).isEqualTo(DEFAULT.getKeepAlive());
  }

  @Test
  void shouldNotHaveClientInterceptors() {
    assertThat(camundaClientConfiguration.getInterceptors()).isEmpty();
  }

  @Test
  void shouldNotHaveAsyncClientChainHandlers() {
    assertThat(camundaClientConfiguration.getChainHandlers()).isEmpty();
  }

  @Test
  void shouldHaveJsonMapper() {
    assertThat(camundaClientConfiguration.getJsonMapper()).isEqualTo(jsonMapper);
  }

  @Test
  void shouldHaveOverrideAuthority() {
    assertThat(camundaClientConfiguration.getOverrideAuthority())
        .isEqualTo(DEFAULT.getOverrideAuthority());
  }

  @Test
  void shouldHaveMaxMessageSize() {
    assertThat(camundaClientConfiguration.getMaxMessageSize())
        .isEqualTo(DEFAULT.getMaxMessageSize());
  }

  @Test
  void shouldHaveMaxMetadataSize() {
    assertThat(camundaClientConfiguration.getMaxMetadataSize())
        .isEqualTo(DEFAULT.getMaxMetadataSize());
  }

  @Test
  void shouldHaveJobWorkerExecutor() {
    assertThat(camundaClientConfiguration.jobWorkerExecutor())
        .isEqualTo(zeebeClientExecutorService.get());
  }

  @Test
  void shouldHaveOwnsJobWorkerExecutor() {
    assertThat(camundaClientConfiguration.ownsJobWorkerExecutor()).isEqualTo(true);
  }

  @Test
  void shouldHaveDefaultJobWorkerStreamEnabled() {
    assertThat(camundaClientConfiguration.getDefaultJobWorkerStreamEnabled())
        .isEqualTo(DEFAULT.getDefaultJobWorkerStreamEnabled());
  }

  @Test
  void shouldHaveDefaultRetryPolicy() {
    assertThat(camundaClientConfiguration.useDefaultRetryPolicy())
        .isEqualTo(DEFAULT.useDefaultRetryPolicy());
  }

  @Test
  void shouldHaveDefaultPreferRestOverGrpc() {
    assertThat(camundaClientConfiguration.preferRestOverGrpc())
        .isEqualTo(DEFAULT.preferRestOverGrpc());
  }
}
