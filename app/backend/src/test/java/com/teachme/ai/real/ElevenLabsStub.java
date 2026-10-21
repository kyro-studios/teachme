package com.teachme.ai.real;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ElevenLabs の API のスタブ。返す応答を先頭から順に決め、受け取った要求を記録する。
 *
 * <p>応答を決めていないときは、500 を返す。本物の API には接続しない。
 */
final class ElevenLabsStub implements AutoCloseable {

  /**
   * 受け取った要求。`target` は、パスとクエリ。`body` は、そのままのバイト列。
   *
   * @param target パスとクエリ (例: `/v1/text-to-speech/voice?output_format=mp3_44100_128`)
   */
  record Recorded(
      String method, String target, String apiKey, String contentType, String accept, byte[] body) {

    String bodyAsIso() {
      return new String(body, StandardCharsets.ISO_8859_1);
    }
  }

  /**
   * 次の応答。
   *
   * @param delayMillis ヘッダーを返すまでの待ち時間 (タイムアウトの試験に使う)
   * @param bodyDelayMillis ヘッダーを返した後、本文を返すまでの待ち時間 (本文の遅れの試験に使う)
   */
  record Reply(int status, byte[] body, long delayMillis, long bodyDelayMillis) {

    static Reply json(String json) {
      return new Reply(200, json.getBytes(StandardCharsets.UTF_8), 0, 0);
    }

    static Reply status(int status) {
      return new Reply(status, "{}".getBytes(StandardCharsets.UTF_8), 0, 0);
    }
  }

  private final HttpServer server;
  private final List<Recorded> requests = new CopyOnWriteArrayList<>();
  private final Queue<Reply> replies = new ConcurrentLinkedQueue<>();

  ElevenLabsStub() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/", this::handle);
    server.start();
  }

  URI endpoint() {
    return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
  }

  void enqueue(Reply reply) {
    replies.add(reply);
  }

  List<Recorded> requests() {
    return requests;
  }

  private void handle(HttpExchange exchange) throws IOException {
    byte[] body = exchange.getRequestBody().readAllBytes();
    requests.add(
        new Recorded(
            exchange.getRequestMethod(),
            exchange.getRequestURI().toString(),
            exchange.getRequestHeaders().getFirst("xi-api-key"),
            exchange.getRequestHeaders().getFirst("Content-Type"),
            exchange.getRequestHeaders().getFirst("Accept"),
            body));
    Reply reply = Objects.requireNonNullElse(replies.poll(), Reply.status(500));
    sleep(reply.delayMillis());
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(reply.status(), reply.body().length);
    // 相手が待ちきれずに切ったときは、書けなくてもよい
    try (OutputStream out = exchange.getResponseBody()) {
      out.flush();
      sleep(reply.bodyDelayMillis());
      out.write(reply.body());
    } catch (IOException ignored) {
      // 応答を書けなかったことは、テストの判定に関係しない
    } finally {
      exchange.close();
    }
  }

  private static void sleep(long millis) {
    if (millis <= 0) {
      return;
    }
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
