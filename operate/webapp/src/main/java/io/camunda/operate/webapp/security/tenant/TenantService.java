/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.tenant;


import io.camunda.authentication.tenant.TenantAttributeHolder;
import io.camunda.security.configuration.SecurityConfiguration;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;

@Component
public class TenantService {

  @Autowired
  private SecurityConfiguration securityConfiguration;

  public List<String> tenantIds() {
    return TenantAttributeHolder.getTenantIds();
  }

  public AuthenticatedTenants getAuthenticatedTenants() {
    if (RequestContextHolder.getRequestAttributes() == null) {
      // if the query comes from the source without request context, return all tenants
      return AuthenticatedTenants.allTenants();
    }

    final var tenants = tenantIds();

    if (tenants != null && !tenants.isEmpty()) {
      return AuthenticatedTenants.assignedTenants(tenants);
    } else {
      return AuthenticatedTenants.noTenantsAssigned();
    }
  }

  private boolean isMultiTenancyEnabled() {
    return securityConfiguration.getMultiTenancy().isEnabled();
  }

  private boolean securityContextPresent() {
    return SecurityContextHolder.getContext().getAuthentication() != null;
  }

  public static class AuthenticatedTenants {

    private final TenantAccessType tenantAccessType;
    private final List<String> ids;

    AuthenticatedTenants(final TenantAccessType tenantAccessType, final List<String> ids) {
      this.tenantAccessType = tenantAccessType;
      this.ids = ids;
    }

    public static AuthenticatedTenants allTenants() {
      return new AuthenticatedTenants(TenantAccessType.TENANT_ACCESS_ALL, null);
    }

    public static AuthenticatedTenants noTenantsAssigned() {
      return new AuthenticatedTenants(TenantAccessType.TENANT_ACCESS_NONE, null);
    }

    public static AuthenticatedTenants assignedTenants(final List<String> tenants) {
      return new AuthenticatedTenants(TenantAccessType.TENANT_ACCESS_ASSIGNED, tenants);
    }

    public TenantAccessType getTenantAccessType() {
      return tenantAccessType;
    }

    public List<String> getTenantIds() {
      return ids;
    }
  }

  public static enum TenantAccessType {
    TENANT_ACCESS_ALL,
    TENANT_ACCESS_ASSIGNED,
    TENANT_ACCESS_NONE
  }
}
