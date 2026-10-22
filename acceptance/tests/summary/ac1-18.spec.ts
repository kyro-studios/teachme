import { expect, test } from '@playwright/test';
import { organizeMaterial } from '../lesson/lesson-helpers';
import { testControlFor } from '../support/test-control';
import { startShortLesson } from './summary-helpers';

const SUMMARY_TEXT = 'やり直しのまとめです。頂点の y 座標を読みました。';

// AC1-18: まとめの作成が失敗して「やり直す」が出ているとき、「やり直す」を押すと、まとめを作り直して表示する
test(
  '「やり直す」を押すと、まとめの作成をもう一度行い、「今日のまとめ」の見出しとまとめ文を表示する',
  { tag: '@AC1-18' },
  async ({ page }) => {
    await organizeMaterial(page);
    await testControlFor(page).scriptAi('summary', [
      { outcome: 'failure' },
      { outcome: 'success', payload: { summaryText: SUMMARY_TEXT, quotes: [] } },
    ]);
    await startShortLesson(page);
    await expect(page.getByRole('button', { name: 'やり直す', exact: true })).toBeVisible();

    await page.getByRole('button', { name: 'やり直す', exact: true }).click();

    await expect(page.getByRole('heading', { name: '今日のまとめ', exact: true })).toBeVisible();
    await expect(page.getByText(SUMMARY_TEXT, { exact: true })).toBeVisible();
  },
);
