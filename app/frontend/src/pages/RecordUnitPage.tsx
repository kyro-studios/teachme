import { useQuery } from "@tanstack/react-query";
import { useParams } from "react-router";
import { RecordUnitDetail } from "../features/records/RecordUnitDetail";
import { failureMessage } from "../features/records/record-text";
import { recordUnitQuery } from "../features/records/use-records";
import { PageFrame } from "../shared/ui/PageFrame";

// 単元の記録 (仕様書 5.8、5.9)。まとめノート、模試の結果、学習レポートの入口と、学習記録の削除を置く
export function RecordUnitPage() {
  const { unitId = "" } = useParams();
  const unit = useQuery(recordUnitQuery(unitId));

  if (unit.error) {
    return (
      <PageFrame title="単元の記録">
        <p role="alert" className="text-lg font-bold text-red-700">
          {failureMessage(unit.error)}
        </p>
      </PageFrame>
    );
  }
  if (!unit.data) {
    return null;
  }

  return (
    <PageFrame title={unit.data.unitName}>
      <RecordUnitDetail unit={unit.data} />
    </PageFrame>
  );
}
