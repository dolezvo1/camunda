/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useNavigate, useLocation} from 'react-router-dom';
import {pages} from 'modules/routing';
import {encodeTaskOpenedRef} from 'modules/utils/reftags';
import {useTaskFilters} from '../hooks/useTaskFilters';

function useAutoSelectNextTask() {
  const {filter, sortBy} = useTaskFilters();
  const navigate = useNavigate();
  const location = useLocation();

  const navigateToTaskDetailsWithRef = (userTaskKey: number) => {
    const search = new URLSearchParams(location.search);
    search.set(
      'ref',
      encodeTaskOpenedRef({
        by: 'auto-select',
        position: 0,
        filter,
        sorting: sortBy,
      }),
    );
    navigate({
      ...location,
      pathname: pages.taskDetails(userTaskKey),
      search: search.toString(),
    });
  };

  return {
    goToTask: navigateToTaskDetailsWithRef,
  };
}

export {useAutoSelectNextTask};
