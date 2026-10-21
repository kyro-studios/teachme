package com.teachme.ai.real;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.teachme.ai.AiCallKind;
import com.teachme.ai.AiFailedException;
import com.teachme.ai.Media;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chat Completions API への 1 回の呼び出し。HTTP、タイムアウト、応答の検査、1 行のログを受け持つ。
 *
 * <p>応答は、JSON スキーマ (`schemas/*.json`) に合うものだけを受け付ける。失敗は、理由によらず {@link AiFailedException}
 * にする。自動の再試行はしない。
 *
 * <p>ログは 1 行に、種類、モデル、所要時間、成否、失敗の理由、HTTP の状態、トークン数だけを出す。入力と出力の中身は出さない。
 */
final class ChatCompletionClient {

  private static final Logger LOG = LoggerFactory.getLogger(ChatCompletionClient.class);

  /** 本番のログの出し先。 */
  static final Consumer<String> TO_SLF4J = line -> LOG.info("{}", line);

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String PATH = "/v1/chat/completions";
  private static final String UNKNOWN = "-";

  /** 利用者の文章や画像など、1 つの内容の部品。 */
  sealed interface Part {

    /** 文章。 */
    record Text(String text) implements Part {}

    /** 画像。長辺が大きければ、送る前に縮める。 */
    record Image(Media media) implements Part {}
  }

  /**
   * 1 回の呼び出しの内容。
   *
   * @param kind 呼び出しの種類 (ログに出す)
   * @param model モデル名
   * @param timeout HTTP のタイムアウト
   * @param systemPrompt 指示
   * @param parts 利用者の入力 (文章と画像)
   * @param schemaName 応答の JSON スキーマの名前。`schemas/<名前>.json` を使う
   * @param maxOutputTokens 出力のトークンの上限。費用の暴走を防ぐ
   */
  record Request(
      AiCallKind kind,
      String model,
      Duration timeout,
      String systemPrompt,
      List<Part> parts,
      String schemaName,
      int maxOutputTokens) {}

  /** ログに出すトークン数。分からないときは `-`。 */
  private record Usage(String input, String output) {
    static final Usage NONE = new Usage(UNKNOWN, UNKNOWN);
  }

  private final URI uri;
  private final String apiKey;
  private final HttpClient http;
  private final Consumer<String> log;

  /**
   * @param endpoint API の基底の URL
   * @param apiKey API キー
   * @param http HTTP のクライアント
   * @param log ログの出し先 (1 行ずつ渡す)
   */
  ChatCompletionClient(URI endpoint, String apiKey, HttpClient http, Consumer<String> log) {
    this.uri = endpoint.resolve(PATH);
    this.apiKey = apiKey;
    this.http = http;
    this.log = log;
  }

  /**
   * @return スキーマに合う応答の中身 (JSON)
   * @throws AiFailedException タイムアウト、HTTP の 4xx と 5xx、応答の形の違反、拒否のとき
   */
  JsonNode complete(Request request) {
    long started = System.nanoTime();
    HttpResponse<String> response;
    try {
      response = send(request);
    } catch (HttpTimeoutException e) {
      throw fail(request, started, "timeout", null, Usage.NONE);
    } catch (IOException e) {
      throw fail(request, started, "network", null, Usage.NONE);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw fail(request, started, "interrupted", null, Usage.NONE);
    }
    int status = response.statusCode();
    if (status < 200 || status >= 300) {
      throw fail(request, started, "http_status", status, Usage.NONE);
    }
    return content(request, started, response.body());
  }

