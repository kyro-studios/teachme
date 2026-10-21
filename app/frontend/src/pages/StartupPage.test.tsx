import { renderToString } from "react-dom/server";
import { expect, test } from "vitest";
import { StartupPage } from "./StartupPage";

test("起動を確かめる画面にアプリケーションの名前を出す", () => {
  expect(renderToString(<StartupPage />)).toContain("teachme");
});
