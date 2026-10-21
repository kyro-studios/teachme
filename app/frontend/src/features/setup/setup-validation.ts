// 初期セットアップの入力の検査 (仕様書 5.1)。画面の表示と、送る値の組み立てを、純粋な関数で行う
import type { components } from "../../api/schema";

export type SettingsInput = components["schemas"]["SettingsInput"];
export type StudentTypeId = components["schemas"]["StudentTypeId"];
export type Subject = components["schemas"]["Subject"];

export const UNIT_NAME_MAX_LENGTH = 30;
export const LESSONS_PER_WEEK_MIN = 1;
export const LESSONS_PER_WEEK_MAX = 7;

/** 個人情報の確認のチェックボックスの文言 (仕様書 10 節) */
export const PRIVACY_LABEL = "氏名、学校名、連絡先は話さず、ボードにも書かないことを確認しました";

/** 画面の入力。まだ選んでいない項目は null や空の文字列で持つ */
export type SetupFormValues = {
  studentTypeId: StudentTypeId | null;
  subject: Subject | null;
  unitName: string;
  lessonsPerWeek: number | null;
  privacyConfirmed: boolean;
};

export type SetupFieldName = "studentTypeId" | "subject" | "unitName" | "lessonsPerWeek";

/** 項目ごとの文言。仕様書 10 節の初期セットアップのエラー */
export type SetupFieldErrors = Partial<Record<SetupFieldName, string>>;

export type SetupValidation =
  | { ok: true; input: SettingsInput }
  | { ok: false; errors: SetupFieldErrors };

/** 単元名の文字数。前後の空白を除き、文字 (コードポイント) で数える */
export function unitNameLength(unitName: string): number {
  return Array.from(unitName.trim()).length;
}

/** 入力を検査し、有効なら送る値を返す。無効な項目があれば、項目ごとの文言を返す */
export function validateSetup(values: SetupFormValues): SetupValidation {
  const errors: SetupFieldErrors = {};
  const { studentTypeId, subject, lessonsPerWeek } = values;

  if (studentTypeId === null) {
    errors.studentTypeId = "生徒を 1 人選んでください";
  }
  if (subject === null) {
    errors.subject = "教科を選んでください";
  }
  const length = unitNameLength(values.unitName);
  if (length < 1 || length > UNIT_NAME_MAX_LENGTH) {
    errors.unitName = "単元名は 1 文字以上 30 文字以内で入力してください";
  }
  if (
    lessonsPerWeek === null ||
    !Number.isInteger(lessonsPerWeek) ||
    lessonsPerWeek < LESSONS_PER_WEEK_MIN ||
    lessonsPerWeek > LESSONS_PER_WEEK_MAX
  ) {
    errors.lessonsPerWeek = "週の授業回数を選んでください";
  }

  if (
    Object.keys(errors).length > 0 ||
    studentTypeId === null ||
    subject === null ||
    lessonsPerWeek === null
  ) {
    return { ok: false, errors };
  }
  return {
    ok: true,
    input: {
      studentTypeId,
      subject,
      unitName: values.unitName.trim(),
      lessonsPerWeek,
      privacyConfirmed: values.privacyConfirmed,
    },
  };
}
