package com.teachme.ai.real;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
 * OpenAI の Chat Completions API のスタブ。返す応答を先頭から順に決め、受け取った要求を記録する。
 *
 * <p>応答を決めていないときは、500 を返す。本物の API には接続しない。
 */
final class OpenAiStub implements AutoCloseable {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** 受け取った要求。`body` は JSON として読んだもの。 */
  record Recorded(String authorization, JsonNode body) {}

  /**
   * 次の応答。
   *
   * @param delayMillis ヘッダーを返すまでの待ち時間 (タイムアウトの試験に使う)
   * @param bodyDelayMillis ヘッダーを返した後、本文を返すまでの待ち時間 (本文の遅れの試験に使う)
   */
  record Reply(int status, String body, long delayMillis, long bodyDelayMillis) {

    Reply(int status, String body, long delayMillis) {
      this(status, body, delayMillis, 0);
    }
  }

  private final HttpServer server;
  private final List<Recorded> requests = new CopyOnWriteArrayList<>();
  private final Queue<Reply> replies = new ConcurrentLinkedQueue<>();

  OpenAiStub() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/v1/chat/completions", this::handle);
    server.start();
  }

  URI endpoint() {
    return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
  }

  void enqueue(Reply reply) {
    replies.add(reply);
  }

  /** 成功の応答を 1 件、中身 (`content`) を指定して積む。トークン数は 120 と 30。 */
  void enqueueContent(String content) {
    enqueue(new Reply(200, completion(content, null), 0));
  }

  List<Recorded> requests() {
    return requests;
  }

  /** 応答の本文 (Chat Completions の形)。`refusal` を指定すると、拒否の応答になる。 */
  static String completion(String content, String refusal) {
    ObjectNode root = MAPPER.createObjectNode();
    ObjectNode message = root.putArray("choices").addObject().putObject("message");
    message.put("role", "assistant");
    if (content == null) {
      message.putNull("content");
    } else {
      message.put("content", content);
    }
    if (refusal == null) {
      message.putNull("refusal");
    } else {
      message.put("refusal", refusal);
    }
    ObjectNode usage = root.putObject("usage");
    usage.put("prompt_tokens", 120);
    usage.put("completion_tokens", 30);
    usage.put("total_tokens", 150);
    return write(root);
  }

  private void handle(HttpExchange exchange) throws IOException {
    byte[] raw = exchange.getRequestBody().readAllBytes();
    requests.add(
        new Recorded(exchange.getRequestHeaders().getFirst("Authorization"), MAPPER.readTree(raw)));
    Reply reply = Objects.requireNonNullElse(replies.poll(), new Reply(500, "{}", 0));
    sleep(reply.delayMillis());
    byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(reply.status(), bytes.length);
    // 相手が待ちきれずに切ったときは、書けなくてもよい
    try (OutputStream out = exchange.getResponseBody()) {
      out.flush();
      sleep(reply.bodyDelayMillis());
      out.write(bytes);
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

  private static String write(ObjectNode root) {
    try {
      return MAPPER.writeValueAsString(root);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
