import { expect, test } from '@playwright/test';
import { LESSON_PRIVACY_NOTICE, organizeMaterial, startLessonWithFirstMaterial } from './lesson-helpers';

// AC1-35: 授業の画面を開くと、「氏名、学校名、連絡先は話さないでください」を表示する
test(
  '授業の画面に、氏名、学校名、連絡先は話さないでくださいを表示する',
  { tag: '@AC1-35' },
  async ({ page }) => {
    await organizeMaterial(page);
    await startLessonWithFirstMaterial(page);

    await expect(page.getByText(LESSON_PRIVACY_NOTICE, { exact: true })).toBeVisible();
  },
);
