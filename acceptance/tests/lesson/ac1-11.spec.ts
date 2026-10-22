import { expect, test } from '@playwright/test';
import { organizeMaterial, startLessonWithFirstMaterial, subtitleList, subtitleOf } from './lesson-helpers';

// AC1-11: 授業を始めると、AI 生徒の最初の発話を字幕「生徒」に 1 件表示する
test(
  '授業を始めると、字幕「生徒」の最初の発話が 1 件表示される',
  { tag: '@AC1-11' },
  async ({ page }) => {
    await organizeMaterial(page);
    await startLessonWithFirstMaterial(page);

    await expect(subtitleList(page).getByRole('listitem')).toHaveCount(1);
    await expect(subtitleOf(page, '生徒')).toHaveCount(1);
  },
);
