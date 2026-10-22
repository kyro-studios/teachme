import { useParams } from "react-router";
import { MaterialDetail } from "../features/materials/MaterialDetail";
import { failureMessage } from "../features/materials/material-text";
import { useMaterial } from "../features/materials/use-materials";
import { PageFrame } from "../shared/ui/PageFrame";

// 教材の詳細 (仕様書 5.3、5.9)。整理の結果を見る画面で、整理中は結果が出るまで取り直す
export function MaterialDetailPage() {
  const { materialId = "" } = useParams();
  const material = useMaterial(materialId);

  if (material.error) {
    return (
      <PageFrame title="教材の詳細">
        <p role="alert" className="text-lg font-bold text-red-700">
          {failureMessage(material.error)}
        </p>
      </PageFrame>
    );
  }
  if (!material.data) {
    return <PageFrame title="教材の詳細" />;
  }

  return (
    <PageFrame title={`教材 ${material.data.number}`}>
      <MaterialDetail material={material.data} />
    </PageFrame>
  );
}
