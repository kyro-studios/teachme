package com.teachme.application;

import com.teachme.domain.StudentType;
import com.teachme.domain.Subject;
import com.teachme.domain.UnitName;
import com.teachme.domain.UserSettings;
import com.teachme.persistence.SettingsRepository;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 利用者の設定 (仕様書 5.1)。設定の保存と取得、設定が完了しているかの確認を行う。 */
public final class SettingsService {

  static final String SETUP_REQUIRED_CODE = "setup_required";
  static final String SETUP_REQUIRED_MESSAGE = "初期セットアップを済ませてください";

  private static final int MIN_LESSONS_PER_WEEK = 1;
  private static final int MAX_LESSONS_PER_WEEK = 7;

  private final SettingsRepository settings;
  private final Clock clock;

  public SettingsService(SettingsRepository settings, Clock clock) {
    this.settings = settings;
    this.clock = clock;
  }

  /**
   * 設定を保存し、完了済みにする。同じ教科に同名の単元があれば、新しく作らず、その単元を使う。
   *
   * <p>完了後は、担当する生徒を変えられない。今週の授業の回数と、保存されている記録は変わらない。
   *
   * @throws InvalidInputException 個人情報の確認が無いとき、週の授業回数が範囲の外のとき、完了後に担当する生徒を変えたとき。何も保存しない
   */
  public UserSettings save(UUID userId, SettingsCommand command) {
    if (!command.privacyConfirmed()) {
      throw new InvalidInputException(Map.of("privacyConfirmed", "個人情報の確認にチェックを入れてください"));
    }
    if (command.lessonsPerWeek() < MIN_LESSONS_PER_WEEK
        || command.lessonsPerWeek() > MAX_LESSONS_PER_WEEK) {
      throw new InvalidInputException(Map.of("lessonsPerWeek", "週の授業回数を選んでください"));
    }
    Optional<UserSettings> saved = settings.find(userId);
    if (saved.isPresent() && saved.get().studentType() != command.studentType()) {
      throw new InvalidInputException(Map.of("studentTypeId", "担当する生徒は変えられません"));
    }
    return settings.save(
        userId,
        command.studentType(),
        command.subject(),
        command.unitName(),
        command.lessonsPerWeek(),
        clock.instant());
  }

  /** 利用者の設定。未完了なら空。 */
  public Optional<UserSettings> find(UUID userId) {
    return settings.find(userId);
  }

  /**
   * 設定が完了している利用者の設定を返す。未完了なら 409 `setup_required`。
   *
   * @throws ConflictException 設定が未完了のとき
   */
  public UserSettings requireCompleted(UUID userId) {
    return settings
        .find(userId)
        .orElseThrow(() -> new ConflictException(SETUP_REQUIRED_CODE, SETUP_REQUIRED_MESSAGE));
  }

  /**
   * 設定の保存の入力。`web` が、項目ごとの形を検査してから作る。
   *
   * @param privacyConfirmed 「氏名、学校名、連絡先は話さず、ボードにも書かないことを確認しました」のチェック
   */
  public record SettingsCommand(
      StudentType studentType,
      Subject subject,
      UnitName unitName,
      int lessonsPerWeek,
      boolean privacyConfirmed) {}
}