  /**
   * 応答の本文の受信が終わるまで、期限を {@link Request#timeout()} で区切る。JDK の HTTP の期限は、ヘッダーの受信まで
   * しか効かないため、本文の遅れで処理が止まらないよう、ここで区切る。期限を過ぎたら通信をキャンセルする。
   */
  private HttpResponse<String> send(Request request)
      throws IOException, InterruptedException, HttpTimeoutException {
    CompletableFuture<HttpResponse<String>> future =
        http.sendAsync(
            httpRequest(request), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    try {
      return future.get(request.timeout().toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      future.cancel(true);
      throw new HttpTimeoutException("応答が期限内に届きませんでした");
    } catch (ExecutionException e) {
      if (e.getCause() instanceof IOException io) {
        throw io;
      }
      throw new IOException(e.getCause());
    }
  }

  private HttpRequest httpRequest(Request request) {
    return HttpRequest.newBuilder(uri)
        .timeout(request.timeout())
        .header("Authorization", "Bearer " + apiKey)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(requestBody(request), StandardCharsets.UTF_8))
        .build();
  }

  private static String requestBody(Request request) {
    ObjectNode body = MAPPER.createObjectNode();
    body.put("model", request.model());
    body.put("max_completion_tokens", request.maxOutputTokens());

    ArrayNode messages = body.putArray("messages");
    messages.addObject().put("role", "system").put("content", request.systemPrompt());
    ArrayNode content = messages.addObject().put("role", "user").putArray("content");
    for (Part part : request.parts()) {
      switch (part) {
        case Part.Text text -> content.addObject().put("type", "text").put("text", text.text());
        case Part.Image image ->
            content
                .addObject()
                .put("type", "image_url")
                .putObject("image_url")
                .put("url", dataUrl(ImageDownscaler.limit(image.media())));
      }
    }

    ObjectNode format = body.putObject("response_format");
    format.put("type", "json_schema");
    ObjectNode jsonSchema = format.putObject("json_schema");
    jsonSchema.put("name", request.schemaName());
    jsonSchema.put("strict", true);
    jsonSchema.set("schema", Schemas.load(request.schemaName()));

    try {
      return MAPPER.writeValueAsString(body);
    } catch (JsonProcessingException e) {
      // ObjectNode の書き出しは失敗しない。失敗したら、実装の誤り
      throw new IllegalStateException("リクエストの JSON を書けませんでした", e);
    }
  }

  private static String dataUrl(Media media) {
    return "data:"
        + media.mimeType()
        + ";base64,"
        + Base64.getEncoder().encodeToString(media.bytes());
  }

  /** 応答の本文から、スキーマに合う中身を取り出す。合わなければ失敗とする。 */
  private JsonNode content(Request request, long started, String body) {
    JsonNode root;
    try {
      root = MAPPER.readTree(body);
    } catch (JsonProcessingException e) {
      throw fail(request, started, "malformed", null, Usage.NONE);
    }
    Usage usage = usage(root);

    JsonNode message = root.path("choices").path(0).path("message");
    if (message.path("refusal").isTextual()) {
      throw fail(request, started, "refusal", null, usage);
    }
    JsonNode content = message.path("content");
    if (!content.isTextual()) {
      throw fail(request, started, "malformed", null, usage);
    }
    JsonNode value;
    try {
      value = MAPPER.readTree(content.asText());
    } catch (JsonProcessingException e) {
      throw fail(request, started, "malformed", null, usage);
    }
    if (!JsonSchemaCheck.matches(Schemas.load(request.schemaName()), value)) {
      throw fail(request, started, "schema_mismatch", null, usage);
    }
    log(request, started, "success", null, null, usage);
    return value;
  }

  private static Usage usage(JsonNode root) {
    JsonNode usage = root.path("usage");
    return new Usage(tokens(usage.path("prompt_tokens")), tokens(usage.path("completion_tokens")));
  }

  private static String tokens(JsonNode node) {
    return node.isIntegralNumber() ? node.asText() : UNKNOWN;
  }

  /** 失敗を 1 行のログに出し、例外を返す。呼び出し側は `throw` する。 */
  private AiFailedException fail(
      Request request, long started, String reason, Integer status, Usage usage) {
    log(request, started, "failure", reason, status, usage);
    return new AiFailedException(request.kind());
  }

  private void log(
      Request request, long started, String outcome, String reason, Integer status, Usage usage) {
    StringBuilder line =
        new StringBuilder("ai_call kind=")
            .append(request.kind().wireName())
            .append(" model=")
            .append(request.model())
            .append(" duration_ms=")
            .append(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
            .append(" outcome=")
            .append(outcome);
    if (reason != null) {
      line.append(" reason=").append(reason);
    }
    if (status != null) {
      line.append(" status=").append(status);
    }
    line.append(" input_tokens=")
        .append(usage.input())
        .append(" output_tokens=")
        .append(usage.output());
    log.accept(line.toString());
  }
}
