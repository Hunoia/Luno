package hunoia.luno.ui.theme

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/**
 * 形状软度画廊的真机入口（**仅 debug 变体**）。
 *
 * ```
 * adb shell am start -n hunoia.luno.debug/hunoia.luno.ui.theme.ShapeGalleryActivity
 * ```
 */
class ShapeGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ShapeGalleryScreen() }
    }
}
