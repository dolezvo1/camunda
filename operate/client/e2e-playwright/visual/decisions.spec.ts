/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test} from '../test-fixtures';
import {
  mockDecisionInstances,
  mockGroupedDecisions,
  mockBatchOperations,
  mockDecisionXml,
  mockResponses,
} from '../mocks/decisions.mocks';
import {URL_PATTERN} from '../constants';

test.describe('decisions page', () => {
  for (const theme of ['light', 'dark']) {
    test(`empty page - ${theme}`, async ({page, commonPage, decisionsPage}) => {
      await commonPage.changeTheme(theme);

      await page.addInitScript(() => {
        window.localStorage.setItem(
          'panelStates',
          JSON.stringify({
            isOperationsCollapsed: false,
          }),
        );
      }, theme);

      await page.route(
        URL_PATTERN,
        mockResponses({
          batchOperations: [],
          groupedDecisions: mockGroupedDecisions,
          decisionXml: '',
          decisionInstances: {
            decisionInstances: [],
            totalCount: 0,
          },
        }),
      );

      await decisionsPage.navigateToDecisions({
        searchParams: {
          evaluated: 'true',
          failed: 'true',
        },
        options: {
          waitUntil: 'networkidle',
        },
      });

      await expect(page).toHaveScreenshot();
    });

    test(`error page - ${theme}`, async ({page, commonPage, decisionsPage}) => {
      await commonPage.changeTheme(theme);

      await page.addInitScript(() => {
        window.localStorage.setItem(
          'panelStates',
          JSON.stringify({
            isDecisionsFiltersCollapsed: true,
            isOperationsCollapsed: false,
          }),
        );
      }, theme);

      await page.route(
        URL_PATTERN,
        mockResponses({
          groupedDecisions: mockGroupedDecisions,
        }),
      );

      await decisionsPage.navigateToDecisions({
        searchParams: {
          evaluated: 'true',
          failed: 'true',
          name: 'invoiceClassification',
          version: '2',
        },
        options: {
          waitUntil: 'networkidle',
        },
      });

      await expect(page).toHaveScreenshot();
    });

    test(`filled with data - ${theme}`, async ({
      page,
      commonPage,
      decisionsPage,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        URL_PATTERN,
        mockResponses({
          groupedDecisions: mockGroupedDecisions,
          batchOperations: mockBatchOperations,
          decisionInstances: mockDecisionInstances,
          decisionXml: mockDecisionXml,
        }),
      );

      await decisionsPage.navigateToDecisions({
        searchParams: {
          evaluated: 'true',
          failed: 'true',
          name: 'invoiceClassification',
          version: '2',
        },
        options: {
          waitUntil: 'networkidle',
        },
      });

      await expect(page).toHaveScreenshot();
    });

    test(`filled with data and operations panel expanded - ${theme}`, async ({
      page,
      commonPage,
      decisionsPage,
    }) => {
      await commonPage.changeTheme(theme);
      await page.addInitScript(() => {
        window.localStorage.setItem(
          'panelStates',
          JSON.stringify({
            isOperationsCollapsed: false,
          }),
        );
      }, theme);

      await page.route(
        URL_PATTERN,
        mockResponses({
          groupedDecisions: mockGroupedDecisions,
          batchOperations: mockBatchOperations,
          decisionInstances: mockDecisionInstances,
          decisionXml: mockDecisionXml,
        }),
      );

      await decisionsPage.navigateToDecisions({
        searchParams: {
          evaluated: 'true',
          failed: 'true',
          name: 'invoiceClassification',
          version: '2',
        },
        options: {
          waitUntil: 'networkidle',
        },
      });

      await expect(page).toHaveScreenshot();
    });

    test(`optional filters visible - ${theme}`, async ({
      page,
      commonPage,
      decisionsPage,
    }) => {
      await commonPage.changeTheme(theme);
      await page.addInitScript(() => {
        window.localStorage.setItem(
          'panelStates',
          JSON.stringify({
            isOperationsCollapsed: false,
          }),
        );
      }, theme);

      await page.route(
        URL_PATTERN,
        mockResponses({
          groupedDecisions: mockGroupedDecisions,
          batchOperations: mockBatchOperations,
          decisionInstances: mockDecisionInstances,
          decisionXml: mockDecisionXml,
        }),
      );

      await decisionsPage.navigateToDecisions({
        searchParams: {
          evaluated: 'true',
          failed: 'true',
          name: 'invoiceClassification',
          version: '2',
        },
        options: {
          waitUntil: 'networkidle',
        },
      });

      await decisionsPage.displayOptionalFilter('Process Instance Key');
      await decisionsPage.displayOptionalFilter('Decision Instance Key(s)');
      await decisionsPage.decisionInstanceKeysFilter.type('aaa');
      await decisionsPage.displayOptionalFilter('Evaluation Date Range');

      await expect(page).toHaveScreenshot();
    });
  }
});
