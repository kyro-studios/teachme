import { readFile } from 'node:fs/promises';
import { expect, test } from '@playwright/test';
import { organizeMaterial } from '../lesson/lesson-helpers';
import { completeLessonWithoutTalk } from '../mock-exam/mock-exam-helpers';
import { openRecordUnitFromHome, openReportFromRecordUnit } from './records-helpers';

const PNG_SIGNATURE = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);

// AC1-23: 学習レポートの画面で「画像を保存」を押すと、PNG 形式の画像ファイルをダウンロードする
test(
  '学習レポートで「画像を保存」を押すと、PNG 形式の画像ファイルをダウンロードする',
  { tag: '@AC1-23' },
  async ({ page }) => {
    test.setTimeout(10 * 60_000);
    await organizeMaterial(page);
    await completeLessonWithoutTalk(page);
    await openRecordUnitFromHome(page);
    await openReportFromRecordUnit(page);

    const downloading = page.waitForEvent('download');
    await page.getByRole('button', { name: '画像を保存', exact: true }).click();
    const download = await downloading;

    expect(download.suggestedFilename()).toMatch(/\.png$/);
    const bytes = await readFile(await download.path());
    expect(bytes.subarray(0, PNG_SIGNATURE.length).equals(PNG_SIGNATURE)).toBe(true);
  },
);
