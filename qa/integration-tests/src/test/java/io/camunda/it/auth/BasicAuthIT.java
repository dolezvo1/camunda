/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.application.commons.CommonsModuleConfiguration;
import io.camunda.client.protocol.rest.UserRequest;
import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.entities.TenantEntity;
import io.camunda.search.entities.UserEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authentication;
import io.camunda.security.entity.Permission;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.service.UserServices;
import io.camunda.zeebe.broker.BrokerModuleConfiguration;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.logging.log4j.util.Base64Util;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest(
    classes = {CommonsModuleConfiguration.class, BrokerModuleConfiguration.class},
    properties = {"spring.profiles.active=broker,auth-basic"})
@WebAppConfiguration
@AutoConfigureMockMvc
public class BasicAuthIT {

  private static final String USERNAME = "correct_username";
  private static final String PASSWORD = "correct_password";
  @MockBean UserServices userService;
  @MockBean AuthorizationServices authorizationServices;
  @MockBean RoleServices roleServices;
  @MockBean TenantServices tenantServices;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private MockMvc mockMvc;
  private String content;

  @BeforeEach
  void setUp() throws JsonProcessingException {
    when(userService.withAuthentication(any(Authentication.class))).thenReturn(userService);
    when(userService.createUser(any()))
        .thenReturn(CompletableFuture.completedFuture(new UserRecord()));
    when(userService.search(any()))
        .thenReturn(
            new SearchQueryResult<>(
                1,
                List.of(new UserEntity(1L, USERNAME, "name", "", passwordEncoder.encode(PASSWORD))),
                null,
                null));
    when(tenantServices.getTenantsByMemberKey(anyLong()))
        .thenReturn(
            List.of(new TenantEntity(9L, "T1", "Tenant 1", "Tenant 1 description", Set.of())));

    when(authorizationServices.findAll(any()))
        .thenReturn(
            List.of(
                new AuthorizationEntity(
                    1L,
                    AuthorizationOwnerType.USER.name(),
                    AuthorizationResourceType.APPLICATION.name(),
                    List.of(new Permission(PermissionType.ACCESS, Set.of("*"))))));

    when(roleServices.findAll(any())).thenReturn(List.of());

    content =
        objectMapper.writeValueAsString(
            new UserRequest()
                .username("demo-".concat(UUID.randomUUID().toString()))
                .name("Demo")
                .password("password")
                .email("demo@email.com"));
  }

  @Test
  void basicAuthWithValidCredentials() throws Exception {
    final MockHttpServletRequestBuilder request =
        MockMvcRequestBuilders.get("/v2/authentication/me")
            .accept("application/json")
            .header("Authorization", "Basic " + Base64Util.encode(USERNAME + ":" + PASSWORD));
    mockMvc.perform(request).andExpect(status().isOk());
  }

  @Test
  void basicAuthWithNoCredentials() throws Exception {
    final MockHttpServletRequestBuilder request =
        MockMvcRequestBuilders.post("/v2/users")
            .accept("application/json")
            .contentType(MediaType.APPLICATION_JSON)
            .content(content);
    mockMvc.perform(request).andExpect(status().isUnauthorized());
  }

  @Test
  void basicAuthWithBadCredentials() throws Exception {
    final MockHttpServletRequestBuilder request =
        MockMvcRequestBuilders.post("/v2/users")
            .accept("application/json")
            .contentType(MediaType.APPLICATION_JSON)
            .header(
                "Authorization", "Basic " + Base64Util.encode(USERNAME + ":" + PASSWORD + "Wrong"))
            .content(content);
    mockMvc.perform(request).andExpect(status().isUnauthorized()).andReturn();
  }
}
