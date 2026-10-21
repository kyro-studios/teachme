package com.teachme.application;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.teachme.ai.AiCallKind;
import com.teachme.ai.ScriptedResponse;
import com.teachme.ai.fake.FakeAi;
import com.teachme.application.DialogueService.Recording;
import com.teachme.application.DialogueService.ReplyTicket;
import com.teachme.application.SettingsService.SettingsCommand;
import com.teachme.domain.Degrees;
import com.teachme.domain.DialogueKind;
import com.teachme.domain.Speaker;
import com.teachme.domain.StudentType;
import com.teachme.domain.Subject;
import com.teachme.domain.UnitName;
import com.teachme.persistence.DialogueRepository.NewTurn;
import com.teachme.persistence.Repositories;
import com.teachme.testsupport.DialogueFixtures;
import com.teachme.testsupport.Fixtures;
import com.teachme.testsupport.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 授業と相談の共通部品 (発話、応答、音声) の統合テスト。DB は Testcontainers の PostgreSQL を使う。 */
class DialogueServiceTest {
  private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final Instant START = Instant.parse("2026-10-21T01:00:00Z");
  private static final Recording RECORDING = new Recording("audio/webm", new byte[] {1, 2, 3});

  /** フェイクの STT の既定の認識結果 (FakeDefaults)。 */
  private static final String TRANSCRIPT = "ここの式変形が分かりません";

  private DataSource dataSource;
  private FakeAi fakeAi;
  private MutableClock clock;
  private DialogueService dialogue;
  private UUID materialId;

  @BeforeEach
  void setUp() {
    dataSource = Fixtures.freshDataSource();
    Repositories repositories = new Repositories(dataSource);
    clock = new MutableClock(START, Fixtures.JAPAN);
    SettingsService settings = new SettingsService(repositories.settings(), clock);
    fakeAi = new FakeAi();
    dialogue = new DialogueService(settings, repositories.dialogues(), fakeAi, fakeAi, clock);
    completeSetup(settings, USER, "二次関数");
    completeSetup(settings, OTHER, "三角比");
    UUID unitId = settings.find(USER).orElseThrow().unitId();
    materialId = DialogueFixtures.insertMaterial(dataSource, unitId);
  }

  private static void completeSetup(SettingsService settings, UUID userId, String unitName) {
    settings.save(
        userId,
        new SettingsCommand(
            StudentType.LOGICAL, Subject.MATH, UnitName.parse(unitName).orElseThrow(), 3, true));
  }

  private UUID startLesson(int roundCount) {
    return DialogueFixtures.insertLesson(
        dataSource, USER, materialId, clock.instant().plusSeconds(600), roundCount);
  }

  /** 発話を 1 件受け付け、その字幕の識別子を返す。 */
  private UUID utter(UUID lessonId) {
    return dialogue
        .acceptUtterance(DialogueKind.LESSON, USER, lessonId, RECORDING)
        .orElseThrow()
        .utteranceId();
  }

  private int sttCalls() {
    return fakeAi.callCounts(USER).get(AiCallKind.STT);
  }

  private int ttsCalls() {
    return fakeAi.callCounts(USER).get(AiCallKind.TTS);
  }

  private static String codeOf(ConflictException error) {
    return error.code();
  }

  @Test
  void 発話は認識した文章を字幕に入れ_未応答の発話は_1_件だけ残る() {
    UUID lessonId = startLesson(0);

    Optional<DialogueService.Recognition> first =
        dialogue.acceptUtterance(DialogueKind.LESSON, USER, lessonId, RECORDING);
    UUID firstId = first.orElseThrow().utteranceId();
    UUID secondId = utter(lessonId);

    assertEquals(TRANSCRIPT, first.orElseThrow().text());
    assertEquals(1, DialogueFixtures.countUnrepliedUserTurns(dataSource, lessonId));
    ConflictException replaced =
        assertThrows(
            ConflictException.class,
            () -> dialogue.beginReply(DialogueKind.LESSON, USER, lessonId, firstId));
    assertEquals("utterance_not_found", codeOf(replaced));
    try (ReplyTicket ticket = dialogue.beginReply(DialogueKind.LESSON, USER, lessonId, secondId)) {
      assertEquals(TRANSCRIPT, ticket.utteranceText());
    }
  }

