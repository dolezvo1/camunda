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
package io.camunda.zeebe.client.process;

import static io.camunda.zeebe.client.util.JsonUtil.fromJsonAsMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep3;
import io.camunda.zeebe.client.api.response.PublishMessageResponse;
import io.camunda.zeebe.client.util.ClientTest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.PublishMessageRequest;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public final class PublishMessageTest extends ClientTest {

  @Test
  public void shouldPublishMessage() {
    // given
    final long messageKey = 123L;
    gatewayService.onPublishMessageRequest(messageKey);

    // when
    final PublishMessageResponse response =
        client
            .newPublishMessageCommand()
            .messageName("name")
            .correlationKey("key")
            .timeToLive(Duration.ofDays(1))
            .messageId("theId")
            .send()
            .join();

    // then
    final PublishMessageRequest request = gatewayService.getLastRequest();
    assertThat(request.getName()).isEqualTo("name");
    assertThat(request.getCorrelationKey()).isEqualTo("key");
    assertThat(request.getMessageId()).isEqualTo("theId");
    assertThat(request.getTimeToLive()).isEqualTo(Duration.ofDays(1).toMillis());
    assertThat(response.getMessageKey()).isEqualTo(messageKey);

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldPublishMessageWithStringVariables() {
    // when
    client
        .newPublishMessageCommand()
        .messageName("name")
        .correlationKey("key")
        .variables("{\"foo\":\"bar\"}")
        .send()
        .join();

    // then
    final PublishMessageRequest request = gatewayService.getLastRequest();
    assertThat(fromJsonAsMap(request.getVariables())).contains(entry("foo", "bar"));
  }

  @Test
  public void shouldPublishMessageWithInputStreamVariables() {
    // given
    final String variables = "{\"foo\":\"bar\"}";
    final ByteArrayInputStream byteArrayInputStream =
        new ByteArrayInputStream(variables.getBytes());

    // when
    client
        .newPublishMessageCommand()
        .messageName("name")
        .correlationKey("key")
        .variables(byteArrayInputStream)
        .send()
        .join();

    // then
    final PublishMessageRequest request = gatewayService.getLastRequest();
    assertThat(fromJsonAsMap(request.getVariables())).contains(entry("foo", "bar"));
  }

  @Test
  public void shouldPublishMessageWithMapVariables() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar");

    // when
    client
        .newPublishMessageCommand()
        .messageName("name")
        .correlationKey("key")
        .variables(variables)
        .send()
        .join();

    // then
    final PublishMessageRequest request = gatewayService.getLastRequest();
    assertThat(fromJsonAsMap(request.getVariables())).contains(entry("foo", "bar"));
  }

  @Test
  public void shouldPublishMessageWithObjectVariables() {
    // when
    client
        .newPublishMessageCommand()
        .messageName("name")
        .correlationKey("key")
        .variables(new Variables())
        .send()
        .join();

    // then
    final PublishMessageRequest request = gatewayService.getLastRequest();
    assertThat(fromJsonAsMap(request.getVariables())).contains(entry("foo", "bar"));
  }

  @Test
  public void shouldPublishMessageWithSingleVariable() {
    // when
    final String key = "key";
    final String value = "value";
    client
        .newPublishMessageCommand()
        .messageName("name")
        .correlationKey("key")
        .variable(key, value)
        .send()
        .join();

    // then
    final PublishMessageRequest request = gatewayService.getLastRequest();
    assertThat(fromJsonAsMap(request.getVariables())).contains(entry(key, value));
  }

  @Test
  public void shouldThrowErrorWhenTryToPublishMessageWithNullVariable() {
    // when
    Assertions.assertThatThrownBy(
            () ->
                client
                    .newPublishMessageCommand()
                    .messageName("name")
                    .correlationKey("key")
                    .variable(null, null)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldRaiseExceptionOnError() {
    // given
    gatewayService.errorOnRequest(
        PublishMessageRequest.class, () -> new ClientException("Invalid request"));

    // when
    assertThatThrownBy(
            () ->
                client
                    .newPublishMessageCommand()
                    .messageName("name")
                    .correlationKey("key")
                    .messageId("foo")
                    .send()
                    .join())
        .isInstanceOf(ClientException.class)
        .hasMessageContaining("Invalid request");
  }

  @Test
  public void shouldSetRequestTimeout() {
    // given
    final Duration requestTimeout = Duration.ofHours(124);

    // when
    client
        .newPublishMessageCommand()
        .messageName("test")
        .correlationKey("test")
        .requestTimeout(requestTimeout)
        .send()
        .join();

    // then
    rule.verifyRequestTimeout(requestTimeout);
  }

  @Test
  public void shouldAllowSpecifyingTenantId() {
    // given
    final PublishMessageCommandStep3 builder =
        client.newPublishMessageCommand().messageName("").correlationKey("");

    // when
    final PublishMessageCommandStep3 builderWithTenant = builder.tenantId("custom tenant");

    // then
    // todo(#13559): verify that tenant id is set in the request
    assertThat(builderWithTenant)
        .describedAs("This method has no effect on the command builder while under development")
        .isEqualTo(builder);
  }

  public static class Variables {

    Variables() {}

    public String getFoo() {
      return "bar";
    }
  }
}
