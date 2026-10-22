import { expect, test } from '@playwright/test';
import { testControlFor } from '../support/test-control';
import { consultationSubtitleOf, startConsultationWithFirstMaterial } from './consultation-helpers';

const RECOGNIZED = 'ここの式変形が分かりません';
const REPLY = 'まず、平方完成の形を作ってみましょう。';

// AC1-8: 相談で発話すると、字幕「あなた」に認識結果、続けて字幕「先輩先生」の応答を 1 件表示する
test(
  '発話すると、字幕「あなた」に認識結果を表示し、続けて字幕「先輩先生」の応答を 1 件表示する',
  { tag: '@AC1-8' },
  async ({ page }) => {
    await startConsultationWithFirstMaterial(page);
    const control = testControlFor(page);
    await control.scriptAi('stt', [{ outcome: 'success', payload: { text: RECOGNIZED } }]);
    await control.scriptAi('mentor_reply', [
      { outcome: 'success', payload: { text: REPLY, boardWrites: [] } },
    ]);

    await page.getByRole('button', { name: '話す', exact: true }).click();
    await page.getByRole('button', { name: '話し終える', exact: true }).click();

    await expect(consultationSubtitleOf(page, 'あなた')).toContainText(RECOGNIZED);
    await expect(consultationSubtitleOf(page, '先輩先生')).toHaveCount(1);
    await expect(consultationSubtitleOf(page, '先輩先生').filter({ hasText: REPLY })).toBeVisible();
  },
);
