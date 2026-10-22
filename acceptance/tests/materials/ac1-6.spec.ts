import { expect, test } from '@playwright/test';
import { testControlFor } from '../support/test-control';
import { ORGANIZE_TIMEOUT, openMaterials, registerMaterial } from './materials-helpers';

// AC1-6: 整理が失敗すると、「整理できませんでした」と「やり直す」を表示し、「やり直す」を押せる
test(
  '整理が失敗すると、整理できませんでしたと、やり直すを表示し、やり直すを押せる',
  { tag: '@AC1-6' },
  async ({ page }) => {
    await openMaterials(page);
    // 自動のやり直し (1 回) も失敗させ、2 回続けて失敗する状態にする
    await testControlFor(page).scriptAi('organize', [{ outcome: 'failure' }, { outcome: 'failure' }]);

    await registerMaterial(page);

    await expect(page.getByText('整理できませんでした', { exact: true })).toBeVisible({
      timeout: ORGANIZE_TIMEOUT,
    });
    await expect(page.getByRole('button', { name: 'やり直す', exact: true })).toBeEnabled();
  },
);
