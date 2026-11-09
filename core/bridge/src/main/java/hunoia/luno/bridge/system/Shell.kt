package hunoia.luno.bridge.system

/**
 * 把参数包成 POSIX shell 单引号字面量：内部的 `'` 写成 `'\''`。
 *
 * 经 Shizuku 执行 `am` / `cmd` 时拼接的参数都走这里，避免空格、引号与换行被 shell
 * 当成语法。只做转义，不做白名单校验。
 */
fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
