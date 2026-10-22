import { expect, test } from '@playwright/test';
import { testControlFor } from '../support/test-control';
import { ORGANIZE_TIMEOUT, openFirstMaterial, openMaterials, registerMaterial } from './materials-helpers';

// AC1-28: 整理が成功した教材の詳細に、整理の結果の内容を、見出しの下に表示する
test(
  '整理の結果の内容を、それぞれの見出しの下に表示する',
  { tag: '@AC1-28' },
  async ({ page }) => {
    await openMaterials(page);
    await testControlFor(page).scriptAi('organize', [
      {
        outcome: 'success',
        payload: {
          problemText: 'y = x^2 - 4x + 3 の頂点を求めなさい',
          figures: [],
          modelAnswer: '頂点は (2, -1)',
          explanationSteps: ['平方完成して頂点を読む'],
          studentWork: 'y = (x - 2)^2 と書いて手が止まっている',
          teachingPoints: ['平方完成の形を作る', '括弧の中の数を読む', '頂点の y 座標を求める'],
        },
      },
    ]);

    await registerMaterial(page);
    await openFirstMaterial(page);

    await expect(
      page.getByRole('region', { name: '問題' }).getByText('y = x^2 - 4x + 3 の頂点を求めなさい', { exact: true }),
    ).toBeVisible({ timeout: ORGANIZE_TIMEOUT });
    await expect(
      page.getByRole('region', { name: '模範解答' }).getByText('頂点は (2, -1)', { exact: true }),
    ).toBeVisible();
    await expect(
      page.getByRole('region', { name: '解説' }).getByText('平方完成して頂点を読む', { exact: true }),
    ).toBeVisible();
    await expect(
      page
        .getByRole('region', { name: '生徒の途中の解答' })
        .getByText('y = (x - 2)^2 と書いて手が止まっている', { exact: true }),
    ).toBeVisible();
  },
);
