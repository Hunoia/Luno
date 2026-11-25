package hunoia.luno.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.InternalMaterialKolorApi
import com.materialkolor.dynamiccolor.DynamicColor
import com.materialkolor.dynamiccolor.DynamicScheme
import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeTonalSpot
import hunoia.luno.config.model.AlertPalette
import hunoia.luno.config.model.BuiltinAlertPalettes
import hunoia.luno.config.model.ThemePaletteSetting
import hunoia.luno.config.model.rainbowRing

/**
 * 色板 → M3 `ColorScheme`：单 seed 走 HCT / tonal palette，再铺满全部语义角色。
 *
 * 色板第 1 色作 seed；彩虹色板没有单 seed（主题选择器会禁用），兜底取彩虹环首色，
 * 保证任何数据都不空主题。结果只依赖 seed 与明暗，可缓存。
 */
@OptIn(InternalMaterialKolorApi::class)
object LunoPaletteScheme {

    fun seedOf(palette: AlertPalette): Int {
        val rgb = palette.colors.firstOrNull()
            ?: palette.rainbowRing().firstOrNull()
            ?: 0xFF6750A4.toInt()
        return rgb or 0xFF000000.toInt()
    }

    fun schemeFor(palette: AlertPalette, dark: Boolean): ColorScheme =
        schemeForSeed(seedOf(palette), dark)

    fun schemeForSeed(seedArgb: Int, dark: Boolean): ColorScheme {
        val scheme = SchemeTonalSpot(
            sourceColorHct = Hct.fromInt(seedArgb or 0xFF000000.toInt()),
            isDark = dark,
            contrastLevel = 0.0,
            platform = DynamicScheme.Platform.PHONE,
        )
        val md = MaterialDynamicColors()
        fun c(color: DynamicColor): Color = Color(scheme.getArgb(color))

        return if (dark) {
            darkColorScheme(
                primary = c(md.primary),
                onPrimary = c(md.onPrimary),
                primaryContainer = c(md.primaryContainer),
                onPrimaryContainer = c(md.onPrimaryContainer),
                inversePrimary = c(md.inversePrimary),
                secondary = c(md.secondary),
                onSecondary = c(md.onSecondary),
                secondaryContainer = c(md.secondaryContainer),
                onSecondaryContainer = c(md.onSecondaryContainer),
                tertiary = c(md.tertiary),
                onTertiary = c(md.onTertiary),
                tertiaryContainer = c(md.tertiaryContainer),
                onTertiaryContainer = c(md.onTertiaryContainer),
                background = c(md.background),
                onBackground = c(md.onBackground),
                surface = c(md.surface),
                onSurface = c(md.onSurface),
                surfaceVariant = c(md.surfaceVariant),
                onSurfaceVariant = c(md.onSurfaceVariant),
                surfaceTint = c(md.surfaceTint),
                inverseSurface = c(md.inverseSurface),
                inverseOnSurface = c(md.inverseOnSurface),
                error = c(md.error),
                onError = c(md.onError),
                errorContainer = c(md.errorContainer),
                onErrorContainer = c(md.onErrorContainer),
                outline = c(md.outline),
                outlineVariant = c(md.outlineVariant),
                scrim = c(md.scrim),
                surfaceBright = c(md.surfaceBright),
                surfaceDim = c(md.surfaceDim),
                surfaceContainer = c(md.surfaceContainer),
                surfaceContainerHigh = c(md.surfaceContainerHigh),
                surfaceContainerHighest = c(md.surfaceContainerHighest),
                surfaceContainerLow = c(md.surfaceContainerLow),
                surfaceContainerLowest = c(md.surfaceContainerLowest),
                primaryFixed = c(md.primaryFixed),
                primaryFixedDim = c(md.primaryFixedDim),
                onPrimaryFixed = c(md.onPrimaryFixed),
                onPrimaryFixedVariant = c(md.onPrimaryFixedVariant),
                secondaryFixed = c(md.secondaryFixed),
                secondaryFixedDim = c(md.secondaryFixedDim),
                onSecondaryFixed = c(md.onSecondaryFixed),
                onSecondaryFixedVariant = c(md.onSecondaryFixedVariant),
                tertiaryFixed = c(md.tertiaryFixed),
                tertiaryFixedDim = c(md.tertiaryFixedDim),
                onTertiaryFixed = c(md.onTertiaryFixed),
                onTertiaryFixedVariant = c(md.onTertiaryFixedVariant),
            )
        } else {
            lightColorScheme(
                primary = c(md.primary),
                onPrimary = c(md.onPrimary),
                primaryContainer = c(md.primaryContainer),
                onPrimaryContainer = c(md.onPrimaryContainer),
                inversePrimary = c(md.inversePrimary),
                secondary = c(md.secondary),
                onSecondary = c(md.onSecondary),
                secondaryContainer = c(md.secondaryContainer),
                onSecondaryContainer = c(md.onSecondaryContainer),
                tertiary = c(md.tertiary),
                onTertiary = c(md.onTertiary),
                tertiaryContainer = c(md.tertiaryContainer),
                onTertiaryContainer = c(md.onTertiaryContainer),
                background = c(md.background),
                onBackground = c(md.onBackground),
                surface = c(md.surface),
                onSurface = c(md.onSurface),
                surfaceVariant = c(md.surfaceVariant),
                onSurfaceVariant = c(md.onSurfaceVariant),
                surfaceTint = c(md.surfaceTint),
                inverseSurface = c(md.inverseSurface),
                inverseOnSurface = c(md.inverseOnSurface),
                error = c(md.error),
                onError = c(md.onError),
                errorContainer = c(md.errorContainer),
                onErrorContainer = c(md.onErrorContainer),
                outline = c(md.outline),
                outlineVariant = c(md.outlineVariant),
                scrim = c(md.scrim),
                surfaceBright = c(md.surfaceBright),
                surfaceDim = c(md.surfaceDim),
                surfaceContainer = c(md.surfaceContainer),
                surfaceContainerHigh = c(md.surfaceContainerHigh),
                surfaceContainerHighest = c(md.surfaceContainerHighest),
                surfaceContainerLow = c(md.surfaceContainerLow),
                surfaceContainerLowest = c(md.surfaceContainerLowest),
                primaryFixed = c(md.primaryFixed),
                primaryFixedDim = c(md.primaryFixedDim),
                onPrimaryFixed = c(md.onPrimaryFixed),
                onPrimaryFixedVariant = c(md.onPrimaryFixedVariant),
                secondaryFixed = c(md.secondaryFixed),
                secondaryFixedDim = c(md.secondaryFixedDim),
                onSecondaryFixed = c(md.onSecondaryFixed),
                onSecondaryFixedVariant = c(md.onSecondaryFixedVariant),
                tertiaryFixed = c(md.tertiaryFixed),
                tertiaryFixedDim = c(md.tertiaryFixedDim),
                onTertiaryFixed = c(md.onTertiaryFixed),
                onTertiaryFixedVariant = c(md.onTertiaryFixedVariant),
            )
        }
    }

    /** 主题设置 → 实际色板；System / 找不到都返回 null（调用方走动态取色）。 */
    fun resolve(
        settings: ThemePaletteSetting,
        customPalettes: List<AlertPalette> = UiAppearance.customPalettes,
    ): AlertPalette? {
        if (settings.mode != hunoia.luno.config.model.ThemeMode.Palette) return null
        val id = settings.paletteId ?: return null
        return BuiltinAlertPalettes.builtin(id)
            ?: customPalettes.firstOrNull { it.id == id }
    }
}