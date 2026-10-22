import { RecordUnitList } from "../features/records/RecordUnitList";
import { PageFrame } from "../shared/ui/PageFrame";

// 学習記録の一覧 (仕様書 5.8)。授業の記録か模試の結果のある単元を並べる
export function RecordsPage() {
  return (
    <PageFrame title="学習記録">
      <RecordUnitList />
    </PageFrame>
  );
}
