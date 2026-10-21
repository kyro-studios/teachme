package com.teachme.bootstrap;

import com.teachme.web.Routes;
import com.teachme.web.SystemRoutes;
import com.teachme.web.WebApp;
import io.javalin.Javalin;
import java.util.List;
import java.util.Locale;

/** バックエンドの起動。設定を読み、部品を組み立てる。 */
public final class Main {
  private Main() {}

  public static void main(String[] args) {
    AppConfig config = AppConfig.fromEnv(System.getenv());
    Components components = Components.assemble(DatabaseConfig.fromEnv(System.getenv()));
    Javalin app = WebApp.create(config.staticDir(), routes(config, components));
    Runtime.getRuntime().addShutdownHook(new Thread(app::stop));
    app.start(config.port());
  }

  /** API ごとの Routes を並べる。機能を足すときは、ここに追加する。 */
  static List<Routes> routes(AppConfig config, Components components) {
    return List.of(new SystemRoutes(config.aiMode().name().toLowerCase(Locale.ROOT)));
  }
}
