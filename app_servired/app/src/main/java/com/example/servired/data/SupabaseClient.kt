package com.example.servired.data

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseClientConfig {

    const val SUPABASE_URL = "https://exgqeypdwkxznwwjnauo.supabase.co/rest/v1/"

    const val SUPABASE_ANON_KEY = "sb_publishable_eBzha_q_8d397xsgvjAN3A_A6IJXXve"

    private val sanitizedUrl = if (SUPABASE_URL.endsWith("/rest/v1/")) {
        SUPABASE_URL.removeSuffix("/rest/v1/")
    } else if (SUPABASE_URL.endsWith("/rest/v1")) {
        SUPABASE_URL.removeSuffix("/rest/v1")
    } else {
        SUPABASE_URL
    }

    val client by lazy {
        createSupabaseClient(
            supabaseUrl = sanitizedUrl,
            supabaseKey = SUPABASE_ANON_KEY
        ) {
            install(Postgrest)
            install(Auth)
        }
    }
}
