package com.teachme.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.teachme.application.SettingsService;
import com.teachme.application.SettingsService.SettingsCommand;
import com.teachme.domain.StudentType;
import com.teachme.domain.Subject;
import com.teachme.domain.UnitName;
import com.teachme.domain.UserSettings;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 初期セットアップと設定 (`GET /student-types`、`GET/PUT /settings`、openapi.yaml の setup)。 */
public final class SettingsRoutes implements Routes {

  private static final String VALIDATION_CODE = "validation_error";
  private static final int MIN_LESSONS_PER_WEEK = 1;
  private static final int MAX_LESSONS_PER_WEEK = 7;

  /** `GET /student-types` の応答。 */
  public record StudentTypesResponse(List<StudentTypeView> studentTypes) {}

  /** AI 生徒の種類 (openapi.yaml の `StudentType`)。 */
  public record StudentTypeView(
      String id, String displayName, int initialDeviation, int targetDeviation) {

    static StudentTypeView of(StudentType type) {
      return new StudentTypeView(
          type.id(), type.displayName(), type.initialDeviation(), type.targetDeviation());
    }
  }

  /** `GET /settings` と `PUT /settings` の応答。未完了のときは `settings` を null で書く (openapi.yaml の決まり)。 */
  public record SettingsResponse(
      boolean completed, @JsonInclude(JsonInclude.Include.ALWAYS) SettingsView settings) {

    static SettingsResponse of(Optional<UserSettings> saved) {
      return saved
          .map(settings -> new SettingsResponse(true, SettingsView.of(settings)))
          .orElse(new SettingsResponse(false, null));
    }
  }

  /** 設定 (openapi.yaml の `Settings`)。 */
  public record SettingsView(
      String studentTypeId,
      String subject,
      String unitId,
      String unitName,
      int lessonsPerWeek,
      boolean privacyConfirmed) {

    static SettingsView of(UserSettings settings) {
      return new SettingsView(
          settings.studentType().id(),
          settings.subject().id(),
          settings.unitId().toString(),
          settings.unitName(),
          settings.lessonsPerWeek(),
          true);
    }
  }

  /** `PUT /settings` の本文 (openapi.yaml の `SettingsInput`)。形は検査前なので、項目は null もありうる。 */
  public record SettingsRequest(
      String studentTypeId,
      String subject,
      String unitName,
      Integer lessonsPerWeek,
      Boolean privacyConfirmed) {}

  private final SettingsService settings;

  public SettingsRoutes(SettingsService settings) {
    this.settings = settings;
  }

  @Override
  public void register(Javalin app) {
    app.get("/api/student-types", this::listStudentTypes);
    app.get("/api/settings", this::getSettings);
    app.put("/api/settings", this::putSettings);
  }

  private void listStudentTypes(Context ctx) {
    ctx.json(
        new StudentTypesResponse(
            Arrays.stream(StudentType.values()).map(StudentTypeView::of).toList()));
  }

  private void getSettings(Context ctx) {
    ctx.json(SettingsResponse.of(settings.find(UserCookie.userId(ctx))));
  }

  private void putSettings(Context ctx) {
    UUID userId = UserCookie.userId(ctx);
    SettingsRequest request = RequestBodies.readJson(ctx, SettingsRequest.class);
    if (request == null) {
      throw ApiException.badRequest(VALIDATION_CODE, ApiErrors.INVALID_REQUEST_MESSAGE);
    }
    FieldErrors errors = new FieldErrors();
    StudentType studentType = StudentType.fromId(request.studentTypeId()).orElse(null);
    if (studentType == null) {
      errors.add("studentTypeId", "生徒を 1 人選んでください");
    }
    Subject subject = Subject.fromId(request.subject()).orElse(null);
    if (subject == null) {
      errors.add("subject", "教科を選んでください");
    }
    UnitName unitName = UnitName.parse(request.unitName()).orElse(null);
    if (unitName == null) {
      errors.add("unitName", "単元名は 1 文字以上 30 文字以内で入力してください");
    }
    if (request.lessonsPerWeek() == null
        || request.lessonsPerWeek() < MIN_LESSONS_PER_WEEK
        || request.lessonsPerWeek() > MAX_LESSONS_PER_WEEK) {
      errors.add("lessonsPerWeek", "週の授業回数を選んでください");
    }
    if (!Boolean.TRUE.equals(request.privacyConfirmed())) {
      errors.add("privacyConfirmed", "個人情報の確認にチェックを入れてください");
    }
    errors.throwIfAny(VALIDATION_CODE, ApiErrors.VALIDATION_MESSAGE);

    UserSettings saved =
        settings.save(
            userId,
            new SettingsCommand(studentType, subject, unitName, request.lessonsPerWeek(), true));
    ctx.json(SettingsResponse.of(Optional.of(saved)));
  }
}
