import { expect, test } from '@playwright/test';
import { testControlFor } from '../support/test-control';
import { ORGANIZE_TIMEOUT, openMaterials, registerMaterial } from '../materials/materials-helpers';
import { boardText, startLessonWithFirstMaterial } from './lesson-helpers';

const PROBLEM_TEXT = 'y = x^2 - 4x + 3 の頂点を求めなさい';
const STUDENT_WORK = 'y = (x - 2)^2 と書いて手が止まっている';

// AC1-29: 授業のボードの初期配置は、問題文と生徒の途中の解答だけを置き、模範解答と解説は出さない
test(
  'ボードの「問題」の下に問題文、「生徒のノート」の下に途中の解答を置き、答えと解説は出さない',
  { tag: '@AC1-29' },
  async ({ page }) => {
    await openMaterials(page);
    await testControlFor(page).scriptAi('organize', [
      {
        outcome: 'success',
        payload: {
          problemText: PROBLEM_TEXT,
          figures: [],
          modelAnswer: '頂点は (2, -1)',
          explanationSteps: ['平方完成して頂点を読む'],
          studentWork: STUDENT_WORK,
          teachingPoints: ['平方完成の形を作る', '括弧の中の数を読む', '頂点の y 座標を求める'],
        },
      },
    ]);
    await registerMaterial(page);
    await expect(page.getByText('整理済み', { exact: true })).toBeVisible({
      timeout: ORGANIZE_TIMEOUT,
    });
    await startLessonWithFirstMaterial(page);

    const problemHeading = boardText(page, '問題');
    const problemText = boardText(page, PROBLEM_TEXT);
    const studentHeading = boardText(page, '生徒のノート');
    const studentWork = boardText(page, STUDENT_WORK);
    await expect(problemText).toBeVisible();
    await expect(studentWork).toBeVisible();

    // 見出しの下に、それぞれの内容を置く
    const headingBox = await problemHeading.boundingBox();
    const problemBox = await problemText.boundingBox();
    const studentHeadingBox = await studentHeading.boundingBox();
    const studentBox = await studentWork.boundingBox();
    expect(headingBox && problemBox && problemBox.y > headingBox.y).toBe(true);
    expect(studentHeadingBox && studentBox && studentBox.y > studentHeadingBox.y).toBe(true);

    await expect(page.getByText('頂点は (2, -1)', { exact: true })).toHaveCount(0);
    await expect(page.getByText('平方完成して頂点を読む', { exact: true })).toHaveCount(0);
  },
);
