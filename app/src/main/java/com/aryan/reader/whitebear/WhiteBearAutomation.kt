package com.aryan.reader.whitebear

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The token gate for the 保存復元 automation contract — the sister-app pattern
 * (renrakusaki, 自由作業盤): a master switch that is OFF until 白い熊 turns it on, plus a
 * random token that a caller must present on every request.
 *
 * The prefs file lives outside every [WhiteBearExport.Cat], so the token can never travel
 * inside a backup ZIP.
 */
object WhiteBearAutomation {

    private const val PREFS = "whitebear_automation"
    private const val KEY_ENABLED = "automation_enabled"
    private const val KEY_TOKEN = "automation_token"

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) token(context)
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

    /** Constant-time comparison, so a wrong token leaks nothing by how long it took. */
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
