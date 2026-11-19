package hunoia.luno.ui.shortcut

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import hunoia.luno.ui.MainActivity

/**
 * 临时默认桌面的 HOME 入口。
 *
 * 系统把「动态快捷方式」的读取 / pin 权限只给默认桌面，所以导入页会引导用户临时选它作为桌面。
 * 用户按 Home 落在这里时，不做任何桌面功能：登记一次「打开导入页」请求并回到 Luno 主界面，
 * 由导航侧消费后跳活动快捷方式页，方便导入完再切回原桌面。
 *
 * 不自动切回原桌面（读取默认桌面需要额外权限）；切回动作由系统选择器或用户在系统设置里完成。
 */
class ShortcutImportHomeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
                putExtra(EXTRA_OPEN_DYNAMIC_SHORTCUTS, true)
            },
        )
        finish()
    }

    companion object {
        const val EXTRA_OPEN_DYNAMIC_SHORTCUTS = "hunoia.luno.extra.OPEN_DYNAMIC_SHORTCUTS"
    }
}
