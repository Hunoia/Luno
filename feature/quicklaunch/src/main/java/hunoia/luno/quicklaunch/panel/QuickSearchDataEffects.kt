package hunoia.luno.quicklaunch.panel
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import hunoia.luno.config.model.QuickSearchSettings
import hunoia.luno.quicklaunch.query.QuickSearchIndex
import hunoia.luno.quicklaunch.query.ensureCalendar
import hunoia.luno.quicklaunch.query.ensureCalls
import hunoia.luno.quicklaunch.query.ensureContacts
import hunoia.luno.quicklaunch.query.ensureMedia
import hunoia.luno.quicklaunch.query.ensureSms
import hunoia.luno.quicklaunch.query.invalidateToggleState
import hunoia.luno.quicklaunch.query.refreshPermissionCache
import hunoia.luno.ui.permission.PermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 数据装载 tick：联系人/媒体/日历/短信/通话各自 IO 装载完成时 +1，根组合把它们合成
 * dataVersion 重建搜索流；权限变化走 [permTick]，快照刷新回执走 [permCacheTick]。
 */
internal class QuickSearchDataTicks {
    var permTick by mutableIntStateOf(0)
    var contactsTick by mutableIntStateOf(0)
    var mediaTick by mutableIntStateOf(0)
    var calendarTick by mutableIntStateOf(0)
    var smsTick by mutableIntStateOf(0)
    var callsTick by mutableIntStateOf(0)
    var permCacheTick by mutableIntStateOf(0)
}

/** 媒体/文件权限快照：授权行显隐与媒体装载效果共用。 */
internal class QuickSearchMediaGrants(
    val audio: Boolean,
    val images: Boolean,
    val video: Boolean,
    val files: Boolean,
    val mediaDeniedForever: Boolean,
)

/**
 * 权限快照随 permTick 重查；「永久拒绝」也按 permTick remember —— 权限结果回调和
 * ON_RESUME 都会 bump permTick，弹框后状态仍会刷新。否则每次打字重组都会打
 * 3 次 shouldShowRequestPermissionRationale 的 binder IPC。
 */
@Composable
internal fun rememberMediaGrants(
    context: Context,
    activity: Activity?,
    permTick: Int,
): QuickSearchMediaGrants {
    val snapshot = remember(permTick) {
        fun granted(p: String) =
            ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        QuickSearchMediaGrants(
            audio = granted(android.Manifest.permission.READ_MEDIA_AUDIO),
            images = granted(android.Manifest.permission.READ_MEDIA_IMAGES),
            video = granted(android.Manifest.permission.READ_MEDIA_VIDEO),
            // all-files 不是运行时权限，单独查。
            files = android.os.Environment.isExternalStorageManager(),
            mediaDeniedForever = false,
        )
    }
    if (snapshot.audio || snapshot.images || snapshot.video) return snapshot
    // 三个全灭且都不再弹系统框 = 永久拒绝，授权行改跳设置页。
    val mediaDeniedForever = remember(permTick, activity) {
        fun rationale(p: String) =
            activity?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, p) } ?: false
        !rationale(android.Manifest.permission.READ_MEDIA_AUDIO) &&
            !rationale(android.Manifest.permission.READ_MEDIA_IMAGES) &&
            !rationale(android.Manifest.permission.READ_MEDIA_VIDEO)
    }
    return QuickSearchMediaGrants(
        audio = snapshot.audio,
        images = snapshot.images,
        video = snapshot.video,
        files = snapshot.files,
        mediaDeniedForever = mediaDeniedForever,
    )
}

/**
 * 联系人/媒体/日历/短信/通话的数据装载与权限快照刷新效果：授权后 IO 线程一次性进内存，
 * 打字只查内存；加载完成 bump tick 触发结果重算。
 */
@Composable
internal fun QuickSearchDataEffects(
    index: QuickSearchIndex,
    settings: QuickSearchSettings,
    ticks: QuickSearchDataTicks,
    grants: QuickSearchMediaGrants,
    contactsPerm: PermissionState,
    calendarPerm: PermissionState,
    smsPerm: PermissionState,
    callsPerm: PermissionState,
) {
    LaunchedEffect(contactsPerm.isGranted, settings.searchContacts) {
        if (contactsPerm.isGranted && settings.searchContacts) {
            withContext(Dispatchers.IO) { index.ensureContacts() }
            ticks.contactsTick++
        }
    }
    // resume tick 覆盖从设置页回来不重组的问题。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) ticks.permTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // P3 媒体权限：三个一次要（部分授权也认，有啥搜啥）。
    LaunchedEffect(
        grants.audio, grants.images, grants.video, grants.files,
        settings.searchMusic, settings.searchFiles,
    ) {
        if ((settings.searchMusic || settings.searchFiles) &&
            (grants.audio || grants.images || grants.video || grants.files)
        ) {
            withContext(Dispatchers.IO) { index.ensureMedia() }
            ticks.mediaTick++
        }
    }
    // 日历/短信/通话：单权限 + IO 一次加载 + tick 重算（联系人同款）。
    LaunchedEffect(calendarPerm.isGranted, settings.searchCalendar) {
        if (calendarPerm.isGranted && settings.searchCalendar) {
            withContext(Dispatchers.IO) { index.ensureCalendar() }
            ticks.calendarTick++
        }
    }
    LaunchedEffect(smsPerm.isGranted, settings.searchSms) {
        if (smsPerm.isGranted && settings.searchSms) {
            withContext(Dispatchers.IO) { index.ensureSms() }
            ticks.smsTick++
        }
    }
    LaunchedEffect(callsPerm.isGranted, settings.searchCalls) {
        if (callsPerm.isGranted && settings.searchCalls) {
            withContext(Dispatchers.IO) { index.ensureCalls() }
            ticks.callsTick++
        }
    }
    // 权限快照 + 开关状态缓存：面板每次打开都重新读一次真实值（打开期间各 tick 变化也刷）。
    LaunchedEffect(
        ticks.permTick, contactsPerm.isGranted, calendarPerm.isGranted, smsPerm.isGranted, callsPerm.isGranted,
    ) {
        withContext(Dispatchers.IO) {
            index.refreshPermissionCache()
            invalidateToggleState()
        }
        ticks.permCacheTick++
    }
}
