/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.job;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue.JobCorrectionsValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.stream.StreamSupport;
import org.agrona.DirectBuffer;

public final class JobCorrections extends UnpackedObject implements JobCorrectionsValue {

  private final StringProperty assigneeProp = new StringProperty("assignee", "");
  private final StringProperty dueDateProp = new StringProperty("dueDate", "");
  private final StringProperty followUpDateProp = new StringProperty("followUpDate", "");
  private final ArrayProperty<StringValue> candidateUsersProp =
      new ArrayProperty<>("candidateUsers", StringValue::new);
  private final ArrayProperty<StringValue> candidateGroupsProp =
      new ArrayProperty<>("candidateGroups", StringValue::new);
  private final IntegerProperty priorityProp = new IntegerProperty("priority", -1);

  public JobCorrections() {
    super(6);
    declareProperty(assigneeProp)
        .declareProperty(dueDateProp)
        .declareProperty(followUpDateProp)
        .declareProperty(candidateUsersProp)
        .declareProperty(candidateGroupsProp)
        .declareProperty(priorityProp);
  }

  public void wrap(final JobCorrections other) {
    setAssignee(other.getAssignee());
    setDueDate(other.getDueDate());
    setFollowUpDate(other.getFollowUpDate());
    setCandidateUsers(other.getCandidateUsers());
    setCandidateGroups(other.getCandidateGroups());
    setPriority(other.getPriority());
  }

  public DirectBuffer getAssigneeBuffer() {
    return assigneeProp.getValue();
  }

  @Override
  public String getAssignee() {
    final var buffer = getAssigneeBuffer();
    return BufferUtil.bufferAsString(buffer);
  }

  public JobCorrections setAssignee(final String assignee) {
    assigneeProp.setValue(assignee);
    return this;
  }

  @Override
  public String getDueDate() {
    final var buffer = getDueDateBuffer();
    return BufferUtil.bufferAsString(buffer);
  }

  public JobCorrections setDueDate(final String dueDate) {
    dueDateProp.setValue(dueDate);
    return this;
  }

  @Override
  public String getFollowUpDate() {
    final var buffer = getFollowUpDateBuffer();
    return BufferUtil.bufferAsString(buffer);
  }

  public JobCorrections setFollowUpDate(final String followUpDate) {
    followUpDateProp.setValue(followUpDate);
    return this;
  }

  @Override
  public List<String> getCandidateGroups() {
    return StreamSupport.stream(candidateGroupsProp.spliterator(), false)
        .map(StringValue::getValue)
        .map(BufferUtil::bufferAsString)
        .toList();
  }

  public JobCorrections setCandidateGroups(final List<String> candidateGroups) {
    candidateGroupsProp.reset();
    candidateGroups.forEach(
        candidateGroup -> candidateGroupsProp.add().wrap(BufferUtil.wrapString(candidateGroup)));
    return this;
  }

  @Override
  public List<String> getCandidateUsers() {
    return StreamSupport.stream(candidateUsersProp.spliterator(), false)
        .map(StringValue::getValue)
        .map(BufferUtil::bufferAsString)
        .toList();
  }

  public JobCorrections setCandidateUsers(final List<String> candidateUsers) {
    candidateUsersProp.reset();
    candidateUsers.forEach(
        candidateUser -> candidateUsersProp.add().wrap(BufferUtil.wrapString(candidateUser)));
    return this;
  }

  @Override
  public int getPriority() {
    return priorityProp.getValue();
  }

  public JobCorrections setPriority(final int priority) {
    priorityProp.setValue(priority);
    return this;
  }

  public DirectBuffer getDueDateBuffer() {
    return dueDateProp.getValue();
  }

  public DirectBuffer getFollowUpDateBuffer() {
    return followUpDateProp.getValue();
  }
}
