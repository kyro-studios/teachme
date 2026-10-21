package com.teachme.ai.fake;

import com.teachme.ai.AiCallContext;
import com.teachme.ai.AiCallKind;
import com.teachme.ai.AiFailedException;
import com.teachme.ai.AiScriptControl;
import com.teachme.ai.LanguageModel;
import com.teachme.ai.LanguageModel.Evaluation;
import com.teachme.ai.LanguageModel.EvaluationInput;
import com.teachme.ai.LanguageModel.HomeworkJudgement;
import com.teachme.ai.LanguageModel.MentorReply;
import com.teachme.ai.LanguageModel.MentorReplyInput;
import com.teachme.ai.LanguageModel.MockExamInput;
import com.teachme.ai.LanguageModel.MockExamResult;
import com.teachme.ai.LanguageModel.OrganizeResult;
import com.teachme.ai.LanguageModel.StudentReply;
import com.teachme.ai.LanguageModel.StudentReplyInput;
import com.teachme.ai.LanguageModel.SummaryInput;
import com.teachme.ai.LanguageModel.SummaryResult;
import com.teachme.ai.Media;
import com.teachme.ai.ScriptedResponse;
import com.teachme.ai.SpeechToText;
import com.teachme.ai.TextToSpeech;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * テスト用のフェイクの外部 AI。`AI_MODE=fake` のときだけ組み立てる。
 *
 * <p>ネットワークへ出ず、秘密情報を読まない。既定では、すべて成功の応答を返す。利用者ごとに、応答の待ち列と呼び出しの数えを持つ。 待ち列はプロセスのメモリに置くため、テスト用に 1
 * つのインスタンスだけで動かす。
 */
public final class FakeAi implements LanguageModel, SpeechToText, TextToSpeech, AiScriptControl {

  private final Map<UUID, UserScript> scripts = new ConcurrentHashMap<>();

  @Override
  public OrganizeResult organize(AiCallContext context, List<Media> images) {
    return respond(context, OrganizeResult.class, FakeDefaults::organize);
  }

  @Override
  public StudentReply studentReply(AiCallContext context, StudentReplyInput input) {
    return respond(context, StudentReply.class, FakeDefaults::studentReply);
  }

  @Override
  public Evaluation evaluate(AiCallContext context, EvaluationInput input) {
    return respond(context, Evaluation.class, FakeDefaults::evaluation);
  }

  @Override
  public MentorReply mentorReply(AiCallContext context, MentorReplyInput input) {
    return respond(context, MentorReply.class, FakeDefaults::mentorReply);
  }

  @Override
  public SummaryResult summarize(AiCallContext context, SummaryInput input) {
    return respond(context, SummaryResult.class, FakeDefaults::summary);
  }

  @Override
  public MockExamResult mockExam(AiCallContext context, MockExamInput input) {
    return respond(context, MockExamResult.class, FakeDefaults::mockExam);
  }

  @Override
  public HomeworkJudgement gradeHomework(AiCallContext context, Media photo) {
    return respond(context, HomeworkJudgement.class, FakeDefaults::homework);
  }

  @Override
  public String transcribe(AiCallContext context, Media recording) {
    return respond(context, String.class, FakeDefaults::transcript);
  }

  @Override
  public Media synthesize(AiCallContext context, Voice voice, String text) {
    return respond(context, Media.class, FakeDefaults::silentSpeech);
  }

  @Override
  public void script(
      UUID userId, AiCallKind kind, List<ScriptedResponse> responses, boolean repeat) {
    if (responses.isEmpty()) {
      throw new IllegalArgumentException("応答は 1 つ以上指定してください");
    }
    // 変換を先に全部行い、形の誤りがあれば、待ち列を変えない
    List<Scripted> entries =
        responses.stream().map(response -> toScripted(kind, response)).toList();
    scriptOf(userId).set(kind, entries, repeat);
  }

  @Override
  public void reset(UUID userId) {
    scripts.remove(userId);
  }

  @Override
  public Map<AiCallKind, Integer> callCounts(UUID userId) {
    return scriptOf(userId).counts();
  }

  /** 待ち列の先頭を使う。待ち列が空なら既定の成功の応答を返す。失敗の応答なら、{@link AiFailedException} を投げる。 */
  private <T> T respond(AiCallContext context, Class<T> type, Supplier<T> fallback) {
    Scripted next = scriptOf(context.userId()).next(context.kind());
    if (next == null) {
      return fallback.get();
    }
    if (!next.success()) {
      throw new AiFailedException(context.kind());
    }
    if (next.payload() == null) {
      return fallback.get();
    }
    return type.cast(next.payload());
  }

  private static Scripted toScripted(AiCallKind kind, ScriptedResponse response) {
    if (!response.success()) {
      return new Scripted(false, null);
    }
    Object payload =
        response.payload() == null ? null : ScriptPayloads.convert(kind, response.payload());
    return new Scripted(true, payload);
  }

  private UserScript scriptOf(UUID userId) {
    return scripts.computeIfAbsent(userId, id -> new UserScript());
  }

  /** 1 つの応答。`payload` は、呼び出しの種類の型に変換済み (成功で、中身が無いときは null)。 */
  private record Scripted(boolean success, Object payload) {}

  /** 1 人の利用者の、種類ごとの待ち列と、呼び出しの数え。 */
  private static final class UserScript {
    private final Map<AiCallKind, Deque<Scripted>> queues = new EnumMap<>(AiCallKind.class);
    private final Set<AiCallKind> repeating = EnumSet.noneOf(AiCallKind.class);
    private final Map<AiCallKind, Integer> counts = new EnumMap<>(AiCallKind.class);

    /** 呼び出しを数え、待ち列の先頭を取り出す。待ち列が空なら null。 */
    synchronized Scripted next(AiCallKind kind) {
      counts.merge(kind, 1, Integer::sum);
      Deque<Scripted> queue = queues.get(kind);
      if (queue == null || queue.isEmpty()) {
        return null;
      }
      if (queue.size() == 1 && repeating.contains(kind)) {
        return queue.peekFirst();
      }
      return queue.pollFirst();
    }

    synchronized void set(AiCallKind kind, List<Scripted> entries, boolean repeat) {
      queues.put(kind, new ArrayDeque<>(entries));
      if (repeat) {
        repeating.add(kind);
      } else {
        repeating.remove(kind);
      }
    }

    /** 呼ばれていない種類も 0 として返す。 */
    synchronized Map<AiCallKind, Integer> counts() {
      Map<AiCallKind, Integer> all = new EnumMap<>(AiCallKind.class);
      for (AiCallKind kind : AiCallKind.values()) {
        all.put(kind, counts.getOrDefault(kind, 0));
      }
      return all;
    }
  }
}
