package com.aryan.reader.whitebear

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * The 保存復元 automation endpoint — 自由作業盤 fires a token-gated broadcast, this app
 * exports itself headlessly and answers with the written path and size.
 *
 * `<pkg>.action.EXPORT_STATE` — run the ordinary Export/Import export with no UI, into the
 * `path` directory when one is given, otherwise into the configured export directory.
 * `<pkg>.action.LIST_CATEGORIES` — answer with the selectable categories.
 *
 * **This receiver never exports.** It checks the switch and the token, validates `items` so a
 * bad request is refused before anything is written, hands the work to [StateExportService]
 * and returns at once — a manifest receiver has ~10 s in the foreground and ~60 s otherwise,
 * `goAsync()` does not extend that, and overrunning it means an ANR that kills the process
 * mid-export (see the header of [StateExportService]). Everything slow, and the one terminal
 * reply that follows it, belongs to the service.
 *
 * The ordered-broadcast result is set when there is one — correct AOSP behaviour — but is
 * never the only reply: EMUI severs that channel between third-party apps.
 */
class StateExportReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val action = intent.action ?: return
        val replyAction = intent.getStringExtra(AutomationWire.EXTRA_REPLY_ACTION)?.trim().orEmpty()
        val replyPackage = intent.getStringExtra(AutomationWire.EXTRA_REPLY_PACKAGE)?.trim().orEmpty()
        val replyId = intent.getStringExtra(AutomationWire.EXTRA_REPLY_ID)?.trim().orEmpty()

        fun reply(result: String) {
            if (replyAction.isNotEmpty() && replyPackage.isNotEmpty()) {
                AutomationWire.sendReply(app, replyAction, replyPackage, replyId, result)
            }
            if (isOrderedBroadcast) runCatching { setResult(Activity.RESULT_OK, result, null) }
        }

        if (replyAction.isEmpty() || replyPackage.isEmpty() || replyId.isEmpty()) {
            Log.w(AutomationWire.TAG, "$action without reply_action/reply_package/reply_id — ignored")
            reply("ERROR:missing reply_action/reply_package/reply_id")
            return
        }
        if (!WhiteBearAutomation.isEnabled(app)) {
            reply("ERROR:automation disabled")
            return
        }
        if (!WhiteBearAutomation.matches(app, intent.getStringExtra(AutomationWire.EXTRA_TOKEN))) {
            reply("ERROR:bad token")
            return
        }

        when (action) {
            "${app.packageName}.action.LIST_CATEGORIES" ->
                reply("OK:" + WhiteBearExport.categoryLines())

            "${app.packageName}.action.EXPORT_STATE" -> {
                // Refused here, so an unknown id costs nothing and writes nothing.
                AutomationWire.resolveCategories(intent.getStringExtra(AutomationWire.EXTRA_ITEMS))
                    .onFailure { reply(it.message ?: "ERROR:bad items"); return }
                val started = runCatching {
                    ContextCompat.startForegroundService(app, StateExportService.intentFor(app, intent))
                }
                // The service owns the terminal reply from here on — unless it never started,
                // which must not leave the batch waiting for a reply that cannot come.
                started.onFailure { error ->
                    Log.e(AutomationWire.TAG, "cannot start the export service", error)
                    reply("ERROR:cannot start the export service: ${error.javaClass.simpleName}")
                }
            }

            else -> reply("ERROR:unknown action $action")
        }
    }
}
