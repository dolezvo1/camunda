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
package io.camunda.zeebe.client.impl.response;

import io.camunda.zeebe.client.api.command.CommandWithTenantStep;
import io.camunda.zeebe.client.api.response.ProcessDefinition;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ProcessMetadata;
import java.util.Objects;

public final class ProcessImpl implements ProcessDefinition {

  private final long processDefinitionKey;
  private final String name;
  private final String resourceName;
  private final int version;
  private final String versionTag;
  private final String processDefinitionId;
  private final String tenantId;

  public ProcessImpl(final ProcessMetadata process) {
    this(
        process.getProcessDefinitionKey(),
        process.getBpmnProcessId(),
        process.getVersion(),
        process.getResourceName(),
        process.getTenantId());
  }

  /**
   * A constructor that provides an instance with the <code><default></code> tenantId set.
   *
   * <p>From version 8.3.0, the java client supports multi-tenancy for this command, which requires
   * the <code>tenantId</code> property to be defined. This constructor is only intended for
   * backwards compatibility in tests.
   *
   * @deprecated since 8.3.0, use {@link ProcessImpl#ProcessImpl(long processDefinitionKey, String
   *     bpmnProcessId, int version, String resourceName, String tenantId)}
   */
  @Deprecated
  public ProcessImpl(
      final long processDefinitionKey,
      final String bpmnProcessId,
      final int version,
      final String resourceName) {
    this(
        processDefinitionKey,
        bpmnProcessId,
        version,
        resourceName,
        CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER);
  }

  public ProcessImpl(
      final long processDefinitionKey,
      final String bpmnProcessId,
      final int version,
      final String resourceName,
      final String tenantId) {
    this(processDefinitionKey, null, resourceName, version, null, bpmnProcessId, tenantId);
  }

  public ProcessImpl(
      long processDefinitionKey,
      String name,
      String resourceName,
      int version,
      String versionTag,
      String processDefinitionId,
      String tenantId) {
    this.processDefinitionKey = processDefinitionKey;
    this.name = name;
    this.resourceName = resourceName;
    this.version = version;
    this.versionTag = versionTag;
    this.processDefinitionId = processDefinitionId;
    this.tenantId = tenantId;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getResourceName() {
    return resourceName;
  }

  @Override
  public int getVersion() {
    return version;
  }

  @Override
  public String getVersionTag() {
    return versionTag;
  }

  @Override
  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ProcessImpl process = (ProcessImpl) o;
    return processDefinitionKey == process.processDefinitionKey
        && version == process.version
        && Objects.equals(name, process.name)
        && Objects.equals(resourceName, process.resourceName)
        && Objects.equals(versionTag, process.versionTag)
        && Objects.equals(processDefinitionId, process.processDefinitionId)
        && Objects.equals(tenantId, process.tenantId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        processDefinitionKey,
        name,
        resourceName,
        version,
        versionTag,
        processDefinitionId,
        tenantId);
  }

  @Override
  public String toString() {
    return "ProcessImpl{"
        + "processDefinitionKey="
        + processDefinitionKey
        + ", name='"
        + name
        + '\''
        + ", resourceName='"
        + resourceName
        + '\''
        + ", version="
        + version
        + ", versionTag='"
        + versionTag
        + '\''
        + ", processDefinitionId='"
        + processDefinitionId
        + '\''
        + ", tenantId='"
        + tenantId
        + '\''
        + '}';
  }
}
