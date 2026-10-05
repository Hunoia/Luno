package hunoia.luno.bridge.ui

import android.os.Looper

fun isMainThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()
