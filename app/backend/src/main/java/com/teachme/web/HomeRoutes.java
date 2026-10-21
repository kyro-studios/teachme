package com.teachme.web;

import com.teachme.application.HomeService;
import com.teachme.application.HomeService.HomeView;
import com.teachme.application.HomeworkService;
import com.teachme.domain.ImageFormat;
import com.teachme.domain.TodayMode;
import com.teachme.web.SettingsRoutes.StudentTypeView;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.UUID;

/**
 * ホーム、今日のモード、宿題の丸つけ (`GET /home`、`PUT /today-mode`、`POST /homework-checks`、openapi.yaml の home)。
 */
public final class HomeRoutes implements Routes {

  private static final String VALIDATION_CODE = "validation_error";
  private static final String PHOTO_FIELD = "photo";
  private static final String PHOTO_CONFIRMED_FIELD = "photoConfirmed";

  /** `GET /home` の応答 (openapi.yaml の `Home`)。 */
  public record HomeResponse(
      StudentTypeView student,
      String subject,
      String unitId,
      String unitName,
      int lessonsThisWeek,
      int lessonsPerWeek,
      boolean mockExamAvailable,
      String todayMode,
      boolean hasOrganizedMaterial) {

    static HomeResponse of(HomeView view) {
      return new HomeResponse(
          StudentTypeView.of(view.student()),
          view.subject().id(),
          view.unitId().toString(),
          view.unitName(),
          view.lessonsThisWeek(),
          view.lessonsPerWeek(),
          view.mockExamAvailable(),
          view.todayMode().id(),
          view.hasOrganizedMaterial());
    }
  }

  /** `PUT /today-mode` の本文。 */
  public record TodayModeRequest(String mode) {}

  /** `POST /homework-checks` の応答 (openapi.yaml の `HomeworkResult`)。 */
  public record HomeworkResponse(String verdict, String comment) {}

  private final HomeService home;
  private final HomeworkService homework;

  public HomeRoutes(HomeService home, HomeworkService homework) {
    this.home = home;
    this.homework = homework;
  }

  @Override
  public void register(Javalin app) {
    app.get("/api/home", this::getHome);
    app.put("/api/today-mode", this::putTodayMode);
    app.post("/api/homework-checks", this::postHomeworkCheck);
  }

  private void getHome(Context ctx) {
    ctx.json(HomeResponse.of(home.home(UserCookie.userId(ctx))));
  }

  private void putTodayMode(Context ctx) {
    UUID userId = UserCookie.userId(ctx);
    TodayModeRequest request = RequestBodies.readJson(ctx, TodayModeRequest.class);
    if (request == null) {
      throw ApiException.badRequest(VALIDATION_CODE, ApiErrors.INVALID_REQUEST_MESSAGE);
    }
    TodayMode mode =
        TodayMode.fromId(request.mode())
            .orElseThrow(
                () ->
                    new ApiException(
                        400,
                        VALIDATION_CODE,
                        ApiErrors.VALIDATION_MESSAGE,
                        Map.of("mode", "モードを選んでください")));
    ctx.json(HomeResponse.of(home.setTodayMode(userId, mode)));
  }

  private void postHomeworkCheck(Context ctx) {
    UUID userId = UserCookie.userId(ctx);
    // 確認のチェックが無いときは、写真を読まず、外部へも送らない (仕様書 5.2、NFR-301)
    if (!"true".equals(ctx.formParam(PHOTO_CONFIRMED_FIELD))) {
      throw ApiException.badRequest("photo_not_confirmed", "写真に氏名、学校名、連絡先が写っていないことを確認してください");
    }
    UploadedFile photo = ctx.uploadedFile(PHOTO_FIELD);
    if (photo == null || !ImageFormat.isWithinLimit(photo.size())) {
      throw invalidImage();
    }
    byte[] bytes = readAll(photo);
    ImageFormat format = ImageFormat.detect(bytes).orElseThrow(HomeRoutes::invalidImage);

    HomeworkService.Result result = homework.check(userId, format, bytes);
    ctx.json(new HomeworkResponse(result.verdict().wireName(), result.comment()));
  }

  private static ApiException invalidImage() {
    return ApiException.badRequest("invalid_images", "画像は JPEG か PNG で、1 枚 10 MB 以下にしてください");
  }

  private static byte[] readAll(UploadedFile file) {
    try {
      return file.content().readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
