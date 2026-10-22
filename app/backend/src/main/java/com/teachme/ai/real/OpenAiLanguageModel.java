package com.teachme.ai.real;

import com.fasterxml.jackson.databind.JsonNode;
import com.teachme.ai.AiCallContext;
import com.teachme.ai.LanguageModel;
import com.teachme.ai.Media;
import com.teachme.ai.real.ChatCompletionClient.Part;
import com.teachme.ai.real.ChatCompletionClient.Request;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * 実際の LLM を呼ぶ {@link LanguageModel} (初期の実装は OpenAI の API、アーキテクチャ設計 4.3)。
 *
 * <p>指示は `prompts/*.md`、応答の形は `schemas/*.json` に置く。応答が形に合わないときや、HTTP の失敗のときは、{@link
 * com.teachme.ai.AiFailedException} にする。
 *
 * <p>モデルは、往復ごとに呼ぶ生徒の応答、学習評価、先輩先生の応答だけ `dialogueModel` を使う。それ以外の 1 回ごとの呼び出しは `organizeModel` を使う。
 */
public final class OpenAiLanguageModel implements LanguageModel {

  /** 整理は、問題の読み取りと、図表の位置の答えが長くなるため、上限を大きくする。 */
  private static final int ORGANIZE_MAX_OUTPUT_TOKENS = 16_000;

  private static final int MAX_OUTPUT_TOKENS = 8_000;

  /** 模試の得点の水準の境界 (仕様書 5.7)。 */
  private static final int HIGH_SCORE = 80;

  private static final int MIDDLE_SCORE = 50;

  private final OpenAiSettings settings;
  private final ChatCompletionClient client;

  public OpenAiLanguageModel(OpenAiSettings settings) {
    this(settings, HttpClient.newHttpClient(), ChatCompletionClient.TO_SLF4J);
  }

  /** テストが、スタブのサーバーと、ログの出し先を差し替えるための口。 */
  OpenAiLanguageModel(OpenAiSettings settings, HttpClient http, Consumer<String> log) {
    this.settings = settings;
    this.client = new ChatCompletionClient(settings.endpoint(), settings.apiKey(), http, log);
  }

  @Override
  public OrganizeResult organize(AiCallContext context, List<Media> images) {
    List<Part> parts = new ArrayList<>();
    parts.add(
        text("教材の写真は " + images.size() + " 枚です。写真の番号 (imagePosition) は、先頭の写真を 1 として数えてください。"));
    images.forEach(image -> parts.add(new Part.Image(image)));
    JsonNode node =
        client.complete(
            new Request(
                context.kind(),
                settings.organizeModel(),
                settings.organizeTimeout(),
                Prompts.system("organize", Map.of()),
                parts,
                "organize",
                ORGANIZE_MAX_OUTPUT_TOKENS));
    List<Figure> figures =
        stream(node.get("figures"))
            .map(
                figure ->
                    new Figure(figure.get("imagePosition").asInt(), region(figure.get("region"))))
            .toList();
    return new OrganizeResult(
        node.get("problemText").asText(),
        figures,
        node.get("modelAnswer").asText(),
        strings(node.get("explanationSteps")),
        node.get("studentWork").asText(),
        strings(node.get("teachingPoints")));
  }

  @Override
  public StudentReply studentReply(AiCallContext context, StudentReplyInput input) {
    List<Part> parts = new ArrayList<>();
    if (input.boardImage() == null) {
      // 授業の最初の発話は、ボードの画像が無い (問題文と途中の解答だけで作る)
      parts.add(
          text(
              section("問題文", input.problemText())
                  + section("生徒の途中の解答", input.studentWork())
                  + section("先生の発話", input.utterance())
                  + section("これまでの字幕", subtitles(input.subtitles()))
                  + section("指している範囲", regions(input.pointedRegions()))));
    } else {
      parts.add(
          text(
              section("問題文", input.problemText())
                  + section("生徒の途中の解答", input.studentWork())
                  + section("先生の発話", input.utterance())
                  + section("これまでの字幕", subtitles(input.subtitles()))
                  + section("指している範囲", regions(input.pointedRegions()))
                  + "次の画像は、ボードの現在の内容です。"));
      parts.add(new Part.Image(input.boardImage()));
    }
    JsonNode node =
        client.complete(
            new Request(
                context.kind(),
                settings.dialogueModel(),
                settings.dialogueTimeout(),
                Prompts.system(
                    "student-reply",
                    Map.of(
                        "displayName",
                        input.studentType().displayName(),
                        "personality",
                        input.studentType().personality())),
                parts,
                "student-reply",
                MAX_OUTPUT_TOKENS));
    JsonNode annotation = node.get("annotation");
    StudentAnnotation studentAnnotation =
        annotation.isNull()
            ? null
            : new StudentAnnotation(
                enumValue(AnnotationKind.class, annotation.get("kind")),
                region(annotation.get("region")));
    return new StudentReply(
        node.get("text").asText(),
        enumValue(StudentExpression.class, node.get("expression")),
        studentAnnotation);
  }

