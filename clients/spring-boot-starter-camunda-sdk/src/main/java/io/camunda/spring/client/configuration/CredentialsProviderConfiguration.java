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

import io.camunda.client.CredentialsProvider;
import io.camunda.client.impl.NoopCredentialsProvider;
import io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.camunda.client.impl.util.Environment;
import io.camunda.spring.client.properties.CamundaClientProperties;
import io.camunda.spring.client.properties.ZeebeClientConfigurationProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CredentialsProviderConfiguration {
  private static final Logger LOG = LoggerFactory.getLogger(CredentialsProviderConfiguration.class);

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
                    "camunda.client.auth.client-id",
                    true,
                    null,
                    null,
                    () -> camundaClientProperties.getAuth().getClientId(),
                    () -> properties.getCloud().getClientId(),
                    () -> Environment.system().get("ZEEBE_CLIENT_ID")))
            .clientSecret(
                getProperty(
                    "camunda.client.auth.client-secret",
                    true,
                    null,
                    null,
                    () -> camundaClientProperties.getAuth().getClientSecret(),
                    () -> properties.getCloud().getClientSecret(),
                    () -> Environment.system().get("ZEEBE_CLIENT_SECRET")))
            .audience(
                getProperty(
                    "camunda.client.auth.audience",
                    false,
                    null,
                    null,
                    () -> camundaClientProperties.getAuth().getAudience(),
                    () -> camundaClientProperties.getZeebe().getAudience(),
                    () -> properties.getCloud().getAudience()))
            .scope(
                getProperty(
                    "camunda.client.auth.scope",
                    false,
                    null,
                    null,
                    () -> camundaClientProperties.getAuth().getScope(),
                    () -> camundaClientProperties.getZeebe().getScope(),
                    () -> properties.getCloud().getScope()))
            .authorizationServerUrl(
                getProperty(
                    "camunda.client.auth.issuer",
                    false,
                    null,
                    null,
                    () -> camundaClientProperties.getAuth().getIssuer().toString(),
                    () -> properties.getCloud().getAuthUrl()))
            .credentialsCachePath(
                getProperty(
                    "camunda.client.auth.credentials-cache-path",
                    false,
                    null,
                    null,
                    () -> camundaClientProperties.getAuth().getCredentialsCachePath(),
                    () -> properties.getCloud().getCredentialsCachePath()))
            .connectTimeout(
                getProperty(
                    "camunda.client.auth.connect-timeout",
                    false,
                    null,
                    null,
                    () -> camundaClientProperties.getAuth().getConnectTimeout()))
            .readTimeout(
                getProperty(
                    "camunda.client.auth.read-timeout",
                    false,
                    null,
                    null,
                    () -> camundaClientProperties.getAuth().getReadTimeout()));

    maybeConfigureIdentityProviderSSLConfig(credBuilder, camundaClientProperties);
    try {
      return credBuilder.build();
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
        LOG.debug("Using keystore {}", keyStore);
        builder.keystorePath(keyStore);
        builder.keystorePassword(camundaClientProperties.getAuth().getKeystorePassword());
        builder.keystoreKeyPassword(camundaClientProperties.getAuth().getKeystoreKeyPassword());
      } else {
        LOG.debug("Keystore {} not found", keyStore);
      }
    }

    if (camundaClientProperties.getAuth().getTruststorePath() != null) {
      final Path trustStore = Paths.get(camundaClientProperties.getAuth().getTruststorePath());
      if (Files.exists(trustStore)) {
        LOG.debug("Using truststore {}", trustStore);
        builder.truststorePath(trustStore);
        builder.truststorePassword(camundaClientProperties.getAuth().getTruststorePassword());
      } else {
        LOG.debug("Truststore {} not found", trustStore);
      }
    }
  }
}
