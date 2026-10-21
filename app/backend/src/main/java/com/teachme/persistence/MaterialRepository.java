package com.teachme.persistence;

import static com.teachme.persistence.generated.Tables.MATERIALS;
import static com.teachme.persistence.generated.Tables.MATERIAL_IMAGES;
import static com.teachme.persistence.generated.Tables.UNITS;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teachme.domain.MaterialStatus;
import com.teachme.domain.OrganizedProblem;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.SelectJoinStep;
import org.jooq.impl.DSL;

/**
 * 教材 (`materials`) と教材の画像 (`material_images`) の保存と取得 (仕様書 5.3、5.9、アーキテクチャ設計 7 節)。
 *
 * <p>整理の結果の列は、`jsonb` のものを Jackson で読み書きする。読んだ結果は、{@link OrganizedProblem#parse} で検査し直す。
 */
public final class MaterialRepository {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final TypeReference<List<OrganizedProblem.Figure>> FIGURE_LIST =
      new TypeReference<>() {};
  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

  /** 教材の画像の枚数。整理の結果の図表の画像の番号を検査するため、読むときに数える。 */
  private static final Field<Integer> IMAGE_COUNT =
      DSL.field(
              DSL.selectCount()
                  .from(MATERIAL_IMAGES)
                  .where(MATERIAL_IMAGES.MATERIAL_ID.eq(MATERIALS.ID)))
          .as("image_count");

  private static final List<Field<?>> ROW_FIELDS =
      List.of(
          MATERIALS.ID,
          MATERIALS.UNIT_ID,
          MATERIALS.STATUS,
          MATERIALS.PROBLEM_TEXT,
          MATERIALS.FIGURES,
          MATERIALS.MODEL_ANSWER,
          MATERIALS.EXPLANATION_STEPS,
          MATERIALS.STUDENT_WORK,
          MATERIALS.TEACHING_POINTS,
          IMAGE_COUNT);

  private final DSLContext dsl;

  MaterialRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /**
   * 教材 1 件 (単元の中での番号は含まない)。`problem` は、状態が `organized` のときだけ値を持つ。
   *
   * @param problem 整理の結果。整理済みでなければ null
   */
  public record MaterialRow(
      UUID id, UUID unitId, MaterialStatus status, OrganizedProblem problem) {}

  /**
   * 教材の画像 1 枚。
   *
   * @param contentType `image/jpeg` か `image/png`
   * @param data 画像の中身
   */
  public record StoredImage(String contentType, byte[] data) {}

  /**
   * 新しい教材の画像 1 枚。番号は、渡した順に 1 から振る。
   *
   * @param contentType `image/jpeg` か `image/png`
   * @param data 画像の中身
   */
  public record NewImage(String contentType, byte[] data) {}

  /**
   * 教材と画像を、1 つのトランザクションで保存し、状態を `organizing` にする。
   *
   * @param startedAt 整理を始めた時刻。途切れた整理を見つける掃除に使う
   */
  public void insertOrganizing(
      UUID materialId, UUID unitId, List<NewImage> images, Instant startedAt) {
    dsl.transaction(
        configuration -> {
          DSLContext tx = DSL.using(configuration);
          tx.insertInto(MATERIALS)
              .set(MATERIALS.ID, materialId)
              .set(MATERIALS.UNIT_ID, unitId)
              .set(MATERIALS.STATUS, MaterialStatus.ORGANIZING.id())
              .set(MATERIALS.ORGANIZE_STARTED_AT, toOffset(startedAt))
              .execute();
          for (int i = 0; i < images.size(); i++) {
            NewImage image = images.get(i);
            tx.insertInto(MATERIAL_IMAGES)
                .set(MATERIAL_IMAGES.ID, UUID.randomUUID())
                .set(MATERIAL_IMAGES.MATERIAL_ID, materialId)
                .set(MATERIAL_IMAGES.POSITION, (short) (i + 1))
                .set(MATERIAL_IMAGES.CONTENT_TYPE, image.contentType())
                .set(MATERIAL_IMAGES.DATA, image.data())
                .execute();
          }
        });
  }

