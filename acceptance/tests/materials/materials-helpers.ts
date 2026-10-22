import { expect, type Page } from '@playwright/test';
import { openHomeAfterSetup } from '../home/home-helpers';

/** 教材の写真の確認のチェック (仕様書 5.3、10 節) */
export const MATERIAL_PHOTO_CONFIRM = '写真に氏名、学校名、連絡先が写っていないことを確認しました';

/** 整理は、フェイクの外部 AI でも画面の更新を待つ。ポーリング (2 秒) の分を含めて余裕を取る */
export const ORGANIZE_TIMEOUT = 15_000;

/** 単元「二次関数」の先生準備室を開く。初期セットアップを済ませ、ホームの「先生準備室へ」を押す */
export async function openMaterials(page: Page): Promise<void> {
  await openHomeAfterSetup(page);
  await page.getByRole('button', { name: '先生準備室へ', exact: true }).click();

  await expect(page.getByRole('heading', { name: '先生準備室', exact: true })).toBeVisible();
}

/** 教材の画像として使う、JPEG の署名を持つ小さな画像 (テストが用意する) */
export function materialPhoto(): { name: string; mimeType: string; buffer: Buffer } {
  return {
    name: 'material.jpg',
    mimeType: 'image/jpeg',
    buffer: Buffer.from([
      0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10, 0x4a, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00,
      0x01, 0x00, 0x01, 0x00, 0x00, 0xff, 0xd9,
    ]),
  };
}

/** 教材の画像を 1 枚選び、確認のチェックを入れて「登録する」を押す */
export async function registerMaterial(page: Page): Promise<void> {
  await page.getByLabel('教材を登録').setInputFiles(materialPhoto());
  await page.getByRole('checkbox', { name: MATERIAL_PHOTO_CONFIRM }).check();
  await page.getByRole('button', { name: '登録する', exact: true }).click();
}

/** 「教材 1」の詳細を開く。先生準備室の一覧から押す */
export async function openFirstMaterial(page: Page): Promise<void> {
  await page.getByRole('link', { name: '教材 1', exact: true }).click();
}
