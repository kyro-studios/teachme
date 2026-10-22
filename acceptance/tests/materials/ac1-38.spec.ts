import { expect, test } from '@playwright/test';
import { MATERIAL_PHOTO_CONFIRM, materialPhoto, openMaterials } from './materials-helpers';

// AC1-38: 写真を選んでも、確認のチェックが無ければ「登録する」を押せない
test(
  '確認のチェックが無いと、教材の画像を選んでも登録するを押せない',
  { tag: '@AC1-38' },
  async ({ page }) => {
    await openMaterials(page);

    await page.getByLabel('教材を登録').setInputFiles(materialPhoto());

    // 画像が選ばれ、確認のチェックが表示されたことを確かめてから、ボタンを見る
    await expect(page.getByRole('checkbox', { name: MATERIAL_PHOTO_CONFIRM })).toBeVisible();
    await expect(page.getByRole('button', { name: '登録する', exact: true })).toBeDisabled();
  },
);
