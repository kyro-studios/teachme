import { expect, test } from '@playwright/test';
import { prepareMockExamOpportunity, takeMockExamFromHome } from './mock-exam-helpers';

// AC1-20: 「模試に挑戦」を押すと、模試の結果の画面に見出しと項目が表示され、得点は 0 以上 100 以下の整数になる
test(
  '「模試に挑戦」を押すと、模試の結果の見出しと得点と偏差値を表示する',
  { tag: '@AC1-20' },
  async ({ page }) => {
    test.setTimeout(10 * 60_000);
    await prepareMockExamOpportunity(page);
    await takeMockExamFromHome(page);

    await expect(page.getByRole('heading', { name: '解答用紙', exact: true })).toBeVisible();
    await expect(page.getByRole('heading', { name: '生徒のコメント', exact: true })).toBeVisible();
    await expect(page.getByRole('group', { name: '得点', exact: true })).toBeVisible();
    await expect(page.getByRole('group', { name: '偏差値', exact: true })).toBeVisible();

    const scoreText = await page
      .getByRole('group', { name: '得点', exact: true })
      .getByText(/^\d+$/)
      .textContent();
    const score = Number(scoreText);
    expect(Number.isInteger(score)).toBe(true);
    expect(score).toBeGreaterThanOrEqual(0);
    expect(score).toBeLessThanOrEqual(100);
  },
);
