import { expect, test } from '@playwright/test';
import { organizeMaterial } from '../lesson/lesson-helpers';
import { testControlFor } from '../support/test-control';
import {
  completeLessonWithOneTurn,
  LESSONS_TO_OPEN_MOCK_EXAM,
  TALKED_LESSON_SECONDS,
  takeMockExamFromHome,
} from './mock-exam-helpers';

// AC1-30: 指導のポイント 3 件がすべて度合い 2 の授業を 3 回完了し、挑戦時の理解度 90 で、得点 90、偏差値 69.0 になる
test(
  '指導のポイントをすべて分かりやすく伝えた授業を 3 回完了すると、得点 90 と偏差値 69.0 の模試の結果になる',
  { tag: '@AC1-30' },
  async ({ page }) => {
    test.setTimeout(10 * 60_000);
    const control = testControlFor(page);
    await control.setLimits({ lessonSeconds: TALKED_LESSON_SECONDS });
    await control.scriptAi('evaluation', [{ outcome: 'success', payload: { degrees: [2, 2, 2] } }], {
      repeat: true,
    });

    await organizeMaterial(page);
    for (let lesson = 1; lesson <= LESSONS_TO_OPEN_MOCK_EXAM; lesson++) {
      await completeLessonWithOneTurn(page);
    }
    await takeMockExamFromHome(page);

    await expect(
      page.getByRole('group', { name: '得点', exact: true }).getByText('90', { exact: true }),
    ).toBeVisible();
    await expect(
      page.getByRole('group', { name: '偏差値', exact: true }).getByText('69.0', { exact: true }),
    ).toBeVisible();

    const history = page.getByRole('list', { name: '偏差値の推移', exact: true });
    await expect(history.getByRole('listitem')).toHaveCount(1);
    await expect(history.getByRole('listitem')).toContainText('69.0');
  },
);
