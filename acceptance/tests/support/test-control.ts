import type { APIRequestContext, APIResponse, Page } from '@playwright/test';

/** 外部 AI の呼び出しの種類 (openapi.yaml の `AiScript` の `kind`)。 */
export type AiCallKind =
  | 'organize'
  | 'student_reply'
  | 'evaluation'
  | 'mentor_reply'
  | 'summary'
  | 'mock_exam'
  | 'homework'
  | 'stt'
  | 'tts';

/** 外部 AI の 1 回の応答。成功のときは、`kind` ごとの中身 (`payload`) を任意で付ける。省くと既定の成功の応答になる。 */
export type AiScriptResponse =
  | { outcome: 'success'; payload?: Record<string, unknown> }
  | { outcome: 'failure' };

export interface AiScriptOptions {
  /** true なら、最後の応答を使い切ったあとも、それを返し続ける。 */
  repeat?: boolean;
}

/** 授業と相談の時間の上限 (秒)。省いた項目は、既定の 600 秒に戻る。 */
export interface TimeLimits {
  lessonSeconds?: number;
  consultationSeconds?: number;
}

/**
 * テストから、フェイクの外部 AI と、授業と相談の時間の上限を操作する (`/api/test/*`)。
 *
 * 利用者は Cookie で識別され、呼んだ利用者の分だけに効く。`AI_MODE=fake` で起動したときだけ使える。
 */
export class TestControl {
  constructor(private readonly request: APIRequestContext) {}

  /**
   * 外部 AI の `kind` の応答を、先頭から 1 つずつ決める。同じ `kind` をもう一度決めると、待ち列を置き換える。
   */
  async scriptAi(
    kind: AiCallKind,
    responses: AiScriptResponse[],
    options: AiScriptOptions = {},
  ): Promise<void> {
    const response = await this.request.put('/api/test/ai-script', {
      data: { kind, repeat: options.repeat ?? false, responses },
    });
    ensureOk(response, 'PUT /api/test/ai-script');
  }

  /** 決めた応答と、呼び出しの数えを、すべて消す。 */
  async resetAi(): Promise<void> {
    const response = await this.request.delete('/api/test/ai-script');
    ensureOk(response, 'DELETE /api/test/ai-script');
  }

  /** 外部 AI の、種類ごとの呼び出しの回数。呼ばれていない種類は 0 になる。 */
  async aiCalls(): Promise<Record<AiCallKind, number>> {
    const response = await this.request.get('/api/test/ai-calls');
    ensureOk(response, 'GET /api/test/ai-calls');
    const body = (await response.json()) as { counts: Record<AiCallKind, number> };
    return body.counts;
  }

  /** 授業と相談の時間の上限を決める。このあと始める授業と相談から効く。 */
  async setLimits(limits: TimeLimits): Promise<void> {
    const response = await this.request.put('/api/test/limits', { data: limits });
    ensureOk(response, 'PUT /api/test/limits');
  }
}

/**
 * 画面 (`page`) と同じ利用者として、テスト用の API を呼ぶ。
 *
 * Playwright の各テストは、新しいブラウザーの文脈 (Cookie の入れ物) で始まるため、テストどうしの利用者は隔離される。
 * `context.request` は `page` と Cookie を共有する (openapi.yaml の共通の決まり)。
 */
export function testControlFor(page: Page): TestControl {
  return new TestControl(page.context().request);
}

function ensureOk(response: APIResponse, label: string): void {
  if (!response.ok()) {
    throw new Error(`${label} が ${response.status()} を返しました`);
  }
}
