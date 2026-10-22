import { expect, test } from '@playwright/test';
import { HOMEWORK_PHOTO_CONFIRM, homeworkPhoto, openHomeAfterSetup } from './home-helpers';

// AC1-37: 宿題の写真を選んでも、確認のチェックが無ければ「判定する」を押せない
test(
  '確認のチェックが無いと、宿題の写真を選んでも判定するを押せない',
  { tag: '@AC1-37' },
  async ({ page }) => {
    await openHomeAfterSetup(page);

    await page.getByRole('button', { name: '宿題の丸つけに切り替える', exact: true }).click();
    await page.getByLabel('宿題の写真を登録').setInputFiles(homeworkPhoto());

    // 写真が選ばれ、確認のチェックが表示されたことを確かめてから、ボタンを見る
    await expect(page.getByRole('checkbox', { name: HOMEWORK_PHOTO_CONFIRM })).toBeVisible();
    await expect(page.getByRole('button', { name: '判定する', exact: true })).toBeDisabled();
  },
);
