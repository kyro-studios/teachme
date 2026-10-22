import { useNavigate } from "react-router";
import { Button } from "../../shared/ui/Button";
import { useStartConsultation } from "./consultation-api";

// 教材の詳細の「先輩先生に相談する」(仕様書 5.4)。押すと相談を始め、相談の画面へ進む。相談の時間は、ここから数える
export function StartConsultationButton({ materialId }: { materialId: string }) {
  const navigate = useNavigate();
  const start = useStartConsultation();

  return (
    <div className="grid justify-items-start gap-3">
      <Button
        disabled={start.isPending}
        onClick={() =>
          start.mutate(materialId, {
            onSuccess: () => navigate(`/materials/${materialId}/consultation`),
          })
        }
      >
        先輩先生に相談する
      </Button>
      {start.error ? (
        <p role="alert" className="text-lg font-bold text-red-700">
          {start.error.message}
        </p>
      ) : null}
    </div>
  );
}
