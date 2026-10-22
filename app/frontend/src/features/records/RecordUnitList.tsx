import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router";
import { SUBJECT_LABELS } from "../../shared/lib/labels";
import { failureMessage } from "./record-text";
import { recordUnitsQuery } from "./use-records";

// 学習記録の単元の一覧 (仕様書 5.8)。授業の記録か模試の結果のある単元を、作成順に並べる。無ければ、その旨を出す
export function RecordUnitList() {
  const units = useQuery(recordUnitsQuery);
  if (units.error) {
    return (
      <p role="alert" className="text-lg font-bold text-red-700">
        {failureMessage(units.error)}
      </p>
    );
  }
  if (!units.data) {
    return null;
  }

  const items = units.data.units;
  if (items.length === 0) {
    return <p className="text-lg text-slate-900">学習記録がありません</p>;
  }

  return (
    <ul className="grid gap-4">
      {items.map((unit) => (
        <li key={unit.unitId}>
          <Link
            to={`/records/units/${unit.unitId}`}
            className="inline-flex min-h-11 items-center rounded-lg px-2 text-lg font-bold text-slate-900 underline outline-offset-2 focus-visible:outline-3 focus-visible:outline-blue-700"
          >
            {`${SUBJECT_LABELS[unit.subject]}・${unit.unitName}`}
          </Link>
        </li>
      ))}
    </ul>
  );
}
