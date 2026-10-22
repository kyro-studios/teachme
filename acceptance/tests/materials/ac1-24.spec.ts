import { expect, test } from '@playwright/test';
import {
  ORGANIZE_TIMEOUT,
  openFirstMaterial,
  openMaterials,
  registerMaterial,
} from './materials-helpers';

// AC1-24: 教材の詳細で「教材を削除」を押し、確認で「削除する」を押すと、教材が一覧から消える
test(
  '教材を削除すると、一覧から消え、教材はまだありませんを表示する',
  { tag: '@AC1-24' },
  async ({ page }) => {
    await openMaterials(page);
    await registerMaterial(page);
    await openFirstMaterial(page);
    await expect(page.getByRole('heading', { name: '問題', exact: true })).toBeVisible({
      timeout: ORGANIZE_TIMEOUT,
    });

    await page.getByRole('button', { name: '教材を削除', exact: true }).click();
    await page.getByRole('alertdialog').getByRole('button', { name: '削除する', exact: true }).click();

    await expect(page.getByRole('link', { name: '教材 1', exact: true })).toHaveCount(0);
    await expect(page.getByText('教材はまだありません', { exact: true })).toBeVisible();
  },
);
