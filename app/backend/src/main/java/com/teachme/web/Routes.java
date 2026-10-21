package com.teachme.web;

import io.javalin.Javalin;

/** API ごとのルートの登録。機能ごとに 1 つ作り、Main が並べて {@link WebApp#create} に渡す。 */
@FunctionalInterface
public interface Routes {
  void register(Javalin app);
}
