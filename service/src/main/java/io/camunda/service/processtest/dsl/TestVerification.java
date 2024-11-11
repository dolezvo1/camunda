/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.processtest.dsl;

import io.camunda.service.processtest.ProcessTestVerifications;
import io.camunda.service.processtest.TestContext;
import io.camunda.service.processtest.VerificationResult;

public interface TestVerification extends TestInstruction {

  VerificationResult verify(
      final TestContext context, final ProcessTestVerifications verifications);
}
