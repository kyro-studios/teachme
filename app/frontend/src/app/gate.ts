// 入口の判定。設定が未完了の利用者は、どの画面を開いても初期セットアップへ寄せる。
// 完了している利用者の「/」は、ホームへ寄せる。設定を変えるための初期セットアップは、完了後も開ける
export const SETUP_PATH = "/setup";
export const HOME_PATH = "/home";

/** 寄せ先の URL を返す。寄せる必要が無ければ null */
export function gateRedirect(pathname: string, completed: boolean): string | null {
  if (pathname === "/") {
    return completed ? HOME_PATH : SETUP_PATH;
  }
  if (!completed && pathname !== SETUP_PATH) {
    return SETUP_PATH;
  }
  return null;
}
