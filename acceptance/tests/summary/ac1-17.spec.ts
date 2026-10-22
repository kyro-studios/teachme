import { expect, test } from '@playwright/test';
import { organizeMaterial } from '../lesson/lesson-helpers';
import { testControlFor } from '../support/test-control';
import { startShortLesson, SUMMARY_FAILED_MESSAGE } from './summary-helpers';

// AC1-17: チャイムの後、まとめの作成が失敗すると、失敗の文言と「やり直す」を表示する
test(
  'まとめの作成が失敗すると、「まとめノートを作れませんでした」と「やり直す」を表示する',
  { tag: '@AC1-17' },
  async ({ page }) => {
    await organizeMaterial(page);
    await testControlFor(page).scriptAi('summary', [{ outcome: 'failure' }]);
    await startShortLesson(page);

    await expect(page.getByText(SUMMARY_FAILED_MESSAGE, { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'やり直す', exact: true })).toBeVisible();
  },
);
