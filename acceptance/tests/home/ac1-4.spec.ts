import { expect, test } from '@playwright/test';
import { HOMEWORK_PHOTO_CONFIRM, homeworkPhoto, openHomeAfterSetup } from './home-helpers';

// AC1-4: 宿題の写真を選び、確認のチェックを入れて「判定する」を押すと、判定とコメントを表示する
test(
  '宿題の写真を判定すると、判定の欄に正解とコメントを表示する',
  { tag: '@AC1-4' },
  async ({ page }) => {
    await openHomeAfterSetup(page);

    await page.getByRole('button', { name: '宿題の丸つけに切り替える', exact: true }).click();
    await page.getByLabel('宿題の写真を登録').setInputFiles(homeworkPhoto());
    await page.getByRole('checkbox', { name: HOMEWORK_PHOTO_CONFIRM }).check();
    await page.getByRole('button', { name: '判定する', exact: true }).click();

    await expect(page.getByRole('heading', { name: '判定', exact: true })).toBeVisible();
    await expect(page.getByText('正解', { exact: true })).toBeVisible();
    await expect(page.getByText('よくできました', { exact: true })).toBeVisible();
  },
);
