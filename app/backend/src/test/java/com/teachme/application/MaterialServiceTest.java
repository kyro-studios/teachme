package com.teachme.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.teachme.testsupport.Fixtures;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 教材のユースケース (MaterialService) の規則。整理は、フェイクの LLM で動かす。 */
class MaterialServiceTest {
  private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID NO_SETTINGS = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0};
  private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};

  private Repositories repositories;
  private Clock clock;
  private SettingsService settings;
  private MaterialOrganizer organizer;
  private MaterialService service;
  private UUID unitId;

  @BeforeEach
  void setUp() {
    DataSource dataSource = Fixtures.freshDataSource();
    repositories = new Repositories(dataSource);
    clock = Fixtures.clockAt("2026-10-21T10:00");
    settings = new SettingsService(repositories.settings(), clock);
    settings.save(USER, command("二次関数"));
    settings.save(OTHER, command("力のはたらき"));
    unitId = settings.find(USER).orElseThrow().unitId();
    organizer = new MaterialOrganizer(repositories.materials(), new FakeAi(), clock);
    service = new MaterialService(settings, repositories.materials(), organizer, clock);
  }

  @AfterEach
  void tearDown() {
    organizer.close();
  }

  private static SettingsCommand command(String unitName) {
    return new SettingsCommand(
        StudentType.LOGICAL, Subject.MATH, UnitName.parse(unitName).orElseThrow(), 3, true);
  }

  /** 整理は依頼しないで、整理中の教材を直接保存する (ワーカーの結果に左右されないため)。 */
  private UUID insertOrganizing(List<NewImage> images) {
    UUID materialId = UUID.randomUUID();
    repositories.materials().insertOrganizing(materialId, unitId, images, clock.instant());
    return materialId;
  }

  private static NewImage png() {
    return new NewImage("image/png", PNG);
  }

  @Test
  void 教材の番号は_登録順に_1_から振り_削除すると詰まる() {
    UUID first = insertOrganizing(List.of(png()));
    UUID second = insertOrganizing(List.of(png()));

    List<MaterialView> before = service.list(USER);
    assertEquals(List.of(first, second), before.stream().map(MaterialView::id).toList());
    assertEquals(List.of(1, 2), before.stream().map(MaterialView::number).toList());

    assertTrue(service.delete(USER, first));

    MaterialView remaining = service.list(USER).get(0);
    assertEquals(second, remaining.id());
    assertEquals(1, remaining.number());
  }

  @Test
  void 設定が無い利用者は_一覧も登録も_setup_required_になる() {
    ConflictException list = assertThrows(ConflictException.class, () -> service.list(NO_SETTINGS));
    assertEquals("setup_required", list.code());

    ConflictException create =
        assertThrows(
            ConflictException.class,
            () ->
                service.create(
                    NO_SETTINGS, List.of(new MaterialService.Upload(ImageFormat.PNG, PNG))));
    assertEquals("setup_required", create.code());
    assertTrue(repositories.materials().listByUnit(unitId).isEmpty());
  }

  @Test
  void 登録した教材は_整理中の状態で返し_画像は番号の順に取り出せる() {
    UUID materialId = insertOrganizing(List.of(png(), new NewImage("image/jpeg", JPEG)));

    MaterialView view = service.find(USER, materialId).orElseThrow();
    assertEquals(MaterialStatus.ORGANIZING, view.status());
    assertNull(view.problem());

    MaterialService.ImageFile second = service.image(USER, materialId, 2).orElseThrow();
    assertEquals("image/jpeg", second.contentType());
    assertEquals(JPEG.length, second.bytes().length);
    assertTrue(service.image(USER, materialId, 3).isEmpty());
  }

  @Test
  void ほかの利用者の教材は_見えず_削除できず_画像も取れない() {
    UUID materialId = insertOrganizing(List.of(png()));

    assertTrue(service.find(OTHER, materialId).isEmpty());
    assertTrue(service.image(OTHER, materialId, 1).isEmpty());
    assertTrue(service.list(OTHER).isEmpty());
    assertFalse(service.delete(OTHER, materialId));

    // 持ち主の教材は、ほかの利用者の削除で消えない
    assertTrue(service.find(USER, materialId).isPresent());
  }

  @Test
  void 削除は何度呼んでも_true_で_教材と画像は消える() {
    UUID materialId = insertOrganizing(List.of(png()));

    assertTrue(service.delete(USER, materialId));
    assertTrue(service.delete(USER, materialId));

    assertTrue(service.find(USER, materialId).isEmpty());
    assertTrue(service.image(USER, materialId, 1).isEmpty());
  }

  @Test
  void 整理中の教材は_やり直せず_409_material_not_failed_になる() {
    UUID materialId = insertOrganizing(List.of(png()));

    ConflictException error =
        assertThrows(ConflictException.class, () -> service.retry(USER, materialId));
    assertEquals("material_not_failed", error.code());
    assertEquals(MaterialStatus.ORGANIZING, service.find(USER, materialId).orElseThrow().status());
  }

  @Test
  void ほかの利用者の教材の_やり直しは_無いものとして_空を返す() {
    UUID materialId = insertOrganizing(List.of(png()));

    assertTrue(service.retry(OTHER, materialId).isEmpty());
  }
}
