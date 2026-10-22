import { expect, test } from '@playwright/test';
import { organizeMaterial } from '../lesson/lesson-helpers';
import { completeLessonWithoutTalk } from '../mock-exam/mock-exam-helpers';
import { openRecordUnitFromHome, openReportFromRecordUnit } from './records-helpers';

// AC1-32: 教材 1 件で授業を 1 回完了した後、学習レポートに、単元名、授業の回数、教えた問題の数、まだ模試がありませんが表示される
test(
  '授業を 1 回完了した学習レポートに、単元名、授業の回数、教えた問題の数、まだ模試がありませんを表示する',
  { tag: '@AC1-32' },
  async ({ page }) => {
    test.setTimeout(10 * 60_000);
    await organizeMaterial(page);
    await completeLessonWithoutTalk(page);
    await openRecordUnitFromHome(page);
    await openReportFromRecordUnit(page);

    await expect(page.getByText('単元名: 二次関数', { exact: true })).toBeVisible();
    await expect(page.getByText('授業の回数: 1 回', { exact: true })).toBeVisible();
    await expect(page.getByText('教えた問題の数: 1 問', { exact: true })).toBeVisible();
    await expect(page.getByText('まだ模試がありません', { exact: true })).toBeVisible();
  },
);
