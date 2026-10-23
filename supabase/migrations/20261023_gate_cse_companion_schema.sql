-- ==============================================================================
-- GATE CSE COMPANION — AUTHORITATIVE POSTGRESQL SCHEMA (SUPABASE)
-- Multi-tenant schema with Row Level Security (RLS) ensuring strict user data isolation.
-- ==============================================================================

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. USER PROFILES
CREATE TABLE IF NOT EXISTS public.user_profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    email TEXT NOT NULL,
    full_name TEXT DEFAULT 'Sridhar',
    target_exam_date DATE DEFAULT '2028-02-12',
    target_exam_name TEXT DEFAULT 'GATE CSE 2028',
    daily_recovery_limit INT DEFAULT 2,
    theme_mode TEXT DEFAULT 'DARK',
    reminder_enabled BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE public.user_profiles ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can view their own profile"
    ON public.user_profiles FOR SELECT
    USING (auth.uid() = id);

CREATE POLICY "Users can update their own profile"
    ON public.user_profiles FOR UPDATE
    USING (auth.uid() = id);

CREATE POLICY "Users can insert their own profile"
    ON public.user_profiles FOR INSERT
    WITH CHECK (auth.uid() = id);

-- 2. CANONICAL BASELINE SCHEDULE TASKS (Shared, immutable reference)
CREATE TABLE IF NOT EXISTS public.schedule_tasks (
    id TEXT PRIMARY KEY,
    date DATE NOT NULL,
    day_of_week TEXT NOT NULL,
    slot INT NOT NULL,
    subject TEXT NOT NULL,
    type TEXT NOT NULL,
    cur_num INT NOT NULL,
    total_count INT NOT NULL,
    title TEXT NOT NULL,
    details TEXT,
    milestone TEXT,
    source_page INT,
    is_buffer BOOLEAN DEFAULT false,
    is_revision BOOLEAN DEFAULT false,
    is_sunday_activity BOOLEAN DEFAULT false
);

CREATE INDEX IF NOT EXISTS idx_schedule_tasks_date ON public.schedule_tasks(date);
CREATE INDEX IF NOT EXISTS idx_schedule_tasks_subject ON public.schedule_tasks(subject);

ALTER TABLE public.schedule_tasks ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Everyone can read canonical schedule tasks"
    ON public.schedule_tasks FOR SELECT
    TO authenticated, anon
    USING (true);

-- 3. USER TASK PROGRESS (Explicit user completions)
CREATE TABLE IF NOT EXISTS public.task_progress (
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    task_id TEXT NOT NULL,
    original_date DATE NOT NULL,
    is_completed BOOLEAN NOT NULL DEFAULT false,
    completed_at TIMESTAMPTZ,
    completion_type TEXT CHECK (completion_type IN ('ON_TIME', 'LATE', 'RECOVERY', 'RECOVERY_LATE')),
    notes TEXT DEFAULT '',
    version INT DEFAULT 1,
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (user_id, task_id)
);

CREATE INDEX IF NOT EXISTS idx_task_progress_user_date ON public.task_progress(user_id, original_date);

ALTER TABLE public.task_progress ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can read own task progress"
    ON public.task_progress FOR SELECT
    USING (auth.uid() = user_id);

CREATE POLICY "Users can insert own task progress"
    ON public.task_progress FOR INSERT
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update own task progress"
    ON public.task_progress FOR UPDATE
    USING (auth.uid() = user_id);

CREATE POLICY "Users can delete own task progress"
    ON public.task_progress FOR DELETE
    USING (auth.uid() = user_id);

-- 4. RECOVERY ASSIGNMENTS
CREATE TABLE IF NOT EXISTS public.recovery_assignments (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    task_id TEXT NOT NULL,
    original_date DATE NOT NULL,
    recovery_date DATE NOT NULL,
    assigned_at TIMESTAMPTZ DEFAULT NOW(),
    is_completed BOOLEAN DEFAULT false,
    completed_at TIMESTAMPTZ,
    priority_level INT DEFAULT 2,
    status TEXT DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'MANUAL_CONFIRMED', 'COMPLETED', 'EXPIRED', 'CANCELLED')),
    version INT DEFAULT 1,
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT uq_user_recovery_task UNIQUE (user_id, task_id)
);

CREATE INDEX IF NOT EXISTS idx_recovery_user_date ON public.recovery_assignments(user_id, recovery_date);

