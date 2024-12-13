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
package io.camunda.zeebe.client.impl.command;

import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.CreateMappingCommandStep1;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.response.CreateMappingResponse;
import io.camunda.zeebe.client.impl.http.HttpClient;
import io.camunda.zeebe.client.impl.http.HttpZeebeFuture;
import io.camunda.zeebe.client.impl.response.CreateMappingResponseImpl;
import io.camunda.zeebe.client.protocol.rest.MappingRuleCreateRequest;
import io.camunda.zeebe.client.protocol.rest.MappingRuleCreateResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class CreateMappingCommandImpl implements CreateMappingCommandStep1 {

  private final MappingRuleCreateRequest mappingRequest;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public CreateMappingCommandImpl(final HttpClient httpClient, final JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    mappingRequest = new MappingRuleCreateRequest();
  }

  @Override
  public CreateMappingCommandStep1 claimName(final String claimName) {
    mappingRequest.claimName(claimName);
    return this;
  }

  @Override
  public CreateMappingCommandStep1 claimValue(final String claimValue) {
    mappingRequest.claimValue(claimValue);
    return this;
  }

  @Override
  public CreateMappingCommandStep1 name(final String name) {
    mappingRequest.name(name);
    return this;
  }

  @Override
  public FinalCommandStep<CreateMappingResponse> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public ZeebeFuture<CreateMappingResponse> send() {
    ArgumentUtil.ensureNotNull("claimName", mappingRequest.getClaimName());
    ArgumentUtil.ensureNotNull("claimValue", mappingRequest.getClaimValue());
    ArgumentUtil.ensureNotNull("name", mappingRequest.getName());
    final HttpZeebeFuture<CreateMappingResponse> result = new HttpZeebeFuture<>();
    final CreateMappingResponseImpl response = new CreateMappingResponseImpl();
    httpClient.post(
        "/mapping-rules",
        jsonMapper.toJson(mappingRequest),
        httpRequestConfig.build(),
        MappingRuleCreateResponse.class,
        response::setResponse,
        result);
    return result;
  }
}
