import { expect, test } from '@playwright/test';
import { testControlFor } from '../support/test-control';
import { organizeMaterial, startLessonWithFirstMaterial, subtitleOf } from './lesson-helpers';

const RECOGNIZED = '二次関数の頂点は平方完成で求めます';
const REPLY = 'では、平方完成の形を作ると頂点が読めますね。';

// AC1-12: 「話す」「話し終える」で発話すると、字幕「あなた」に認識結果、続けて字幕「生徒」の応答を表示する
test(
  '発話すると、字幕「あなた」に認識結果を表示し、続けて字幕「生徒」の応答を 1 件表示する',
  { tag: '@AC1-12' },
  async ({ page }) => {
    await organizeMaterial(page);
    await startLessonWithFirstMaterial(page);
    const control = testControlFor(page);
    await control.scriptAi('stt', [{ outcome: 'success', payload: { text: RECOGNIZED } }]);
    await control.scriptAi('student_reply', [
      { outcome: 'success', payload: { text: REPLY, expression: 'normal', annotation: null } },
    ]);

    await page.getByRole('button', { name: '話す', exact: true }).click();
    await page.getByRole('button', { name: '話し終える', exact: true }).click();

    await expect(subtitleOf(page, 'あなた')).toContainText(RECOGNIZED);
    await expect(subtitleOf(page, '生徒').filter({ hasText: REPLY })).toHaveCount(1);
    await expect(subtitleOf(page, '生徒').filter({ hasText: REPLY })).toBeVisible();
  },
);
