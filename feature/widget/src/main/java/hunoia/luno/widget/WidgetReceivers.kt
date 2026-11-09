package hunoia.luno.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** 桌面部件接收器的共同生命周期：每次系统更新 / 首次启用时拉起实时同步。 */
abstract class SyncWidgetReceiver : GlanceAppWidgetReceiver() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetSync.ensureStarted(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetSync.ensureStarted(context)
    }
}

/** 习惯桌面小部件接收器：Glance 自动处理更新，仅负责拉起实时同步。 */
class HabitWidgetReceiver : SyncWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = HabitWidget()
}

/** 待办桌面小部件接收器。 */
class TodoWidgetReceiver : SyncWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = TodoWidget()
}
