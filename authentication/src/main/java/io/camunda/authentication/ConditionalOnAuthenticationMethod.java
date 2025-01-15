/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import io.camunda.authentication.config.AuthenticationProperties;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public final class ConditionalOnAuthenticationMethod {
  private ConditionalOnAuthenticationMethod() {}

  public static class C implements Condition {

    @Override
    public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
      return false;
    }
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.TYPE, ElementType.METHOD})
  @Documented
  // @ConditionalOnProperty(name = AuthenticationProperties.METHOD, havingValue = "basic")
  @ConditionalOnExpression("'BASIC'.equalsIgnoreCase('${camunda.security.authentication.method}')")
  // @Conditional(C.class)
  public @interface BASIC {}

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.TYPE, ElementType.METHOD})
  @Documented
  @ConditionalOnProperty(name = AuthenticationProperties.METHOD, havingValue = "oidc")
  public @interface OIDC {}
}
