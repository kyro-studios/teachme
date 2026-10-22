package com.teachme.persistence;

import static com.teachme.persistence.generated.Tables.CONSULTATIONS;
import static com.teachme.persistence.generated.Tables.DIALOGUE_TURNS;
import static com.teachme.persistence.generated.Tables.LESSONS;
import static com.teachme.persistence.generated.Tables.USER_SETTINGS;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teachme.domain.Annotation;
import com.teachme.domain.Degrees;
import com.teachme.domain.DialogueKind;
import com.teachme.domain.Expression;
import com.teachme.domain.OrganizedProblem;
import com.teachme.domain.Speaker;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Table;
import org.jooq.impl.DSL;

/**
 * 進行中の授業と相談 (`lessons`、`consultations`) の親の行と、字幕 (`dialogue_turns`) のリポジトリ (アーキテクチャ設計 4.4、7.3)。
 *
 * <p>親の行は、{@link DialogueKind} ごとに別の表にある。列の対応は {@link Parent} にまとめ、呼び出し側は種類だけを渡す。
 *
 * <p>発話の確定と応答の確定は、親の行を `for update` で取ってから書く。中断と並んでも、どちらか一方だけが結果を残す。
 */
public final class DialogueRepository {

  private final DSLContext dsl;

  DialogueRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /**
   * 親の行の、応答と発話の受け付けに使う状態。
   *
   * @param roundCount 往復の数
   * @param deadlineAt 期限
   * @param materialId 使う教材の識別子
   */
  public record ParentState(int roundCount, Instant deadlineAt, UUID materialId) {}

  /**
   * 字幕の 1 行。
   *
   * @param audio 生成した音声。まだ無ければ null
   */
  public record TurnRow(
      Speaker speaker, String body, boolean replied, boolean audioAttempted, byte[] audio) {}

  /**
   * 字幕の、話し手と文章。順序は、字幕の追加の順 (`seq`)。
   *
   * @param id 字幕の識別子
   */
  public record Subtitle(UUID id, Speaker speaker, String body) {}

  /**
   * 確定した応答の結果。
   *
   * @param turnId 入れた応答の字幕の識別子。API の `turn.turnId` と音声の取得に使う
   * @param roundCount 増やした後の往復の数
   */
  public record RecordedReply(UUID turnId, int roundCount) {}

  /**
   * 字幕に入れる内容。
   *
   * @param expression 生徒や先輩先生の表情。無ければ null
   * @param extras 生徒の印、または先輩先生のボードへの書き込み (JSON の文字列)。無ければ null
   */
  public record NewTurn(Speaker speaker, String body, String expression, String extras) {

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * 生徒の字幕。生徒の印は、字幕の追加情報 (JSON) として入れる。
     *
     * @param annotation ボードに示す印。無ければ空
     */
    public static NewTurn student(
        String body, Expression expression, Optional<Annotation> annotation) {
      return new NewTurn(
          Speaker.STUDENT,
          body,
          expression.wireName(),
          annotation.map(NewTurn::annotationJson).orElse(null));
    }

    private static String annotationJson(Annotation annotation) {
      try {
        return JSON.writeValueAsString(
            new StoredAnnotation(annotation.kind().wireName(), annotation.region()));
      } catch (JsonProcessingException e) {
        throw new IllegalStateException("生徒の印を JSON にできません", e);
      }
    }

    private record StoredAnnotation(String kind, OrganizedProblem.Region region) {}
  }

  /** 親の行の、この機能で使う列。授業と相談で列の名前が違うため、まとめて持つ。 */
  private record Parent(
      Table<?> table,
      Field<UUID> id,
      Field<UUID> userId,
      Field<Short> roundCount,
      Field<OffsetDateTime> startedAt,
      Field<OffsetDateTime> deadlineAt,
      Field<OffsetDateTime> busySince,
      Field<UUID> materialId,
      Field<UUID> turnParentId) {

    static Parent of(DialogueKind kind) {
      return switch (kind) {
        case LESSON ->
            new Parent(
                LESSONS,
                LESSONS.ID,
                LESSONS.USER_ID,
                LESSONS.ROUND_COUNT,
                LESSONS.STARTED_AT,
                LESSONS.DEADLINE_AT,
                LESSONS.BUSY_SINCE,
                LESSONS.MATERIAL_ID,
                DIALOGUE_TURNS.LESSON_ID);
        case CONSULTATION ->
            new Parent(
                CONSULTATIONS,
                CONSULTATIONS.ID,
                CONSULTATIONS.USER_ID,
                CONSULTATIONS.ROUND_COUNT,
                CONSULTATIONS.STARTED_AT,
                CONSULTATIONS.DEADLINE_AT,
                CONSULTATIONS.BUSY_SINCE,
                CONSULTATIONS.MATERIAL_ID,
                DIALOGUE_TURNS.CONSULTATION_ID);
      };
    }
  }

