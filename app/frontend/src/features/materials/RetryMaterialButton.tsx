import { Button } from "../../shared/ui/Button";
import { failureMessage } from "./material-text";
import { useRetryMaterial } from "./use-materials";

// 「やり直す」(仕様書 5.3)。押すと状態を整理中にし、整理をもう一度始める。整理中は表示しない (呼び出し側で決める)
export function RetryMaterialButton({ materialId }: { materialId: string }) {
  const retry = useRetryMaterial();
  return (
    <div className="grid justify-items-start gap-2">
      <Button
        variant="secondary"
        disabled={retry.isPending}
        onClick={() => retry.mutate(materialId)}
      >
        やり直す
      </Button>
      {retry.error ? (
        <p role="alert" className="text-lg font-bold text-red-700">
          {failureMessage(retry.error)}
        </p>
      ) : null}
    </div>
  );
}
