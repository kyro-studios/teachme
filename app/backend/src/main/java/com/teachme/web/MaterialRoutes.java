package com.teachme.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.teachme.application.MaterialOrganizer;
import com.teachme.application.MaterialService;
import com.teachme.application.MaterialService.ImageFile;
import com.teachme.application.MaterialService.MaterialView;
import com.teachme.application.MaterialService.Upload;
import com.teachme.domain.ImageFormat;
import com.teachme.domain.OrganizedProblem;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 教材 (`/api/materials`、openapi.yaml の materials)。登録は画像と確認のチェックを検査し、整理は {@link MaterialOrganizer}
 * に任せる。
 *
 * <p>整理のワーカーと掃除は、サーバーの起動と停止に合わせて開始と終了する。
 */
public final class MaterialRoutes implements Routes {

  private static final String IMAGES_FIELD = "images";
  private static final String PHOTO_CONFIRMED_FIELD = "photoConfirmed";
  private static final String PHOTO_CONFIRMED_CODE = "photo_not_confirmed";
  private static final String PHOTO_CONFIRMED_MESSAGE = "写真に氏名、学校名、連絡先が写っていないことを確認してください";
  private static final String INVALID_IMAGES_CODE = "invalid_images";
  private static final String INVALID_IMAGES_COUNT_MESSAGE = "画像は 1 枚から 3 枚まで選んでください";
  private static final String INVALID_IMAGE_MESSAGE = "画像は JPEG か PNG で、1 枚 10 MB 以下にしてください";

  /** `GET /materials` の応答。 */
  public record MaterialListResponse(List<MaterialSummaryResponse> materials) {}

  /** 教材の概要 (openapi.yaml の `MaterialSummary`)。 */
  public record MaterialSummaryResponse(String id, int number, String status) {

    static MaterialSummaryResponse of(MaterialView view) {
      return new MaterialSummaryResponse(view.id().toString(), view.number(), view.status().id());
    }
  }

  /** 教材の詳細 (openapi.yaml の `MaterialDetail`)。整理済みのときだけ `problem` を持ち、それ以外は null で書く。 */
  public record MaterialDetailResponse(
      String id,
      int number,
      String status,
      @JsonInclude(JsonInclude.Include.ALWAYS) ProblemResponse problem) {

    static MaterialDetailResponse of(MaterialView view) {
      return new MaterialDetailResponse(
          view.id().toString(),
          view.number(),
          view.status().id(),
          view.problem() == null ? null : ProblemResponse.of(view.problem()));
    }
  }

  /** 整理の結果 (openapi.yaml の `Problem`)。図表の矩形は、`OrganizedProblem.Figure` の形のまま返す。 */
  public record ProblemResponse(
      String problemText,
      List<OrganizedProblem.Figure> figures,
      String modelAnswer,
      List<String> explanationSteps,
      String studentWork,
      List<String> teachingPoints) {

    static ProblemResponse of(OrganizedProblem problem) {
      return new ProblemResponse(
          problem.problemText(),
          problem.figures(),
          problem.modelAnswer(),
          problem.explanationSteps(),
          problem.studentWork(),
          problem.teachingPoints());
    }
  }

  private final MaterialService materials;
  private final MaterialOrganizer organizer;

  public MaterialRoutes(MaterialService materials, MaterialOrganizer organizer) {
    this.materials = materials;
    this.organizer = organizer;
  }

  @Override
  public void register(Javalin app) {
    app.events(
        events -> {
          events.serverStarting(organizer::start);
          events.serverStopping(organizer::close);
        });
    app.get("/api/materials", this::listMaterials);
    app.post("/api/materials", this::createMaterial);
    app.get("/api/materials/{materialId}", this::getMaterial);
    app.delete("/api/materials/{materialId}", this::deleteMaterial);
    app.post("/api/materials/{materialId}/retry", this::retryMaterial);
    app.get("/api/materials/{materialId}/images/{position}", this::getImage);
  }

  private void listMaterials(Context ctx) {
    List<MaterialSummaryResponse> items =
        materials.list(UserCookie.userId(ctx)).stream().map(MaterialSummaryResponse::of).toList();
    ctx.json(new MaterialListResponse(items));
  }

