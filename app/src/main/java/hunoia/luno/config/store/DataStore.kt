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



inline fun <reified T> Context.dataStore(fileName: String, defValue: T): DataStore<T> {
    val serializer = object : Serializer<T> {
        override val defaultValue: T = defValue

        override suspend fun readFrom(input: InputStream): T {
            val bytes = try { input.readBytes() } catch (_: Exception) { ByteArray(0) }
            return try {
                val string = bytes.decodeToString()
                if (string.isBlank()) {
                    Log.d("DataStore", "read $fileName: blank, using default")
                    return defaultValue
                }
                val result = JsonSerializer.decodeFromString<T>(string)
                // :service 常驻进程持有 10 个 DataStore，每次读都打 INFO + 拼字符串，
                // logcat 落盘是实打实的 IO，用 Log.d 让 logcat 按级别过滤掉。
                Log.d("DataStore", "read $fileName: success size=${bytes.size}")
                result
            } catch (e: Exception) {
                Log.e("DataStore", "read $fileName failed: ${e::class.simpleName} ${e.message} size=${bytes.size}")
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
        File("${filesDir.absolutePath}/ds/$fileName")
    }
}
