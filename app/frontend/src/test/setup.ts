// 単体テストの共通の準備。Vitest は globals を使わないため、後片付けを明示する
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

// jsdom に無い ResizeObserver を、Radix の部品 (Checkbox、RadioGroup) が使うための空の代わり
if (!("ResizeObserver" in globalThis)) {
  class ResizeObserverStub {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
  Object.defineProperty(globalThis, "ResizeObserver", { value: ResizeObserverStub });
}

afterEach(() => {
  cleanup();
});