  @Test
  void 応答を確定すると往復が増え_発話は応答済みになり_度合いは最大値で残る() {
    UUID lessonId = startLesson(0);
    UUID firstId = utter(lessonId);

    try (ReplyTicket ticket = dialogue.beginReply(DialogueKind.LESSON, USER, lessonId, firstId)) {
      int rounds =
          dialogue.commitReply(
              ticket,
              new NewTurn(Speaker.STUDENT, "なるほど", "normal", null),
              Optional.of(new Degrees(List.of(1, 0, 2))));
      assertEquals(1, rounds);
    }
    ConflictException replied =
        assertThrows(
            ConflictException.class,
            () -> dialogue.beginReply(DialogueKind.LESSON, USER, lessonId, firstId));
    assertEquals("already_replied", codeOf(replied));

    UUID secondId = utter(lessonId);
    try (ReplyTicket ticket = dialogue.beginReply(DialogueKind.LESSON, USER, lessonId, secondId)) {
      dialogue.commitReply(
          ticket,
          new NewTurn(Speaker.STUDENT, "なるほど", "convinced", null),
          Optional.of(new Degrees(List.of(0, 2, 0))));
    }

    assertEquals(2, DialogueFixtures.lessonRoundCount(dataSource, lessonId));
    assertEquals(List.of(1, 2, 2), DialogueFixtures.lessonDegrees(dataSource, lessonId));
  }

  @Test
  void 処理中の印がある間は_発話も応答も_409_busy_になり_STT_を呼ばない() {
    UUID lessonId = startLesson(0);
    UUID utteranceId = utter(lessonId);
    int sttBefore = sttCalls();

    try (ReplyTicket ticket =
        dialogue.beginReply(DialogueKind.LESSON, USER, lessonId, utteranceId)) {
      ConflictException utterance =
          assertThrows(
              ConflictException.class,
              () -> dialogue.acceptUtterance(DialogueKind.LESSON, USER, lessonId, RECORDING));
      ConflictException reply =
          assertThrows(
              ConflictException.class,
              () -> dialogue.beginReply(DialogueKind.LESSON, USER, lessonId, utteranceId));

      assertEquals("busy", codeOf(utterance));
      assertEquals("busy", codeOf(reply));
      assertEquals(sttBefore, sttCalls());
    }

    assertTrue(
        dialogue.acceptUtterance(DialogueKind.LESSON, USER, lessonId, RECORDING).isPresent());
  }

  @Test
  void 処理中の印は_90_秒を過ぎると無視される() {
    UUID lessonId = startLesson(0);
    UUID utteranceId = utter(lessonId);

    try (ReplyTicket ticket =
        dialogue.beginReply(DialogueKind.LESSON, USER, lessonId, utteranceId)) {
      clock.advance(Duration.ofSeconds(89));
      assertEquals(
          "busy",
          codeOf(
              assertThrows(
                  ConflictException.class,
                  () -> dialogue.acceptUtterance(DialogueKind.LESSON, USER, lessonId, RECORDING))));

      clock.advance(Duration.ofSeconds(2));
      assertTrue(
          dialogue.acceptUtterance(DialogueKind.LESSON, USER, lessonId, RECORDING).isPresent());
    }
  }

  @Test
  void 授業が上限の_20_往復に達していれば_録音を捨てて_limit_reached_になり_STT_を呼ばない() {
    UUID lessonId = startLesson(20);
    int sttBefore = sttCalls();

    ConflictException error =
        assertThrows(
            ConflictException.class,
            () -> dialogue.acceptUtterance(DialogueKind.LESSON, USER, lessonId, RECORDING));

    assertEquals("limit_reached", codeOf(error));
    assertEquals(sttBefore, sttCalls());
    assertEquals(0, DialogueFixtures.countTurns(dataSource, lessonId));
  }

