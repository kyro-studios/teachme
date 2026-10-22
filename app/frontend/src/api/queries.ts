// TanStack Query の問い合わせの定義。画面と app の入口の判定は、ここで定めた queryKey を使う
import { queryOptions } from "@tanstack/react-query";
import { apiFetch } from "./client";
import type { components, paths } from "./schema";

/** 実行時の設定 (GET /config)。`aiMode` が fake のとき、録音の代わりに固定の音声を送る */
export type Config = paths["/config"]["get"]["responses"][200]["content"]["application/json"];

export type SettingsResponse = components["schemas"]["SettingsResponse"];

export type StudentTypesResponse =
  paths["/student-types"]["get"]["responses"][200]["content"]["application/json"];

export type Home = components["schemas"]["Home"];

/** 模試の結果 (`GET /mock-exams/{id}`、`POST /mock-exams` の応答)。`history` は同じ単元の偏差値の推移 */
export type MockExamResult = components["schemas"]["MockExamResult"];

export const configQuery = queryOptions({
  queryKey: ["config"],
  queryFn: () => apiFetch<Config>("/config"),
});

export const settingsQuery = queryOptions({
  queryKey: ["settings"],
  queryFn: () => apiFetch<SettingsResponse>("/settings"),
});

export const studentTypesQuery = queryOptions({
  queryKey: ["student-types"],
  queryFn: () => apiFetch<StudentTypesResponse>("/student-types"),
});

export const homeQuery = queryOptions({
  queryKey: ["home"],
  queryFn: () => apiFetch<Home>("/home"),
});

/** 模試の結果 (`GET /mock-exams/{id}`)。挑戦の直後は、その結果を置いておき、結果の画面をすぐ表示する */
export function mockExamQuery(mockExamId: string) {
  return queryOptions({
    queryKey: ["mock-exam", mockExamId],
    queryFn: () => apiFetch<MockExamResult>(`/mock-exams/${mockExamId}`),
  });
}