  @Override
  public Evaluation evaluate(AiCallContext context, EvaluationInput input) {
    List<Part> parts =
        List.of(
            text(
                section("指導のポイント", numbered(input.teachingPoints()))
                    + section("模範解答", input.modelAnswer())
                    + section("解説", numbered(input.explanationSteps()))
                    + section("問題文", input.problemText())
                    + section("先生の発話", input.utterance())
                    + section("これまでの字幕", subtitles(input.subtitles()))
                    + section("指している範囲", regions(input.pointedRegions()))
                    + "次の画像は、ボードの現在の内容です。"),
            new Part.Image(input.boardImage()));
    JsonNode node =
        client.complete(
            new Request(
                context.kind(),
                settings.dialogueModel(),
                settings.dialogueTimeout(),
                Prompts.system("evaluation", Map.of()),
                parts,
                "evaluation",
                MAX_OUTPUT_TOKENS));
    return new Evaluation(stream(node.get("degrees")).map(JsonNode::asInt).toList());
  }

  @Override
  public MentorReply mentorReply(AiCallContext context, MentorReplyInput input) {
    List<Part> parts =
        List.of(
            text(
                section("問題文", input.problemText())
                    + section("模範解答", input.modelAnswer())
                    + section("解説", numbered(input.explanationSteps()))
                    + section("指導のポイント", numbered(input.teachingPoints()))
                    + section("先生の発話", input.utterance())
                    + section("これまでの字幕", subtitles(input.subtitles()))
                    + section("指している範囲", regions(input.pointedRegions()))
                    + "次の画像は、ボードの現在の内容です。"),
            new Part.Image(input.boardImage()));
    JsonNode node =
        client.complete(
            new Request(
                context.kind(),
                settings.dialogueModel(),
                settings.dialogueTimeout(),
                Prompts.system("mentor-reply", Map.of()),
                parts,
                "mentor-reply",
                MAX_OUTPUT_TOKENS));
    List<BoardWrite> boardWrites =
        stream(node.get("boardWrites"))
            .map(
                write ->
                    new BoardWrite(
                        enumValue(BoardWriteKind.class, write.get("kind")),
                        nullableText(write.get("text")),
                        write.get("region").isNull() ? null : region(write.get("region"))))
            .toList();
    return new MentorReply(node.get("text").asText(), boardWrites);
  }

  @Override
  public SummaryResult summarize(AiCallContext context, SummaryInput input) {
    List<Part> parts =
        List.of(
            text(section("これまでの字幕", subtitles(input.subtitles())) + "次の画像は、ボードの現在の内容です。"),
            new Part.Image(input.boardImage()));
    JsonNode node =
        client.complete(
            new Request(
                context.kind(),
                settings.organizeModel(),
                settings.dialogueTimeout(),
                Prompts.system(
                    "summary",
                    Map.of(
                        "displayName",
                        input.studentType().displayName(),
                        "personality",
                        input.studentType().personality())),
                parts,
                "summary",
                MAX_OUTPUT_TOKENS));
    return new SummaryResult(node.get("summaryText").asText(), strings(node.get("quotes")));
  }