  @Test
  void 相談が上限の_15_往復に達していれば_発話も応答も_limit_reached_になる() {
    UUID consultationId =
        DialogueFixtures.insertConsultation(
            dataSource, USER, materialId, clock.instant().plusSeconds(600), 15);

    ConflictException utterance =
        assertThrows(
            ConflictException.class,
            () ->
                dialogue.acceptUtterance(
                    DialogueKind.CONSULTATION, USER, consultationId, RECORDING));
    assertEquals("limit_reached", codeOf(utterance));
  }

  @Test
  void 応答を受け付けた時点で往復が上限に達していれば_次の発話を拒否する() {
    UUID lessonId = startLesson(19);
    UUID utteranceId = utter(lessonId);

    try (ReplyTicket ticket =
        dialogue.beginReply(DialogueKind.LESSON, USER, lessonId, utteranceId)) {
      dialogue.commitReply(
          ticket, new NewTurn(Speaker.STUDENT, "なるほど", "normal", null), Optional.empty());
    }

    ConflictException error =
        assertThrows(
            ConflictException.class,
            () -> dialogue.acceptUtterance(DialogueKind.LESSON, USER, lessonId, RECORDING));
    assertEquals("limit_reached", codeOf(error));
    assertEquals(20, DialogueFixtures.lessonRoundCount(dataSource, lessonId));
  }

  @Test
  void 期限の時刻に達すると_発話と応答を受け付けない() {
    UUID lessonId = startLesson(0);
    UUID utteranceId = utter(lessonId);

    clock.advance(Duration.ofSeconds(600));

    assertEquals(
        "limit_reached",
        codeOf(
            assertThrows(
                ConflictException.class,
                () -> dialogue.beginReply(DialogueKind.LESSON, USER, lessonId, utteranceId))));
    assertEquals(
        "limit_reached",
        codeOf(
            assertThrows(
                ConflictException.class,
                () -> dialogue.acceptUtterance(DialogueKind.LESSON, USER, lessonId, RECORDING))));
  }

  @Test
  void 期限の前に受け付けた応答は_期限をまたいでも完了する() {
    UUID lessonId = startLesson(0);
    UUID utteranceId = utter(lessonId);

    try (ReplyTicket ticket =
        dialogue.beginReply(DialogueKind.LESSON, USER, lessonId, utteranceId)) {
      clock.advance(Duration.ofSeconds(601));

      int rounds =
          dialogue.commitReply(
              ticket, new NewTurn(Speaker.STUDENT, "なるほど", "normal", null), Optional.empty());
      assertEquals(1, rounds);
    }
    assertEquals(1, DialogueFixtures.lessonRoundCount(dataSource, lessonId));
  }

  @Test
  void 応答の途中で授業が中断されたら_結果を捨てて_404_になり_記録も字幕も残らない() {
    UUID lessonId = startLesson(0);
    UUID utteranceId = utter(lessonId);

    try (ReplyTicket ticket =
        dialogue.beginReply(DialogueKind.LESSON, USER, lessonId, utteranceId)) {
      DialogueFixtures.deleteLesson(dataSource, lessonId);

      assertThrows(
          NotFoundException.class,
          () ->
              dialogue.commitReply(
                  ticket,
                  new NewTurn(Speaker.STUDENT, "なるほど", "normal", null),
                  Optional.of(Degrees.initial())));
    }
    assertEquals(0, DialogueFixtures.countTurns(dataSource, lessonId));
  }

  @Test
  void 親の行が無ければ_発話は_404_になり_STT_の結果も入れない() {
    UUID missing = UUID.randomUUID();

    assertThrows(
        NotFoundException.class,
        () -> dialogue.acceptUtterance(DialogueKind.LESSON, USER, missing, RECORDING));
    assertEquals(0, DialogueFixtures.countTurns(dataSource, missing));
  }

  @Test
  void ほかの利用者の授業は_404_になる() {
    UUID lessonId = startLesson(0);

    assertThrows(
        NotFoundException.class,
        () -> dialogue.acceptUtterance(DialogueKind.LESSON, OTHER, lessonId, RECORDING));
  }

