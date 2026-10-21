import { QueryClient } from "@tanstack/react-query";

// アプリ全体で 1 つの問い合わせの管理者。ルーターの loader と画面が、同じキャッシュを使う
export const queryClient = new QueryClient();
