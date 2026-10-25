package hunoia.luno.config.store

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.core.Serializer
import hunoia.luno.core.JsonSerializer
import java.io.File
import java.io.InputStream
import java.io.OutputStream



/** DataStore 文件落点：唯一权威路径（BootReceiver 等同步读文件处共用）。 */
fun Context.dataStoreFile(fileName: String): File = File("${filesDir.absolutePath}/ds/$fileName")

inline fun <reified T> Context.dataStore(fileName: String, defValue: T): DataStore<T> {
    val targetFile = dataStoreFile(fileName)
    val serializer = object : Serializer<T> {
        override val defaultValue: T = defValue

        override suspend fun readFrom(input: InputStream): T {
            val bytes = try { input.readBytes() } catch (_: Exception) { ByteArray(0) }
            return try {
                val string = bytes.decodeToString()
                if (string.isBlank()) {
                    return defaultValue
                }
                JsonSerializer.decodeFromString<T>(string)
            } catch (e: Exception) {
                Log.e("DataStore", "read $fileName failed: ${e::class.simpleName} ${e.message} size=${bytes.size}")
                // 先留一份不可解析的原文件：直接回默认值的话，下一次任意 updateData 就会把
                // 默认值整份写回，原数据再无恢复可能。
                if (bytes.isNotEmpty()) {
                    runCatching {
                        targetFile.parentFile?.mkdirs()
                        File("${targetFile.absolutePath}.corrupt").writeBytes(bytes)
                    }
                }
                defaultValue
            }
        }

        override suspend fun writeTo(t: T, output: OutputStream) {
            try {
                val string = JsonSerializer.encodeToString(t)
                output.write(string.encodeToByteArray())
            } catch (e: Exception) {
                Log.e("DataStore", "write $fileName failed: ${e::class.simpleName} ${e.message}")
                throw e
            }
        }
    }
    return MultiProcessDataStoreFactory.create(serializer) {
        targetFile
    }
}
