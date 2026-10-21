package com.teachme.bootstrap;

import com.teachme.ai.LanguageModel;
import com.teachme.ai.fake.FakeAi;
import com.teachme.ai.real.NotConfiguredLanguageModel;
import com.teachme.application.HomeService;
import com.teachme.application.HomeworkService;
import com.teachme.application.SettingsService;
import com.teachme.application.TestControlService;
import com.teachme.persistence.Repositories;
import com.teachme.web.HomeRoutes;
import com.teachme.web.Routes;
import com.teachme.web.SettingsRoutes;
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
    boolean fake = config.aiMode() == AppConfig.AiMode.FAKE;
    FakeAi fakeAi = new FakeAi();
    // 実際の LLM の実装は、まだ無い。real では、呼び出しを失敗として返す
    LanguageModel languageModel = fake ? fakeAi : new NotConfiguredLanguageModel();
    routes.addAll(userRoutes(components, languageModel));
    if (fake) {
      routes.add(new TestRoutes(new TestControlService(fakeAi)));
    }
    return routes;
  }

  /** 利用者ごとの機能 (初期セットアップ、ホーム、今日のモード、宿題の丸つけ)。 */
  private static List<Routes> userRoutes(Components components, LanguageModel languageModel) {
    Repositories repositories = new Repositories(components.dataSource());
    SettingsService settings = new SettingsService(repositories.settings(), components.clock());
    HomeService home =
        new HomeService(
            settings,
            repositories.lessonRecords(),
            repositories.todayModes(),
            repositories.units(),
            components.clock());
    HomeworkService homework =
        new HomeworkService(settings, repositories.todayModes(), languageModel, components.clock());
    return List.of(new SettingsRoutes(settings), new HomeRoutes(home, homework));
  }
}
