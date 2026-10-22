import { expect, test } from '@playwright/test';
import { organizeMaterial } from '../lesson/lesson-helpers';
import { startShortLesson, summaryNoteHeading } from './summary-helpers';

// AC1-26: 授業を 1 回完了し、まとめノートの画面で「ホームに戻る」を押すと、ホームに今週の授業の回数を表示する
test(
  '「ホームに戻る」を押すと、ホームに「今週の授業 1 / 3 回」を表示する',
  { tag: '@AC1-26' },
  async ({ page }) => {
    await organizeMaterial(page);
    await startShortLesson(page);
    await expect(summaryNoteHeading(page)).toBeVisible();

    await page.getByRole('button', { name: 'ホームに戻る', exact: true }).click();

    await expect(page.getByRole('heading', { name: 'ホーム', exact: true })).toBeVisible();
    await expect(page.getByText('今週の授業 1 / 3 回', { exact: true })).toBeVisible();
  },
);
