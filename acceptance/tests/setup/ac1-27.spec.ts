import { expect, test } from '@playwright/test';

// AC1-27: 初期セットアップが未完了の状態で、担当する生徒の選択肢を見る
test(
  '担当する生徒の選択肢に、3 種類と、それぞれの初期偏差値と目標偏差値を表示する',
  { tag: '@AC1-27' },
  async ({ page }) => {
    await page.goto('/');

    const group = page.getByRole('radiogroup', { name: '担当する生徒' });
    await expect(group.getByRole('radio')).toHaveCount(3);

    const expected = [
      { name: '直感・感覚派', initial: '初期偏差値 45', target: '目標偏差値 50' },
      { name: '真面目な暗記派', initial: '初期偏差値 52', target: '目標偏差値 58' },
      { name: '論理・ツッコミ派', initial: '初期偏差値 60', target: '目標偏差値 65' },
    ];
    for (const type of expected) {
      const option = page.locator('label').filter({ hasText: type.name });
      await expect(option).toContainText(type.initial);
      await expect(option).toContainText(type.target);
    }
  },
);
