import { HomeSummary } from "../features/home/HomeSummary";
import { PageFrame } from "../shared/ui/PageFrame";

export function HomePage() {
  return (
    <PageFrame title="ホーム">
      <HomeSummary />
    </PageFrame>
  );
}
