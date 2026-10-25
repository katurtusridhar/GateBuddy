-- ==============================================================================
-- GATE CSE COMPANION — STAGING MIGRATION: DPP RECORDS & RLS POLICIES
-- Enables DPP (Daily Practice Problems) multi-tenant tracking with RLS.
-- ==============================================================================

CREATE TABLE IF NOT EXISTS public.dpp_records (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    date DATE NOT NULL,
    subject_code TEXT NOT NULL,
    subject_name TEXT NOT NULL,
    topic TEXT DEFAULT '',
    total_questions INT NOT NULL DEFAULT 0,
    questions_correct INT NOT NULL DEFAULT 0,
    accuracy_percentage NUMERIC(5,2) DEFAULT 0.0,
    notes TEXT DEFAULT '',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_dpp_records_user_date ON public.dpp_records(user_id, date);
CREATE INDEX IF NOT EXISTS idx_dpp_records_user_subject ON public.dpp_records(user_id, subject_code);

ALTER TABLE public.dpp_records ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Users can read own dpp records" ON public.dpp_records;
CREATE POLICY "Users can read own dpp records"
    ON public.dpp_records FOR SELECT
    USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users can insert own dpp records" ON public.dpp_records;
CREATE POLICY "Users can insert own dpp records"
    ON public.dpp_records FOR INSERT
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users can update own dpp records" ON public.dpp_records;
CREATE POLICY "Users can update own dpp records"
    ON public.dpp_records FOR UPDATE
    USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users can delete own dpp records" ON public.dpp_records;
CREATE POLICY "Users can delete own dpp records"
    ON public.dpp_records FOR DELETE
    USING (auth.uid() = user_id);

DO $$
BEGIN
    BEGIN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.dpp_records;
    EXCEPTION WHEN duplicate_object THEN NULL;
    END;
END $$;
