package com.teachme.web;

import io.javalin.Javalin;

/** 実行時の設定 (`GET /api/config`)。 */
public record SystemRoutes(String aiMode) implements Routes {

  /**
   * @param aiMode `real` か `fake`
   */
  public record ConfigResponse(String aiMode) {}

  @Override
  public void register(Javalin app) {
    app.get("/api/config", ctx -> ctx.json(new ConfigResponse(aiMode)));
  }
}
