import { expect, test } from '@playwright/test';
import { organizeMaterial, startLessonWithFirstMaterial } from '../lesson/lesson-helpers';
import { expectChime, summaryNoteHeading, talkOneRound } from './summary-helpers';

const LESSONS_TO_COMPLETE = 3;
const ROUNDS_PER_LESSON = 20;

// AC1-19: 同じ単元で授業を 3 回続けて完了すると、ホームに「模試に挑戦」を表示する
test(
  '授業を 3 回完了すると、ホームに「模試に挑戦」を表示する',
  { tag: '@AC1-19' },
  async ({ page }) => {
    test.setTimeout(10 * 60_000);
    await organizeMaterial(page);

    for (let lesson = 1; lesson <= LESSONS_TO_COMPLETE; lesson++) {
      await startLessonWithFirstMaterial(page);
      for (let round = 1; round <= ROUNDS_PER_LESSON; round++) {
        await talkOneRound(page, round);
      }
      await expectChime(page);
      await expect(summaryNoteHeading(page)).toBeVisible();
      await page.getByRole('button', { name: 'ホームに戻る', exact: true }).click();
      await expect(page.getByRole('heading', { name: 'ホーム', exact: true })).toBeVisible();
    }

    await expect(page.getByRole('button', { name: '模試に挑戦', exact: true })).toBeVisible();
  },
);
