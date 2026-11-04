package hunoia.luno.quicklaunch

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/** 动作入口：只使用可用的系统识别弹窗，不创建 SpeechRecognizer。 */
class SpeechRecognitionActivity : ComponentActivity() {
    private val recognizer = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            if (text == null) {
                finish()
            } else {
                val resultIntent = Intent(ACTION_SPEECH_RESULT).apply {
                    setPackage(packageName)
                    putExtra(EXTRA_TEXT, text)
                }
                finish()
                // 先让识别 Activity 完全退出，原页面恢复焦点后再扫描输入框。
                Handler(Looper.getMainLooper()).postDelayed(
                    { applicationContext.sendBroadcast(resultIntent) },
                    250L,
                )
            }
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recognizer.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        })
    }

    companion object {
        const val ACTION_SPEECH_RESULT = "hunoia.luno.action.SPEECH_RESULT"
        const val EXTRA_TEXT = "text"
        fun start(context: Context) {
            context.startActivity(Intent(context, SpeechRecognitionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}