  /** 利用者の、進行中の親の行の状態。無ければ (ほかの利用者のもの、中断された後も含む) 空。 */
  public Optional<ParentState> findParent(DialogueKind kind, UUID userId, UUID parentId) {
    Parent parent = Parent.of(kind);
    return dsl.select(parent.roundCount(), parent.deadlineAt(), parent.materialId())
        .from(parent.table())
        .where(parent.id().eq(parentId))
        .and(parent.userId().eq(userId))
        .fetchOptional(
            row ->
                new ParentState(row.value1().intValue(), row.value2().toInstant(), row.value3()));
  }

  /**
   * 授業を始める。同じ利用者の進行中の授業は、中断として消す (字幕も連鎖して消える)。新しい授業と、最初の発話 (生徒の字幕) を、1 つのトランザクションで入れる。
   *
   * @param turnId 最初の発話の字幕の識別子
   */
  public void startLesson(
      UUID userId,
      UUID unitId,
      UUID materialId,
      UUID lessonId,
      Instant startedAt,
      Instant deadlineAt,
      UUID turnId,
      NewTurn opening) {
    dsl.transaction(
        configuration -> {
          DSLContext tx = DSL.using(configuration);
          // 利用者の設定の行を先に取り、同じ利用者の開始どうしを直列にする (複数のタブや再送で、進行中の授業が複数残らないように)
          tx.selectOne()
              .from(USER_SETTINGS)
              .where(USER_SETTINGS.USER_ID.eq(userId))
              .forUpdate()
              .fetch();
          tx.deleteFrom(LESSONS).where(LESSONS.USER_ID.eq(userId)).execute();
          tx.insertInto(LESSONS)
              .set(LESSONS.ID, lessonId)
              .set(LESSONS.USER_ID, userId)
              .set(LESSONS.UNIT_ID, unitId)
              .set(LESSONS.MATERIAL_ID, materialId)
              .set(LESSONS.STARTED_AT, toOffsetDateTime(startedAt))
              .set(LESSONS.DEADLINE_AT, toOffsetDateTime(deadlineAt))
              .execute();
          insertTurn(tx, Parent.of(DialogueKind.LESSON), lessonId, turnId, opening);
        });
  }

  /** 利用者の進行中の親の行を消す (中断)。字幕も連鎖して消える。無ければ何もしない。 */
  public void deleteParent(DialogueKind kind, UUID userId, UUID parentId) {
    Parent parent = Parent.of(kind);
    dsl.deleteFrom(parent.table())
        .where(parent.id().eq(parentId))
        .and(parent.userId().eq(userId))
        .execute();
  }

  /**
   * 開始から threshold より前に始まった、進行中の親の行を消す (掃除)。字幕も連鎖して消える。
   *
   * @return 消した件数
   */
  public int deleteStartedBefore(DialogueKind kind, Instant threshold) {
    Parent parent = Parent.of(kind);
    return dsl.deleteFrom(parent.table())
        .where(parent.startedAt().lessThan(toOffsetDateTime(threshold)))
        .execute();
  }

  /**
   * 利用者の進行中の親の字幕を、追加の順に返す。ほかの利用者のもの、無い親のものは空。
   *
   * @return 字幕の列。利用者の字幕も含む
   */
  public List<Subtitle> listSubtitles(DialogueKind kind, UUID userId, UUID parentId) {
    Parent parent = Parent.of(kind);
    return dsl.select(DIALOGUE_TURNS.ID, DIALOGUE_TURNS.SPEAKER, DIALOGUE_TURNS.BODY)
        .from(DIALOGUE_TURNS)
        .join(parent.table())
        .on(parent.id().eq(parent.turnParentId()))
        .where(parent.turnParentId().eq(parentId))
        .and(parent.userId().eq(userId))
        .orderBy(DIALOGUE_TURNS.SEQ)
        .fetch(row -> new Subtitle(row.value1(), Speaker.fromWireName(row.value2()), row.value3()));
  }

