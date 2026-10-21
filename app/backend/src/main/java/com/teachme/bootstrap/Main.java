package com.teachme.bootstrap;

import com.teachme.ai.fake.FakeAi;
import com.teachme.application.TestControlService;
import com.teachme.web.Routes;
import com.teachme.web.SystemRoutes;
import com.teachme.web.TestRoutes;
import com.teachme.web.WebApp;
import io.javalin.Javalin;
import java.util.ArrayList;
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

  /**
   * API ごとの Routes を並べる。機能を足すときは、ここに追加する。
   *
   * <p>テスト用の API (`/api/test/*`) と、フェイクの外部 AI は、`AI_MODE=fake` のときだけ組み立てて登録する。`real` では登録せず、404
   * を返す。
   */
  static List<Routes> routes(AppConfig config, Components components) {
    List<Routes> routes = new ArrayList<>();
    routes.add(new SystemRoutes(config.aiMode().name().toLowerCase(Locale.ROOT)));
    if (config.aiMode() == AppConfig.AiMode.FAKE) {
      routes.add(new TestRoutes(new TestControlService(new FakeAi())));
    }
    return routes;
  }
}
