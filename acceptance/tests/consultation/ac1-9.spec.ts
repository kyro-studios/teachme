import { expect, test } from '@playwright/test';
import { testControlFor } from '../support/test-control';
import { consultationSubtitleOf, startConsultationWithFirstMaterial } from './consultation-helpers';

const RECOGNIZED = 'ここの式変形が分かりません';
const REPLY = 'まず、平方完成の形を作ってみましょう。';

/** 相談の上限は 15 往復 (仕様書 9 節)。往復ごとに、録音、認識、応答、音声の再生を行う */
const MAX_ROUNDS = 15;

// AC1-9: 相談で 15 往復すると、相談の時間が終わりを表示し、「話す」を押せなくする
test(
  '15 往復すると「相談の時間が終わりました」を表示し、「話す」を押せなくする',
  { tag: '@AC1-9' },
  async ({ page }) => {
    test.setTimeout(180_000);
    await startConsultationWithFirstMaterial(page);
    const control = testControlFor(page);
    await control.scriptAi('stt', [{ outcome: 'success', payload: { text: RECOGNIZED } }], {
      repeat: true,
    });
    await control.scriptAi(
      'mentor_reply',
      [{ outcome: 'success', payload: { text: REPLY, boardWrites: [] } }],
      { repeat: true },
    );

    const talk = page.getByRole('button', { name: '話す', exact: true });
    for (let round = 1; round <= MAX_ROUNDS; round++) {
      await talk.click();
      await page.getByRole('button', { name: '話し終える', exact: true }).click();
      await expect(consultationSubtitleOf(page, '先輩先生')).toHaveCount(round);
    }

    await expect(page.getByText('相談の時間が終わりました', { exact: true })).toBeVisible({
      timeout: 30_000,
    });
    await expect(talk).toBeDisabled();
  },
);
