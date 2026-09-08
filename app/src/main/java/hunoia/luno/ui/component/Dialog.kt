package hunoia.luno.ui.component
import hunoia.luno.ui.theme.*

import android.view.Gravity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.aaron.compose.ktx.clipToBackground
import com.aaron.compose.ktx.onSingleClick
import com.aaron.compose.utils.SystemFontScaleHandler
import hunoia.luno.R
import hunoia.luno.action.api.ActionFacade
import hunoia.luno.config.defaults.SettingsUiDefaults.DimAlpha
import hunoia.luno.config.model.Action
import hunoia.luno.ui.component.actionText
import hunoia.luno.core.Events
import hunoia.luno.bridge.WallpaperChangedEvent
import hunoia.luno.config.model.ThemeColorKey
import hunoia.luno.ui.theme.resolveColor
import hunoia.luno.ui.theme.DialogHexTextWidth
import hunoia.luno.ui.theme.DialogTitlePadding
import hunoia.luno.ui.theme.ItemPadding
import hunoia.luno.ui.theme.SubMinInteractiveSize
import hunoia.luno.ui.component.displayNameRes
import hunoia.luno.bridge.DensityProvider
import hunoia.luno.ui.component.displayNameRes



@Composable
fun MyAlertDialog(
    onDismissRequest: () -> Unit,
    onConfirmClick: () -> Unit,
    title: String?,
    text: String,
    onCancelClick: (() -> Unit)? = null,
    autoDismissWhenClick: Boolean = true
) {
    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surface,
        onDismissRequest = onDismissRequest,
        title = {
            if (!title.isNullOrEmpty()) {
                Text(text = title)
            }
        },
        text = {
            Text(text = text)
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (autoDismissWhenClick) {
                        onDismissRequest()
                    }
                    onConfirmClick()
                }
            ) {
                Text(text = stringResource(id = R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (autoDismissWhenClick) {
                        onDismissRequest()
                    }
                    onCancelClick?.invoke()
                }
            ) {
                Text(text = stringResource(id = R.string.cancel))
            }
        }
    )
}





