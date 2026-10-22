import { expect, type Page } from '@playwright/test';

/** 学習記録の一覧に出す、単元「二次関数」の項目 (仕様書 5.8。教科と単元名の並び) */
export const RECORD_UNIT_LINK = '数学・二次関数';

/** 学習記録の画面を開き、単元「二次関数」の記録を開く。ホームの「学習記録」から入る */
export async function openRecordUnitFromHome(page: Page): Promise<void> {
  await page.goto('/home');
  const recordsButton = page.getByRole('button', { name: '学習記録', exact: true });
  await expect(recordsButton).toBeVisible();
  await recordsButton.click();
  await expect(page.getByRole('heading', { name: '学習記録', exact: true })).toBeVisible();

  const unitLink = page.getByRole('link', { name: RECORD_UNIT_LINK, exact: true });
  await expect(unitLink).toBeVisible();
  await unitLink.click();
  await expect(page.getByRole('heading', { name: '二次関数', exact: true })).toBeVisible();
}

/** 単元の記録から「学習レポート」を開き、学習レポートの見出しが出るまで待つ */
export async function openReportFromRecordUnit(page: Page): Promise<void> {
  const reportLink = page.getByRole('link', { name: '学習レポート', exact: true });
  await expect(reportLink).toBeVisible();
  await reportLink.click();
  await expect(page.getByRole('heading', { name: '学習レポート', exact: true })).toBeVisible();
}
