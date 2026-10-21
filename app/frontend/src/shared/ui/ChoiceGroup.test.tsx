import { fireEvent, render, screen } from "@testing-library/react";
import { expect, test, vi } from "vitest";
import { ChoiceGroup } from "./ChoiceGroup";

const options = [
  { value: "intuitive", label: "直感・感覚派 (初期偏差値 45)" },
  { value: "diligent", label: "真面目な暗記派 (初期偏差値 52)" },
  { value: "logical", label: "論理・ツッコミ派 (初期偏差値 60)" },
] as const;

test("選択肢をラベルの文言で見つけられ、何も選んでいなければどれも選ばれていない", () => {
  render(
    <ChoiceGroup label="担当する生徒" options={options} value={null} onValueChange={() => {}} />,
  );

  expect(screen.getByRole("radiogroup", { name: "担当する生徒" })).toBeTruthy();
  for (const option of options) {
    const radio = screen.getByRole("radio", { name: option.label });
    expect(radio.getAttribute("aria-checked")).toBe("false");
  }
});

test("選んだ値を、選択肢に反映する", () => {
  render(
    <ChoiceGroup
      label="担当する生徒"
      options={options}
      value="diligent"
      onValueChange={() => {}}
    />,
  );

  expect(
    screen
      .getByRole("radio", { name: "真面目な暗記派 (初期偏差値 52)" })
      .getAttribute("aria-checked"),
  ).toBe("true");
});

test("押した選択肢の値を onValueChange へ渡す", () => {
  const onValueChange = vi.fn();
  render(
    <ChoiceGroup
      label="担当する生徒"
      options={options}
      value={null}
      onValueChange={onValueChange}
    />,
  );

  fireEvent.click(screen.getByRole("radio", { name: "論理・ツッコミ派 (初期偏差値 60)" }));

  expect(onValueChange).toHaveBeenCalledWith("logical");
});
