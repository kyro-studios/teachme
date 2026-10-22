import { MaterialList } from "../features/materials/MaterialList";
import { MaterialRegister } from "../features/materials/MaterialRegister";
import { PageFrame } from "../shared/ui/PageFrame";

// 先生準備室 (仕様書 5.3)。現在の単元の教材を登録し、一覧で整理の状態を見る
export function MaterialsPage() {
  return (
    <PageFrame title="先生準備室">
      <MaterialRegister />
      <MaterialList />
    </PageFrame>
  );
}
