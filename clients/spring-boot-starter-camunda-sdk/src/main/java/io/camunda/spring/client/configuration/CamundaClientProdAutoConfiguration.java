/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.spring.client.configuration;

import static io.camunda.spring.client.configuration.PropertyUtil.getProperty;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.CredentialsProvider;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.impl.CamundaClientImpl;
import io.camunda.client.impl.NoopCredentialsProvider;
import io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.camunda.client.impl.util.Environment;
import io.camunda.client.impl.util.ExecutorResource;
import io.camunda.spring.client.jobhandling.CamundaClientExecutorService;
import io.camunda.spring.client.properties.CamundaClientProperties;
import io.camunda.spring.client.properties.ZeebeClientConfigurationProperties;
import io.camunda.spring.client.testsupport.CamundaSpringProcessTestContext;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/*
 * All configurations that will only be used in production code - meaning NO TEST cases
 */
@ConditionalOnProperty(
    prefix = "camunda.client",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@ConditionalOnMissingBean(CamundaSpringProcessTestContext.class)
@ImportAutoConfiguration({
  ExecutorServiceConfiguration.class,
  CamundaActuatorConfiguration.class,
  JsonMapperConfiguration.class,
})
@AutoConfigureBefore(CamundaClientAllAutoConfiguration.class)
public class CamundaClientProdAutoConfiguration {

  private static final Logger LOG =
      LoggerFactory.getLogger(CamundaClientProdAutoConfiguration.class);

  @Bean
  @ConditionalOnMissingBean
  public CredentialsProvider camundaClientCredentialsProvider(
      final ZeebeClientConfigurationProperties properties,
      final CamundaClientProperties camundaClientProperties) {
    final OAuthCredentialsProviderBuilder credBuilder =
        CredentialsProvider.newCredentialsProviderBuilder()
            .applyEnvironmentOverrides(false)
            .clientId(
                getProperty(
                    "credentialsProvider.clientId",
                    null,
                    null,
                    () -> camundaClientProperties.getAuth().getClientId(),
                    () -> properties.getCloud().getClientId(),
                    () -> Environment.system().get("ZEEBE_CLIENT_ID")))
            .clientSecret(
                getProperty(
                    "credentialsProvider.clientSecret",
                    null,
                    null,
                    () -> camundaClientProperties.getAuth().getClientSecret(),
                    () -> properties.getCloud().getClientSecret(),
                    () -> Environment.system().get("ZEEBE_CLIENT_SECRET")))
            .audience(
                getProperty(
                    "credentialProvider.audience",
                    null,
                    null,
                    () -> camundaClientProperties.getAuth().getAudience(),
                    () -> camundaClientProperties.getZeebe().getAudience(),
                    () -> properties.getCloud().getAudience()))
            .scope(
                getProperty(
                    "credentialsProvider.scope",
                    null,
                    null,
                    () -> camundaClientProperties.getAuth().getScope(),
                    () -> camundaClientProperties.getZeebe().getScope(),
                    () -> properties.getCloud().getScope()))
            .authorizationServerUrl(
                getProperty(
                    "credentialsProvider.authorizationServerUrl",
                    null,
                    null,
                    () -> camundaClientProperties.getAuth().getIssuer().toString(),
                    () -> properties.getCloud().getAuthUrl()))
            .credentialsCachePath(
                getProperty(
                    "credentialsProvider.credentialsCachePath",
                    null,
                    null,
                    () -> camundaClientProperties.getAuth().getCredentialsCachePath(),
                    () -> properties.getCloud().getCredentialsCachePath()))
            .connectTimeout(
                getProperty(
                    "credentialsProvider.connectTimeout",
                    null,
                    null,
                    () -> camundaClientProperties.getAuth().getConnectTimeout()))
            .readTimeout(
                getProperty(
                    "credentialsProvider.readTimeout",
                    null,
                    null,
                    () -> camundaClientProperties.getAuth().getReadTimeout()));

    maybeConfigureIdentityProviderSSLConfig(credBuilder, camundaClientProperties);
    try {
      final CredentialsProvider credProvider = credBuilder.build();
      return credProvider;
    } catch (final Exception e) {
      LOG.warn("Failed to configure credential provider", e);
      return new NoopCredentialsProvider();
    }
  }

  private void maybeConfigureIdentityProviderSSLConfig(
      final OAuthCredentialsProviderBuilder builder,
      final CamundaClientProperties camundaClientProperties) {
    if (camundaClientProperties.getAuth() == null) {
      return;
    }
    if (camundaClientProperties.getAuth().getKeystorePath() != null) {
      final Path keyStore = Paths.get(camundaClientProperties.getAuth().getKeystorePath());
      if (Files.exists(keyStore)) {
        builder.keystorePath(keyStore);
        builder.keystorePassword(camundaClientProperties.getAuth().getKeystorePassword());
        builder.keystoreKeyPassword(camundaClientProperties.getAuth().getKeystoreKeyPassword());
      }
    }

    if (camundaClientProperties.getAuth().getTruststorePath() != null) {
      final Path trustStore = Paths.get(camundaClientProperties.getAuth().getTruststorePath());
      if (Files.exists(trustStore)) {
        builder.truststorePath(trustStore);
        builder.truststorePassword(camundaClientProperties.getAuth().getTruststorePassword());
      }
    }
  }

  @Bean
  public CamundaClientConfiguration camundaClientConfiguration(
      final ZeebeClientConfigurationProperties properties,
      final CamundaClientProperties camundaClientProperties,
      final JsonMapper jsonMapper,
      final List<ClientInterceptor> interceptors,
      final List<AsyncExecChainHandler> chainHandlers,
      final CamundaClientExecutorService camundaClientExecutorService,
      final CredentialsProvider camundaClientCredentialsProvider) {
    return new CamundaClientConfigurationImpl(
        properties,
        camundaClientProperties,
        jsonMapper,
        interceptors,
        chainHandlers,
        camundaClientExecutorService,
        camundaClientCredentialsProvider) {};
  }

  @Bean(destroyMethod = "close")
  public CamundaClient camundaClient(final CamundaClientConfiguration configuration) {
    LOG.info("Creating camundaClient using CamundaClientConfiguration [{}]", configuration);
    final ScheduledExecutorService jobWorkerExecutor = configuration.jobWorkerExecutor();
    if (jobWorkerExecutor != null) {
      final ManagedChannel managedChannel = CamundaClientImpl.buildChannel(configuration);
      final GatewayGrpc.GatewayStub gatewayStub =
          CamundaClientImpl.buildGatewayStub(managedChannel, configuration);
      final ExecutorResource executorResource =
          new ExecutorResource(jobWorkerExecutor, configuration.ownsJobWorkerExecutor());
      return new CamundaClientImpl(configuration, managedChannel, gatewayStub, executorResource);
    } else {
      return new CamundaClientImpl(configuration);
    }
  }
}
