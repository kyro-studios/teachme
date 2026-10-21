-- 初期のスキーマ (アーキテクチャ設計 7 節)。
-- 利用者の識別子 (id) は、Cookie teachme_uid の値を、アプリケーションが決めて入れる。

-- 利用者。行は、設定を初めて保存するときに作る
create table users (
    id uuid primary key,
    created_at timestamptz not null default now()
);

-- 単元。教科ごとの学習の区切り
create table units (
    id uuid primary key,
    user_id uuid not null references users (id),
    subject text not null check (subject in ('math', 'science')),
    name text not null check (char_length(name) between 1 and 30),
    understanding smallint not null default 0 check (understanding between 0 and 100),
    lessons_since_exam smallint not null default 0 check (lessons_since_exam >= 0),
    created_at timestamptz not null default now(),
    unique (user_id, subject, name)
);

-- 設定が完了した利用者だけが行を持つ。個人情報の確認が true のものだけを入れる
create table user_settings (
    user_id uuid primary key references users (id),
    student_type text not null check (student_type in ('intuitive', 'diligent', 'logical')),
    current_unit_id uuid not null references units (id),
    lessons_per_week smallint not null check (lessons_per_week between 1 and 7),
    privacy_confirmed boolean not null constraint user_settings_privacy_confirmed_true check (privacy_confirmed),
    updated_at timestamptz not null default now()
);

-- 今日のモード。mode_date が今日 (日本時間) でなければ、通常として読む
create table today_modes (
    user_id uuid primary key references users (id),
    mode text not null check (mode in ('normal', 'self_study', 'homework_check')),
    mode_date date not null
);

-- 教材。整理の結果の列は、organized のときだけ値を持つ
create table materials (
    id uuid primary key,
    unit_id uuid not null references units (id) on delete cascade,
    status text not null check (status in ('organizing', 'organized', 'failed')),
    organize_started_at timestamptz,
    problem_text text,
    figures jsonb,
    model_answer text,
    explanation_steps jsonb,
    student_work text,
    teaching_points jsonb,
    created_at timestamptz not null default now(),
    constraint materials_result_only_when_organized check (
        status = 'organized'
        or (
            problem_text is null
            and figures is null
            and model_answer is null
            and explanation_steps is null
            and student_work is null
            and teaching_points is null
        )
    ),
    constraint materials_result_required_when_organized check (
        status <> 'organized'
        or (
            problem_text is not null
            and figures is not null
            and model_answer is not null
            and explanation_steps is not null
            and student_work is not null
            and teaching_points is not null
        )
    )
);

create index materials_unit_id_created_at_idx on materials (unit_id, created_at);

-- 教材の画像 (1 枚から 3 枚)。教材の削除で、一緒に消える
create table material_images (
    id uuid primary key,
    material_id uuid not null references materials (id) on delete cascade,
    position smallint not null check (position between 1 and 3),
    content_type text not null,
    data bytea not null,
    unique (material_id, position)
);

-- 進行中の授業だけを持つ。記録されると消える
create table lessons (
    id uuid primary key,
    user_id uuid not null references users (id),
    unit_id uuid not null references units (id),
    material_id uuid not null references materials (id) on delete cascade,
    started_at timestamptz not null,
    deadline_at timestamptz not null,
    round_count smallint not null default 0 check (round_count >= 0),
    -- 指導のポイントの度合い (3 件、各 0 から 2)
    degrees smallint[] not null default '{0,0,0}' check (
        cardinality(degrees) = 3
        and (degrees[1] between 0 and 2) is true
        and (degrees[2] between 0 and 2) is true
        and (degrees[3] between 0 and 2) is true
    ),
    busy_since timestamptz
);

create index lessons_user_id_idx on lessons (user_id);

-- 進行中の先輩先生への相談だけを持つ
create table consultations (
    id uuid primary key,
    user_id uuid not null references users (id),
    material_id uuid not null references materials (id) on delete cascade,
    started_at timestamptz not null,
    deadline_at timestamptz not null,
    round_count smallint not null default 0 check (round_count >= 0),
    busy_since timestamptz
);

create index consultations_user_id_idx on consultations (user_id);

-- 進行中の授業か相談の字幕。lesson_id か consultation_id のどちらか一方だけが入る
create table dialogue_turns (
    id uuid primary key,
    lesson_id uuid references lessons (id) on delete cascade,
    consultation_id uuid references consultations (id) on delete cascade,
    seq integer not null,
    speaker text not null check (speaker in ('user', 'student', 'mentor')),
    body text not null,
    expression text check (expression in ('normal', 'troubled', 'convinced')),
    extras jsonb,
    replied boolean not null default false,
    audio bytea,
    audio_attempted boolean not null default false,
    created_at timestamptz not null default now(),
    constraint dialogue_turns_one_parent check ((lesson_id is null) <> (consultation_id is null)),
    unique (lesson_id, seq),
    unique (consultation_id, seq)
);

-- 未応答の利用者の字幕は、授業か相談ごとに 1 件まで
create unique index dialogue_turns_unreplied_user_per_lesson
    on dialogue_turns (lesson_id)
    where speaker = 'user' and replied = false;

create unique index dialogue_turns_unreplied_user_per_consultation
    on dialogue_turns (consultation_id)
    where speaker = 'user' and replied = false;

-- 授業の記録とまとめノート。id は授業の識別子と同じ値。material_ref は、教材を削除しても残すため外部キーにしない
create table lesson_records (
    id uuid primary key,
    user_id uuid not null references users (id),
    unit_id uuid not null references units (id) on delete cascade,
    material_ref uuid not null,
    completed_at timestamptz not null,
    degrees smallint[] not null check (
        cardinality(degrees) = 3
        and (degrees[1] between 0 and 2) is true
        and (degrees[2] between 0 and 2) is true
        and (degrees[3] between 0 and 2) is true
    ),
    summary_text text not null,
    quotes jsonb not null default '[]'::jsonb
);

create index lesson_records_user_id_completed_at_idx on lesson_records (user_id, completed_at);
create index lesson_records_unit_id_completed_at_idx on lesson_records (unit_id, completed_at);

-- 模試の結果。保存されたときだけ行を作る
create table mock_exams (
    id uuid primary key,
    unit_id uuid not null references units (id) on delete cascade,
    taken_at timestamptz not null,
    score smallint not null check (score between 0 and 100),
    deviation numeric(4, 1) not null,
    problem_text text not null,
    answer_sheet text not null,
    student_comment text not null
);

create index mock_exams_unit_id_taken_at_idx on mock_exams (unit_id, taken_at);
