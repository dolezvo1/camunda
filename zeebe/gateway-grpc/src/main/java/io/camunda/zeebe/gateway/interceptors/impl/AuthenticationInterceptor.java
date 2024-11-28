/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.interceptors.impl;

import com.auth0.jwt.JWT;
import io.camunda.zeebe.gateway.interceptors.InterceptorUtil;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthenticationInterceptor implements ServerInterceptor {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationInterceptor.class);
  private static final Metadata.Key<String> AUTH_KEY =
      Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);

  @Override
  public <ReqT, RespT> Listener<ReqT> interceptCall(final ServerCall<ReqT, RespT> call,
      final Metadata headers, final ServerCallHandler<ReqT, RespT> next) {
    final var methodDescriptor = call.getMethodDescriptor();

    final var authorization = headers.get(AUTH_KEY);
    if (authorization == null) {
      LOGGER.debug(
          "Denying call {} as no token was provided", methodDescriptor.getFullMethodName());
      return deny(
          call,
          Status.UNAUTHENTICATED.augmentDescription(
              "Expected bearer token at header with key [%s], but found nothing"
                  .formatted(AUTH_KEY.name())));
    }

    final String token = authorization.replaceFirst("^Bearer ", "");

    // TODO verify the token, do we really want to delete the identity SDK ? It's not easy to verify a token

    try {
      // get user claims and set them in the context
      final var decodedJWT = JWT.decode(token);
      final var claims = decodedJWT.getClaims();
      final var context = InterceptorUtil.setUserClaims(claims);
      return Contexts.interceptCall(context, call, headers, next);

    } catch (final RuntimeException e) {
      LOGGER.debug(
          "Denying call {} as the token is not valid. Error message: {}",
          methodDescriptor.getFullMethodName(),
          e.getMessage());
      return deny(
          call,
          Status.UNAUTHENTICATED
              .augmentDescription(
                  "Expected a valid token, see cause for details")
              .withCause(e));
    }
  }

  private <ReqT> ServerCall.Listener<ReqT> deny(
      final ServerCall<ReqT, ?> call, final Status status) {
    call.close(status, new Metadata());
    return new ServerCall.Listener<>() {};
  }
}
