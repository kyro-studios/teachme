package com.teachme.ai.fake;

import com.teachme.ai.AiCallKind;
import com.teachme.ai.LanguageModel.AnnotationKind;
import com.teachme.ai.LanguageModel.BoardWrite;
import com.teachme.ai.LanguageModel.BoardWriteKind;
import com.teachme.ai.LanguageModel.Evaluation;
import com.teachme.ai.LanguageModel.Figure;
import com.teachme.ai.LanguageModel.HomeworkJudgement;
import com.teachme.ai.LanguageModel.HomeworkVerdict;
import com.teachme.ai.LanguageModel.MentorReply;
import com.teachme.ai.LanguageModel.MockExamResult;
import com.teachme.ai.LanguageModel.OrganizeResult;
import com.teachme.ai.LanguageModel.Region;
import com.teachme.ai.LanguageModel.StudentAnnotation;
import com.teachme.ai.LanguageModel.StudentExpression;
import com.teachme.ai.LanguageModel.StudentReply;
import com.teachme.ai.LanguageModel.SummaryResult;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * テストが決めた応答の中身 (`payload`、JSON から読んだ Map) を、呼び出しの種類の型に変換する。
 *
 * <p>形が合わないときは {@link IllegalArgumentException} を投げる。テストの指定の誤りは、PUT の時点で返すため、待ち列には入れない。
 * 例外の文言には、キーの名前と期待する形だけを入れ、中身は入れない。
 */
final class ScriptPayloads {

  private ScriptPayloads() {}

  /**
   * @return `kind` の型の値。音声 (`tts`) の中身は持たないため null
   * @throws IllegalArgumentException 形が合わないとき
   */
  static Object convert(AiCallKind kind, Map<String, Object> payload) {
    return switch (kind) {
      case ORGANIZE -> organize(payload);
      case STUDENT_REPLY -> studentReply(payload);
      case EVALUATION -> new Evaluation(degrees(payload));
      case MENTOR_REPLY -> mentorReply(payload);
      case SUMMARY ->
          new SummaryResult(string(payload, "summaryText"), strings(payload, "quotes", false));
      case MOCK_EXAM ->
          new MockExamResult(
              string(payload, "problemText"),
              string(payload, "answerSheet"),
              string(payload, "studentComment"));
      case HOMEWORK ->
          new HomeworkJudgement(
              enumValue(payload, "verdict", HomeworkVerdict.class), string(payload, "comment"));
      case STT -> string(payload, "text");
      case TTS -> null;
    };
  }

  private static OrganizeResult organize(Map<String, Object> payload) {
    List<Figure> figures =
        objects(payload, "figures", false).stream()
            .map(figure -> new Figure(integer(figure, "imagePosition"), region(figure, "region")))
            .toList();
    return new OrganizeResult(
        string(payload, "problemText"),
        figures,
        string(payload, "modelAnswer"),
        strings(payload, "explanationSteps", true),
        string(payload, "studentWork"),
        strings(payload, "teachingPoints", true));
  }

  private static StudentReply studentReply(Map<String, Object> payload) {
    Map<String, Object> annotation = optionalObject(payload, "annotation");
    StudentAnnotation studentAnnotation =
        annotation == null
            ? null
            : new StudentAnnotation(
                enumValue(annotation, "kind", AnnotationKind.class), region(annotation, "region"));
    return new StudentReply(
        string(payload, "text"),
        enumValue(payload, "expression", StudentExpression.class),
        studentAnnotation);
  }

  private static MentorReply mentorReply(Map<String, Object> payload) {
    List<BoardWrite> boardWrites =
        objects(payload, "boardWrites", false).stream()
            .map(
                write ->
                    new BoardWrite(
                        enumValue(write, "kind", BoardWriteKind.class),
                        optionalString(write, "text"),
                        optionalRegion(write, "region")))
            .toList();
    return new MentorReply(string(payload, "text"), boardWrites);
  }

  /** 度合いは、0 から 2 の整数を 3 つ (指導のポイントの数と同じ)。 */
  private static List<Integer> degrees(Map<String, Object> payload) {
    List<Integer> degrees =
        list(payload, "degrees", true).stream()
            .map(
                degree -> {
                  if (degree instanceof Integer value && value >= 0 && value <= 2) {
                    return value;
                  }
                  throw invalid("degrees", "0 から 2 の整数");
                })
            .toList();
    if (degrees.size() != 3) {
      throw invalid("degrees", "3 件");
    }
    return degrees;
  }

  private static Region region(Map<String, Object> map, String key) {
    Map<String, Object> region = object(map, key);
    return new Region(
        number(region, "x"),
        number(region, "y"),
        number(region, "width"),
        number(region, "height"));
  }

  private static Region optionalRegion(Map<String, Object> map, String key) {
    return map.get(key) == null ? null : region(map, key);
  }

  private static String string(Map<String, Object> map, String key) {
    if (map.get(key) instanceof String value) {
      return value;
    }
    throw invalid(key, "文字列");
  }

  private static String optionalString(Map<String, Object> map, String key) {
    return map.get(key) == null ? null : string(map, key);
  }

  private static int integer(Map<String, Object> map, String key) {
    if (map.get(key) instanceof Integer value) {
      return value;
    }
    throw invalid(key, "整数");
  }

  private static double number(Map<String, Object> map, String key) {
    if (map.get(key) instanceof Number value) {
      return value.doubleValue();
    }
    throw invalid(key, "数");
  }

  private static Map<String, Object> object(Map<String, Object> map, String key) {
    Map<String, Object> value = optionalObject(map, key);
    if (value == null) {
      throw invalid(key, "オブジェクト");
    }
    return value;
  }

  private static Map<String, Object> optionalObject(Map<String, Object> map, String key) {
    Object value = map.get(key);
    if (value == null) {
      return null;
    }
    return asObject(value, key);
  }

  private static List<Map<String, Object>> objects(
      Map<String, Object> map, String key, boolean required) {
    return list(map, key, required).stream().map(item -> asObject(item, key)).toList();
  }

  private static List<String> strings(Map<String, Object> map, String key, boolean required) {
    return list(map, key, required).stream()
        .map(
            item -> {
              if (item instanceof String value) {
                return value;
              }
              throw invalid(key, "文字列の配列");
            })
        .toList();
  }

  /** 必須でないとき、無ければ空の配列とみなす。 */
  private static List<?> list(Map<String, Object> map, String key, boolean required) {
    Object value = map.get(key);
    if (value instanceof List<?> items) {
      return items;
    }
    if (value == null && !required) {
      return List.of();
    }
    throw invalid(key, "配列");
  }

  private static <E extends Enum<E>> E enumValue(
      Map<String, Object> map, String key, Class<E> type) {
    String value = string(map, key);
    try {
      return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw invalid(key, "決まった値");
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asObject(Object value, String key) {
    if (value instanceof Map<?, ?>) {
      return (Map<String, Object>) value;
    }
    throw invalid(key, "オブジェクト");
  }

  private static IllegalArgumentException invalid(String key, String expected) {
    return new IllegalArgumentException("payload の " + key + " は" + expected + "にしてください");
  }
}
