import { describe, expect, test } from "vitest";
import { unitNameLength, validateSetup, type SetupFormValues } from "./setup-validation";

const VALID: SetupFormValues = {
  studentTypeId: "logical",
  subject: "math",
  unitName: "二次関数",
  lessonsPerWeek: 3,
  privacyConfirmed: true,
};

describe("validateSetup", () => {
  test("すべての入力が有効なら、送る値を組み立てる (単元名は前後の空白を除く)", () => {
    const result = validateSetup({ ...VALID, unitName: "  二次関数  " });

    expect(result).toEqual({
      ok: true,
      input: {
        studentTypeId: "logical",
        subject: "math",
        unitName: "二次関数",
        lessonsPerWeek: 3,
        privacyConfirmed: true,
      },
    });
  });

  test("何も選んでいなければ、項目ごとの文言を返す", () => {
    const result = validateSetup({
      studentTypeId: null,
      subject: null,
      unitName: "",
      lessonsPerWeek: null,
      privacyConfirmed: false,
    });

    expect(result).toEqual({
      ok: false,
      errors: {
        studentTypeId: "生徒を 1 人選んでください",
        subject: "教科を選んでください",
        unitName: "単元名は 1 文字以上 30 文字以内で入力してください",
        lessonsPerWeek: "週の授業回数を選んでください",
      },
    });
  });

  test("単元名が空白だけなら、1 文字以上の文言を返す", () => {
    const result = validateSetup({ ...VALID, unitName: "   " });

    expect(result).toMatchObject({
      ok: false,
      errors: { unitName: "単元名は 1 文字以上 30 文字以内で入力してください" },
    });
  });

  test("単元名は、前後の空白を除いて 30 文字までを有効とし、31 文字からは無効とする", () => {
    expect(validateSetup({ ...VALID, unitName: "あ".repeat(30) }).ok).toBe(true);
    expect(validateSetup({ ...VALID, unitName: `  ${"あ".repeat(30)}  ` }).ok).toBe(true);

    expect(validateSetup({ ...VALID, unitName: "あ".repeat(31) })).toMatchObject({
      ok: false,
      errors: { unitName: "単元名は 1 文字以上 30 文字以内で入力してください" },
    });
  });

  test("週の授業回数は 1 以上 7 以下の整数だけを有効とする", () => {
    expect(validateSetup({ ...VALID, lessonsPerWeek: 1 }).ok).toBe(true);
    expect(validateSetup({ ...VALID, lessonsPerWeek: 7 }).ok).toBe(true);

    for (const lessonsPerWeek of [0, 8, 2.5]) {
      expect(validateSetup({ ...VALID, lessonsPerWeek })).toMatchObject({
        ok: false,
        errors: { lessonsPerWeek: "週の授業回数を選んでください" },
      });
    }
  });
});

describe("unitNameLength", () => {
  test("前後の空白を除き、文字の数で数える", () => {
    expect(unitNameLength("  二次関数  ")).toBe(4);
    expect(unitNameLength("   ")).toBe(0);
  });
});
