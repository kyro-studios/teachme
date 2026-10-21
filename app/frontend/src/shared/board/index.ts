// shared/board の公開の入口。features と pages は、ここから読む
export { Board, type BoardHandle, type BoardProps } from "./Board";
export type { BoardFigure, BoardWrite, InitialContent, StudentAnnotation } from "./types";
export { cropFigure, cropRect, type CroppedFigure, type PixelRect } from "./figure";
export { fitLongSide, type Point, type Region, type ScreenFrame } from "./geometry";
export { fitWidth, stackFigures, type Placement, type Size } from "./layout";
export {
  PenTracker,
  POINTED_LOOKBACK_MS,
  POINTED_MAX_COUNT,
  selectPointedRegions,
  type Contact,
  type RecordingRange,
} from "./pen";
export { BOARD_IMAGE_MAX_LONG_SIDE } from "./board-shapes";
