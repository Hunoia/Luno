package hunoia.luno.quicklaunch.icon

import coil.key.Keyer
import coil.request.Options

/**
 * 快速启动器图标的 Coil 数据模型。
 *
 * 之前图标走 `PackageManager.getApplicationIcon` 的原生 Drawable，自己在
 * `IconResizeCache` 里再缓存一份。现在改为把 packageName 交给 Coil，由 Coil
 * 按目标像素出图并缓存，原生尺寸的 Drawable 不再常驻。
 *
 * [sizePx] 是面板格子的实际渲染边长（像素）。放进 model 而不是让 Fetcher 去解析
 * `Options.size`，是为了不依赖 `coil.size.Dimension` 的内部形态 —— 该类型在不同
 * Coil 版本里只有 `Pixels` / `Undefined` 两种，读法不稳定。
 *
 * 调用方需保证它与 `ImageRequest.Builder.size()` 一致：请求尺寸决定 Coil 的缓存键，
 * [sizePx] 决定栅格化到什么大小，两者不一致会让键下的图尺寸错配。
 */
data class AppIconModel(val packageName: String, val sizePx: Int)

/**
 * 让 Coil 用包名做内存缓存键。
 *
 * 只返回包名即可 —— Coil 会把 size / precision / scale 等 extras 拼到键后面，
 * 所以不同像素尺寸的同一个应用是两条独立缓存，不会互相覆盖。
 *
 * 注意不要用 `ImageRequest.Builder.memoryCacheKey()` 直接写死包名：那个值会被
 * 原样当作完整键，候选行与网格两个尺寸就会互相污染。
 */
internal object AppIconKeyer : Keyer<AppIconModel> {
    override fun key(data: AppIconModel, options: Options): String = data.packageName
}