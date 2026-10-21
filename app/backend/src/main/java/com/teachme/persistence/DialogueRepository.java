package com.teachme.persistence;

import static com.teachme.persistence.generated.Tables.CONSULTATIONS;
import static com.teachme.persistence.generated.Tables.DIALOGUE_TURNS;
import static com.teachme.persistence.generated.Tables.LESSONS;

import com.teachme.domain.Degrees;
import com.teachme.domain.DialogueKind;
import com.teachme.domain.Speaker;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
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
   */
  public record ParentState(int roundCount, Instant deadlineAt) {}

  /**
   * 字幕の 1 行。
   *
   * @param audio 生成した音声。まだ無ければ null
   */
  public record TurnRow(
      Speaker speaker, String body, boolean replied, boolean audioAttempted, byte[] audio) {}

  /**
   * 字幕に入れる内容。
   *
   * @param expression 生徒や先輩先生の表情。無ければ null
   * @param extras 生徒の印、または先輩先生のボードへの書き込み (JSON の文字列)。無ければ null
   */
  public record NewTurn(Speaker speaker, String body, String expression, String extras) {}

  /** 親の行の、この機能で使う列。授業と相談で列の名前が違うため、まとめて持つ。 */
  private record Parent(
      Table<?> table,
      Field<UUID> id,
      Field<UUID> userId,
      Field<Short> roundCount,
      Field<OffsetDateTime> deadlineAt,
      Field<OffsetDateTime> busySince,
      Field<UUID> turnParentId) {

    static Parent of(DialogueKind kind) {
      return switch (kind) {
        case LESSON ->
            new Parent(
                LESSONS,
                LESSONS.ID,
                LESSONS.USER_ID,
                LESSONS.ROUND_COUNT,
                LESSONS.DEADLINE_AT,
                LESSONS.BUSY_SINCE,
                DIALOGUE_TURNS.LESSON_ID);
        case CONSULTATION ->
            new Parent(
                CONSULTATIONS,
                CONSULTATIONS.ID,
                CONSULTATIONS.USER_ID,
                CONSULTATIONS.ROUND_COUNT,
                CONSULTATIONS.DEADLINE_AT,
                CONSULTATIONS.BUSY_SINCE,
                DIALOGUE_TURNS.CONSULTATION_ID);
      };
    }
  }

  /** 利用者の、進行中の親の行の状態。無ければ (ほかの利用者のもの、中断された後も含む) 空。 */
  public Optional<ParentState> findParent(DialogueKind kind, UUID userId, UUID parentId) {
    Parent parent = Parent.of(kind);
    return dsl.select(parent.roundCount(), parent.deadlineAt())
        .from(parent.table())
        .where(parent.id().eq(parentId))
        .and(parent.userId().eq(userId))
        .fetchOptional(row -> new ParentState(row.value1().intValue(), row.value2().toInstant()));
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
   * @return 増やした後の往復の数。親の行が無ければ (中断された後なら) 何もせず空
   * @throws IllegalStateException 対象の字幕が未応答の利用者の字幕として見つからないとき (処理中の印の不整合)
   * @throws IllegalArgumentException 相談に度合いを渡したとき
   */
  public Optional<Integer> recordReply(
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
          insertTurn(in, parent, parentId, UUID.randomUUID(), reply);
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
          return Optional.of(roundCount.intValue());
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
