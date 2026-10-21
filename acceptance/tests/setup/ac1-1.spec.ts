import { expect, test } from '@playwright/test';

// AC1-1: 初期セットアップが未完了の状態で、アプリを開く
test(
  '初期セットアップが未完了なら、担当する生徒の選択を表示し、ホームは表示しない',
  { tag: '@AC1-1' },
  async ({ page }) => {
    await page.goto('/');

    await expect(page.getByRole('heading', { name: '担当する生徒を選んでください' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'ホーム', exact: true })).toHaveCount(0);
  },
);
