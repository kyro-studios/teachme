import { expect, test } from '@playwright/test';
import { testControlFor } from '../support/test-control';
import { organizeMaterial, startLessonWithFirstMaterial } from './lesson-helpers';

// AC1-15: 生徒の応答を作る LLM が失敗すると、やり直しの文言を表示し、「話す」を押せる状態に戻る
test(
  '生徒の応答が失敗すると、文言を表示し、「話す」を押せる状態に戻る',
  { tag: '@AC1-15' },
  async ({ page }) => {
    await organizeMaterial(page);
    await startLessonWithFirstMaterial(page);
    await testControlFor(page).scriptAi('student_reply', [{ outcome: 'failure' }]);

    await page.getByRole('button', { name: '話す', exact: true }).click();
    await page.getByRole('button', { name: '話し終える', exact: true }).click();

    await expect(
      page.getByText('生徒の応答を作れませんでした。もう一度話してください', { exact: true }),
    ).toBeVisible();
    await expect(page.getByRole('button', { name: '話す', exact: true })).toBeEnabled();
  },
);
