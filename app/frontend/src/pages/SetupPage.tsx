import { useQuery } from "@tanstack/react-query";
import { useNavigate } from "react-router";
import { ApiError } from "../api/client";
import { settingsQuery, studentTypesQuery } from "../api/queries";
import { SetupForm } from "../features/setup/SetupForm";
import { useSaveSettings } from "../features/setup/use-save-settings";
import { PageFrame } from "../shared/ui/PageFrame";

// 初期セットアップ (仕様書 5.1)。未完了なら入力を始め、完了後は設定の変更になる (担当する生徒は変えられない)
export function SetupPage() {
  const navigate = useNavigate();
  const settings = useQuery(settingsQuery);
  const studentTypes = useQuery(studentTypesQuery);
  const save = useSaveSettings();

  if (!settings.data || !studentTypes.data) {
    return <PageFrame title="初期セットアップ" />;
  }

  const saved = settings.data.settings ?? undefined;
  return (
    <PageFrame title="初期セットアップ">
      <SetupForm
        mode={saved ? "edit" : "initial"}
        studentTypes={studentTypes.data.studentTypes}
        saved={saved}
        pending={save.isPending}
        serverError={save.error instanceof ApiError ? save.error : null}
        onSubmit={(input) => save.mutate(input, { onSuccess: () => navigate("/home") })}
      />
    </PageFrame>
  );
}
