package com.teachme.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.teachme.ai.AiCallKind;
import com.teachme.ai.LanguageModel;
import com.teachme.ai.ScriptedResponse;
import com.teachme.ai.fake.FakeAi;
import com.teachme.application.MaterialService.MaterialView;
import com.teachme.application.SettingsService.SettingsCommand;
import com.teachme.domain.ImageFormat;
import com.teachme.domain.MaterialStatus;
import com.teachme.domain.StudentType;
import com.teachme.domain.Subject;
import com.teachme.domain.UnitName;
import com.teachme.persistence.MaterialRepository.NewImage;
import com.teachme.persistence.Repositories;
import com.teachme.testsupport.Await;
import com.teachme.testsupport.Fixtures;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 教材の整理 (MaterialOrganizer) を、DB と、フェイクの LLM で確かめる。 */
class MaterialOrganizerTest {
  private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0};

  private Repositories repositories;
  private Clock clock;
  private FakeAi fakeAi;
  private MaterialOrganizer organizer;
  private MaterialService service;
  private UUID unitId;

  @BeforeEach
  void setUp() {
    DataSource dataSource = Fixtures.freshDataSource();
    repositories = new Repositories(dataSource);
    clock = Fixtures.clockAt("2026-10-21T10:00");
    SettingsService settings = new SettingsService(repositories.settings(), clock);
    settings.save(
        USER,
        new SettingsCommand(
            StudentType.LOGICAL, Subject.MATH, UnitName.parse("二次関数").orElseThrow(), 3, true));
    unitId = settings.find(USER).orElseThrow().unitId();
    fakeAi = new FakeAi();
    organizer = new MaterialOrganizer(repositories.materials(), fakeAi, clock);
    service = new MaterialService(settings, repositories.materials(), organizer, clock);
  }

  @AfterEach
  void tearDown() {
    organizer.close();
  }

  private static MaterialService.Upload png() {
    return new MaterialService.Upload(ImageFormat.PNG, PNG);
  }

  private MaterialStatus statusOf(UUID materialId) {
    return service.find(USER, materialId).orElseThrow().status();
  }

  private int organizeCalls() {
    return fakeAi.callCounts(USER).get(AiCallKind.ORGANIZE);
  }

  /** 指導のポイントや図表の形を、形の不正な整理の結果にする。 */
  private static Map<String, Object> organizePayload(
      List<String> teachingPoints, List<Map<String, Object>> figures) {
    return Map.of(
        "problemText",
        "y = x^2 - 4x + 3 の頂点を求めなさい",
        "figures",
        figures,
        "modelAnswer",
        "頂点は (2, -1)",
        "explanationSteps",
        List.of("平方完成して頂点を読む"),
        "studentWork",
        "y = (x - 2)^2 と書いて手が止まっている",
        "teachingPoints",
        teachingPoints);
  }

  private static Map<String, Object> figure(double x, double width) {
    return Map.of(
        "imagePosition", 1, "region", Map.of("x", x, "y", 0.0, "width", width, "height", 0.5));
  }

  @Test
  void 登録した教材は_整理が成功すると_整理済みになり_結果を読める() throws InterruptedException {
    MaterialView created = service.create(USER, List.of(png()));
    assertEquals(MaterialStatus.ORGANIZING, created.status());
    assertEquals(1, created.number());

    Await.until("整理済み", () -> statusOf(created.id()) == MaterialStatus.ORGANIZED);

    MaterialView organized = service.find(USER, created.id()).orElseThrow();
    assertEquals(3, organized.problem().teachingPoints().size());
    assertEquals(1, organizeCalls());
  }

  @Test
  void 整理が_1_回失敗しても_自動のやり直しで_整理済みになり_呼び出しは_2_回() throws InterruptedException {
    fakeAi.script(
        USER,
        AiCallKind.ORGANIZE,
        List.of(new ScriptedResponse(false, null), new ScriptedResponse(true, null)),
        false);

    MaterialView created = service.create(USER, List.of(png()));

    Await.until("整理済み", () -> statusOf(created.id()) == MaterialStatus.ORGANIZED);
    assertEquals(2, organizeCalls());
  }

  @Test
  void 整理が_2_回続けて失敗すると_整理できませんでしたになり_呼び出しは_2_回で止まる() throws InterruptedException {
    fakeAi.script(
        USER,
        AiCallKind.ORGANIZE,
        List.of(new ScriptedResponse(false, null), new ScriptedResponse(false, null)),
        false);

    MaterialView created = service.create(USER, List.of(png()));

    Await.until("整理できませんでした", () -> statusOf(created.id()) == MaterialStatus.FAILED);
    assertEquals(2, organizeCalls());
    assertNull(service.find(USER, created.id()).orElseThrow().problem());
  }

  @Test
  void 指導のポイントが_3_件でない応答は_失敗として_やり直す() throws InterruptedException {
    fakeAi.script(
        USER,
        AiCallKind.ORGANIZE,
        List.of(
            new ScriptedResponse(true, organizePayload(List.of("一つ目", "二つ目"), List.of())),
            new ScriptedResponse(true, null)),
        false);

    MaterialView created = service.create(USER, List.of(png()));

    Await.until("整理済み", () -> statusOf(created.id()) == MaterialStatus.ORGANIZED);
    assertEquals(2, organizeCalls());
  }

  @Test
  void 図表の矩形が範囲の外の応答も_形の不正として_やり直す() throws InterruptedException {
    fakeAi.script(
        USER,
        AiCallKind.ORGANIZE,
        List.of(
            new ScriptedResponse(
                true, organizePayload(List.of("a", "b", "c"), List.of(figure(0.9, 0.5)))),
            new ScriptedResponse(true, null)),
        false);

    MaterialView created = service.create(USER, List.of(png()));

    Await.until("整理済み", () -> statusOf(created.id()) == MaterialStatus.ORGANIZED);
    assertEquals(2, organizeCalls());
  }

  @Test
  void 整理の途中で教材を削除すると_結果を捨て_教材は残らない() throws InterruptedException {
    UUID materialId = UUID.randomUUID();
    repositories
        .materials()
        .insertOrganizing(
            materialId, unitId, List.of(new NewImage("image/png", PNG)), clock.instant());
    MaterialOrganizer deleting =
        new MaterialOrganizer(repositories.materials(), deletingDuringOrganize(materialId), clock);

    deleting.enqueue(USER, materialId);

    Await.until("教材が消える", () -> service.find(USER, materialId).isEmpty());
    // 進行中の整理が終わるまで待ち、結果が書き戻されていないことを確かめる
    deleting.close();
    assertTrue(service.find(USER, materialId).isEmpty());
    assertEquals(1, organizeCalls());
  }

  /** 整理の呼び出しの中で教材を削除する LLM。ほかの呼び出しは、フェイクに任せる。 */
  private LanguageModel deletingDuringOrganize(UUID materialId) {
    return (LanguageModel)
        Proxy.newProxyInstance(
            LanguageModel.class.getClassLoader(),
            new Class<?>[] {LanguageModel.class},
            (proxy, method, args) -> {
              if (method.getName().equals("organize")) {
                repositories.materials().delete(materialId);
              }
              try {
                return method.invoke(fakeAi, args);
              } catch (InvocationTargetException e) {
                throw e.getCause();
              }
            });
  }

  @Test
  void 整理できなかった教材を_二重に_やり直すと_2_回目は_409_になる() throws InterruptedException {
    fakeAi.script(
        USER,
        AiCallKind.ORGANIZE,
        List.of(new ScriptedResponse(false, null), new ScriptedResponse(false, null)),
        false);
    MaterialView created = service.create(USER, List.of(png()));
    Await.until("整理できませんでした", () -> statusOf(created.id()) == MaterialStatus.FAILED);

    // 1 回目のやり直しは受け付け、整理が終わるまで待つ。終わるとまた failed ではなくなる
    service.retry(USER, created.id());
    Await.until("整理済み", () -> statusOf(created.id()) == MaterialStatus.ORGANIZED);

    ConflictException error =
        assertThrows(ConflictException.class, () -> service.retry(USER, created.id()));
    assertEquals("material_not_failed", error.code());
    assertEquals(3, organizeCalls());
  }

  @Test
  void 途切れた整理は_掃除で_整理できませんでしたになり_10_分以内の整理は残す() {
    NewImage image = new NewImage("image/png", PNG);
    UUID stale = UUID.randomUUID();
    UUID fresh = UUID.randomUUID();
    repositories
        .materials()
        .insertOrganizing(
            stale, unitId, List.of(image), clock.instant().minus(Duration.ofMinutes(11)));
    repositories
        .materials()
        .insertOrganizing(
            fresh, unitId, List.of(image), clock.instant().minus(Duration.ofMinutes(9)));

    assertEquals(1, organizer.sweepStale());

    assertEquals(MaterialStatus.FAILED, statusOf(stale));
    assertEquals(MaterialStatus.ORGANIZING, statusOf(fresh));
    assertFalse(organizer.sweepStale() > 0);
  }
}
