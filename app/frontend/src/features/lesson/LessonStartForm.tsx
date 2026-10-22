import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useNavigate } from "react-router";
import { Button } from "../../shared/ui/Button";
import { ChoiceGroup } from "../../shared/ui/ChoiceGroup";
import { organizedMaterialsQuery, useStartLesson } from "./lesson-api";

// 授業の教材の選択と、授業を始める操作 (仕様書 5.5)。整理済みの教材から 1 つ選ぶ
export function LessonStartForm() {
  const navigate = useNavigate();
  const materials = useQuery(organizedMaterialsQuery);
  const start = useStartLesson();
  const [selected, setSelected] = useState<string | null>(null);

  if (materials.error) {
    return (
      <p role="alert" className="text-lg font-bold text-red-700">
        {materials.error.message}
      </p>
    );
  }
  if (!materials.data) {
    return null;
  }
  if (materials.data.length === 0) {
    return (
      <p className="text-lg text-slate-900">授業を始めるには、先生準備室で教材を登録してください</p>
    );
  }

  const startLesson = () => {
    if (!selected) {
      return;
    }
    start.mutate(selected, {
      onSuccess: (entry) => navigate(`/lessons/${entry.lessonId}`),
    });
  };

  return (
    <section className="grid max-w-xl gap-6">
      <ChoiceGroup
        label="整理済みの教材"
        options={materials.data.map((material) => ({
          value: material.id,
          label: `教材 ${material.number}`,
        }))}
        value={selected}
        onValueChange={setSelected}
      />
      {start.error ? (
        <p role="alert" className="text-lg font-bold text-red-700">
          {start.error.message}
        </p>
      ) : null}
      <div>
        <Button disabled={!selected || start.isPending} onClick={startLesson}>
          授業を始める
        </Button>
      </div>
    </section>
  );
}
