import { expect, test } from '@playwright/test';

// AC1-2: 初期セットアップを入力して「始める」を押すと、ホームに設定の内容が表示される
test(
  '初期セットアップを入力して始めると、ホームに生徒、教科と単元、今週の授業の回数を表示する',
  { tag: '@AC1-2' },
  async ({ page }) => {
    await page.goto('/');

    await page.getByRole('radio', { name: /論理・ツッコミ派/ }).click();
    await page.getByRole('radio', { name: '数学', exact: true }).click();
    await page.getByRole('textbox', { name: '単元名' }).fill('二次関数');
    await page.getByRole('combobox', { name: '週の授業回数' }).selectOption({ label: '3 回' });
    await page
      .getByRole('checkbox', {
        name: '氏名、学校名、連絡先は話さず、ボードにも書かないことを確認しました',
      })
      .check();
    await page.getByRole('button', { name: '始める' }).click();

    await expect(page.getByRole('heading', { name: 'ホーム', exact: true })).toBeVisible();
    await expect(page.getByText('論理・ツッコミ派', { exact: true })).toBeVisible();
    await expect(page.getByText('数学・二次関数', { exact: true })).toBeVisible();
    await expect(page.getByText('今週の授業 0 / 3 回', { exact: true })).toBeVisible();
  },
);
