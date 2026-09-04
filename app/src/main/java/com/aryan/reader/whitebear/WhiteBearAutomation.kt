package com.aryan.reader.whitebear

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The gate for the 保存復元 automation contract — one switch, and a token that is only asked
 * for when 白い熊 asks for it.
 *
 * ## What changed in contract v2 (2026-09-04)
 *
 * v1 shipped this app **closed**: the switch was off until 白い熊 turned it on, and a caller
 * also had to present a 48-character secret pasted out of the settings page below. That is the
 * wrong shape for what this now exists to serve. **A pasted secret cannot survive a wipe**, and
 * the case the family is being built for is 応用管理 restoring apps *and their data* onto a
 * clean phone, where nothing has been configured and nobody has pasted anything. A gate that
 * only works once the phone is already set up is no gate for setting the phone up.
 *
 * So the switch defaults **on**, the token defaults **off**, and what protects the half of the
 * surface that can actually move data — the provider in `automation/` — is the caller's
 * identity and pinned signature rather than a shared secret.
 *
 * The token stays, because closing one app off has to remain possible and a feature that can be
 * turned on but never off is one 白い熊 cannot retreat from.
 *
 * The prefs file lives outside every [WhiteBearExport.Cat], so the token can never travel
 * inside a backup ZIP.
 */
object WhiteBearAutomation {

    private const val PREFS = "whitebear_automation"
    private const val KEY_ENABLED = "automation_enabled"
    private const val KEY_REQUIRE_TOKEN = "automation_require_token"
    private const val KEY_TOKEN = "automation_token"

    /**
     * Default **on** (v2). Out of the box this app answers the batch and the data door, which is
     * the only way a freshly restored phone can put anything back.
     */
    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** Default **off** (v2) — a caller is identified, not asked for a password. */
    fun requiresToken(context: Context): Boolean =
        prefs(context).getBoolean(KEY_REQUIRE_TOKEN, false)

    fun setRequiresToken(context: Context, required: Boolean) {
        prefs(context).edit().putBoolean(KEY_REQUIRE_TOKEN, required).apply()
        if (required) token(context)
    }

    /**
     * The one place both checks live. `null` = proceed; anything else is the exact `ERROR:`
     * line to answer with.
     *
     * Written once rather than at each entry point because two copies of "disabled" and "bad
     * token" is precisely how the two drift apart across forty-two apps — and they are reported
     * as distinct errors on purpose, since they debug differently.
     */
    fun refuse(context: Context, candidate: String?): String? = when {
        !isEnabled(context) -> "ERROR:automation disabled"
        requiresToken(context) && !matches(context, candidate) -> "ERROR:bad token"
        else -> null
    }

    /** The token, generated on first read so the settings row always shows a value. */
    @Synchronized
    fun token(context: Context): String {
        val prefs = prefs(context)
        prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val fresh = newToken()
        prefs.edit().putString(KEY_TOKEN, fresh).apply()
        return fresh
    }

    /** Replaces the token — every pasted copy elsewhere stops working. */
    @Synchronized
    fun regenerate(context: Context): String {
        val fresh = newToken()
        prefs(context).edit().putString(KEY_TOKEN, fresh).apply()
        return fresh
    }

    /**
     * Constant-time comparison, so a wrong token leaks nothing by how long it took.
     *
     * Only ever reached through [refuse] when the token is actually being asked for. **A token
     * handed to this app while [requiresToken] is off is ignored, never refused** — tokens live
     * in task arguments and workspace variables that outlive the setting they were pasted for,
     * and a caller still sending one, because it was configured last year or because another
     * app on the batch does want one, must be served. Refusing it would turn "白い熊 turned a
     * switch off" into "half the batch mysteriously fails", which is the exact friction the
     * switch exists to remove.
     */
    fun matches(context: Context, candidate: String?): Boolean {
        if (candidate.isNullOrEmpty()) return false
        return MessageDigest.isEqual(candidate.toByteArray(), token(context).toByteArray())
    }

    /** `80922d8c…4c49a87c` — what the settings row shows instead of the full token. */
    fun abbreviated(token: String): String =
        if (token.length <= 20) token else token.take(8) + "…" + token.takeLast(8)

    private fun newToken(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
