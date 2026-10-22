import { expect, test } from '@playwright/test';
import { organizeMaterial } from '../lesson/lesson-helpers';
import { expectChime, startShortLesson, summaryNoteHeading } from './summary-helpers';

// AC1-14: 授業を始めてから、発話をせずに時間が経過すると、チャイムが鳴り、まとめノートに進む
test(
  '発話をせずに時間が経過すると、チャイムの文言を表示し、その後「まとめノート」の見出しを表示する',
  { tag: '@AC1-14' },
  async ({ page }) => {
    await organizeMaterial(page);
    await startShortLesson(page);

    await expectChime(page);
    await expect(summaryNoteHeading(page)).toBeVisible();
  },
);
