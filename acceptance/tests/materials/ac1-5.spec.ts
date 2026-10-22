import { expect, test } from '@playwright/test';
import { ORGANIZE_TIMEOUT, openFirstMaterial, openMaterials, registerMaterial } from './materials-helpers';

// AC1-5: 写真を 1 枚選び、確認のチェックを入れて「登録する」を押すと、整理が成功し、教材の詳細に結果を表示する
test(
  '教材を登録して整理が成功すると、教材の詳細に 5 つの見出しと、指導のポイント 3 件を表示する',
  { tag: '@AC1-5' },
  async ({ page }) => {
    await openMaterials(page);

    await registerMaterial(page);
    await openFirstMaterial(page);

    for (const heading of ['問題', '模範解答', '解説', '生徒の途中の解答', '指導のポイント']) {
      await expect(page.getByRole('heading', { name: heading, exact: true })).toBeVisible({
        timeout: ORGANIZE_TIMEOUT,
      });
    }
    await expect(page.getByRole('region', { name: '指導のポイント' }).getByRole('listitem')).toHaveCount(3);
  },
);
