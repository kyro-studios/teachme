import { expect, test } from '@playwright/test';
import { organizeMaterial, startLessonWithFirstMaterial } from '../lesson/lesson-helpers';
import { expectChime, summaryNoteHeading, talkOneRound } from './summary-helpers';

// AC1-13: 授業中、利用者の発話と生徒の応答を 20 往復すると、チャイムが鳴り、まとめノートに進む
test(
  '20 往復すると、チャイムの文言を表示し、その後「まとめノート」の見出しを表示する',
  { tag: '@AC1-13' },
  async ({ page }) => {
    test.setTimeout(180_000);
    await organizeMaterial(page);
    await startLessonWithFirstMaterial(page);

    for (let round = 1; round <= 20; round++) {
      await talkOneRound(page, round);
    }

    await expectChime(page);
    await expect(summaryNoteHeading(page)).toBeVisible();
  },
);
