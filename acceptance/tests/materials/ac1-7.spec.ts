import { expect, test } from '@playwright/test';
import { testControlFor } from '../support/test-control';
import {
  ORGANIZE_TIMEOUT,
  openFirstMaterial,
  openMaterials,
  registerMaterial,
} from './materials-helpers';

// AC1-7: 整理が 2 回続けて失敗した教材で「やり直す」を押すと、整理をもう一度始め、結果を表示する
test(
  '整理が失敗した教材で、やり直すを押すと、整理が成功して指導のポイントを表示する',
  { tag: '@AC1-7' },
  async ({ page }) => {
    await openMaterials(page);
    const testControl = testControlFor(page);
    await testControl.scriptAi('organize', [{ outcome: 'failure' }, { outcome: 'failure' }]);
    await registerMaterial(page);
    await expect(page.getByText('整理できませんでした', { exact: true })).toBeVisible({
      timeout: ORGANIZE_TIMEOUT,
    });

    // 次の整理は成功する状態にしてから、やり直すを押す
    await testControl.scriptAi('organize', [{ outcome: 'success' }]);
    await page.getByRole('button', { name: 'やり直す', exact: true }).click();

    await expect(page.getByText('整理済み', { exact: true })).toBeVisible({ timeout: ORGANIZE_TIMEOUT });
    await openFirstMaterial(page);
    await expect(page.getByRole('heading', { name: '指導のポイント', exact: true })).toBeVisible();
    await expect(page.getByRole('region', { name: '指導のポイント' }).getByRole('listitem')).toHaveCount(3);
  },
);
