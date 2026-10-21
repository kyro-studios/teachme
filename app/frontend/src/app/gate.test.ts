import { describe, expect, test } from "vitest";
import { gateRedirect } from "./gate";

describe("gateRedirect", () => {
  test("設定が未完了なら、「/」も、ほかの画面も初期セットアップへ寄せる", () => {
    expect(gateRedirect("/", false)).toBe("/setup");
    expect(gateRedirect("/home", false)).toBe("/setup");
    expect(gateRedirect("/materials", false)).toBe("/setup");
    expect(gateRedirect("/records/units/1", false)).toBe("/setup");
  });

  test("設定が未完了でも、初期セットアップは開ける", () => {
    expect(gateRedirect("/setup", false)).toBeNull();
  });

  test("設定が完了していれば、「/」はホームへ寄せる", () => {
    expect(gateRedirect("/", true)).toBe("/home");
  });

  test("設定が完了していれば、ほかの画面 (設定の変更を含む) はそのまま開ける", () => {
    expect(gateRedirect("/home", true)).toBeNull();
    expect(gateRedirect("/setup", true)).toBeNull();
    expect(gateRedirect("/lessons/new", true)).toBeNull();
  });
});
