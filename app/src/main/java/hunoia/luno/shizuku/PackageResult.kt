package hunoia.luno.shizuku

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PackageResult(
    val success: Boolean,
    val packageName: String = "",
    val errorMessage: String = "",
) : Parcelable
