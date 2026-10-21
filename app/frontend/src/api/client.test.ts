import { afterEach, describe, expect, test, vi } from "vitest";
import { ApiError, apiFetch } from "./client";

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

/** 失敗するはずの呼び出しを待ち、投げられた ApiError を返す */
async function catchError(promise: Promise<unknown>): Promise<ApiError> {
  try {
    await promise;
  } catch (error) {
    return error as ApiError;
  }
  throw new Error("エラーが起きませんでした");
}

function stubFetch(response: Response) {
  const fetchMock = vi.fn().mockResolvedValue(response);
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("apiFetch", () => {
  test("/api の下へ GET を送り、JSON の応答を返す", async () => {
    const fetchMock = stubFetch(jsonResponse({ completed: false, settings: null }));

    const result = await apiFetch<{ completed: boolean }>("/settings");

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/settings",
      expect.objectContaining({ method: "GET" }),
    );
    expect(result).toEqual({ completed: false, settings: null });
  });

  test("JSON の body は文字列にし、Content-Type を付けて送る", async () => {
    const fetchMock = stubFetch(jsonResponse({ ok: true }));

    await apiFetch("/today-mode", { method: "PUT", body: { todayMode: "self_study" } });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/today-mode");
    expect(init.method).toBe("PUT");
    expect(init.headers).toEqual({ "Content-Type": "application/json" });
    expect(init.body).toBe(JSON.stringify({ todayMode: "self_study" }));
  });

  test("FormData の body は、そのまま送り、Content-Type を付けない", async () => {
    const fetchMock = stubFetch(jsonResponse({ ok: true }));
    const form = new FormData();
    form.append("audio", new Blob(["voice"]), "voice.webm");

    await apiFetch("/lessons/1/utterances", { method: "POST", body: form });

    const [, init] = fetchMock.mock.calls[0];
    expect(init.body).toBe(form);
    expect(init.headers).toEqual({});
  });

  test("keepalive を fetch へ渡す (画面を離れるときの中断)", async () => {
    const fetchMock = stubFetch(new Response(null, { status: 204 }));

    await apiFetch("/lessons/1/abandon", { method: "POST", keepalive: true });

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/lessons/1/abandon",
      expect.objectContaining({ method: "POST", keepalive: true }),
    );
  });

  test("204 は本文が無いので、undefined を返す", async () => {
    stubFetch(new Response(null, { status: 204 }));

    await expect(apiFetch("/consultations/1/abandon", { method: "POST" })).resolves.toBeUndefined();
  });

  test("エラーの本体を ApiError にし、code、message、fields を取り出せる", async () => {
    stubFetch(
      jsonResponse(
        {
          error: {
            code: "validation_error",
            message: "単元名は 1 文字以上 30 文字以内で入力してください",
            fields: { unitName: "単元名は 1 文字以上 30 文字以内で入力してください" },
          },
        },
        400,
      ),
    );

    const error = await catchError(apiFetch("/settings", { method: "PUT", body: {} }));

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(400);
    expect(error.code).toBe("validation_error");
    expect(error.message).toBe("単元名は 1 文字以上 30 文字以内で入力してください");
    expect(error.fields).toEqual({
      unitName: "単元名は 1 文字以上 30 文字以内で入力してください",
    });
  });

  test("409 の setup_required も、code で判定できる", async () => {
    stubFetch(
      jsonResponse(
        { error: { code: "setup_required", message: "初期セットアップを済ませてください" } },
        409,
      ),
    );

    const error = await catchError(apiFetch("/home"));

    expect(error.status).toBe(409);
    expect(error.code).toBe("setup_required");
    expect(error.fields).toBeUndefined();
  });

  test("エラーの本体が JSON でないときは、code を unknown にして通信の失敗を伝える", async () => {
    stubFetch(new Response("<html>Bad Gateway</html>", { status: 502 }));

    const error = await catchError(apiFetch("/lessons", { method: "POST" }));

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(502);
    expect(error.code).toBe("unknown");
    expect(error.message).toBe("サーバーとの通信に失敗しました");
  });
});
