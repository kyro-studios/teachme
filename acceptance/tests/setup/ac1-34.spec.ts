import { expect, test } from '@playwright/test';

// AC1-34: 個人情報の確認のチェックを入れないまま、「始める」を見る
test('個人情報の確認にチェックを入れないと、「始める」を押せない', { tag: '@AC1-34' }, async ({ page }) => {
  await page.goto('/');

  await page.getByRole('radio', { name: /論理・ツッコミ派/ }).click();
  await page.getByRole('radio', { name: '数学', exact: true }).click();
  await page.getByRole('textbox', { name: '単元名' }).fill('二次関数');
  await page.getByRole('combobox', { name: '週の授業回数' }).selectOption({ label: '3 回' });

  await expect(page.getByRole('button', { name: '始める' })).toBeDisabled();
});
