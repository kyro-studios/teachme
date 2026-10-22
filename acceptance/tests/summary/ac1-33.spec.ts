import { expect, test } from '@playwright/test';
import { organizeMaterial } from '../lesson/lesson-helpers';
import { startShortLesson, summaryNoteHeading } from './summary-helpers';

// AC1-33: 授業を 1 回完了した後、同じブラウザでアプリを開き直しても、設定は残り、ホームに今週の授業を表示する
test(
  '開き直すと、セットアップに戻らず、ホームに「今週の授業 1 / 3 回」を表示する',
  { tag: '@AC1-33' },
  async ({ page }) => {
    await organizeMaterial(page);
    await startShortLesson(page);
    await expect(summaryNoteHeading(page)).toBeVisible();
    await page.getByRole('button', { name: 'ホームに戻る', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'ホーム', exact: true })).toBeVisible();

    // 同じブラウザ (同じ Cookie) で、新しいタブからアプリを開き直す
    const reopened = await page.context().newPage();
    await reopened.goto('/');

    await expect(reopened.getByRole('heading', { name: 'ホーム', exact: true })).toBeVisible();
    await expect(reopened.getByText('今週の授業 1 / 3 回', { exact: true })).toBeVisible();
    await expect(reopened.getByText('担当する生徒を選んでください', { exact: true })).toHaveCount(0);
  },
);
