package com.teachme.ai.real;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.teachme.ai.AiCallContext;
import com.teachme.ai.AiCallKind;
import com.teachme.ai.AiFailedException;
import com.teachme.ai.LanguageModel;
import com.teachme.ai.LanguageModel.AnnotationKind;
import com.teachme.ai.LanguageModel.EvaluationInput;
import com.teachme.ai.LanguageModel.HomeworkVerdict;
import com.teachme.ai.LanguageModel.LastLesson;
import com.teachme.ai.LanguageModel.MentorReplyInput;
import com.teachme.ai.LanguageModel.MockExamInput;
import com.teachme.ai.LanguageModel.OrganizeResult;
import com.teachme.ai.LanguageModel.Region;
import com.teachme.ai.LanguageModel.Speaker;
import com.teachme.ai.LanguageModel.StudentExpression;
import com.teachme.ai.LanguageModel.StudentReplyInput;
import com.teachme.ai.LanguageModel.Subtitle;
import com.teachme.ai.LanguageModel.SummaryInput;
import com.teachme.ai.Media;
import com.teachme.ai.real.OpenAiStub.Recorded;
import com.teachme.ai.real.OpenAiStub.Reply;
import com.teachme.domain.StudentType;
import com.teachme.domain.Subject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OpenAiLanguageModelTest {

  private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final Duration LONG = Duration.ofSeconds(5);

  private static final String ORGANIZE_JSON =
      """
      {"problemText":"y = x^2 - 4x + 3 の頂点を求めなさい",
       "figures":[{"imagePosition":1,"region":{"x":0.1,"y":0.2,"width":0.5,"height":0.3}}],
       "modelAnswer":"頂点は (2, -1)",
       "explanationSteps":["平方完成して頂点を読む"],
       "studentWork":"y = (x - 2)^2 と書いて手が止まっている",
       "teachingPoints":["平方完成の形を作る","括弧の中の数を読む","頂点の y 座標を求める"]}
      """;

  private static final String STUDENT_JSON =
      """
      {"text":"なぜそうなるのですか？","expression":"troubled",
       "annotation":{"kind":"point","region":{"x":0.1,"y":0.1,"width":0.2,"height":0.2}}}
      """;

  private static final String LOG_PATTERN =
      "ai_call kind=%s model=%s duration_ms=\\d+ outcome=%s%s input_tokens=%s output_tokens=%s";

  private OpenAiStub stub;
  private final List<String> logs = new CopyOnWriteArrayList<>();

  @BeforeEach
  void startStub() throws IOException {
    stub = new OpenAiStub();
  }

  @AfterEach
  void stopStub() {
    stub.close();
  }

  private OpenAiLanguageModel model() {
    return model(LONG, LONG);
  }

  private OpenAiLanguageModel model(Duration dialogueTimeout, Duration organizeTimeout) {
    OpenAiSettings settings =
        new OpenAiSettings(
            stub.endpoint(),
            "test-key",
            "organize-model",
            "dialogue-model",
            dialogueTimeout,
            organizeTimeout);
    return new OpenAiLanguageModel(
        settings, HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build(), logs::add);
  }

  private static AiCallContext context(AiCallKind kind) {
    return new AiCallContext(USER, kind);
  }

  private static Media png(int width, int height) {
    try {
      BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(image, "png", out);
      return new Media("image/png", out.toByteArray());
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static StudentReplyInput studentReplyInput(String utterance) {
    return new StudentReplyInput(
        StudentType.LOGICAL,
        "y = x^2 - 4x + 3 の頂点を求めなさい",
        "y = (x - 2)^2 と書いて手が止まっている",
        png(40, 30),
        utterance,
        List.of(new Subtitle(Speaker.USER, utterance)),
        List.of(new Region(0.1, 0.2, 0.3, 0.4)));
  }

  private static JsonNode userContent(Recorded request) {
    return request.body().at("/messages/1/content");
  }

  private static String systemPrompt(Recorded request) {
    return request.body().at("/messages/0/content").asText();
  }

  private void assertLogged(String kind, String model, String outcome, String rest) {
    String expected = LOG_PATTERN.formatted(kind, model, outcome, rest, "(\\d+|-)", "(\\d+|-)");
    assertTrue(
        logs.stream().anyMatch(line -> line.matches(expected)), () -> "ログに一致する行が無い: " + logs);
  }

  @Test
  void 整理は_構造化された応答を_整理の結果にし_整理のモデルを使う() {
    stub.enqueueContent(ORGANIZE_JSON);

    OrganizeResult result = model().organize(context(AiCallKind.ORGANIZE), List.of(png(100, 80)));

    assertEquals("y = x^2 - 4x + 3 の頂点を求めなさい", result.problemText());
    assertEquals(1, result.figures().size());
    assertEquals(1, result.figures().get(0).imagePosition());
    assertEquals(0.5, result.figures().get(0).region().width());
    assertEquals(3, result.teachingPoints().size());
    assertEquals(List.of("平方完成して頂点を読む"), result.explanationSteps());

    Recorded request = stub.requests().get(0);
    assertEquals("Bearer test-key", request.authorization());
    assertEquals("organize-model", request.body().get("model").asText());
    assertEquals("json_schema", request.body().at("/response_format/type").asText());
    assertEquals("organize", request.body().at("/response_format/json_schema/name").asText());
    assertTrue(request.body().at("/response_format/json_schema/strict").asBoolean());
    assertTrue(
        userContent(request)
            .get(1)
            .get("image_url")
            .get("url")
            .asText()
            .startsWith("data:image/png;base64,"));
    assertLogged("organize", "organize-model", "success", "");
    assertTrue(logs.stream().anyMatch(line -> line.endsWith("input_tokens=120 output_tokens=30")));
  }

  @Test
  void 生徒の応答は_対話のモデルを使い_表情と印を_対応する型にする() {
    stub.enqueueContent(STUDENT_JSON);

    LanguageModel.StudentReply reply =
        model().studentReply(context(AiCallKind.STUDENT_REPLY), studentReplyInput("平方完成をします"));

    assertEquals("なぜそうなるのですか？", reply.text());
    assertEquals(StudentExpression.TROUBLED, reply.expression());
    assertEquals(AnnotationKind.POINT, reply.annotation().kind());
    assertEquals(0.1, reply.annotation().region().x());

    Recorded request = stub.requests().get(0);
    assertEquals("dialogue-model", request.body().get("model").asText());
    assertTrue(systemPrompt(request).contains("論理・ツッコミ派"));
    assertTrue(systemPrompt(request).contains("説明の飛躍"));
    assertEquals("student-reply", request.body().at("/response_format/json_schema/name").asText());
    assertLogged("student_reply", "dialogue-model", "success", "");
  }

  @Test
  void 生徒の応答は_印が無いとき_annotation_を_null_にする() {
    stub.enqueueContent("{\"text\":\"なるほど！\",\"expression\":\"convinced\",\"annotation\":null}");

    LanguageModel.StudentReply reply =
        model().studentReply(context(AiCallKind.STUDENT_REPLY), studentReplyInput("分かりました"));

    assertEquals(StudentExpression.CONVINCED, reply.expression());
    assertNull(reply.annotation());
  }

  @Test
  void 学習評価は_指導のポイントの順に_3_件の度合いを返す() {
    stub.enqueueContent("{\"degrees\":[2,1,0]}");

    List<Integer> degrees =
        model()
            .evaluate(
                context(AiCallKind.EVALUATION),
                new EvaluationInput(
                    List.of("平方完成の形を作る", "括弧の中の数を読む", "頂点の y 座標を求める"),
                    "頂点は (2, -1)",
                    List.of("平方完成して頂点を読む"),
                    "y = x^2 - 4x + 3 の頂点を求めなさい",
                    png(40, 30),
                    "平方完成をします",
                    List.of(new Subtitle(Speaker.USER, "平方完成をします")),
                    List.of()))
            .degrees();

    assertEquals(List.of(2, 1, 0), degrees);
    assertEquals("dialogue-model", stub.requests().get(0).body().get("model").asText());
    assertLogged("evaluation", "dialogue-model", "success", "");
  }

  @Test
  void 先輩先生の応答は_書き込みを_種類ごとに読む() {
    stub.enqueueContent(
        """
        {"text":"平方完成を使いましょう。",
         "boardWrites":[
           {"kind":"text","text":"y = (x-2)^2 - 1","region":null},
           {"kind":"highlight","text":null,"region":{"x":0.1,"y":0.1,"width":0.3,"height":0.1}}]}
        """);

    LanguageModel.MentorReply reply =
        model()
            .mentorReply(
                context(AiCallKind.MENTOR_REPLY),
                new MentorReplyInput(
                    "y = x^2 - 4x + 3 の頂点を求めなさい",
                    "頂点は (2, -1)",
                    List.of("平方完成して頂点を読む"),
                    List.of("平方完成の形を作る", "括弧の中の数を読む", "頂点の y 座標を求める"),
                    png(40, 30),
                    "どう解けばいいですか",
                    List.of(),
                    List.of()));

    assertEquals("平方完成を使いましょう。", reply.text());
    assertEquals(2, reply.boardWrites().size());
    assertEquals("y = (x-2)^2 - 1", reply.boardWrites().get(0).text());
    assertNull(reply.boardWrites().get(0).region());
    assertNull(reply.boardWrites().get(1).text());
    assertEquals(0.3, reply.boardWrites().get(1).region().width());
    assertEquals("dialogue-model", stub.requests().get(0).body().get("model").asText());
  }

  @Test
  void まとめは_整理のモデルを使い_引用が無いときは空の配列を返す() {
    stub.enqueueContent("{\"summaryText\":\"今日は頂点の求め方を教わりました。\",\"quotes\":[]}");

    LanguageModel.SummaryResult result =
        model()
            .summarize(
                context(AiCallKind.SUMMARY),
                new SummaryInput(
                    StudentType.DILIGENT,
                    List.of(new Subtitle(Speaker.MENTOR, "平方完成です")),
                    png(40, 30)));

    assertEquals("今日は頂点の求め方を教わりました。", result.summaryText());
    assertEquals(List.of(), result.quotes());
    assertEquals("organize-model", stub.requests().get(0).body().get("model").asText());
    assertTrue(systemPrompt(stub.requests().get(0)).contains("真面目な暗記派"));
    assertLogged("summary", "organize-model", "success", "");
  }

  @ParameterizedTest
  @ValueSource(ints = {85, 80, 79, 50, 49, 0})
  void 模試の得点の水準で_指示が変わる(int score) {
    stub.enqueueContent(
        "{\"problemText\":\"類題\",\"answerSheet\":\"解答\",\"studentComment\":\"コメント\"}");

    model()
        .mockExam(
            context(AiCallKind.MOCK_EXAM),
            new MockExamInput(
                StudentType.INTUITIVE,
                Subject.MATH,
                "二次関数",
                score,
                new LastLesson("y = x^2 - 6x + 5 の頂点", List.of("平方完成の形を作る"))));

    String prompt = systemPrompt(stub.requests().get(0));
    String expected = score >= 80 ? "最後まで解き" : score >= 50 ? "途中までは正しく解き" : "途中で手が止まり";
    assertTrue(prompt.contains(expected), () -> "得点 " + score + " の指示が無い");
    assertFalse(prompt.contains("{{"));
    assertTrue(
        userContent(stub.requests().get(0)).get(0).get("text").asText().contains(score + " 点"));
  }

  @Test
  void 模試は_直近の授業が無いときも_作れる() {
    stub.enqueueContent(
        "{\"problemText\":\"類題\",\"answerSheet\":\"解答\",\"studentComment\":\"コメント\"}");

    LanguageModel.MockExamResult result =
        model()
            .mockExam(
                context(AiCallKind.MOCK_EXAM),
                new MockExamInput(StudentType.LOGICAL, Subject.SCIENCE, "電流", 70, null));

    assertEquals("類題", result.problemText());
    assertTrue(
        userContent(stub.requests().get(0)).get(0).get("text").asText().contains("直近の授業はありません"));
  }

  @Test
  void 宿題の丸つけは_判定を_列挙に読む() {
    stub.enqueueContent("{\"verdict\":\"undetermined\",\"comment\":\"\"}");

    LanguageModel.HomeworkJudgement judgement =
        model().gradeHomework(context(AiCallKind.HOMEWORK), png(40, 30));

    assertEquals(HomeworkVerdict.UNDETERMINED, judgement.verdict());
    assertEquals("", judgement.comment());
    assertEquals("organize-model", stub.requests().get(0).body().get("model").asText());
    assertLogged("homework", "organize-model", "success", "");
  }

  @Test
  void スキーマに合わない応答は_失敗とし_再試行しない() {
    stub.enqueueContent("{\"degrees\":[1,1]}");

    assertThrows(
        AiFailedException.class,
        () ->
            model()
                .evaluate(
                    context(AiCallKind.EVALUATION),
                    new EvaluationInput(
                        List.of("a", "b", "c"),
                        "m",
                        List.of("s"),
                        "p",
                        png(10, 10),
                        "u",
                        List.of(),
                        List.of())));

    assertEquals(1, stub.requests().size());
    assertLogged("evaluation", "dialogue-model", "failure", " reason=schema_mismatch");
  }

  @Test
  void 余分な項目や_範囲外の値も_スキーマに合わないとして失敗する() {
    stub.enqueueContent("{\"degrees\":[1,1,3]}");

    assertThrows(
        AiFailedException.class,
        () ->
            model()
                .evaluate(
                    context(AiCallKind.EVALUATION),
                    new EvaluationInput(
                        List.of("a", "b", "c"),
                        "m",
                        List.of("s"),
                        "p",
                        png(10, 10),
                        "u",
                        List.of(),
                        List.of())));

    assertLogged("evaluation", "dialogue-model", "failure", " reason=schema_mismatch");
  }

  @Test
  void 指導のポイントが_3_件でない整理は_スキーマに合わないとして失敗する() {
    stub.enqueueContent(
        ORGANIZE_JSON.replace(
            "\"teachingPoints\":[\"平方完成の形を作る\",\"括弧の中の数を読む\",\"頂点の y 座標を求める\"]",
            "\"teachingPoints\":[\"1\",\"2\"]"));

    assertThrows(
        AiFailedException.class,
        () -> model().organize(context(AiCallKind.ORGANIZE), List.of(png(10, 10))));

    assertEquals(1, stub.requests().size());
    assertLogged("organize", "organize-model", "failure", " reason=schema_mismatch");
  }

  @Test
  void 応答が_JSON_でないときは_失敗する() {
    stub.enqueueContent("これは JSON ではありません");

    assertThrows(
        AiFailedException.class,
        () -> model().studentReply(context(AiCallKind.STUDENT_REPLY), studentReplyInput("u")));

    assertLogged("student_reply", "dialogue-model", "failure", " reason=malformed");
  }

  @Test
  void 拒否の応答は_失敗とする() {
    stub.enqueue(new Reply(200, OpenAiStub.completion(null, "お答えできません"), 0));

    assertThrows(
        AiFailedException.class,
        () -> model().studentReply(context(AiCallKind.STUDENT_REPLY), studentReplyInput("u")));

    assertLogged("student_reply", "dialogue-model", "failure", " reason=refusal");
  }

  @Test
  void 応答が遅く_タイムアウトしたら_失敗とし_再試行しない() {
    stub.enqueue(new Reply(200, OpenAiStub.completion(STUDENT_JSON, null), 1_500));

    assertThrows(
        AiFailedException.class,
        () ->
            model(Duration.ofMillis(200), LONG)
                .studentReply(context(AiCallKind.STUDENT_REPLY), studentReplyInput("u")));

    assertEquals(1, stub.requests().size());
    assertLogged("student_reply", "dialogue-model", "failure", " reason=timeout");
  }

  @Test
  void 整理のタイムアウトは_対話のタイムアウトとは別に決まる() {
    stub.enqueue(new Reply(200, OpenAiStub.completion(ORGANIZE_JSON, null), 400));

    OrganizeResult result =
        model(Duration.ofMillis(100), Duration.ofSeconds(5))
            .organize(context(AiCallKind.ORGANIZE), List.of(png(10, 10)));

    assertEquals("y = x^2 - 4x + 3 の頂点を求めなさい", result.problemText());
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 401, 429, 500, 503})
  void HTTP_の_4xx_と_5xx_は_失敗とし_再試行しない(int status) {
    stub.enqueue(new Reply(status, "{\"error\":{\"message\":\"x\"}}", 0));

    assertThrows(
        AiFailedException.class,
        () -> model().studentReply(context(AiCallKind.STUDENT_REPLY), studentReplyInput("u")));

    assertEquals(1, stub.requests().size());
    assertLogged(
        "student_reply", "dialogue-model", "failure", " reason=http_status status=" + status);
  }

  @Test
  void 長辺が_1280_px_を超える画像は_縮めて送る() throws IOException {
    stub.enqueueContent(ORGANIZE_JSON);

    model().organize(context(AiCallKind.ORGANIZE), List.of(png(2000, 1000)));

    String url = userContent(stub.requests().get(0)).get(1).get("image_url").get("url").asText();
    byte[] sent = Base64.getDecoder().decode(url.substring(url.indexOf(";base64,") + 8));
    BufferedImage image = ImageIO.read(new ByteArrayInputStream(sent));
    assertEquals(1280, image.getWidth());
    assertEquals(640, image.getHeight());
  }

  @Test
  void ログには_発話の中身と_API_キーを出さない() {
    stub.enqueueContent(STUDENT_JSON);

    model().studentReply(context(AiCallKind.STUDENT_REPLY), studentReplyInput("秘密の発話内容"));

    assertFalse(logs.isEmpty());
    for (String line : logs) {
      assertFalse(line.contains("秘密の発話内容"), line);
      assertFalse(line.contains("test-key"), line);
      assertFalse(line.contains("なぜそうなるのですか"), line);
    }
  }
}