  /**
   * 処理中の印を立てる。印が無いか、`staleBefore` より前に立てられた印なら、立てられる。
   *
   * @param busySince 印の時刻。後で {@link #releaseBusy} に同じ値を渡す
   * @param staleBefore この時刻より前の印は、異常終了の名残として無視する
   * @return 立てられたら true。ほかの処理が進行中なら false
   */
  public boolean markBusy(
      DialogueKind kind, UUID userId, UUID parentId, Instant busySince, Instant staleBefore) {
    Parent parent = Parent.of(kind);
    OffsetDateTime since = toOffsetDateTime(busySince);
    int updated =
        dsl.update(parent.table())
            .set(parent.busySince(), since)
            .where(parent.id().eq(parentId))
            .and(parent.userId().eq(userId))
            .and(
                parent
                    .busySince()
                    .isNull()
                    .or(parent.busySince().lt(toOffsetDateTime(staleBefore))))
            .execute();
    return updated == 1;
  }

  /** 処理中の印を外す。`busySince` が一致するときだけ外すため、後から立った別の処理の印は消さない。 */
  public void releaseBusy(DialogueKind kind, UUID parentId, Instant busySince) {
    Parent parent = Parent.of(kind);
    dsl.update(parent.table())
        .set(parent.busySince(), (OffsetDateTime) null)
        .where(parent.id().eq(parentId))
        .and(parent.busySince().eq(toOffsetDateTime(busySince)))
        .execute();
  }

  /**
   * 認識できた発話を字幕に入れる。未応答の利用者の字幕は消して、置き換える。
   *
   * @return 入れた字幕の識別子。親の行が無ければ (中断された後なら) 何もせず空
   */
  public Optional<UUID> recordUtterance(
      DialogueKind kind, UUID userId, UUID parentId, String body) {
    Parent parent = Parent.of(kind);
    return dsl.transactionResult(
        tx -> {
          DSLContext in = tx.dsl();
          if (!lockParent(in, parent, userId, parentId)) {
            return Optional.empty();
          }
          in.deleteFrom(DIALOGUE_TURNS)
              .where(parent.turnParentId().eq(parentId))
              .and(DIALOGUE_TURNS.SPEAKER.eq(Speaker.USER.wireName()))
              .and(DIALOGUE_TURNS.REPLIED.eq(false))
              .execute();
          UUID id = UUID.randomUUID();
          insertTurn(in, parent, parentId, id, new NewTurn(Speaker.USER, body, null, null));
          return Optional.of(id);
        });
  }

  /**
   * 応答を確定する。利用者の字幕を応答済みにし、応答の字幕を入れ、往復を 1 つ増やす。`evaluated` があれば、度合いを最大値で取り込む (授業のみ)。
   *
   * @param utteranceId 応答の対象の、利用者の字幕。処理中の印の下で、未応答であることを前提とする
   * @return 入れた応答の字幕の識別子と、増やした後の往復の数。親の行が無ければ (中断された後なら) 何もせず空
   * @throws IllegalStateException 対象の字幕が未応答の利用者の字幕として見つからないとき (処理中の印の不整合)
   * @throws IllegalArgumentException 相談に度合いを渡したとき
   */
  public Optional<RecordedReply> recordReply(
      DialogueKind kind,
      UUID userId,
      UUID parentId,
      UUID utteranceId,
      NewTurn reply,
      Optional<Degrees> evaluated) {
    if (evaluated.isPresent() && kind != DialogueKind.LESSON) {
      throw new IllegalArgumentException("度合いは授業だけが持つ");
    }
    Parent parent = Parent.of(kind);
    return dsl.transactionResult(
        tx -> {
          DSLContext in = tx.dsl();
          if (!lockParent(in, parent, userId, parentId)) {
            return Optional.empty();
          }
          int replied =
              in.update(DIALOGUE_TURNS)
                  .set(DIALOGUE_TURNS.REPLIED, true)
                  .where(DIALOGUE_TURNS.ID.eq(utteranceId))
                  .and(parent.turnParentId().eq(parentId))
                  .and(DIALOGUE_TURNS.SPEAKER.eq(Speaker.USER.wireName()))
                  .and(DIALOGUE_TURNS.REPLIED.eq(false))
                  .execute();
          if (replied != 1) {
            throw new IllegalStateException("未応答の発話が見つからない (処理中の印の不整合)");
          }
          UUID turnId = UUID.randomUUID();
          insertTurn(in, parent, parentId, turnId, reply);
          if (evaluated.isPresent()) {
            mergeDegrees(in, parentId, evaluated.get());
          }
          Short roundCount =
              in.update(parent.table())
                  .set(parent.roundCount(), parent.roundCount().plus(1))
                  .where(parent.id().eq(parentId))
                  .returningResult(parent.roundCount())
                  .fetchSingle()
                  .value1();
          return Optional.of(new RecordedReply(turnId, roundCount.intValue()));
        });
  }

