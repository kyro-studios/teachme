// まとめノートの表示だけの部品 (仕様書 5.6、5.8)。授業の直後と、記録の画面で使う。データは取らず、props だけで描く
// shared は api に依存しないため、型は自前で持つ

export type SummaryNoteViewProps = {
  /** まとめ文。AI 生徒の言葉で、今日教わった内容を整理した文章 */
  summaryText: string;
  /** 引用する板書 (利用者が描いた図や式の文章)。0 件のときは「先生の図と式」を出さない */
  quotes: readonly string[];
};

export function SummaryNoteView({ summaryText, quotes }: SummaryNoteViewProps) {
  return (
    <article className="grid gap-5 rounded-xl border-2 border-slate-400 bg-white p-6">
      <h2 className="text-2xl font-bold text-slate-900">まとめノート</h2>
      <section className="grid gap-2">
        <h3 className="text-xl font-bold text-slate-900">今日のまとめ</h3>
        <p className="text-lg leading-relaxed text-slate-900">{summaryText}</p>
      </section>
      {quotes.length > 0 ? (
        <section className="grid gap-2">
          <h3 className="text-xl font-bold text-slate-900">先生の図と式</h3>
          <ul className="grid gap-2">
            {quotes.map((quote, index) => (
              <li key={index} className="rounded-lg bg-slate-100 px-4 py-2 text-lg text-slate-900">
                {quote}
              </li>
            ))}
          </ul>
        </section>
      ) : null}
    </article>
  );
}
