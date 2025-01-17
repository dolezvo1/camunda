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
package io.camunda.zeebe.spring.client.configuration;

import static io.camunda.zeebe.spring.client.configuration.PropertyUtil.*;
import static io.camunda.zeebe.spring.client.properties.ZeebeClientConfigurationProperties.*;
import static org.springframework.util.StringUtils.hasText;

import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.CredentialsProvider;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.impl.NoopCredentialsProvider;
import io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.camunda.client.impl.util.Environment;
import io.camunda.zeebe.spring.client.jobhandling.ZeebeClientExecutorService;
import io.camunda.zeebe.spring.client.properties.CamundaClientProperties;
import io.camunda.zeebe.spring.client.properties.CamundaClientProperties.ClientMode;
import io.camunda.zeebe.spring.client.properties.PropertiesUtil;
import io.camunda.zeebe.spring.client.properties.ZeebeClientConfigurationProperties;
import io.grpc.ClientInterceptor;
import jakarta.annotation.PostConstruct;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class CamundaClientConfigurationImpl implements CamundaClientConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(CamundaClientConfigurationImpl.class);
  private final Map<String, Object> configCache = new HashMap<>();
  private final ZeebeClientConfigurationProperties properties;
  private final CamundaClientProperties camundaClientProperties;
  private final JsonMapper jsonMapper;
  private final List<ClientInterceptor> interceptors;
  private final List<AsyncExecChainHandler> chainHandlers;
  private final ZeebeClientExecutorService zeebeClientExecutorService;

  @Autowired
  public CamundaClientConfigurationImpl(
      final ZeebeClientConfigurationProperties properties,
      final CamundaClientProperties camundaClientProperties,
      final JsonMapper jsonMapper,
      final List<ClientInterceptor> interceptors,
      final List<AsyncExecChainHandler> chainHandlers,
      final ZeebeClientExecutorService zeebeClientExecutorService) {
    this.properties = properties;
    this.camundaClientProperties = camundaClientProperties;
    this.jsonMapper = jsonMapper;
    this.interceptors = interceptors;
    this.chainHandlers = chainHandlers;
    this.zeebeClientExecutorService = zeebeClientExecutorService;
  }

  @PostConstruct
  public void applyLegacy() {
    // make sure environment variables and other legacy config options are taken into account
    // (duplicate, also done by  qPostConstruct, whatever)
    properties.applyOverrides();
  }

  @Override
  public String getGatewayAddress() {
    return getLegacyOrLatestOrDefault(
        "GatewayAddress",
        () -> PropertiesUtil.getZeebeGatewayAddress(properties),
        this::composeGatewayAddress,
        DEFAULT.getGatewayAddress(),
        configCache);
  }

  @Override
  public URI getRestAddress() {
    return getLegacyOrLatestOrDefault(
        "RestAddress",
        () -> properties.getBroker().getRestAddress(),
        () -> camundaClientProperties.getZeebe().getRestAddress(),
        DEFAULT.getRestAddress(),
        configCache);
  }

  @Override
  public URI getGrpcAddress() {
    return getLegacyOrLatestOrDefault(
        "GrpcAddress",
        properties::getGrpcAddress,
        () -> camundaClientProperties.getZeebe().getGrpcAddress(),
        DEFAULT.getGrpcAddress(),
        configCache);
  }

  @Override
  public String getDefaultTenantId() {
    return getLegacyOrLatestOrDefault(
        "DefaultTenantId",
        properties::getDefaultTenantId,
        prioritized(
            DEFAULT.getDefaultTenantId(),
            List.of(
                () -> camundaClientProperties.getTenantIds().get(0),
                () -> camundaClientProperties.getZeebe().getDefaults().getTenantIds().get(0))),
        DEFAULT.getDefaultTenantId(),
        configCache);
  }

  @Override
  public List<String> getDefaultJobWorkerTenantIds() {
    return getLegacyOrLatestOrDefault(
        "DefaultJobWorkerTenantIds",
        properties::getDefaultJobWorkerTenantIds,
        prioritized(
            DEFAULT.getDefaultJobWorkerTenantIds(),
            List.of(
                camundaClientProperties::getTenantIds,
                () -> camundaClientProperties.getZeebe().getDefaults().getTenantIds())),
        DEFAULT.getDefaultJobWorkerTenantIds(),
        configCache);
  }

  @Override
  public int getNumJobWorkerExecutionThreads() {
    return getLegacyOrLatestOrDefault(
        "NumJobWorkerExecutionThreads",
        () -> properties.getWorker().getThreads(),
        () -> camundaClientProperties.getZeebe().getExecutionThreads(),
        DEFAULT.getNumJobWorkerExecutionThreads(),
        configCache);
  }

  @Override
  public int getDefaultJobWorkerMaxJobsActive() {
    return getLegacyOrLatestOrDefault(
        "DefaultJobWorkerMaxJobsActive",
        () -> properties.getWorker().getMaxJobsActive(),
        () -> camundaClientProperties.getZeebe().getDefaults().getMaxJobsActive(),
        DEFAULT.getDefaultJobWorkerMaxJobsActive(),
        configCache);
  }

  @Override
  public String getDefaultJobWorkerName() {
    return getLegacyOrLatestOrDefault(
        "DefaultJobWorkerName",
        () -> properties.getWorker().getDefaultName(),
        () -> camundaClientProperties.getZeebe().getDefaults().getName(),
        DEFAULT.getDefaultJobWorkerName(),
        configCache);
  }

  @Override
  public Duration getDefaultJobTimeout() {
    return getLegacyOrLatestOrDefault(
        "DefaultJobTimeout",
        () -> properties.getJob().getTimeout(),
        () -> camundaClientProperties.getZeebe().getDefaults().getTimeout(),
        DEFAULT.getDefaultJobTimeout(),
        configCache);
  }

  @Override
  public Duration getDefaultJobPollInterval() {
    return getLegacyOrLatestOrDefault(
        "DefaultJobPollInterval",
        () -> properties.getJob().getPollInterval(),
        () -> camundaClientProperties.getZeebe().getDefaults().getPollInterval(),
        DEFAULT.getDefaultJobPollInterval(),
        configCache);
  }

  @Override
  public Duration getDefaultMessageTimeToLive() {
    return getLegacyOrLatestOrDefault(
        "DefaultMessageTimeToLive",
        () -> properties.getMessage().getTimeToLive(),
        () -> camundaClientProperties.getZeebe().getMessageTimeToLive(),
        DEFAULT.getDefaultMessageTimeToLive(),
        configCache);
  }

  @Override
  public Duration getDefaultRequestTimeout() {
    return getLegacyOrLatestOrDefault(
        "DefaultRequestTimeout",
        properties::getRequestTimeout,
        prioritized(
            DEFAULT.getDefaultRequestTimeout(),
            List.of(
                () -> camundaClientProperties.getZeebe().getRequestTimeout(),
                () -> camundaClientProperties.getZeebe().getDefaults().getRequestTimeout())),
        DEFAULT.getDefaultRequestTimeout(),
        configCache);
  }

  @Override
  public boolean isPlaintextConnectionEnabled() {
    return getLegacyOrLatestOrDefault(
        "PlaintextConnectionEnabled",
        () -> properties.getSecurity().isPlaintext(),
        this::composePlaintext,
        DEFAULT.isPlaintextConnectionEnabled(),
        configCache);
  }

  @Override
  public String getCaCertificatePath() {
    return getLegacyOrLatestOrDefault(
        "CaCertificatePath",
        () -> properties.getSecurity().getCertPath(),
        () -> camundaClientProperties.getZeebe().getCaCertificatePath(),
        DEFAULT.getCaCertificatePath(),
        configCache);
  }

  @Override
  public CredentialsProvider getCredentialsProvider() {
    return getLegacyOrLatestOrDefault(
        "CredentialsProvider",
        this::legacyCredentialsProvider,
        this::credentialsProvider,
        null,
        configCache);
  }

  @Override
  public Duration getKeepAlive() {
    return getLegacyOrLatestOrDefault(
        "KeepAlive",
        () -> properties.getBroker().getKeepAlive(),
        () -> camundaClientProperties.getZeebe().getKeepAlive(),
        DEFAULT.getKeepAlive(),
        configCache);
  }

  @Override
  public List<ClientInterceptor> getInterceptors() {
    return interceptors;
  }

  @Override
  public List<AsyncExecChainHandler> getChainHandlers() {
    return chainHandlers;
  }

  @Override
  public JsonMapper getJsonMapper() {
    return jsonMapper;
  }

  @Override
  public String getOverrideAuthority() {
    return getLegacyOrLatestOrDefault(
        "OverrideAuthority",
        () -> properties.getSecurity().getOverrideAuthority(),
        () -> camundaClientProperties.getZeebe().getOverrideAuthority(),
        DEFAULT.getOverrideAuthority(),
        configCache);
  }

  @Override
  public int getMaxMessageSize() {
    return getLegacyOrLatestOrDefault(
        "MaxMessageSize",
        () -> properties.getMessage().getMaxMessageSize(),
        () -> camundaClientProperties.getZeebe().getMaxMessageSize(),
        DEFAULT.getMaxMessageSize(),
        configCache);
  }

  @Override
  public int getMaxMetadataSize() {
    return getOrDefault(
        "MaxMetadataSize",
        () -> camundaClientProperties.getZeebe().getMaxMessageSize(),
        DEFAULT.getMaxMetadataSize(),
        configCache);
  }

  @Override
  public ScheduledExecutorService jobWorkerExecutor() {
    return zeebeClientExecutorService.get();
  }

  @Override
  public boolean ownsJobWorkerExecutor() {
    return getLegacyOrLatestOrDefault(
        "ownsJobWorkerExecutor",
        properties::ownsJobWorkerExecutor,
        zeebeClientExecutorService::isOwnedByZeebeClient,
        DEFAULT.ownsJobWorkerExecutor(),
        configCache);
  }

  @Override
  public boolean getDefaultJobWorkerStreamEnabled() {
    return getLegacyOrLatestOrDefault(
        "DefaultJobWorkerStreamEnabled",
        properties::getDefaultJobWorkerStreamEnabled,
        () -> camundaClientProperties.getZeebe().getDefaults().getStreamEnabled(),
        DEFAULT.getDefaultJobWorkerStreamEnabled(),
        configCache);
  }

  @Override
  public boolean useDefaultRetryPolicy() {
    return false;
  }

  @Override
  public boolean preferRestOverGrpc() {
    return getOrDefault(
        "preferRestOverGrpc",
        () -> camundaClientProperties.getZeebe().isPreferRestOverGrpc(),
        DEFAULT.preferRestOverGrpc(),
        configCache);
  }

  private CredentialsProvider credentialsProvider() {
    final ClientMode clientMode = camundaClientProperties.getMode();
    if (ClientMode.selfManaged.equals(clientMode) || ClientMode.saas.equals(clientMode)) {
      final OAuthCredentialsProviderBuilder credBuilder =
          CredentialsProvider.newCredentialsProviderBuilder()
              .clientId(camundaClientProperties.getAuth().getClientId())
              .clientSecret(camundaClientProperties.getAuth().getClientSecret())
              .audience(camundaClientProperties.getZeebe().getAudience())
              .authorizationServerUrl(camundaClientProperties.getAuth().getIssuer())
              .scope(camundaClientProperties.getZeebe().getScope());

      maybeConfigureIdentityProviderSSLConfig(credBuilder);

      return credBuilder.build();
    }
    return new NoopCredentialsProvider();
  }

  private void maybeConfigureIdentityProviderSSLConfig(
      final OAuthCredentialsProviderBuilder builder) {
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

  private String composeGatewayAddress() {
    final URI gatewayUrl = camundaClientProperties.getZeebe().getGrpcAddress();
    final int port = gatewayUrl.getPort();
    final String host = gatewayUrl.getHost();

    // port is set
    if (port != -1) {
      return composeAddressWithPort(host, port, "Gateway port is set");
    }

    // port is not set, attempting to use default
    int defaultPort;
    try {
      defaultPort = gatewayUrl.toURL().getDefaultPort();
    } catch (final MalformedURLException e) {
      LOG.warn("Invalid gateway url: {}", gatewayUrl);
      // could not get a default port, setting it to -1 and moving to the next statement
      defaultPort = -1;
    }
    if (defaultPort != -1) {
      return composeAddressWithPort(host, defaultPort, "Gateway port has default");
    }

    // do not use any port
    LOG.debug("Gateway cannot be determined, address will be '{}'", host);
    return host;
  }

  private String composeAddressWithPort(
      final String host, final int port, final String debugMessage) {
    final String gatewayAddress = host + ":" + port;
    LOG.debug(debugMessage + ", address will be '{}'", gatewayAddress);
    return gatewayAddress;
  }

  private boolean composePlaintext() {
    final String protocol = camundaClientProperties.getZeebe().getGrpcAddress().getScheme();
    return switch (protocol) {
      case "http" -> true;
      case "https" -> false;
      default ->
          throw new IllegalStateException(
              String.format("Unrecognized zeebe protocol '%s'", protocol));
    };
  }

  private CredentialsProvider legacyCredentialsProvider() {
    if (hasText(properties.getCloud().getClientId())
        && hasText(properties.getCloud().getClientSecret())) {
      return CredentialsProvider.newCredentialsProviderBuilder()
          .clientId(properties.getCloud().getClientId())
          .clientSecret(properties.getCloud().getClientSecret())
          .audience(properties.getCloud().getAudience())
          .scope(properties.getCloud().getScope())
          .authorizationServerUrl(properties.getCloud().getAuthUrl())
          .credentialsCachePath(properties.getCloud().getCredentialsCachePath())
          .build();
    } else if (Environment.system().get("ZEEBE_CLIENT_ID") != null
        && Environment.system().get("ZEEBE_CLIENT_SECRET") != null) {
      // Copied from ZeebeClientBuilderImpl
      final OAuthCredentialsProviderBuilder builder =
          CredentialsProvider.newCredentialsProviderBuilder();
      final int separatorIndex = properties.getBroker().getGatewayAddress().lastIndexOf(58); // ":"
      if (separatorIndex > 0) {
        builder.audience(properties.getBroker().getGatewayAddress().substring(0, separatorIndex));
      }
      return builder.build();
    }
    return null;
  }

  @Override
  public String toString() {
    return "ZeebeClientConfigurationImpl{"
        + "properties="
        + properties
        + ", camundaClientProperties="
        + camundaClientProperties
        + ", jsonMapper="
        + jsonMapper
        + ", interceptors="
        + interceptors
        + ", chainHandlers="
        + chainHandlers
        + ", zeebeClientExecutorService="
        + zeebeClientExecutorService
        + '}';
  }
}