ALTER TABLE public.recovery_assignments ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can read own recoveries"
    ON public.recovery_assignments FOR SELECT
    USING (auth.uid() = user_id);

CREATE POLICY "Users can insert own recoveries"
    ON public.recovery_assignments FOR INSERT
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update own recoveries"
    ON public.recovery_assignments FOR UPDATE
    USING (auth.uid() = user_id);

CREATE POLICY "Users can delete own recoveries"
    ON public.recovery_assignments FOR DELETE
    USING (auth.uid() = user_id);

-- 5. ASSESSMENT RESULTS (Tests & Mistake notebook)
CREATE TABLE IF NOT EXISTS public.assessment_results (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    test_date DATE NOT NULL,
    test_name TEXT NOT NULL,
    subject_name TEXT NOT NULL,
    marks_obtained NUMERIC(5,2) NOT NULL,
    max_marks NUMERIC(5,2) NOT NULL,
    percentage NUMERIC(5,2) NOT NULL,
    questions_attempted INT,
    questions_correct INT,
    topic TEXT DEFAULT '',
    mistakes_analysis TEXT DEFAULT '',
    follow_up_tasks TEXT DEFAULT '',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_assessment_user_date ON public.assessment_results(user_id, test_date);

ALTER TABLE public.assessment_results ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can read own assessments"
    ON public.assessment_results FOR SELECT
    USING (auth.uid() = user_id);

CREATE POLICY "Users can insert own assessments"
    ON public.assessment_results FOR INSERT
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update own assessments"
    ON public.assessment_results FOR UPDATE
    USING (auth.uid() = user_id);

CREATE POLICY "Users can delete own assessments"
    ON public.assessment_results FOR DELETE
    USING (auth.uid() = user_id);

-- 6. CUSTOM TASKS
CREATE TABLE IF NOT EXISTS public.custom_tasks (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    date DATE NOT NULL,
    title TEXT NOT NULL,
    subject_code TEXT DEFAULT 'GATE',
    subject_name TEXT DEFAULT 'General / Practice',
    is_completed BOOLEAN DEFAULT false,
    completed_at TIMESTAMPTZ,
    notes TEXT DEFAULT '',
    points_value INT DEFAULT 5,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE public.custom_tasks ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can read own custom tasks"
    ON public.custom_tasks FOR SELECT
    USING (auth.uid() = user_id);

CREATE POLICY "Users can insert own custom tasks"
    ON public.custom_tasks FOR INSERT
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update own custom tasks"
    ON public.custom_tasks FOR UPDATE
    USING (auth.uid() = user_id);

CREATE POLICY "Users can delete own custom tasks"
    ON public.custom_tasks FOR DELETE
    USING (auth.uid() = user_id);

-- 7. USER ACHIEVEMENTS
CREATE TABLE IF NOT EXISTS public.user_achievements (
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    code TEXT NOT NULL,
    is_unlocked BOOLEAN DEFAULT false,
    unlocked_at TIMESTAMPTZ,
    PRIMARY KEY (user_id, code)
);

ALTER TABLE public.user_achievements ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can read own achievements"
    ON public.user_achievements FOR SELECT
    USING (auth.uid() = user_id);

CREATE POLICY "Users can upsert own achievements"
    ON public.user_achievements FOR ALL
    USING (auth.uid() = user_id);

-- 8. SYNC AUDIT LOG / REVISION TRACKING
CREATE TABLE IF NOT EXISTS public.sync_operations (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    client_id TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    operation TEXT NOT NULL, -- 'INSERT', 'UPDATE', 'DELETE'
    payload JSONB NOT NULL,
    server_timestamp TIMESTAMPTZ DEFAULT NOW(),
    version INT NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_sync_user_ts ON public.sync_operations(user_id, server_timestamp);

ALTER TABLE public.sync_operations ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can read own sync operations"
    ON public.sync_operations FOR SELECT
    USING (auth.uid() = user_id);

CREATE POLICY "Users can insert own sync operations"
    ON public.sync_operations FOR INSERT
    WITH CHECK (auth.uid() = user_id);

-- 9. REALTIME PUBLICATION SETUP
-- Enable Realtime for clients to receive instant push updates
ALTER PUBLICATION supabase_realtime ADD TABLE public.task_progress;
ALTER PUBLICATION supabase_realtime ADD TABLE public.recovery_assignments;
ALTER PUBLICATION supabase_realtime ADD TABLE public.assessment_results;
ALTER PUBLICATION supabase_realtime ADD TABLE public.user_profiles;
