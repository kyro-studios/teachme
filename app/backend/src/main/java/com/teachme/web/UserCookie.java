package com.teachme.web;

import io.javalin.http.Context;
import io.javalin.http.Cookie;
import io.javalin.http.SameSite;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Cookie `teachme_uid` で利用者を識別する。無ければ UUID を発行し、HttpOnly、SameSite=Lax、400 日で設定する。
 *
 * <p>利用者の行 (`users`) は、設定の保存のときに初めて作る。ここでは DB に触れない。
 */
final class UserCookie {
  static final String NAME = "teachme_uid";
  static final int MAX_AGE_SECONDS = 400 * 24 * 60 * 60;

  private static final String ATTRIBUTE = "teachme.userId";
  private static final Pattern UUID_FORMAT =
      Pattern.compile(
          "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

  private UserCookie() {}

  /** `/api` の各リクエストの最初に呼ぶ。利用者の識別子を、リクエストの属性に入れる。 */
  static void resolve(Context ctx) {
    UUID userId =
        parse(ctx.cookie(NAME))
            .orElseGet(
                () -> {
                  UUID issued = UUID.randomUUID();
                  ctx.cookie(newCookie(issued));
                  return issued;
                });
    ctx.attribute(ATTRIBUTE, userId);
  }

  /** {@link #resolve} の後に、呼び出す。 */
  static UUID userId(Context ctx) {
    UUID userId = ctx.attribute(ATTRIBUTE);
    if (userId == null) {
      throw new IllegalStateException("利用者の識別子は、Cookie の解決の後に読んでください");
    }
    return userId;
  }

  /** 形式が不正な値は、無いものとして扱う (作り直す)。 */
  private static Optional<UUID> parse(String value) {
    if (value == null || !UUID_FORMAT.matcher(value).matches()) {
      return Optional.empty();
    }
    return Optional.of(UUID.fromString(value));
  }

  private static Cookie newCookie(UUID userId) {
    // secure は付けない (仕様の決まりに無い)。本番の HTTPS は Cloud Run が受ける
    return new Cookie(
        NAME, userId.toString(), "/", MAX_AGE_SECONDS, false, 0, true, null, null, SameSite.LAX);
  }
}
