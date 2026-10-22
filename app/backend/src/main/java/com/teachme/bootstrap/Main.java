package com.teachme.bootstrap;

import com.teachme.ai.LanguageModel;
import com.teachme.ai.SpeechToText;
import com.teachme.ai.TextToSpeech;
import com.teachme.ai.fake.FakeAi;
import com.teachme.ai.real.NotConfiguredLanguageModel;
import com.teachme.ai.real.NotConfiguredSpeech;
import com.teachme.application.DialogueService;
import com.teachme.application.DialogueSweeper;
import com.teachme.application.HomeService;
import com.teachme.application.HomeworkService;
import com.teachme.application.LessonService;
import com.teachme.application.MaterialOrganizer;
import com.teachme.application.MaterialService;
import com.teachme.application.SettingsService;
import com.teachme.application.TestControlService;
import com.teachme.domain.DialogueLimits;
import com.teachme.persistence.Repositories;
import com.teachme.web.HomeRoutes;
import com.teachme.web.LessonRoutes;
import com.teachme.web.MaterialRoutes;
import com.teachme.web.Routes;
import com.teachme.web.SettingsRoutes;
import com.teachme.web.SystemRoutes;
import com.teachme.web.TestRoutes;
import com.teachme.web.WebApp;
import io.javalin.Javalin;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

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
    TestControlService testControl = new TestControlService(fakeAi);
    // 実際の LLM と音声の実装は、まだ無い。real では、呼び出しを失敗として返す
    LanguageModel languageModel = fake ? fakeAi : new NotConfiguredLanguageModel();
    SpeechToText speechToText = fake ? fakeAi : new NotConfiguredSpeech();
    TextToSpeech textToSpeech = fake ? fakeAi : new NotConfiguredSpeech();
    // 授業の時間の上限は、テストの上書きを反映する。real では上書きは無い
    Function<UUID, DialogueLimits> lessonLimits =
        fake ? testControl::lessonLimits : userId -> DialogueLimits.lesson();
    routes.addAll(userRoutes(components, languageModel, speechToText, textToSpeech, lessonLimits));
    if (fake) {
      routes.add(new TestRoutes(testControl));
    }
    return routes;
  }

  /** 利用者ごとの機能 (初期セットアップ、ホーム、今日のモード、宿題の丸つけ、教材、授業)。 */
  private static List<Routes> userRoutes(
      Components components,
      LanguageModel languageModel,
      SpeechToText speechToText,
      TextToSpeech textToSpeech,
      Function<UUID, DialogueLimits> lessonLimits) {
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
    MaterialOrganizer organizer =
        new MaterialOrganizer(repositories.materials(), languageModel, components.clock());
    MaterialService materials =
        new MaterialService(settings, repositories.materials(), organizer, components.clock());
    DialogueService dialogue =
        new DialogueService(
            settings, repositories.dialogues(), speechToText, textToSpeech, components.clock());
    LessonService lessons =
        new LessonService(
            settings,
            dialogue,
            repositories.dialogues(),
            repositories.materials(),
            repositories.todayModes(),
            repositories.lessonRecords(),
            languageModel,
            lessonLimits,
            components.clock());
    DialogueSweeper sweeper = new DialogueSweeper(repositories.dialogues(), components.clock());
    return List.of(
        new SettingsRoutes(settings),
        new HomeRoutes(home, homework),
        new MaterialRoutes(materials, organizer),
        new LessonRoutes(lessons, sweeper));
  }
}
