import { useQuery } from "@tanstack/react-query";
import { ApiError } from "../../api/client";
import { homeQuery } from "../../api/queries";
import { TODAY_MODE_LABELS } from "../../shared/lib/labels";
import { Button } from "../../shared/ui/Button";
import { HomeworkCheck } from "./HomeworkCheck";
import { useSetTodayMode } from "./use-today-mode";

// 今日のモードの表示と切り替え (仕様書 5.2、FR-104)。切り替えは 1 回押すだけで、確認は出さない
export function TodayModePanel() {
  const home = useQuery(homeQuery);
  const setMode = useSetTodayMode();
  if (!home.data) {
    return null;
  }

  const { todayMode, unitName } = home.data;
  const failure = setMode.error
    ? setMode.error instanceof ApiError
      ? setMode.error.message
      : "サーバーとの通信に失敗しました"
    : null;

  return (
    <section className="grid gap-4">
      <p className="text-lg text-slate-900">今日のモード: {TODAY_MODE_LABELS[todayMode]}</p>

      {todayMode === "normal" ? (
        <div className="flex flex-wrap gap-3">
          <Button
            variant="secondary"
            disabled={setMode.isPending}
            onClick={() => setMode.mutate("self_study")}
          >
            自習指示に切り替える
          </Button>
          <Button
            variant="secondary"
            disabled={setMode.isPending}
            onClick={() => setMode.mutate("homework_check")}
          >
            宿題の丸つけに切り替える
          </Button>
        </div>
      ) : (
        <div>
          <Button
            variant="secondary"
            disabled={setMode.isPending}
            onClick={() => setMode.mutate("normal")}
          >
            通常に戻す
          </Button>
        </div>
      )}

      {todayMode === "self_study" ? (
        <div className="grid gap-2">
          <p className="text-2xl font-bold text-slate-900">今日は自習指示の日です</p>
          <p className="text-lg text-slate-900">
            今日は「{unitName}」の教材から、問題を 1 つ解いてみてください。
          </p>
        </div>
      ) : null}

      {todayMode === "homework_check" ? (
        <div className="grid gap-4">
          <p className="text-2xl font-bold text-slate-900">今日は宿題の丸つけの日です</p>
          <HomeworkCheck />
        </div>
      ) : null}

      {failure ? (
        <p role="alert" className="text-lg font-bold text-red-700">
          {failure}
        </p>
      ) : null}
    </section>
  );
}
