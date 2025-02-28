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

import io.camunda.client.impl.command.ArgumentUtil;
import io.camunda.client.protocol.rest.AuthorizationPatchRequest;
import io.camunda.client.protocol.rest.AuthorizationPatchRequest.ActionEnum;
import io.camunda.client.protocol.rest.PermissionDTO;
import io.camunda.client.protocol.rest.PermissionTypeEnum;
import io.camunda.client.protocol.rest.ResourceTypeEnum;
import java.util.List;

public class PatchAuthorizationCommand {

  private final AuthorizationPatchRequest request;
  private PermissionDTO currentPermission;

  public PatchAuthorizationCommand(final ActionEnum action) {
    request = new AuthorizationPatchRequest().action(action);
  }

  public void resourceType(final ResourceTypeEnum resourceType) {
    ArgumentUtil.ensureNotNull("resourceType", resourceType);
    request.resourceType(resourceType);
  }

  public void permission(final PermissionTypeEnum permissionType) {
    ArgumentUtil.ensureNotNull("permissionType", permissionType);
    currentPermission = new PermissionDTO();
    currentPermission.permissionType(permissionType);
    request.addPermissionsItem(currentPermission);
  }

  public void resourceIds(final List<String> resourceIds) {
    ArgumentUtil.ensureNotNullOrEmpty("resourceIds", resourceIds);
    resourceIds.forEach(this::resourceId);
  }

  public void resourceId(final String resourceId) {
    ArgumentUtil.ensureNotNullNorEmpty("resourceId", resourceId);
    currentPermission.addResourceIdsItem(resourceId);
  }

  public AuthorizationPatchRequest getRequest() {
    return request;
  }
}
