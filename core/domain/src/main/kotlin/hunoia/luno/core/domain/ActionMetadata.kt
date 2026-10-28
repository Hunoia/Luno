package hunoia.luno.core.domain

/**
 * 动作的纯契约元数据：执行口做参数校验与能力闸所需的最小信息。
 *
 * 这是 ADR-0001 D4 的「纯契约 / 展示投影」二分之一：
 * - `ActionMetadata`（本接口，`:core:domain`）：能力需求、模板排除键、必填文本键；
 * - `ActionDefinition`（`:feature:actions`）：名称、图标、参数编辑器等展示投影。
 *
 * 执行口只读本接口；展示层不得把 Compose / 资源类型泄漏进契约。
 */
interface ActionMetadata {
    /** 动作声明的能力需求；`Capability.None` 表示无需额外能力。 */
    val capability: Capability

    /** 不做模板展开的参数键（坐标、正则等原样落库的参数）。 */
    val excludedTemplateKeys: Set<String>

    /** 必填文本参数键：模板展开后为空即判参数无效。 */
    val requiredTextKeys: Set<String>
}
