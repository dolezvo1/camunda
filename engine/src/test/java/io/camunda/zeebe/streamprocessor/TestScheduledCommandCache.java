/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.streamprocessor;

import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

public class TestScheduledCommandCache implements ScheduledCommandCache {
  protected final Map<Intent, Set<Long>> cachedKeys = new ConcurrentHashMap<>();

  @Override
  public void add(final Intent intent, final long key) {
    cacheForIntent(intent).add(key);
  }

  @Override
  public boolean contains(final Intent intent, final long key) {
    return cacheForIntent(intent).contains(key);
  }

  @Override
  public void remove(final Intent intent, final long key) {
    cacheForIntent(intent).remove(key);
  }

  private Set<Long> cacheForIntent(final Intent intent) {
    return cachedKeys.computeIfAbsent(intent, ignored -> new ConcurrentSkipListSet<>());
  }

  public static final class TestCommandCache extends TestScheduledCommandCache
      implements StageableScheduledCommandCache {
    private final StagedCache stagedCache = new StagedCache();

    @Override
    public StagedScheduledCommandCache stage() {
      stagedCache.persisted = false;
      return stagedCache;
    }

    public StagedCache stagedCache() {
      return stagedCache;
    }

    public final class StagedCache extends TestScheduledCommandCache
        implements StagedScheduledCommandCache {

      private volatile boolean persisted;

      @Override
      public boolean contains(final Intent intent, final long key) {
        return super.contains(intent, key) || TestCommandCache.this.contains(intent, key);
      }

      @Override
      public void persist() {
        persisted = true;
        cachedKeys.forEach((i, keys) -> keys.forEach(key -> TestCommandCache.this.add(i, key)));
        cachedKeys.clear();
      }

      public boolean persisted() {
        return persisted;
      }
    }
  }
}
