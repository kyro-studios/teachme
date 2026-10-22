import { expect, type Page } from '@playwright/test';
import { startLessonWithFirstMaterial, subtitleOf } from '../lesson/lesson-helpers';
import { testControlFor } from '../support/test-control';

/** 発話をせずに期限に達させるための、授業の時間の上限 (秒)。仕様書 9 節の 10 分の代わりに使う */
export const SHORT_LESSON_SECONDS = 3;

/** チャイムの文言 (仕様書 5.5、10 節) */
export const CHIME_MESSAGE = 'チャイムが鳴りました。授業のまとめに入ります';

/** まとめノートの作成が失敗したときの文言 (仕様書 5.6、10 節) */
export const SUMMARY_FAILED_MESSAGE = 'まとめノートを作れませんでした';

/** 時間の上限を短くして、「二次関数」の教材で授業を始める。発話をしなければ、期限でチャイムに進む */
export async function startShortLesson(page: Page): Promise<void> {
  await testControlFor(page).setLimits({ lessonSeconds: SHORT_LESSON_SECONDS });
  await startLessonWithFirstMaterial(page);
}

/**
 * 利用者の発話を 1 往復させる。`round` は 1 から数える。
 * 応答の字幕「生徒」が増え、「話す」が押せる状態に戻るまで待つ (開始の発話は字幕「生徒」の 1 件目)
 */
export async function talkOneRound(page: Page, round: number): Promise<void> {
  await page.getByRole('button', { name: '話す', exact: true }).click();
  await page.getByRole('button', { name: '話し終える', exact: true }).click();
  await expect(subtitleOf(page, '生徒')).toHaveCount(round + 1);
}

/** チャイムの文言が出ていることを確かめる */
export async function expectChime(page: Page): Promise<void> {
  await expect(page.getByText(CHIME_MESSAGE, { exact: true })).toBeVisible();
}

/** まとめノートの見出しが出ていることを確かめる (仕様書 10 節) */
export function summaryNoteHeading(page: Page) {
  return page.getByRole('heading', { name: 'まとめノート', exact: true });
}
