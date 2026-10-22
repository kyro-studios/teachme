import { expect, test } from '@playwright/test';
import { boardText, organizeMaterial, startLessonWithFirstMaterial } from './lesson-helpers';

// AC1-10: 整理済みの教材を選び、「授業を始める」を押すと、ボードに「問題」と「生徒のノート」の見出しを表示する
test(
  '授業を始めると、ボードに「問題」と「生徒のノート」の見出しを表示する',
  { tag: '@AC1-10' },
  async ({ page }) => {
    await organizeMaterial(page);
    await startLessonWithFirstMaterial(page);

    await expect(boardText(page, '問題')).toBeVisible();
    await expect(boardText(page, '生徒のノート')).toBeVisible();
  },
);
