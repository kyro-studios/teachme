import { act, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, test, vi } from "vitest";
import { AVATAR_ASSETS } from "./avatar-config";
import { AVATAR_EXPRESSIONS, type AvatarExpression } from "./avatar-model";
import { Avatar } from "./Avatar";
import { canRenderLive2D, createLive2DView, type Live2DView } from "./live2d";

// Live2D の描画は、WebGL と実際のモデルが要るため、テストでは差し替える
vi.mock("./live2d", () => ({
  canRenderLive2D: vi.fn(() => false),
  createLive2DView: vi.fn(),
}));

const canRender = vi.mocked(canRenderLive2D);
const createView = vi.mocked(createLive2DView);

function fakeView() {
  return { setExpression: vi.fn(), playMotion: vi.fn(), destroy: vi.fn() } satisfies Live2DView;
}

beforeEach(() => {
  vi.clearAllMocks();
  canRender.mockReturnValue(false);
});

test("Live2D が使えないとき、静止画の代替を、話し手と表情の名前で出し、読み込みを試みない", () => {
  render(<Avatar character="intuitive" expression="troubled" gesture="nod" />);

  expect(screen.getByRole("img", { name: "直感・感覚派、困り" })).toBeTruthy();
  expect(createView).not.toHaveBeenCalled();
});

test("静止画の代替は、表情が変わると口の形も変わる", () => {
  const mouths = AVATAR_EXPRESSIONS.map((expression: AvatarExpression) => {
    const { container, unmount } = render(
      <Avatar character="mentor" expression={expression} gesture="talk" />,
    );
    const mouth = container.querySelector('[data-part="mouth"]')?.getAttribute("d");
    unmount();
    return mouth;
  });

  expect(mouths.every((mouth) => typeof mouth === "string")).toBe(true);
  expect(new Set(mouths).size).toBe(AVATAR_EXPRESSIONS.length);
});

test("身振りの「考える」は、静止画の代替の頭を傾ける", () => {
  const { container } = render(
    <Avatar character="logical" expression="thinking" gesture="think" />,
  );

  expect(container.querySelector("svg")?.getAttribute("class")).toContain("-rotate-6");
});

test("Live2D の読み込みに失敗しても、静止画の代替のまま画面が崩れない", async () => {
  canRender.mockReturnValue(true);
  createView.mockRejectedValue(new Error("model not found"));
  const { container } = render(<Avatar character="diligent" expression="normal" gesture="nod" />);

  await waitFor(() => expect(container.querySelector("canvas")).toBeNull());
  expect(screen.getByRole("img", { name: "真面目な暗記派、通常" })).toBeTruthy();
  expect(container.querySelector("svg")).not.toBeNull();
});

test("Live2D が描けるとき、話し手のモデルを読み、表情と身振りをモデルへ渡す", async () => {
  canRender.mockReturnValue(true);
  const view = fakeView();
  createView.mockResolvedValue(view);
  const { container, rerender, unmount } = render(
    <Avatar character="mentor" expression="normal" gesture="nod" />,
  );

  await waitFor(() => expect(view.playMotion).toHaveBeenCalledWith(AVATAR_ASSETS.motionGroups.nod));
  expect(createView.mock.calls[0]?.[1]).toEqual({
    cubismCoreUrl: AVATAR_ASSETS.cubismCoreUrl,
    modelUrl: AVATAR_ASSETS.modelUrls.mentor,
  });
  expect(view.setExpression).toHaveBeenLastCalledWith(AVATAR_ASSETS.expressionNames.normal);
  // 描画できたら、静止画の代替は出さない
  expect(container.querySelector("svg")).toBeNull();

  rerender(<Avatar character="mentor" expression="troubled" gesture="talk" />);
  expect(view.setExpression).toHaveBeenLastCalledWith(AVATAR_ASSETS.expressionNames.troubled);
  expect(view.playMotion).toHaveBeenLastCalledWith(AVATAR_ASSETS.motionGroups.talk);

  unmount();
  expect(view.destroy).toHaveBeenCalledTimes(1);
});

test("話し手が変わると、前のモデルを捨て、その話し手のモデルを読む", async () => {
  canRender.mockReturnValue(true);
  const first = fakeView();
  const second = fakeView();
  createView.mockResolvedValueOnce(first).mockResolvedValueOnce(second);
  const { rerender } = render(<Avatar character="intuitive" expression="normal" gesture="nod" />);
  await waitFor(() => expect(first.setExpression).toHaveBeenCalled());

  rerender(<Avatar character="logical" expression="normal" gesture="nod" />);

  expect(first.destroy).toHaveBeenCalledTimes(1);
  await waitFor(() => expect(second.setExpression).toHaveBeenCalled());
  expect(createView.mock.calls[1]?.[1].modelUrl).toBe(AVATAR_ASSETS.modelUrls.logical);
});

test("読み込みの途中で話し手が変わったら、後から届いた古いモデルは描かずに捨てる", async () => {
  canRender.mockReturnValue(true);
  let resolveFirst: (view: Live2DView) => void = () => {};
  createView.mockReturnValueOnce(
    new Promise<Live2DView>((resolve) => {
      resolveFirst = resolve;
    }),
  );
  createView.mockResolvedValueOnce(fakeView());
  const { rerender } = render(<Avatar character="intuitive" expression="normal" gesture="nod" />);

  rerender(<Avatar character="logical" expression="normal" gesture="nod" />);
  const stale = fakeView();
  await act(async () => {
    resolveFirst(stale);
  });

  expect(stale.destroy).toHaveBeenCalledTimes(1);
  expect(stale.setExpression).not.toHaveBeenCalled();
});
