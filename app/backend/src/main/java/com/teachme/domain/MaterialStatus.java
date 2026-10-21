package com.teachme.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * 教材の状態 (仕様書 7.2)。`id` は openapi.yaml の `MaterialStatus` と、`materials.status` と同じ値にする。
 *
 * <p>`ORGANIZING` は「整理中」、`ORGANIZED` は「整理済み」、`FAILED` は「整理できませんでした」。
 */
public enum MaterialStatus {
  ORGANIZING("organizing"),
  ORGANIZED("organized"),
  FAILED("failed");

  private final String id;

  MaterialStatus(String id) {
    this.id = id;
  }

  /** 識別子から状態を求める。知らない識別子なら空。 */
  public static Optional<MaterialStatus> fromId(String id) {
    return Arrays.stream(values()).filter(status -> status.id.equals(id)).findFirst();
  }

  public String id() {
    return id;
  }
}
