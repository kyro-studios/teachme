import { HomeMenu } from "../features/home/HomeMenu";
import { HomeSummary } from "../features/home/HomeSummary";
import { TodayModePanel } from "../features/home/TodayModePanel";
import { PageFrame } from "../shared/ui/PageFrame";

// ホーム (仕様書 5.2)。担当する生徒と今週の授業、今日のモード、操作ボタンを並べる
export function HomePage() {
  return (
    <PageFrame title="ホーム">
      <HomeSummary />
      <TodayModePanel />
      <HomeMenu />
    </PageFrame>
  );
}