  @Test
  void 認識結果が空なら_何も入れず_往復にも数えない() {
    UUID lessonId = startLesson(0);
    fakeAi.script(
        USER, AiCallKind.STT, List.of(new ScriptedResponse(true, Map.of("text", "  "))), false);

    Optional<DialogueService.Recognition> result =
        dialogue.acceptUtterance(DialogueKind.LESSON, USER, lessonId, RECORDING);

    assertTrue(result.isEmpty());
    assertEquals(0, DialogueFixtures.countTurns(dataSource, lessonId));
    assertEquals(0, DialogueFixtures.lessonRoundCount(dataSource, lessonId));
  }

  @Test
  void 認識の失敗は_認識できなかったものとして_空を返し_印を残さない() {
    UUID lessonId = startLesson(0);
    fakeAi.script(USER, AiCallKind.STT, List.of(new ScriptedResponse(false, null)), false);

    assertTrue(dialogue.acceptUtterance(DialogueKind.LESSON, USER, lessonId, RECORDING).isEmpty());
    assertTrue(
        dialogue.acceptUtterance(DialogueKind.LESSON, USER, lessonId, RECORDING).isPresent());
  }

  @Test
  void 音声は字幕_1_件につき_1_回だけ生成し_二度目以降は保存したものを返す() {
    UUID lessonId = startLesson(0);
    UUID utteranceId = utter(lessonId);
    try (ReplyTicket ticket =
        dialogue.beginReply(DialogueKind.LESSON, USER, lessonId, utteranceId)) {
      dialogue.commitReply(
          ticket, new NewTurn(Speaker.STUDENT, "なるほど", "normal", null), Optional.empty());
    }
    UUID studentTurnId = DialogueFixtures.turnIdOf(dataSource, lessonId, "student");

    byte[] first = dialogue.synthesizeAudio(DialogueKind.LESSON, USER, lessonId, studentTurnId);
    byte[] second = dialogue.synthesizeAudio(DialogueKind.LESSON, USER, lessonId, studentTurnId);

    assertArrayEquals(first, second);
    assertEquals(1, ttsCalls());
  }

  @Test
  void 音声の生成が失敗すると_502_tts_failed_になり_同じ字幕では再度生成しない() {
    UUID lessonId = startLesson(0);
    UUID utteranceId = utter(lessonId);
    try (ReplyTicket ticket =
        dialogue.beginReply(DialogueKind.LESSON, USER, lessonId, utteranceId)) {
      dialogue.commitReply(
          ticket, new NewTurn(Speaker.STUDENT, "なるほど", "normal", null), Optional.empty());
    }
    UUID studentTurnId = DialogueFixtures.turnIdOf(dataSource, lessonId, "student");
    fakeAi.script(USER, AiCallKind.TTS, List.of(new ScriptedResponse(false, null)), false);

    ExternalFailureException first =
        assertThrows(
            ExternalFailureException.class,
            () -> dialogue.synthesizeAudio(DialogueKind.LESSON, USER, lessonId, studentTurnId));
    ExternalFailureException second =
        assertThrows(
            ExternalFailureException.class,
            () -> dialogue.synthesizeAudio(DialogueKind.LESSON, USER, lessonId, studentTurnId));

    assertEquals("tts_failed", first.code());
    assertEquals("tts_failed", second.code());
    assertEquals(1, ttsCalls());
  }

  @Test
  void 利用者の字幕には音声を作らない() {
    UUID lessonId = startLesson(0);
    UUID utteranceId = utter(lessonId);

    assertThrows(
        NotFoundException.class,
        () -> dialogue.synthesizeAudio(DialogueKind.LESSON, USER, lessonId, utteranceId));
    assertEquals(0, ttsCalls());
  }

  @Test
  void 設定が未完了の利用者は_setup_required_になる() {
    UUID lessonId = startLesson(0);
    UUID stranger = UUID.randomUUID();

    ConflictException error =
        assertThrows(
            ConflictException.class,
            () -> dialogue.acceptUtterance(DialogueKind.LESSON, stranger, lessonId, RECORDING));

    assertEquals("setup_required", codeOf(error));
  }
}
