package hunoia.luno.action.template

import hunoia.luno.config.model.ParamType

data class TemplateParam(
    val key: String,
    val label: String,
    val type: ParamType,
    val defaultValue: String,
    val options: List<String> = emptyList()
)