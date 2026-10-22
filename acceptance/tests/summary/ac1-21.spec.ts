import { readFile } from 'node:fs/promises';
import { expect, test } from '@playwright/test';
import { organizeMaterial } from '../lesson/lesson-helpers';
import { startShortLesson, summaryNoteHeading } from './summary-helpers';

const PNG_SIGNATURE = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);

// AC1-21: まとめノートの画面で「画像を保存」を押すと、PNG 形式の画像ファイルがダウンロードされる
test(
  '「画像を保存」を押すと、PNG 形式の画像ファイルをダウンロードする',
  { tag: '@AC1-21' },
  async ({ page }) => {
    await organizeMaterial(page);
    await startShortLesson(page);
    await expect(summaryNoteHeading(page)).toBeVisible();

    const downloading = page.waitForEvent('download');
    await page.getByRole('button', { name: '画像を保存', exact: true }).click();
    const download = await downloading;

    expect(download.suggestedFilename()).toMatch(/\.png$/);
    const bytes = await readFile(await download.path());
    expect(bytes.subarray(0, PNG_SIGNATURE.length).equals(PNG_SIGNATURE)).toBe(true);
  },
);
