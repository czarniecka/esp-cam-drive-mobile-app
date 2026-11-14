package com.ola.espcamdrive

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/**
 * Wysyłanie plików do Dysku Google (REST API v3, zakres drive.file).
 * Mutex: wiele wysyłek naraz (tryb cykliczny) nie zakłada duplikatów folderu.
 */
class DriveUploader(private val auth: GoogleAuth, private val scope: CoroutineScope) {
    private val mutex = Mutex()
    private val folderTasks = HashMap<String, Deferred<String>>()

    suspend fun upload(file: File, mime: String, folderName: String) {
        val folderID = folder(folderName)
        val token = auth.validAccessToken()

        withContext(Dispatchers.IO) {
            val boundary = UUID.randomUUID().toString()
            val meta = JSONObject()
                .put("name", file.name)
                .put("parents", JSONArray().put(folderID))
                .toString()

            val conn = URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                .openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 30_000
                conn.readTimeout = 60_000
                conn.setChunkedStreamingMode(256 * 1024)   // plik idzie strumieniem, film nie ląduje w pamięci
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
                conn.outputStream.use { out ->
                    out.write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray())
                    out.write(meta.toByteArray())
                    out.write("\r\n--$boundary\r\nContent-Type: $mime\r\n\r\n".toByteArray())
                    file.inputStream().use { it.copyTo(out, 1 shl 20) }
                    out.write("\r\n--$boundary--".toByteArray())
                }
                check(conn.responseCode, conn)
            } finally {
                conn.disconnect()
            }
        }
    }

    /** Wszystkie równoległe wysyłki czekają na to samo zadanie szukania/zakładania folderu. */
    private suspend fun folder(name: String): String {
        val task = mutex.withLock {
            folderTasks.getOrPut(name) { scope.async(Dispatchers.IO) { resolveFolder(name) } }
        }
        try {
            return task.await()
        } catch (e: Exception) {
            mutex.withLock { if (folderTasks[name] === task) folderTasks.remove(name) }
            throw e
        }
    }

    private suspend fun resolveFolder(name: String): String {
        val token = auth.validAccessToken()

        val escaped = name.replace("\\", "\\\\").replace("'", "\\'")
        val q = "name='$escaped' and mimeType='application/vnd.google-apps.folder' and trashed=false"
        val listURL = "https://www.googleapis.com/drive/v3/files?q=${URLEncoder.encode(q, "UTF-8")}" +
            "&fields=${URLEncoder.encode("files(id)", "UTF-8")}"
        val listConn = URL(listURL).openConnection() as HttpURLConnection
        val listBody = try {
            listConn.connectTimeout = 15_000
            listConn.readTimeout = 30_000
            listConn.setRequestProperty("Authorization", "Bearer $token")
            check(listConn.responseCode, listConn)
            listConn.inputStream.use { String(it.readBytes()) }
        } finally {
            listConn.disconnect()
        }
        JSONObject(listBody).getJSONArray("files").let { files ->
            if (files.length() > 0) return files.getJSONObject(0).getString("id")
        }

        val create = URL("https://www.googleapis.com/drive/v3/files?fields=id")
            .openConnection() as HttpURLConnection
        try {
            create.requestMethod = "POST"
            create.doOutput = true
            create.connectTimeout = 15_000
            create.readTimeout = 30_000
            create.setRequestProperty("Authorization", "Bearer $token")
            create.setRequestProperty("Content-Type", "application/json")
            create.outputStream.use {
                it.write(
                    JSONObject().put("name", name)
                        .put("mimeType", "application/vnd.google-apps.folder")
                        .toString().toByteArray()
                )
            }
            check(create.responseCode, create)
            return JSONObject(create.inputStream.use { String(it.readBytes()) }).getString("id")
        } finally {
            create.disconnect()
        }
    }

    private fun check(code: Int, conn: HttpURLConnection) {
        if (code !in 200..299) {
            val body = conn.errorStream?.use { String(it.readBytes()) } ?: ""
            throw AppError("Dysk Google ($code): ${body.take(200)}")
        }
    }
}
