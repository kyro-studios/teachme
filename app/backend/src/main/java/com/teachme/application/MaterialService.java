package com.teachme.application;

import com.teachme.domain.ImageFormat;
import com.teachme.domain.MaterialStatus;
import com.teachme.domain.OrganizedProblem;
import com.teachme.persistence.MaterialRepository;
import com.teachme.persistence.MaterialRepository.MaterialRow;
import com.teachme.persistence.MaterialRepository.NewImage;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * 教材 (仕様書 5.3、5.9)。登録、一覧、詳細、削除、「やり直す」、画像の取得を行う。
 *
 * <p>教材の番号は、同じ単元の中で登録順に 1 から振る。保存はせず、表示のたびに求める。
 */
public final class MaterialService {

  /** 登録できる画像の枚数 (仕様書 5.3)。 */
  public static final int MIN_IMAGES = 1;

  public static final int MAX_IMAGES = 3;

  static final String MATERIAL_NOT_FAILED_CODE = "material_not_failed";
  static final String MATERIAL_NOT_FAILED_MESSAGE = "整理できなかった教材だけ、やり直せます";

  private final SettingsService settings;
  private final MaterialRepository materials;
  private final MaterialOrganizer organizer;
  private final Clock clock;

  public MaterialService(
      SettingsService settings,
      MaterialRepository materials,
      MaterialOrganizer organizer,
      Clock clock) {
    this.settings = settings;
    this.materials = materials;
    this.organizer = organizer;
    this.clock = clock;
  }

  /**
   * 現在の単元の教材を、登録順に返す。
   *
   * @throws ConflictException 設定が未完了のとき (`setup_required`)
   */
  public List<MaterialView> list(UUID userId) {
    UUID unitId = settings.requireCompleted(userId).unitId();
    return numbered(materials.listByUnit(unitId));
  }

  /**
   * 教材を登録し、整理を始める。画像は、`web` が形式と大きさを検査してから渡す。整理の結果は待たず、整理中の状態を返す。
   *
   * @param images 教材の画像。1 枚から 3 枚 ({@link #MIN_IMAGES} から {@link #MAX_IMAGES})
   * @throws ConflictException 設定が未完了のとき (`setup_required`)
   */
  public MaterialView create(UUID userId, List<Upload> images) {
    UUID unitId = settings.requireCompleted(userId).unitId();
    UUID materialId = UUID.randomUUID();
    List<NewImage> stored =
        images.stream()
            .map(image -> new NewImage(image.format().mimeType(), image.bytes()))
            .toList();
    materials.insertOrganizing(materialId, unitId, stored, clock.instant());
    // 整理の依頼より前に、返す内容を決める。依頼の後では、整理が終わって状態が変わっているかもしれない
    MaterialView view =
        viewIn(unitId, materialId)
            .orElseThrow(() -> new IllegalStateException("登録した教材が単元の一覧に見つかりません"));
    organizer.enqueue(userId, materialId);
    return view;
  }

  /**
   * 教材の詳細。ほかの利用者の教材や、無い教材は空。
   *
   * @throws ConflictException 設定が未完了のとき (`setup_required`)
   */
  public Optional<MaterialView> find(UUID userId, UUID materialId) {
    settings.requireCompleted(userId);
    return materials.find(userId, materialId).flatMap(row -> viewIn(row.unitId(), materialId));
  }

  /**
   * 教材と、その整理の結果を削除する。授業の記録と模試の結果は残す。何度呼んでも、結果は同じ (冪等)。
   *
   * @return 削除した、またはすでに無かったとき true。ほかの利用者の教材のときは false (何もしない)
   * @throws ConflictException 設定が未完了のとき (`setup_required`)
   */
  public boolean delete(UUID userId, UUID materialId) {
    settings.requireCompleted(userId);
    Optional<UUID> owner = materials.findOwner(materialId);
    if (owner.isEmpty()) {
      return true;
    }
    if (!owner.get().equals(userId)) {
      return false;
    }
    materials.delete(materialId);
    return true;
  }

  /**
   * 整理できなかった教材の整理を、もう一度始める (「やり直す」)。
   *
   * @return 整理を始めた教材。ほかの利用者の教材や、無い教材は空
   * @throws ConflictException 設定が未完了のとき (`setup_required`)、状態が `failed` ではないとき
   *     (`material_not_failed`)
   */
  public Optional<MaterialView> retry(UUID userId, UUID materialId) {
    settings.requireCompleted(userId);
    Optional<MaterialRow> row = materials.find(userId, materialId);
    if (row.isEmpty()) {
      return Optional.empty();
    }
    // 状態が failed のときだけ整理中に戻す。二重に押されたときは、2 回目を受け付けない
    if (!materials.restartIfFailed(materialId, clock.instant())) {
      throw new ConflictException(MATERIAL_NOT_FAILED_CODE, MATERIAL_NOT_FAILED_MESSAGE);
    }
    // 依頼の前に、返す内容を決める (依頼の後では、整理が終わって状態が変わっているかもしれない)
    Optional<MaterialView> view = viewIn(row.get().unitId(), materialId);
    organizer.enqueue(userId, materialId);
    return view;
  }

  /**
   * 教材の画像 1 枚。ほかの利用者の教材や、番号が無いときは空。
   *
   * @param position 教材の画像の番号 (1 から 3)
   */
  public Optional<ImageFile> image(UUID userId, UUID materialId, int position) {
    return materials
        .findImage(userId, materialId, position)
        .map(image -> new ImageFile(image.contentType(), image.data()));
  }

  /** 単元の一覧の中の、教材の表示内容。確かめた後に削除されていれば空。 */
  private Optional<MaterialView> viewIn(UUID unitId, UUID materialId) {
    return numbered(materials.listByUnit(unitId)).stream()
        .filter(view -> view.id().equals(materialId))
        .findFirst();
  }

  private static List<MaterialView> numbered(List<MaterialRow> rows) {
    return IntStream.range(0, rows.size())
        .mapToObj(
            index -> {
              MaterialRow row = rows.get(index);
              return new MaterialView(row.id(), index + 1, row.status(), row.problem());
            })
        .toList();
  }

  /**
   * 登録する画像 1 枚。
   *
   * @param format 形式 (`web` が署名から求めたもの)
   * @param bytes 画像の中身
   */
  public record Upload(ImageFormat format, byte[] bytes) {}

  /**
   * 教材の画像 1 枚。
   *
   * @param contentType `image/jpeg` か `image/png`
   * @param bytes 画像の中身
   */
  public record ImageFile(String contentType, byte[] bytes) {}

  /**
   * 教材の表示内容。
   *
   * @param number 同じ単元の中での登録順の番号 (1 から)
   * @param problem 整理の結果。整理済みのときだけ値を持ち、それ以外は null
   */
  public record MaterialView(
      UUID id, int number, MaterialStatus status, OrganizedProblem problem) {}
}
