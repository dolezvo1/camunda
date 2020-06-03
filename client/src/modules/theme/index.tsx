/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const theme = {
  colors: {
    blue: '#4d90ff',
    green: '#10d070',
    red: '#ff3d3d',
    orange: '#ffa533',
    ui01: '#f2f3f5',
    ui02: '#f7f8fa',
    ui03: '#b0bac7',
    ui04: '#fdfdfe',
    ui05: '#d8dce3',
    ui06: '#62626e',
    ui07: '#45464e',
    item: {
      odd: '#fdfdfe',
      even: '#f9fafc',
    },
    text: {
      button: 'rgba(69, 70, 78, 0.9)',
      copyrightNotice: 'rgba(98, 98, 110, 0.9)',
      black: '#45464e',
    },
    overlay: 'rgba(255, 255, 255, 0.75)',
    active: '#bcc6d2',
    focusOuter: '#8CB7FF',
    focusInner: '#2B7BFF',
    label01: 'rgba(69, 70, 78, 0.9)', //charcoal-grey-90
    link: {
      active: '#eaf3ff',
    },
  },
} as const;

export {theme};
