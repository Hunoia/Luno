package hunoia.luno.domain.log

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import hunoia.luno.core.IncidentKind
import hunoia.luno.core.IncidentLog
import hunoia.luno.core.LunoLog

/**
 * debug-only：故障记录 / 通知探针，免去为了看一条错误去构造真实故障。
 *
 * ```
 * adb shell am broadcast -n hunoia.luno.debug/hunoia.luno.domain.log.IncidentProbeReceiver \
 *     -a hunoia.luno.debug.INCIDENT_PROBE --es mode error|crash|anr|native
 * ```
 */
class IncidentProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getStringExtra("mode")) {
            "crash" -> IncidentLog.recordCrash(
                IllegalStateException("probe crash"),
                Thread.currentThread(),
            )
            "anr" -> IncidentLog.recordSystem(
                IncidentKind.ANR,
                "IncidentProbe",
                "probe ANR",
                "probe anr trace",
            )
            "native" -> IncidentLog.recordSystem(
                IncidentKind.NATIVE,
                "IncidentProbe",
                "probe native crash",
                "probe native trace",
            )
            else -> LunoLog.e(
                "IncidentProbe",
                "probe error",
                IllegalStateException("probe error"),
            )
        }
    }
}
