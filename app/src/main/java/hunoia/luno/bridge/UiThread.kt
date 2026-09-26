package hunoia.luno.bridge

import android.os.Looper

fun isMainThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()