  private void createMaterial(Context ctx) {
    UUID userId = UserCookie.userId(ctx);
    // 確認のチェックが無いときは、画像を読まず、外部へも送らない (仕様書 5.3、NFR-301)
    if (!"true".equals(ctx.formParam(PHOTO_CONFIRMED_FIELD))) {
      throw ApiException.badRequest(PHOTO_CONFIRMED_CODE, PHOTO_CONFIRMED_MESSAGE);
    }
    List<UploadedFile> files = ctx.uploadedFiles(IMAGES_FIELD);
    if (files.size() < MaterialService.MIN_IMAGES || files.size() > MaterialService.MAX_IMAGES) {
      throw ApiException.badRequest(INVALID_IMAGES_CODE, INVALID_IMAGES_COUNT_MESSAGE);
    }
    List<Upload> uploads = files.stream().map(MaterialRoutes::readUpload).toList();
    MaterialView view = materials.create(userId, uploads);
    ctx.status(202).json(MaterialSummaryResponse.of(view));
  }

  private void getMaterial(Context ctx) {
    UUID userId = UserCookie.userId(ctx);
    Optional<MaterialView> view =
        materialId(ctx).flatMap(materialId -> materials.find(userId, materialId));
    if (view.isEmpty()) {
      ApiErrors.notFound(ctx);
      return;
    }
    ctx.json(MaterialDetailResponse.of(view.get()));
  }

  private void deleteMaterial(Context ctx) {
    UUID userId = UserCookie.userId(ctx);
    // ほかの利用者の教材は、無いものとして 404 にする (アーキテクチャ設計 8 節の NFR-304)。削除済みは 204 のまま
    boolean allowed =
        materialId(ctx).map(materialId -> materials.delete(userId, materialId)).orElse(false);
    if (!allowed) {
      ApiErrors.notFound(ctx);
      return;
    }
    ctx.status(204);
  }

  private void retryMaterial(Context ctx) {
    UUID userId = UserCookie.userId(ctx);
    Optional<MaterialView> view =
        materialId(ctx).flatMap(materialId -> materials.retry(userId, materialId));
    if (view.isEmpty()) {
      ApiErrors.notFound(ctx);
      return;
    }
    ctx.status(202).json(MaterialSummaryResponse.of(view.get()));
  }

  private void getImage(Context ctx) {
    UUID userId = UserCookie.userId(ctx);
    Optional<ImageFile> image =
        materialId(ctx)
            .flatMap(
                materialId ->
                    imagePosition(ctx)
                        .flatMap(position -> materials.image(userId, materialId, position)));
    if (image.isEmpty()) {
      ApiErrors.notFound(ctx);
      return;
    }
    ctx.contentType(image.get().contentType()).result(image.get().bytes());
  }

  /** パスの教材の識別子。形式が不正なら、無い教材として扱う。 */
  private static Optional<UUID> materialId(Context ctx) {
    try {
      return Optional.of(UUID.fromString(ctx.pathParam("materialId")));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  /** パスの画像の番号 (1 から 3)。範囲の外や形式が不正なら、無い画像として扱う。 */
  private static Optional<Integer> imagePosition(Context ctx) {
    try {
      int position = Integer.parseInt(ctx.pathParam("position"));
      boolean inRange =
          position >= MaterialService.MIN_IMAGES && position <= MaterialService.MAX_IMAGES;
      return inRange ? Optional.of(position) : Optional.empty();
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  /** アップロードの 1 枚を検査し、形式と中身を読む。 */
  private static Upload readUpload(UploadedFile file) {
    if (!ImageFormat.isWithinLimit(file.size())) {
      throw invalidImage();
    }
    byte[] bytes = readAll(file);
    ImageFormat format = ImageFormat.detect(bytes).orElseThrow(MaterialRoutes::invalidImage);
    return new Upload(format, bytes);
  }

  private static ApiException invalidImage() {
    return ApiException.badRequest(INVALID_IMAGES_CODE, INVALID_IMAGE_MESSAGE);
  }

  private static byte[] readAll(UploadedFile file) {
    try {
      return file.content().readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
