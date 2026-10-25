import { createClient, SupabaseClient } from "@supabase/supabase-js";

/**
 * Server-only Supabase Service-Role Client.
 *
 * CRITICAL SECURITY INVARIANTS:
 * 1. SUPABASE_SERVICE_ROLE_KEY is strictly server-only. It MUST NEVER be prefixed
 *    with NEXT_PUBLIC_ or exported to browser bundles, client responses, or logs.
 * 2. Elevated database privileges (bypassing RLS) are used solely for backend
 *    sync reconciliation and server-side maintenance.
 * 3. The service client MUST NOT persist sessions or share state across requests
 *    (persistSession: false, autoRefreshToken: false).
 * 4. Application code MUST enforce strict tenant-isolation and ownership verification
 *    before executing any query or mutation using this client.
 */

const supabaseUrl =
  process.env.SUPABASE_URL ||
  process.env.NEXT_PUBLIC_SUPABASE_URL ||
  "https://placeholder-gate-companion.supabase.co";

const serviceRoleKey =
  process.env.SUPABASE_SERVICE_ROLE_KEY ||
  process.env.SUPABASE_SERVICE_KEY ||
  "";

export const isSupabaseServerConfigured =
  Boolean(supabaseUrl) &&
  !supabaseUrl.includes("placeholder") &&
  Boolean(serviceRoleKey) &&
  !serviceRoleKey.includes("placeholder");

/**
 * Creates an isolated, unmemoized Supabase service-role client.
 * Does not retain session cookies or user headers.
 */
export function getSupabaseServiceClient(): SupabaseClient | null {
  if (!isSupabaseServerConfigured || !serviceRoleKey) {
    return null;
  }

  return createClient(supabaseUrl, serviceRoleKey, {
    auth: {
      persistSession: false,
      autoRefreshToken: false,
      detectSessionInUrl: false,
    },
  });
}