  /** 単元の教材を、登録順 (`created_at`、同じときは識別子) に返す。 */
  public List<MaterialRow> listByUnit(UUID unitId) {
    return selectRows()
        .where(MATERIALS.UNIT_ID.eq(unitId))
        .orderBy(MATERIALS.CREATED_AT, MATERIALS.ID)
        .fetch(MaterialRepository::toRow);
  }

  /** 利用者の教材を返す。ほかの利用者の教材や、無い教材は空。 */
  public Optional<MaterialRow> find(UUID userId, UUID materialId) {
    return selectRows()
        .join(UNITS)
        .on(UNITS.ID.eq(MATERIALS.UNIT_ID))
        .where(MATERIALS.ID.eq(materialId))
        .and(UNITS.USER_ID.eq(userId))
        .fetchOptional(MaterialRepository::toRow);
  }

  /** 教材の持ち主 (利用者の識別子)。教材が無ければ空。 */
  public Optional<UUID> findOwner(UUID materialId) {
    return dsl.select(UNITS.USER_ID)
        .from(MATERIALS)
        .join(UNITS)
        .on(UNITS.ID.eq(MATERIALS.UNIT_ID))
        .where(MATERIALS.ID.eq(materialId))
        .fetchOptional(UNITS.USER_ID);
  }

  /** 教材を削除する。画像も、一緒に消える。無ければ何もしない。 */
  public void delete(UUID materialId) {
    dsl.deleteFrom(MATERIALS).where(MATERIALS.ID.eq(materialId)).execute();
  }

  /** 利用者の教材の画像 (番号は 1 から)。教材が無い、ほかの利用者のもの、番号が無い場合は空。 */
  public Optional<StoredImage> findImage(UUID userId, UUID materialId, int position) {
    return dsl.select(MATERIAL_IMAGES.CONTENT_TYPE, MATERIAL_IMAGES.DATA)
        .from(MATERIAL_IMAGES)
        .join(MATERIALS)
        .on(MATERIALS.ID.eq(MATERIAL_IMAGES.MATERIAL_ID))
        .join(UNITS)
        .on(UNITS.ID.eq(MATERIALS.UNIT_ID))
        .where(MATERIAL_IMAGES.MATERIAL_ID.eq(materialId))
        .and(MATERIAL_IMAGES.POSITION.eq((short) position))
        .and(UNITS.USER_ID.eq(userId))
        .fetchOptional(
            row ->
                new StoredImage(
                    row.get(MATERIAL_IMAGES.CONTENT_TYPE), row.get(MATERIAL_IMAGES.DATA)));
  }

  /** 教材の画像を、番号の順に返す。教材が無ければ空の一覧。 */
  public List<StoredImage> loadImages(UUID materialId) {
    return dsl.select(MATERIAL_IMAGES.CONTENT_TYPE, MATERIAL_IMAGES.DATA)
        .from(MATERIAL_IMAGES)
        .where(MATERIAL_IMAGES.MATERIAL_ID.eq(materialId))
        .orderBy(MATERIAL_IMAGES.POSITION)
        .fetch(
            row ->
                new StoredImage(
                    row.get(MATERIAL_IMAGES.CONTENT_TYPE), row.get(MATERIAL_IMAGES.DATA)));
  }

  /**
   * 整理中の教材を、整理済みにする。状態が `organizing` のときだけ更新する。
   *
   * @return 更新したら true。教材が消えた、または整理中でなければ false (結果は捨てる)
   */
  public boolean markOrganized(UUID materialId, OrganizedProblem problem) {
    return dsl.update(MATERIALS)
            .set(MATERIALS.STATUS, MaterialStatus.ORGANIZED.id())
            .set(MATERIALS.PROBLEM_TEXT, problem.problemText())
            .set(MATERIALS.FIGURES, toJsonb(problem.figures()))
            .set(MATERIALS.MODEL_ANSWER, problem.modelAnswer())
            .set(MATERIALS.EXPLANATION_STEPS, toJsonb(problem.explanationSteps()))
            .set(MATERIALS.STUDENT_WORK, problem.studentWork())
            .set(MATERIALS.TEACHING_POINTS, toJsonb(problem.teachingPoints()))
            .where(MATERIALS.ID.eq(materialId))
            .and(MATERIALS.STATUS.eq(MaterialStatus.ORGANIZING.id()))
            .execute()
        == 1;
  }

