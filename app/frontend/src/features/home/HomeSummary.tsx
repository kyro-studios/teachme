import { useQuery } from "@tanstack/react-query";
import { homeQuery } from "../../api/queries";
import { SUBJECT_LABELS } from "../../shared/lib/labels";

// ホームの要約 (仕様書 5.2 の担当する生徒、教科と単元名、今週の授業)
export function HomeSummary() {
  const home = useQuery(homeQuery);
  if (!home.data) {
    return null;
  }

  const { student, subject, unitName, lessonsThisWeek, lessonsPerWeek } = home.data;
  return (
    <section className="grid gap-2 text-lg text-slate-900">
      <p className="text-2xl font-bold">{student.displayName}</p>
      <p>
        {SUBJECT_LABELS[subject]}・{unitName}
      </p>
      <p>
        今週の授業 {lessonsThisWeek} / {lessonsPerWeek} 回
      </p>
    </section>
  );
}
