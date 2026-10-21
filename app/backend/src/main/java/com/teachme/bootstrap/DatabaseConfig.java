package com.teachme.bootstrap;

import java.util.Map;

/** 環境変数から読む、PostgreSQL への接続の設定。すべて必須。 */
public record DatabaseConfig(String url, String user, String password) {

  /**
   * @throws IllegalStateException 指定が無いとき (起動に失敗する)
   */
  public static DatabaseConfig fromEnv(Map<String, String> env) {
    return new DatabaseConfig(
        required(env, "DATABASE_URL"),
        required(env, "DATABASE_USER"),
        required(env, "DATABASE_PASSWORD"));
  }

  private static String required(Map<String, String> env, String name) {
    String value = env.get(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(name + " を指定してください");
    }
    return value;
  }
}
