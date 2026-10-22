import { expect, test } from '@playwright/test';
import { openHomeAfterSetup } from './home-helpers';

// AC1-3: 「自習指示に切り替える」を押すと、指示の文を表示し、「授業を始める」は表示しない
test(
  '自習指示に切り替えると、指示の文を表示し、授業を始めるを表示しない',
  { tag: '@AC1-3' },
  async ({ page }) => {
    await openHomeAfterSetup(page);

    await page.getByRole('button', { name: '自習指示に切り替える', exact: true }).click();

    await expect(page.getByText('今日は自習指示の日です', { exact: true })).toBeVisible();
    await expect(
      page.getByText('今日は「二次関数」の教材から、問題を 1 つ解いてみてください。', { exact: true }),
    ).toBeVisible();
    await expect(page.getByRole('button', { name: '授業を始める', exact: true })).toHaveCount(0);
  },
);
