import { expect, type Page } from '@playwright/test';
import { organizeMaterial, startLessonWithFirstMaterial } from '../lesson/lesson-helpers';
import {
  CHIME_MESSAGE,
  startShortLesson,
  summaryNoteHeading,
  talkOneRound,
} from '../summary/summary-helpers';

/** 模試の機会ができる、授業の完了の回数 (仕様書 5.7) */
export const LESSONS_TO_OPEN_MOCK_EXAM = 3;

/** 発話を 1 往復させる授業の時間の上限 (秒)。発話の後に期限を待つため、発話をしない授業より長くする */
export const TALKED_LESSON_SECONDS = 45;

/** 期限のチャイムを待つ時間 (ミリ秒) */
const CHIME_TIMEOUT = 90_000;

/** まとめノートの作成を待つ時間 (ミリ秒) */
const SUMMARY_TIMEOUT = 30_000;

/** ホームの見出し (仕様書 10 節) */
export function homeHeading(page: Page) {
  return page.getByRole('heading', { name: 'ホーム', exact: true });
}

/** まとめノートから、ホームに戻る */
export async function goHomeFromSummary(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'ホームに戻る', exact: true }).click();
  await expect(homeHeading(page)).toBeVisible();
}

/** 発話をせずに、期限のチャイムで授業を完了させ、ホームに戻る (模試の機会を作るための授業) */
export async function completeLessonWithoutTalk(page: Page): Promise<void> {
  await startShortLesson(page);
  await expect(summaryNoteHeading(page)).toBeVisible({ timeout: SUMMARY_TIMEOUT });
  await goHomeFromSummary(page);
}

/**
 * 発話を 1 往復させてから、期限のチャイムで授業を完了させ、ホームに戻る。
 * 授業の時間の上限は、呼び出し側で `TALKED_LESSON_SECONDS` にしておく
 */
export async function completeLessonWithOneTurn(page: Page): Promise<void> {
  await startLessonWithFirstMaterial(page);
  await talkOneRound(page, 1);
  await expect(page.getByText(CHIME_MESSAGE, { exact: true })).toBeVisible({
    timeout: CHIME_TIMEOUT,
  });
  await expect(summaryNoteHeading(page)).toBeVisible({ timeout: SUMMARY_TIMEOUT });
  await goHomeFromSummary(page);
}

/**
 * 「二次関数」の教材を整理し、発話をしない授業を 3 回完了して、ホームに「模試に挑戦」を出す
 */
export async function prepareMockExamOpportunity(page: Page): Promise<void> {
  await organizeMaterial(page);
  for (let lesson = 1; lesson <= LESSONS_TO_OPEN_MOCK_EXAM; lesson++) {
    await completeLessonWithoutTalk(page);
  }
  await expect(page.getByRole('button', { name: '模試に挑戦', exact: true })).toBeVisible();
}

/** ホームの「模試に挑戦」を押し、模試の結果の画面が開くまで待つ */
export async function takeMockExamFromHome(page: Page): Promise<void> {
  await page.getByRole('button', { name: '模試に挑戦', exact: true }).click();
  await expect(page.getByRole('heading', { name: '模試の結果', exact: true })).toBeVisible();
}
