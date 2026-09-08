package hunoia.luno.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring

/**
 * Material 3 Expressive (M3E) motion constants, inlined from the ExpressiveMotionTokens
 * that are marked internal in material3 1.4.0. Mirrors MotionScheme.expressive spring specs
 * so the app gets the same bouncy, springy feel without depending on the internal API.
 */
object ExpressiveMotion {

    // Default spatial spring
    val defaultSpatialStiffness = 380f
    val defaultSpatialDamping = 0.8f

    // Fast spatial spring
    val fastSpatialStiffness = 800f
    val fastSpatialDamping = 0.6f

    // Slow spatial spring
    val slowSpatialStiffness = 200f
    val slowSpatialDamping = 0.8f

    // Default effects spring
    val defaultEffectsStiffness = 1600f
    val defaultEffectsDamping = 1f

    // Fast effects spring
    val fastEffectsStiffness = 3800f
    val fastEffectsDamping = 1f

    // Slow effects spring
    val slowEffectsStiffness = 800f
    val slowEffectsDamping = 1f

    /** Default spatial spec: the "bread and butter" move, springy but composed. */
    fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = spring(
        stiffness = defaultSpatialStiffness,
        dampingRatio = defaultSpatialDamping,
    )

    /** Fast spatial spec: quick, snappy move for immediate feedback. */
    fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = spring(
        stiffness = fastSpatialStiffness,
        dampingRatio = fastSpatialDamping,
    )

    /** Slow spatial spec: entrance/large layout change, more deliberate. */
    fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = spring(
        stiffness = slowSpatialStiffness,
        dampingRatio = slowSpatialDamping,
    )

    /** Default effects spec: opacity/effects, crisp without bounciness. */
    fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = spring(
        stiffness = defaultEffectsStiffness,
        dampingRatio = defaultEffectsDamping,
    )

    /** Fast effects spec. */
    fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = spring(
        stiffness = fastEffectsStiffness,
        dampingRatio = fastEffectsDamping,
    )

    /** Slow effects spec. */
    fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = spring(
        stiffness = slowEffectsStiffness,
        dampingRatio = slowEffectsDamping,
    )
}
