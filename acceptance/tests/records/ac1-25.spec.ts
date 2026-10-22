import { expect, test } from '@playwright/test';
import { organizeMaterial } from '../lesson/lesson-helpers';
import { completeLessonWithoutTalk } from '../mock-exam/mock-exam-helpers';
import { openRecordUnitFromHome, RECORD_UNIT_LINK } from './records-helpers';

// AC1-25: 単元の学習記録を削除すると、確認の後、学習記録の一覧から単元が消え、学習記録がありませんを表示する
test(
  '単元の学習記録を削除すると、一覧から単元が消え、学習記録がありませんを表示する',
  { tag: '@AC1-25' },
  async ({ page }) => {
    test.setTimeout(10 * 60_000);
    await organizeMaterial(page);
    await completeLessonWithoutTalk(page);
    await openRecordUnitFromHome(page);

    const deleteButton = page.getByRole('button', { name: 'この単元の学習記録を削除', exact: true });
    await expect(deleteButton).toBeVisible();
    await deleteButton.click();
    const confirmButton = page.getByRole('button', { name: '削除する', exact: true });
    await expect(confirmButton).toBeVisible();
    await confirmButton.click();

    await expect(page.getByText('学習記録がありません', { exact: true })).toBeVisible();
    await expect(page.getByRole('link', { name: RECORD_UNIT_LINK, exact: true })).toHaveCount(0);

    // 画面の表示だけでなく、サーバーからも消えていることを、開き直して確かめる
    await page.goto('/records');
    await expect(page.getByText('学習記録がありません', { exact: true })).toBeVisible();
    await expect(page.getByRole('link', { name: RECORD_UNIT_LINK, exact: true })).toHaveCount(0);
  },
);
