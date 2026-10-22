import { expect, type Page } from '@playwright/test';

/** 宿題の写真の確認のチェック (仕様書 5.2、10 節) */
export const HOMEWORK_PHOTO_CONFIRM = '写真に氏名、学校名、連絡先が写っていないことを確認しました';

/**
 * 初期セットアップを完了して、ホームを開く。
 * 担当する生徒は「論理・ツッコミ派」、教科は「数学」、単元名は「二次関数」、週の授業回数は 3 回。
 */
export async function openHomeAfterSetup(page: Page): Promise<void> {
  await page.goto('/');

  await page.getByRole('radio', { name: /論理・ツッコミ派/ }).click();
  await page.getByRole('radio', { name: '数学', exact: true }).click();
  await page.getByRole('textbox', { name: '単元名' }).fill('二次関数');
  await page.getByRole('combobox', { name: '週の授業回数' }).selectOption({ label: '3 回' });
  await page
    .getByRole('checkbox', {
      name: '氏名、学校名、連絡先は話さず、ボードにも書かないことを確認しました',
    })
    .check();
  await page.getByRole('button', { name: '始める' }).click();

  await expect(page.getByRole('heading', { name: 'ホーム', exact: true })).toBeVisible();
}

/** 宿題の写真として使う、JPEG の署名を持つ小さな画像 (テストが用意する) */
export function homeworkPhoto(): { name: string; mimeType: string; buffer: Buffer } {
  return {
    name: 'homework.jpg',
    mimeType: 'image/jpeg',
    buffer: Buffer.from([
      0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10, 0x4a, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00,
      0x01, 0x00, 0x01, 0x00, 0x00, 0xff, 0xd9,
    ]),
  };
}
