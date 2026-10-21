import { createBrowserRouter } from "react-router";
import { StartupPage } from "../pages/StartupPage";

// 画面の URL は、受け入れテストを書く段階で決める。ここには、起動を確かめる画面だけを置く
export const router = createBrowserRouter([{ path: "*", element: <StartupPage /> }]);