  /** 利用者の進行中の親の、字幕 1 行。親の行が無い、またはほかの利用者のものなら空。 */
  public Optional<TurnRow> findTurn(DialogueKind kind, UUID userId, UUID parentId, UUID turnId) {
    Parent parent = Parent.of(kind);
    return dsl.select(
            DIALOGUE_TURNS.SPEAKER,
            DIALOGUE_TURNS.BODY,
            DIALOGUE_TURNS.REPLIED,
            DIALOGUE_TURNS.AUDIO_ATTEMPTED,
            DIALOGUE_TURNS.AUDIO)
        .from(DIALOGUE_TURNS)
        .join(parent.table())
        .on(parent.id().eq(parent.turnParentId()))
        .where(DIALOGUE_TURNS.ID.eq(turnId))
        .and(parent.turnParentId().eq(parentId))
        .and(parent.userId().eq(userId))
        .fetchOptional(
            row ->
                new TurnRow(
                    Speaker.fromWireName(row.value1()),
                    row.value2(),
                    row.value3(),
                    row.value4(),
                    row.value5()));
  }

  /** 字幕の音声を、生成済みにする。まだ生成していない (`audio_attempted` が false) ときだけ、立てて true を返す。 */
  public boolean markAudioAttempted(UUID turnId) {
    int updated =
        dsl.update(DIALOGUE_TURNS)
            .set(DIALOGUE_TURNS.AUDIO_ATTEMPTED, true)
            .where(DIALOGUE_TURNS.ID.eq(turnId))
            .and(DIALOGUE_TURNS.AUDIO_ATTEMPTED.eq(false))
            .execute();
    return updated == 1;
  }

  /** 生成した音声を、字幕に保存する。 */
  public void saveAudio(UUID turnId, byte[] audio) {
    dsl.update(DIALOGUE_TURNS)
        .set(DIALOGUE_TURNS.AUDIO, audio)
        .where(DIALOGUE_TURNS.ID.eq(turnId))
        .execute();
  }

  /** 親の行を `for update` で取る。行があれば true。 */
  private static boolean lockParent(DSLContext in, Parent parent, UUID userId, UUID parentId) {
    return in.selectOne()
        .from(parent.table())
        .where(parent.id().eq(parentId))
        .and(parent.userId().eq(userId))
        .forUpdate()
        .fetchOptional()
        .isPresent();
  }

  /** 授業の度合いを、今の値と、取り込む値の最大値に更新する。親の行は `for update` で取ってあること。 */
  private static void mergeDegrees(DSLContext in, UUID lessonId, Degrees evaluated) {
    Short[] stored =
        in.select(LESSONS.DEGREES)
            .from(LESSONS)
            .where(LESSONS.ID.eq(lessonId))
            .fetchSingle()
            .value1();
    Degrees merged =
        new Degrees(Arrays.stream(stored).map(Short::intValue).toList()).mergeMax(evaluated);
    Short[] values =
        merged.values().stream().map(value -> (short) value.intValue()).toArray(Short[]::new);
    in.update(LESSONS).set(LESSONS.DEGREES, values).where(LESSONS.ID.eq(lessonId)).execute();
  }

  /** 字幕を 1 行入れる (未応答の印は立てない)。`seq` は、親ごとの最後の番号の次。親の行は `for update` で取ってあること。 */
  private static void insertTurn(
      DSLContext in, Parent parent, UUID parentId, UUID id, NewTurn turn) {
    Integer maxSeq =
        in.select(DSL.max(DIALOGUE_TURNS.SEQ))
            .from(DIALOGUE_TURNS)
            .where(parent.turnParentId().eq(parentId))
            .fetchSingle()
            .value1();
    JSONB extras = turn.extras() == null ? null : JSONB.valueOf(turn.extras());
    in.insertInto(DIALOGUE_TURNS)
        .set(DIALOGUE_TURNS.ID, id)
        .set(parent.turnParentId(), parentId)
        .set(DIALOGUE_TURNS.SEQ, maxSeq == null ? 1 : maxSeq + 1)
        .set(DIALOGUE_TURNS.SPEAKER, turn.speaker().wireName())
        .set(DIALOGUE_TURNS.BODY, turn.body())
        .set(DIALOGUE_TURNS.EXPRESSION, turn.expression())
        .set(DIALOGUE_TURNS.EXTRAS, extras)
        .set(DIALOGUE_TURNS.REPLIED, false)
        .execute();
  }

  private static OffsetDateTime toOffsetDateTime(Instant instant) {
    return OffsetDateTime.ofInstant(instant.truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC);
  }
}