  @Override
  public MockExamResult mockExam(AiCallContext context, MockExamInput input) {
    String level = level(input.score());
    LastLesson lastLesson = input.lastLesson();
    String lastLessonText =
        lastLesson == null
            ? "(直近の授業はありません)"
            : section("直近の授業の問題", lastLesson.problemText())
                + section("直近の授業の指導のポイント", numbered(lastLesson.teachingPoints()));
    List<Part> parts =
        List.of(
            text(
                section("教科", input.subject().displayName())
                    + section("単元名", input.unitName())
                    + section("模試の得点", input.score() + " 点")
                    + lastLessonText));
    JsonNode node =
        client.complete(
            new Request(
                context.kind(),
                settings.organizeModel(),
                settings.dialogueTimeout(),
                Prompts.system(
                    "mock-exam",
                    Map.of(
                        "displayName",
                        input.studentType().displayName(),
                        "personality",
                        input.studentType().personality(),
                        "levelRules",
                        Prompts.fragment("mock-exam-" + level))),
                parts,
                "mock-exam",
                MAX_OUTPUT_TOKENS));
    return new MockExamResult(
        node.get("problemText").asText(),
        node.get("answerSheet").asText(),
        node.get("studentComment").asText());
  }

  @Override
  public HomeworkJudgement gradeHomework(AiCallContext context, Media photo) {
    List<Part> parts = List.of(text("次の写真は、生徒が解いた宿題です。"), new Part.Image(photo));
    JsonNode node =
        client.complete(
            new Request(
                context.kind(),
                settings.organizeModel(),
                settings.dialogueTimeout(),
                Prompts.system("homework", Map.of()),
                parts,
                "homework",
                MAX_OUTPUT_TOKENS));
    return new HomeworkJudgement(
        enumValue(HomeworkVerdict.class, node.get("verdict")), node.get("comment").asText());
  }

  /** 模試の得点の水準の名前。仕様書 5.7 の 3 つの水準。 */
  static String level(int score) {
    if (score >= HIGH_SCORE) {
      return "high";
    }
    if (score >= MIDDLE_SCORE) {
      return "middle";
    }
    return "low";
  }

  private static Part.Text text(String value) {
    return new Part.Text(value);
  }

  /** 見出しと本文を、利用者の入力の区切りとして並べる。本文が空のときは「(無し)」と書く。 */
  private static String section(String title, String body) {
    String value = body == null || body.isBlank() ? "(無し)" : body;
    return "## " + title + "\n" + value + "\n\n";
  }

  private static String subtitles(List<Subtitle> subtitles) {
    return subtitles.stream()
        .map(subtitle -> "- " + speakerLabel(subtitle.speaker()) + ": " + subtitle.text())
        .collect(Collectors.joining("\n"));
  }

  private static String speakerLabel(Speaker speaker) {
    return switch (speaker) {
      case USER -> "先生";
      case STUDENT -> "生徒";
      case MENTOR -> "先輩先生";
    };
  }

  private static String regions(List<Region> regions) {
    return regions.stream()
        .map(
            region ->
                String.format(
                    Locale.ROOT,
                    "- 左から %.2f、上から %.2f、幅 %.2f、高さ %.2f (画像全体に対する割合)",
                    region.x(),
                    region.y(),
                    region.width(),
                    region.height()))
        .collect(Collectors.joining("\n"));
  }

  private static String numbered(List<String> items) {
    return IntStream.range(0, items.size())
        .mapToObj(index -> (index + 1) + ". " + items.get(index))
        .collect(Collectors.joining("\n"));
  }

  private static Region region(JsonNode node) {
    return new Region(
        node.get("x").asDouble(),
        node.get("y").asDouble(),
        node.get("width").asDouble(),
        node.get("height").asDouble());
  }

  private static List<String> strings(JsonNode node) {
    return stream(node).map(JsonNode::asText).toList();
  }

  private static String nullableText(JsonNode node) {
    return node.isNull() ? null : node.asText();
  }

  /** 列挙の値は、応答の文字列を大文字にして読む (`question_mark` は `QUESTION_MARK`)。 */
  private static <E extends Enum<E>> E enumValue(Class<E> type, JsonNode node) {
    return Enum.valueOf(type, node.asText().toUpperCase(Locale.ROOT));
  }

  private static Stream<JsonNode> stream(JsonNode array) {
    return StreamSupport.stream(array.spliterator(), false);
  }
}
