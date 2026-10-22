import { useQuery } from "@tanstack/react-query";
import { useRef } from "react";
import { useNavigate } from "react-router";
import { homeQuery } from "../../api/queries";
import { Button } from "../../shared/ui/Button";
import { useTakeMockExam } from "./use-take-mock-exam";

const FAILED_MESSAGE = "模試を作れませんでした";

// ホームの「模試に挑戦」(仕様書 5.2、5.7)。押すと模試を作り、結果の画面へ進む。
// 作れなかったときは、結果を保存せず模試の機会も残るため、「やり直す」を出す。作成中は押せないようにし、二重に呼ばない
export function MockExamPanel() {
  const navigate = useNavigate();
  const home = useQuery(homeQuery);
  const take = useTakeMockExam();
  // disabled は再描画の後にしか効かないため、描画の前に届いた二度目の押下を、ref で止める
  const starting = useRef(false);
  if (!home.data?.mockExamAvailable) {
    return null;
  }

  const takeMockExam = () => {
    if (starting.current) {
      return;
    }
    starting.current = true;
    take.mutate(undefined, {
      onSuccess: (result) => navigate(`/mock-exams/${result.id}`),
      onSettled: () => {
        starting.current = false;
      },
    });
  };

  if (take.isError) {
    return (
      <section className="grid justify-items-start gap-3">
        <p role="alert" className="text-lg font-bold text-red-700">
          {FAILED_MESSAGE}
        </p>
        <Button onClick={takeMockExam}>やり直す</Button>
      </section>
    );
  }

  return (
    <div>
      <Button disabled={take.isPending} onClick={takeMockExam}>
        模試に挑戦
      </Button>
    </div>
  );
}
