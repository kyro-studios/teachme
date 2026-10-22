import { useQuery } from "@tanstack/react-query";
import { useNavigate } from "react-router";
import { homeQuery } from "../../api/queries";
import { Button } from "../../shared/ui/Button";

// ホームの操作ボタン (仕様書 5.2)。「授業を始める」は今日のモードが通常のときだけ出す
export function HomeMenu() {
  const navigate = useNavigate();
  const home = useQuery(homeQuery);
  if (!home.data) {
    return null;
  }

  const { todayMode, hasOrganizedMaterial } = home.data;
  return (
    <section className="grid gap-4">
      {todayMode === "normal" ? (
        <div className="grid justify-items-start gap-3">
          <Button onClick={() => navigate("/lessons/new")}>授業を始める</Button>
          {hasOrganizedMaterial ? null : (
            <p className="text-lg text-slate-900">
              授業を始めるには、先生準備室で教材を登録してください
            </p>
          )}
        </div>
      ) : null}

      <div className="flex flex-wrap gap-3">
        <Button variant="secondary" onClick={() => navigate("/materials")}>
          先生準備室へ
        </Button>
        <Button variant="secondary" onClick={() => navigate("/records")}>
          学習記録
        </Button>
        <Button variant="secondary" onClick={() => navigate("/setup")}>
          設定を変える
        </Button>
      </div>
    </section>
  );
}
