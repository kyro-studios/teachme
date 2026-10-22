import { expect, type Page } from '@playwright/test';
import { ORGANIZE_TIMEOUT, openMaterials, registerMaterial } from '../materials/materials-helpers';

/** 授業の画面の注意 (仕様書 5.5、10 節) */
export const LESSON_PRIVACY_NOTICE = '氏名、学校名、連絡先は話さないでください';

/** 単元「二次関数」の先生準備室で教材を 1 つ登録し、整理が終わるまで待つ */
export async function organizeMaterial(page: Page): Promise<void> {
  await openMaterials(page);
  await registerMaterial(page);
  await expect(page.getByText('整理済み', { exact: true })).toBeVisible({
    timeout: ORGANIZE_TIMEOUT,
  });
}

/**
 * ホームから「授業を始める」を押し、整理済みの「教材 1」を選んで、授業の画面を開く。
 * AI 生徒の最初の発話が出るまで待つ。最初の発話も生徒の応答の呼び出しなので、テストが応答を決める前に済ませておく
 */
export async function startLessonWithFirstMaterial(page: Page): Promise<void> {
  await page.goto('/home');
  await page.getByRole('button', { name: '授業を始める', exact: true }).click();
  await page.getByRole('radio', { name: '教材 1', exact: true }).click();
  await page.getByRole('button', { name: '授業を始める', exact: true }).click();
  await expect(subtitleOf(page, '生徒')).toHaveCount(1);
}

/**
 * ボードの中の文言。tldraw は、文字の寸法を測るための隠れた要素を同じ文言で持つことがあるため、見えている要素だけを対象にする
 */
export function boardText(page: Page, text: string) {
  return page
    .getByRole('region', { name: 'ボード' })
    .getByText(text, { exact: true })
    .filter({ visible: true });
}

/** 授業の画面の字幕の一覧。1 件ごとに、表示名 (「あなた」か「生徒」) と文言を持つ */
export function subtitleList(page: Page) {
  return page.getByRole('list', { name: '字幕' });
}

/** 字幕の 1 件。表示名で絞り込む */
export function subtitleOf(page: Page, speaker: 'あなた' | '生徒') {
  return subtitleList(page)
    .getByRole('listitem')
    .filter({ has: page.getByText(speaker, { exact: true }) });
}
