/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.scheduler;

import static io.camunda.zeebe.scheduler.ActorTask.TaskSchedulingState.QUEUED;

/** Workstealing group maintains a queue per thread. */
public final class WorkStealingGroup implements TaskScheduler {
  private final int numOfThreads;
  private final ActorTaskQueue[] taskQueues;

  public WorkStealingGroup(final int numOfThreads) {
    this.numOfThreads = numOfThreads;
    taskQueues = new ActorTaskQueue[numOfThreads];
    for (int i = 0; i < numOfThreads; i++) {
      taskQueues[i] = new ActorTaskQueue();
    }
  }

  /**
   * Submit the task into the provided thread's queue
   *
   * @param task the task to submit
   * @param threadId the id of the thread into which queue the task should be submitted
   */
  public void submit(final ActorTask task, final int threadId) {
    task.schedulingState.set(QUEUED);
    taskQueues[threadId].append(task);
  }

  /**
   * Attempts to acquire the next task to execute
   *
   * @return the acquired task or null if no task is available
   */
  @Override
  public ActorTask getNextTask() {
    final ActorThread currentThread = ActorThread.current();
    ActorTask nextTask = taskQueues[currentThread.getRunnerId()].pop();

    if (nextTask == null) {
      nextTask = trySteal(currentThread);
    }

    return nextTask;
  }

  /**
   * Work stealing: when this runner (aka. the "thief") has no more tasks to run, it attempts to
   * take ("steal") a task from another runner (aka. the "victim").
   *
   * <p>Work stealing is a mechanism for <em>load balancing</em>: it relies upon the assumption that
   * there is more work to do than there is resources (threads) to run it.
   */
  private ActorTask trySteal(final ActorThread currentThread) {
    return null;
  }
}
