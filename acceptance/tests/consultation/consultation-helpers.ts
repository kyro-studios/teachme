import { expect, type Page } from '@playwright/test';
import { ORGANIZE_TIMEOUT, openFirstMaterial, openMaterials, registerMaterial } from '../materials/materials-helpers';

/** 先輩先生との相談の画面の注意 (仕様書 5.4、10 節) */
export const CONSULTATION_PRIVACY_NOTICE = '氏名、学校名、連絡先は話さないでください';

/**
 * 単元「二次関数」の先生準備室で教材を 1 つ登録し、整理が済んだ教材の詳細から「先輩先生に相談する」を押して、
 * 相談の画面を開く。「話す」が押せるまで待つ。
 */
export async function startConsultationWithFirstMaterial(page: Page): Promise<void> {
  await openMaterials(page);
  await registerMaterial(page);
  await expect(page.getByText('整理済み', { exact: true })).toBeVisible({
    timeout: ORGANIZE_TIMEOUT,
  });
  await openFirstMaterial(page);
  await page.getByRole('button', { name: '先輩先生に相談する', exact: true }).click();
  await expect(page.getByRole('button', { name: '話す', exact: true })).toBeEnabled();
}

/** 相談の画面の字幕の一覧。1 件ごとに、表示名 (「あなた」か「先輩先生」) と文言を持つ */
export function consultationSubtitleList(page: Page) {
  return page.getByRole('list', { name: '字幕' });
}

/** 字幕の 1 件。表示名で絞り込む */
export function consultationSubtitleOf(page: Page, speaker: 'あなた' | '先輩先生') {
  return consultationSubtitleList(page)
    .getByRole('listitem')
    .filter({ has: page.getByText(speaker, { exact: true }) });
}
