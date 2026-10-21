package com.teachme.bootstrap;

import com.teachme.web.WebApp;
import io.javalin.Javalin;

/** バックエンドの起動。設定を読み、部品を組み立てる。 */
public final class Main {
  private Main() {}

  public static void main(String[] args) {
    AppConfig config = AppConfig.fromEnv(System.getenv());
    Javalin app = WebApp.create(config.staticDir());
    Runtime.getRuntime().addShutdownHook(new Thread(app::stop));
    app.start(config.port());
  }
}
