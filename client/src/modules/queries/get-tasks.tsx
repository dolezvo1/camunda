/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import {gql} from 'apollo-boost';
import {tasks} from '../mock-schema/mocks/tasks';

import {Task} from 'modules/types';

interface GetTasks {
  tasks: ReadonlyArray<{
    key: Task['key'];
    name: Task['name'];
    assignee: Task['assignee'];
    workflowName: Task['workflowName'];
    creationTime: Task['creationTime'];
  }>;
}

const GET_TASKS =
  process.env.NODE_ENV === 'test'
    ? gql`
        query GetTasks {
          tasks {
            key
            name
            workflowName
            assignee
            creationTime
          }
        }
      `
    : gql`
        query GetTasks {
          tasks @client {
            key
            name
            workflowName
            assignee
            creationTime
          }
        }
      `;

const mockGetTasks = {
  request: {
    query: GET_TASKS,
  },
  result: {
    data: {
      tasks: tasks,
    },
  },
} as const;

const mockGetEmptyTasks = {
  request: {
    query: GET_TASKS,
  },
  result: {
    data: {
      tasks: [],
    },
  },
} as const;

export type {GetTasks};
export {GET_TASKS, mockGetTasks, mockGetEmptyTasks};
