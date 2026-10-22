import { expect, test } from '@playwright/test';
import { organizeMaterial } from '../lesson/lesson-helpers';
import {
  completeLessonWithOneTurn,
  LESSONS_TO_OPEN_MOCK_EXAM,
  takeMockExamFromHome,
  TALKED_LESSON_SECONDS,
} from '../mock-exam/mock-exam-helpers';
import { testControlFor } from '../support/test-control';
import { openRecordUnitFromHome, openReportFromRecordUnit } from './records-helpers';

// AC1-36: 授業を 3 回完了して模試に挑戦した後、学習レポートに、授業の回数、教えた問題の数、最新の模試が表示される
test(
  '授業を 3 回完了して模試を保存した後、学習レポートに授業の回数、教えた問題の数、最新の模試を表示する',
  { tag: '@AC1-36' },
  async ({ page }) => {
    test.setTimeout(10 * 60_000);
    const control = testControlFor(page);
    // 3 回の授業の度合いを 2 にして、挑戦時の理解度を 90 にする (模試の得点 90、偏差値 69.0)
    await control.setLimits({ lessonSeconds: TALKED_LESSON_SECONDS });
    await control.scriptAi('evaluation', [{ outcome: 'success', payload: { degrees: [2, 2, 2] } }], {
      repeat: true,
    });

    await organizeMaterial(page);
    for (let lesson = 1; lesson <= LESSONS_TO_OPEN_MOCK_EXAM; lesson++) {
      await completeLessonWithOneTurn(page);
    }
    await takeMockExamFromHome(page);

    await openRecordUnitFromHome(page);
    await openReportFromRecordUnit(page);

    await expect(page.getByText('授業の回数: 3 回', { exact: true })).toBeVisible();
    await expect(page.getByText('教えた問題の数: 1 問', { exact: true })).toBeVisible();
    await expect(page.getByText('最新の模試: 得点 90、偏差値 69.0', { exact: true })).toBeVisible();
  },
);