  /**
   * 整理中の教材を、整理できませんでした (`failed`) にする。状態が `organizing` のときだけ更新する。
   *
   * @return 更新したら true
   */
  public boolean markFailed(UUID materialId) {
    return dsl.update(MATERIALS)
            .set(MATERIALS.STATUS, MaterialStatus.FAILED.id())
            .where(MATERIALS.ID.eq(materialId))
            .and(MATERIALS.STATUS.eq(MaterialStatus.ORGANIZING.id()))
            .execute()
        == 1;
  }

  /**
   * 整理できなかった教材を、整理中に戻す (「やり直す」)。状態が `failed` のときだけ更新する。
   *
   * @param startedAt 整理を始めた時刻
   * @return 更新したら true。`failed` でなければ false (二重の「やり直す」を防ぐ)
   */
  public boolean restartIfFailed(UUID materialId, Instant startedAt) {
    return dsl.update(MATERIALS)
            .set(MATERIALS.STATUS, MaterialStatus.ORGANIZING.id())
            .set(MATERIALS.ORGANIZE_STARTED_AT, toOffset(startedAt))
            .where(MATERIALS.ID.eq(materialId))
            .and(MATERIALS.STATUS.eq(MaterialStatus.FAILED.id()))
            .execute()
        == 1;
  }

  /**
   * 開始から threshold より前に始まったまま、整理中の教材を、整理できませんでした (`failed`) にする。
   *
   * @return 失敗にした件数
   */
  public int failOrganizingStartedBefore(Instant threshold) {
    return dsl.update(MATERIALS)
        .set(MATERIALS.STATUS, MaterialStatus.FAILED.id())
        .where(MATERIALS.STATUS.eq(MaterialStatus.ORGANIZING.id()))
        .and(MATERIALS.ORGANIZE_STARTED_AT.lessThan(toOffset(threshold)))
        .execute();
  }

  private SelectJoinStep<Record> selectRows() {
    return dsl.select(ROW_FIELDS).from(MATERIALS);
  }

  private static MaterialRow toRow(Record record) {
    MaterialStatus status =
        MaterialStatus.fromId(record.get(MATERIALS.STATUS)).orElseThrow(IllegalStateException::new);
    OrganizedProblem problem = status == MaterialStatus.ORGANIZED ? restoreProblem(record) : null;
    return new MaterialRow(
        record.get(MATERIALS.ID), record.get(MATERIALS.UNIT_ID), status, problem);
  }

  /** 整理済みの行から、整理の結果を読む。形が外れていれば、保存の誤りとして例外にする。 */
  private static OrganizedProblem restoreProblem(Record record) {
    return OrganizedProblem.parse(
            record.get(MATERIALS.PROBLEM_TEXT),
            fromJson(record.get(MATERIALS.FIGURES), FIGURE_LIST),
            record.get(MATERIALS.MODEL_ANSWER),
            fromJson(record.get(MATERIALS.EXPLANATION_STEPS), STRING_LIST),
            record.get(MATERIALS.STUDENT_WORK),
            fromJson(record.get(MATERIALS.TEACHING_POINTS), STRING_LIST),
            record.get(IMAGE_COUNT))
        .orElseThrow(() -> new IllegalStateException("保存された整理の結果の形が正しくありません"));
  }

  private static JSONB toJsonb(Object value) {
    try {
      return JSONB.valueOf(JSON.writeValueAsString(value));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("整理の結果を JSON にできません", e);
    }
  }

  private static <T> T fromJson(JSONB value, TypeReference<T> type) {
    try {
      return JSON.readValue(value.data(), type);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("保存された整理の結果を読めません", e);
    }
  }

  private static OffsetDateTime toOffset(Instant instant) {
    return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }
}
