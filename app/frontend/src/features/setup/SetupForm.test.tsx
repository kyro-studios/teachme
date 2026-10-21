import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, test, vi } from "vitest";
import { ApiError } from "../../api/client";
import { SetupForm, type SetupFormProps } from "./SetupForm";

const STUDENT_TYPES = [
  { id: "intuitive", displayName: "直感・感覚派", initialDeviation: 45, targetDeviation: 50 },
  { id: "diligent", displayName: "真面目な暗記派", initialDeviation: 52, targetDeviation: 58 },
  { id: "logical", displayName: "論理・ツッコミ派", initialDeviation: 60, targetDeviation: 65 },
] as const;

const PRIVACY = "氏名、学校名、連絡先は話さず、ボードにも書かないことを確認しました";

function renderForm(props: Partial<SetupFormProps> = {}) {
  const onSubmit = vi.fn();
  render(
    <SetupForm
      mode="initial"
      studentTypes={STUDENT_TYPES}
      pending={false}
      onSubmit={onSubmit}
      {...props}
    />,
  );
  return { onSubmit };
}

function fillAll() {
  fireEvent.click(screen.getByRole("radio", { name: /論理・ツッコミ派/ }));
  fireEvent.click(screen.getByRole("radio", { name: "数学" }));
  fireEvent.change(screen.getByRole("textbox", { name: "単元名" }), {
    target: { value: "二次関数" },
  });
  fireEvent.change(screen.getByRole("combobox", { name: "週の授業回数" }), {
    target: { value: "3" },
  });
}

describe("SetupForm (初回)", () => {
  test("3 種類の生徒を、偏差値と一緒に選択肢として出す", () => {
    renderForm();

    expect(screen.getByRole("heading", { name: "担当する生徒を選んでください" })).toBeTruthy();
    expect(screen.getAllByRole("radio", { name: /偏差値/ })).toHaveLength(3);
    expect(screen.getByText("初期偏差値 60")).toBeTruthy();
    expect(screen.getByText("目標偏差値 65")).toBeTruthy();
  });

  test("個人情報の確認にチェックが無いと、「始める」を押せない", () => {
    renderForm();
    fillAll();

    expect(screen.getByRole("button", { name: "始める" }).hasAttribute("disabled")).toBe(true);
  });

  test("個人情報の確認にチェックを入れると、「始める」を押せる", () => {
    renderForm();
    fillAll();

    fireEvent.click(screen.getByRole("checkbox", { name: PRIVACY }));

    expect(screen.getByRole("button", { name: "始める" }).hasAttribute("disabled")).toBe(false);
  });

  test("無効な入力のまま押すと、項目ごとの文言を出し、保存を呼ばない", () => {
    const { onSubmit } = renderForm();
    fireEvent.click(screen.getByRole("checkbox", { name: PRIVACY }));

    fireEvent.click(screen.getByRole("button", { name: "始める" }));

    expect(screen.getByText("生徒を 1 人選んでください")).toBeTruthy();
    expect(screen.getByText("教科を選んでください")).toBeTruthy();
    expect(screen.getByText("単元名は 1 文字以上 30 文字以内で入力してください")).toBeTruthy();
    expect(screen.getByText("週の授業回数を選んでください")).toBeTruthy();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  test("有効な入力で押すと、整えた値を onSubmit へ渡す", () => {
    const { onSubmit } = renderForm();
    fillAll();
    fireEvent.click(screen.getByRole("checkbox", { name: PRIVACY }));

    fireEvent.click(screen.getByRole("button", { name: "始める" }));

    expect(onSubmit).toHaveBeenCalledWith({
      studentTypeId: "logical",
      subject: "math",
      unitName: "二次関数",
      lessonsPerWeek: 3,
      privacyConfirmed: true,
    });
  });

  test("保存の API が項目の文言を返したら、その項目の下に出す", () => {
    const serverError = new ApiError(400, {
      code: "validation_error",
      message: "入力に誤りがあります",
      fields: { unitName: "単元名は 1 文字以上 30 文字以内で入力してください" },
    });
    renderForm({ serverError });

    expect(screen.getByText("単元名は 1 文字以上 30 文字以内で入力してください")).toBeTruthy();
  });

  test("送信中は、「始める」を押せない", () => {
    renderForm({ pending: true });
    fireEvent.click(screen.getByRole("checkbox", { name: PRIVACY }));

    expect(screen.getByRole("button", { name: "始める" }).hasAttribute("disabled")).toBe(true);
  });
});

describe("SetupForm (設定の変更)", () => {
  const saved = {
    studentTypeId: "diligent",
    subject: "science",
    unitId: "00000000-0000-4000-8000-000000000001",
    unitName: "電気",
    lessonsPerWeek: 5,
    privacyConfirmed: true,
  } as const;

  test("生徒は選べず、保存されている値を初期値として出す", () => {
    renderForm({ mode: "edit", saved });

    expect(screen.queryByRole("radiogroup", { name: "担当する生徒" })).toBeNull();
    expect(screen.getByText("真面目な暗記派")).toBeTruthy();
    expect(screen.getByRole("radio", { name: "理科" }).getAttribute("aria-checked")).toBe("true");
    expect(screen.getByRole("textbox", { name: "単元名" })).toHaveProperty("value", "電気");
    expect(screen.getByRole("combobox", { name: "週の授業回数" })).toHaveProperty("value", "5");
  });

  test("個人情報の確認にチェックが無いと、「保存する」を押せない", () => {
    renderForm({ mode: "edit", saved });

    expect(screen.getByRole("button", { name: "保存する" }).hasAttribute("disabled")).toBe(true);
  });

  test("チェックを入れて押すと、保存されている生徒の ID を含めて onSubmit へ渡す", () => {
    const { onSubmit } = renderForm({ mode: "edit", saved });
    fireEvent.click(screen.getByRole("checkbox", { name: PRIVACY }));

    fireEvent.click(screen.getByRole("button", { name: "保存する" }));

    expect(onSubmit).toHaveBeenCalledWith({
      studentTypeId: "diligent",
      subject: "science",
      unitName: "電気",
      lessonsPerWeek: 5,
      privacyConfirmed: true,
    });
  });
});
