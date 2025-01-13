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

import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.CredentialsProvider;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.impl.CamundaClientBuilderImpl;
import io.camunda.spring.client.jobhandling.CamundaClientExecutorService;
import io.camunda.spring.client.properties.CamundaClientProperties;
import io.camunda.spring.client.properties.PropertiesUtil;
import io.camunda.spring.client.properties.ZeebeClientConfigurationProperties;
import io.grpc.ClientInterceptor;
import jakarta.annotation.PostConstruct;
import java.net.MalformedURLException;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class CamundaClientConfigurationImpl implements CamundaClientConfiguration {
  public static final CamundaClientBuilderImpl DEFAULT =
      (CamundaClientBuilderImpl) new CamundaClientBuilderImpl().withProperties(new Properties());
  private static final Logger LOG = LoggerFactory.getLogger(CamundaClientConfigurationImpl.class);
  private final Map<String, Object> configCache = new HashMap<>();
  private final ZeebeClientConfigurationProperties zeebeClientLegacyProperties;
  private final CamundaClientProperties camundaClientProperties;
  private final JsonMapper jsonMapper;
  private final List<ClientInterceptor> interceptors;
  private final List<AsyncExecChainHandler> chainHandlers;
  private final CamundaClientExecutorService zeebeClientExecutorService;
  private final CredentialsProvider credentialsProvider;

  @Autowired
  public CamundaClientConfigurationImpl(
      final ZeebeClientConfigurationProperties zeebeClientLegacyProperties,
      final CamundaClientProperties camundaClientProperties,
      final JsonMapper jsonMapper,
      final List<ClientInterceptor> interceptors,
      final List<AsyncExecChainHandler> chainHandlers,
      final CamundaClientExecutorService zeebeClientExecutorService,
      final CredentialsProvider credentialsProvider) {
    this.zeebeClientLegacyProperties = zeebeClientLegacyProperties;
    this.camundaClientProperties = camundaClientProperties;
    this.jsonMapper = jsonMapper;
    this.interceptors = interceptors;
    this.chainHandlers = chainHandlers;
    this.zeebeClientExecutorService = zeebeClientExecutorService;
    this.credentialsProvider = credentialsProvider;
  }

  @PostConstruct
  public void applyLegacy() {
    // make sure environment variables and other legacy config options are taken into account
    // (duplicate, also done by  qPostConstruct, whatever)
    zeebeClientLegacyProperties.applyOverrides();
  }

  @Override
  public String getGatewayAddress() {
    return getProperty(
        "GatewayAddress (composed)",
        false,
        configCache,
        DEFAULT.getGatewayAddress(),
        this::composeGatewayAddress,
        () -> PropertiesUtil.getZeebeGatewayAddress(zeebeClientLegacyProperties));
  }

  @Override
  public URI getRestAddress() {
    return getProperty(
        "camunda.client.rest-address",
        false,
        configCache,
        DEFAULT.getRestAddress(),
        camundaClientProperties::getRestAddress,
        () -> camundaClientProperties.getZeebe().getRestAddress(),
        () -> zeebeClientLegacyProperties.getBroker().getRestAddress());
  }

  @Override
  public URI getGrpcAddress() {
    return getProperty(
        "camunda.client.grpc-address",
        false,
        configCache,
        DEFAULT.getGrpcAddress(),
        camundaClientProperties::getGrpcAddress,
        () -> camundaClientProperties.getZeebe().getGrpcAddress(),
        zeebeClientLegacyProperties::getGrpcAddress);
  }

  @Override
  public String getDefaultTenantId() {
    return getProperty(
        "camunda.client.defaults.tenant-ids[0]",
        false,
        configCache,
        DEFAULT.getDefaultTenantId(),
        () -> camundaClientProperties.getDefaults().getTenantIds().get(0),
        () -> camundaClientProperties.getTenantIds().get(0),
        () -> camundaClientProperties.getZeebe().getDefaults().getTenantIds().get(0),
        zeebeClientLegacyProperties::getDefaultTenantId);
  }

  @Override
  public List<String> getDefaultJobWorkerTenantIds() {
    return getProperty(
        "camunda.client.defaults.tenant-ids",
        false,
        configCache,
        DEFAULT.getDefaultJobWorkerTenantIds(),
        () -> camundaClientProperties.getDefaults().getTenantIds(),
        camundaClientProperties::getTenantIds,
        () -> camundaClientProperties.getZeebe().getDefaults().getTenantIds(),
        zeebeClientLegacyProperties::getDefaultJobWorkerTenantIds);
  }

  @Override
  public int getNumJobWorkerExecutionThreads() {
    return getProperty(
        "camunda.client.execution-threads",
        false,
        configCache,
        DEFAULT.getNumJobWorkerExecutionThreads(),
        camundaClientProperties::getExecutionThreads,
        () -> camundaClientProperties.getZeebe().getExecutionThreads(),
        () -> zeebeClientLegacyProperties.getWorker().getThreads());
  }

  @Override
  public int getDefaultJobWorkerMaxJobsActive() {
    return getProperty(
        "camunda.client.defaults.max-jobs-active",
        false,
        configCache,
        DEFAULT.getDefaultJobWorkerMaxJobsActive(),
        () -> camundaClientProperties.getDefaults().getMaxJobsActive(),
        () -> camundaClientProperties.getZeebe().getDefaults().getMaxJobsActive(),
        () -> zeebeClientLegacyProperties.getWorker().getMaxJobsActive());
  }

  @Override
  public String getDefaultJobWorkerName() {
    return getProperty(
        "camunda.client.defaults.name",
        false,
        configCache,
        DEFAULT.getDefaultJobWorkerName(),
        () -> camundaClientProperties.getDefaults().getName(),
        () -> camundaClientProperties.getZeebe().getDefaults().getName(),
        () -> zeebeClientLegacyProperties.getWorker().getDefaultName());
  }

  @Override
  public Duration getDefaultJobTimeout() {
    return getProperty(
        "camunda.client.defaults.timeout",
        false,
        configCache,
        DEFAULT.getDefaultJobTimeout(),
        () -> camundaClientProperties.getDefaults().getTimeout(),
        () -> camundaClientProperties.getZeebe().getDefaults().getTimeout(),
        () -> zeebeClientLegacyProperties.getJob().getTimeout());
  }

  @Override
  public Duration getDefaultJobPollInterval() {
    return getProperty(
        "camunda.client.defaults.poll-interval",
        false,
        configCache,
        DEFAULT.getDefaultJobPollInterval(),
        () -> camundaClientProperties.getDefaults().getPollInterval(),
        () -> camundaClientProperties.getZeebe().getDefaults().getPollInterval(),
        () -> zeebeClientLegacyProperties.getJob().getPollInterval());
  }

  @Override
  public Duration getDefaultMessageTimeToLive() {
    return getProperty(
        "camunda.client.message-time-to-live",
        false,
        configCache,
        DEFAULT.getDefaultMessageTimeToLive(),
        camundaClientProperties::getMessageTimeToLive,
        () -> camundaClientProperties.getZeebe().getMessageTimeToLive(),
        () -> zeebeClientLegacyProperties.getMessage().getTimeToLive());
  }

  @Override
  public Duration getDefaultRequestTimeout() {
    return getProperty(
        "camunda.client.defaults.request-timeout",
        false,
        configCache,
        DEFAULT.getDefaultRequestTimeout(),
        () -> camundaClientProperties.getDefaults().getRequestTimeout(),
        () -> camundaClientProperties.getZeebe().getRequestTimeout(),
        () -> camundaClientProperties.getZeebe().getDefaults().getRequestTimeout(),
        zeebeClientLegacyProperties::getRequestTimeout);
  }

  @Override
  public boolean isPlaintextConnectionEnabled() {
    return getProperty(
        "PlaintextConnectionEnabled (composed)",
        false,
        configCache,
        DEFAULT.isPlaintextConnectionEnabled(),
        this::composePlaintext,
        () -> zeebeClientLegacyProperties.getSecurity().isPlaintext());
  }

  @Override
  public String getCaCertificatePath() {
    return getProperty(
        "camunda.client.ca-certificate-path",
        false,
        configCache,
        DEFAULT.getCaCertificatePath(),
        camundaClientProperties::getCaCertificatePath,
        () -> camundaClientProperties.getZeebe().getCaCertificatePath(),
        () -> zeebeClientLegacyProperties.getSecurity().getCertPath());
  }

  @Override
  public CredentialsProvider getCredentialsProvider() {
    return credentialsProvider;
  }

  @Override
  public Duration getKeepAlive() {
    return getProperty(
        "camunda.client.keep-alive",
        false,
        configCache,
        DEFAULT.getKeepAlive(),
        camundaClientProperties::getKeepAlive,
        () -> camundaClientProperties.getZeebe().getKeepAlive(),
        () -> zeebeClientLegacyProperties.getBroker().getKeepAlive());
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
    return getProperty(
        "camunda.client.override-authority",
        false,
        configCache,
        DEFAULT.getOverrideAuthority(),
        camundaClientProperties::getOverrideAuthority,
        () -> camundaClientProperties.getZeebe().getOverrideAuthority(),
        () -> zeebeClientLegacyProperties.getSecurity().getOverrideAuthority());
  }

  @Override
  public int getMaxMessageSize() {
    return getProperty(
        "camunda.client.max-message-size",
        false,
        configCache,
        DEFAULT.getMaxMessageSize(),
        camundaClientProperties::getMaxMessageSize,
        () -> camundaClientProperties.getZeebe().getMaxMessageSize(),
        () -> zeebeClientLegacyProperties.getMessage().getMaxMessageSize());
  }

  @Override
  public int getMaxMetadataSize() {
    return getProperty(
        "camunda.client.max-metadata-size",
        false,
        configCache,
        DEFAULT.getMaxMetadataSize(),
        camundaClientProperties::getMaxMetadataSize,
        () -> camundaClientProperties.getZeebe().getMaxMessageSize());
  }

  @Override
  public ScheduledExecutorService jobWorkerExecutor() {
    return zeebeClientExecutorService.get();
  }

  @Override
  public boolean ownsJobWorkerExecutor() {
    return zeebeClientExecutorService.isOwnedByCamundaClient();
  }

  @Override
  public boolean getDefaultJobWorkerStreamEnabled() {
    return getProperty(
        "camunda.client.defaults.stream-enabled",
        false,
        configCache,
        DEFAULT.getDefaultJobWorkerStreamEnabled(),
        () -> camundaClientProperties.getDefaults().getStreamEnabled(),
        () -> camundaClientProperties.getZeebe().getDefaults().getStreamEnabled(),
        zeebeClientLegacyProperties::getDefaultJobWorkerStreamEnabled);
  }

  @Override
  public boolean useDefaultRetryPolicy() {
    return false;
  }

  @Override
  public boolean preferRestOverGrpc() {
    return getProperty(
        "camunda.client.prefer-rest-over-grpc",
        false,
        configCache,
        DEFAULT.preferRestOverGrpc(),
        camundaClientProperties::getPreferRestOverGrpc,
        () -> camundaClientProperties.getZeebe().isPreferRestOverGrpc());
  }

  private String composeGatewayAddress() {
    final URI gatewayUrl = getGrpcAddress();
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
    final String protocol = getGrpcAddress().getScheme();
    return switch (protocol) {
      case "http" -> true;
      case "https" -> false;
      default ->
          throw new IllegalStateException(
              String.format("Unrecognized zeebe protocol '%s'", protocol));
    };
  }

  @Override
  public String toString() {
    return "CamundaClientConfigurationImpl{"
        + "camundaClientProperties="
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
