import { expect, test } from '@playwright/test';
import { organizeMaterial } from '../lesson/lesson-helpers';
import { testControlFor } from '../support/test-control';
import { startShortLesson, summaryNoteHeading } from './summary-helpers';

const SUMMARY_TEXT = '今日は頂点の求め方を教わりました。平方完成で頂点を読みます。';

// AC1-16: チャイムの後、まとめの作成が成功すると、まとめノートにまとめ文を表示する
test(
  'まとめの作成が成功すると、「まとめノート」と「今日のまとめ」の見出しと、まとめ文を表示する',
  { tag: '@AC1-16' },
  async ({ page }) => {
    await organizeMaterial(page);
    await testControlFor(page).scriptAi('summary', [
      { outcome: 'success', payload: { summaryText: SUMMARY_TEXT, quotes: [] } },
    ]);
    await startShortLesson(page);

    await expect(summaryNoteHeading(page)).toBeVisible();
    await expect(page.getByRole('heading', { name: '今日のまとめ', exact: true })).toBeVisible();
    await expect(page.getByText(SUMMARY_TEXT, { exact: true })).toBeVisible();
  },
);
