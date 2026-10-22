import { Link } from "react-router";
import { RetryMaterialButton } from "./RetryMaterialButton";
import { MATERIAL_STATUS_LABELS, failureMessage } from "./material-text";
import { useMaterials } from "./use-materials";

// 現在の単元の教材の一覧 (仕様書 5.3)。「教材 1」「教材 2」と番号を付け、状態を並べる。
// 整理に失敗した教材は、一覧からも「やり直す」を押せる
export function MaterialList() {
  const materials = useMaterials();
  if (materials.error) {
    return (
      <p role="alert" className="text-lg font-bold text-red-700">
        {failureMessage(materials.error)}
      </p>
    );
  }
  if (!materials.data) {
    return null;
  }

  const items = materials.data.materials;
  if (items.length === 0) {
    return <p className="text-lg text-slate-900">教材はまだありません</p>;
  }

  return (
    <ul className="grid gap-4">
      {items.map((material) => (
        <li key={material.id} className="flex flex-wrap items-center gap-4 text-lg text-slate-900">
          <Link
            to={`/materials/${material.id}`}
            className="inline-flex min-h-11 items-center rounded-lg px-2 font-bold underline outline-offset-2 focus-visible:outline-3 focus-visible:outline-blue-700"
          >
            {`教材 ${material.number}`}
          </Link>
          <span>{MATERIAL_STATUS_LABELS[material.status]}</span>
          {material.status === "failed" ? <RetryMaterialButton materialId={material.id} /> : null}
        </li>
      ))}
    </ul>
  );
}
