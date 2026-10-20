package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import moe.shizuku.manager.utils.BubbleHelper

class BubbleTriggerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_OPEN_BUBBLE) {
            val pendingResult = goAsync()
            
            // Quit the app completely (remove from recents) to prevent conflicts before spawning the bubble
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            for (appTask in am.appTasks) {
                try {
                    appTask.finishAndRemoveTask()
                } catch (e: Exception) {}
            }
            
            // Give the OS a tiny moment to flush the task before triggering the bubble
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                BubbleHelper.displayBubble(context)
                pendingResult.finish()
            }, 800)
        } else if (intent?.action == ACTION_DISMISS_BUBBLE) {
            BubbleHelper.dismissBubble(context)
        }
    }

    companion object {
        const val ACTION_OPEN_BUBBLE = "moe.shizuku.manager.action.OPEN_BUBBLE"
        const val ACTION_DISMISS_BUBBLE = "moe.shizuku.manager.action.DISMISS_BUBBLE"
    }
}
