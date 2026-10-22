import { expect, test, type Locator } from '@playwright/test';
import { testControlFor } from '../support/test-control';
import { prepareMockExamOpportunity, takeMockExamFromHome } from './mock-exam-helpers';

const ANSWER_SHEET = 'x = 2 で最小値 -1';
const STUDENT_COMMENT = '先生の説明で頂点の求め方は分かりました';

// AC1-31: 模試の結果の画面で、解答用紙と生徒のコメントが、それぞれの見出しの下に表示される
test(
  '解答用紙と生徒のコメントを、それぞれの見出しの下に表示する',
  { tag: '@AC1-31' },
  async ({ page }) => {
    test.setTimeout(10 * 60_000);
    await prepareMockExamOpportunity(page);
    await testControlFor(page).scriptAi('mock_exam', [
      {
        outcome: 'success',
        payload: {
          problemText: 'y = x^2 - 4x + 3 の頂点を求めなさい',
          answerSheet: ANSWER_SHEET,
          studentComment: STUDENT_COMMENT,
        },
      },
    ]);
    await takeMockExamFromHome(page);

    const answerHeading = page.getByRole('heading', { name: '解答用紙', exact: true });
    const answer = page.getByText(ANSWER_SHEET, { exact: true });
    const commentHeading = page.getByRole('heading', { name: '生徒のコメント', exact: true });
    const comment = page.getByText(STUDENT_COMMENT, { exact: true });

    await expect(answer).toBeVisible();
    await expect(comment).toBeVisible();
    await expectBelow(answer, answerHeading);
    await expectBelow(comment, commentHeading);
  },
);

/** `lower` が `upper` の下に、縦の位置で並んでいることを確かめる */
async function expectBelow(lower: Locator, upper: Locator): Promise<void> {
  const lowerBox = await lower.boundingBox();
  const upperBox = await upper.boundingBox();
  expect(lowerBox).not.toBeNull();
  expect(upperBox).not.toBeNull();
  expect(lowerBox!.y).toBeGreaterThan(upperBox!.y);
}
