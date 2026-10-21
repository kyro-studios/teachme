package com.teachme.ai.real;

import com.teachme.ai.AiCallKind;
import com.teachme.ai.AiFailedException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ElevenLabs の API への 1 回の呼び出し。HTTP、タイムアウト、応答の検査、1 行のログを受け持つ。
 *
 * <p>失敗は、理由によらず {@link AiFailedException} にする。自動の再試行はしない。
 *
 * <p>ログは 1 行に、種類、モデル、所要時間、成否、失敗の理由、HTTP の状態だけを出す。入力と出力の中身は出さない。
 */
final class ElevenLabsClient {

  private static final Logger LOG = LoggerFactory.getLogger(ElevenLabsClient.class);

  /** 本番のログの出し先。 */
  static final Consumer<String> TO_SLF4J = line -> LOG.info("{}", line);

  /** 応答の本文を読む。形が合わないときは {@link IOException} を投げる。 */
  @FunctionalInterface
  interface BodyReader<T> {

    T read(byte[] body) throws IOException;
  }

  private final URI endpoint;
  private final String apiKey;
  private final HttpClient http;
  private final Consumer<String> log;

  /**
   * @param endpoint API の基底の URL
   * @param apiKey API キー
   * @param http HTTP のクライアント
   * @param log ログの出し先 (1 行ずつ渡す)
   */
  ElevenLabsClient(URI endpoint, String apiKey, HttpClient http, Consumer<String> log) {
    this.endpoint = endpoint;
    this.apiKey = apiKey;
    this.http = http;
    this.log = log;
  }

  /** API キーを付けた要求の下書き。`path` は基底の URL からの相対のパス (クエリを含んでよい)。 */
  HttpRequest.Builder request(String path, Duration timeout) {
    return HttpRequest.newBuilder(endpoint.resolve(path))
        .timeout(timeout)
        .header("xi-api-key", apiKey);
  }

  /**
   * @return 応答の本文を読んだ結果
   * @throws AiFailedException タイムアウト、HTTP の 4xx と 5xx、応答の形の違反のとき
   */
  <T> T send(
      AiCallKind kind, String model, Duration timeout, HttpRequest request, BodyReader<T> reader) {
    long started = System.nanoTime();
    HttpResponse<byte[]> response;
    try {
      response = exchange(request, timeout);
    } catch (HttpTimeoutException e) {
      throw fail(kind, model, started, "timeout", null);
    } catch (IOException e) {
      throw fail(kind, model, started, "network", null);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw fail(kind, model, started, "interrupted", null);
    }
    int status = response.statusCode();
    if (status < 200 || status >= 300) {
      throw fail(kind, model, started, "http_status", status);
    }
    T value;
    try {
      value = reader.read(response.body());
    } catch (IOException e) {
      throw fail(kind, model, started, "malformed", null);
    }
    log(kind, model, started, "success", null, null);
    return value;
  }

  /**
   * 応答の本文の受信が終わるまで、期限を `timeout` で区切る。JDK の HTTP の期限は、ヘッダーの受信までしか効かないため、本文の遅れで処理が
   * 止まらないよう、ここで区切る。期限を過ぎたら通信をキャンセルする。
   */
  private HttpResponse<byte[]> exchange(HttpRequest request, Duration timeout)
      throws IOException, InterruptedException, HttpTimeoutException {
    CompletableFuture<HttpResponse<byte[]>> future =
        http.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
    try {
      return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
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

  /** 失敗を 1 行のログに出し、例外を返す。呼び出し側は `throw` する。 */
  private AiFailedException fail(
      AiCallKind kind, String model, long started, String reason, Integer status) {
    log(kind, model, started, "failure", reason, status);
    return new AiFailedException(kind);
  }

  private void log(
      AiCallKind kind, String model, long started, String outcome, String reason, Integer status) {
    StringBuilder line =
        new StringBuilder("ai_call kind=")
            .append(kind.wireName())
            .append(" model=")
            .append(model)
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
    log.accept(line.toString());
  }
}
