package com.ola.espcamdrive

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/** Połączenia do kamery idą przez WiFi, nawet gdy sieć WiFi kamery nie ma internetu. */
object Net {
    fun openLocal(url: URL): HttpURLConnection {
        val cm = EspApp.instance.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifi = cm.allNetworks.firstOrNull {
            cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        return (wifi?.openConnection(url) ?: url.openConnection()) as HttpURLConnection
    }
}

/**
 * Zakłada standardowy szkic Arduino "CameraWebServer":
 *   zdjęcie:  http://IP/capture   (port 80)
 *   strumień: http://IP:81/stream (MJPEG)
 */
class CameraClient(private val host: String) {
    private val base: String
        get() {
            var h = host.trim()
            for (p in listOf("http://", "https://")) if (h.startsWith(p)) h = h.removePrefix(p)
            return h.trimEnd('/')
        }
    private val hostOnly: String get() = base.split(":").firstOrNull().orEmpty()

    val captureURL: URL? get() = runCatching { URL("http://$base/capture") }.getOrNull()
    val streamURL: URL? get() = runCatching { URL("http://$hostOnly:81/stream") }.getOrNull()

    suspend fun captureJPEG(timeoutSec: Int = 10): ByteArray = withContext(Dispatchers.IO) {
        val url = captureURL ?: throw AppError("Nieprawidłowy adres kamery")
        val conn = Net.openLocal(url)
        try {
            conn.connectTimeout = timeoutSec * 1000
            conn.readTimeout = timeoutSec * 1000
            conn.useCaches = false
            val code = conn.responseCode
            val data = if (code == 200) conn.inputStream.use { it.readBytes() } else ByteArray(0)
            if (code != 200 || data.size <= 1000 ||
                data[0] != 0xFF.toByte() || data[1] != 0xD8.toByte()
            ) throw AppError("Kamera nie zwróciła zdjęcia (sprawdź adres IP)")
            data
        } finally {
            conn.disconnect()
        }
    }
}

/** Bezpieczna dla wątków flaga "zakończ nagrywanie". */
class StopFlag {
    private val v = AtomicBoolean(false)
    val isSet: Boolean get() = v.get()
    fun set() = v.set(true)
}

/**
 * Nagrywa strumień MJPEG z ESP32-CAM do pliku MP4 (H.264).
 * Kończy się, gdy ustawisz `stop` albo gdy minie `maxSeconds` (zabezpieczenie).
 */
object MJPEGRecorder {
    suspend fun record(
        camera: CameraClient,
        maxSeconds: Double = 3600.0,
        stop: StopFlag,
        to: File,
    ) = withContext(Dispatchers.IO) {
        val streamURL = camera.streamURL ?: throw AppError("Nieprawidłowy adres kamery")
        val conn = Net.openLocal(streamURL)
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        var encoder: Mp4Encoder? = null
        try {
            if (conn.responseCode != 200) {
                throw AppError("Nie można otworzyć strumienia (czy inne urządzenie go nie ogląda?)")
            }
            to.delete()

            val start = System.nanoTime()
            var frames = 0
            val buf = ByteArrayOutputStream(120_000)
            var inFrame = false
            var prev = 0
            BufferedInputStream(conn.inputStream, 64 * 1024).use { input ->
                while (true) {
                    val b = input.read()
                    if (b < 0) break
                    if (inFrame) {
                        buf.write(b)
                        if (prev == 0xFF && b == 0xD9) {                 // koniec klatki JPEG
                            inFrame = false
                            val t = (System.nanoTime() - start) / 1e9
                            val bytes = buf.toByteArray()
                            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (bmp != null) {
                                val enc = encoder ?: Mp4Encoder(to, bmp.width, bmp.height).also { encoder = it }
                                enc.addFrame(bmp)
                                frames++
                                bmp.recycle()
                            }
                            buf.reset()
                            if (stop.isSet || t >= maxSeconds) break
                        }
                    } else if (prev == 0xFF && b == 0xD8) {              // początek klatki JPEG
                        inFrame = true
                        buf.reset()
                        buf.write(0xFF); buf.write(0xD8)
                    }
                    prev = b
                }
            }
            val enc = encoder
            if (enc == null || frames == 0) throw AppError("Nie odebrano żadnych klatek ze strumienia")
            encoder = null
            if (!enc.finish()) throw AppError("Nie udało się zapisać filmu")
        } finally {
            encoder?.abort()
            conn.disconnect()
        }
    }
}

/** Koder H.264 z wejściem przez Surface; klatki dostają znaczniki czasu rzeczywistego. */
private class Mp4Encoder(file: File, srcW: Int, srcH: Int) {
    private val w = srcW and 1.inv()
    private val h = srcH and 1.inv()
    private val codec: MediaCodec
    private val surface: android.view.Surface
    private val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val info = MediaCodec.BufferInfo()
    private val dst = Rect(0, 0, w, h)
    private var track = -1
    private var muxerStarted = false
    private var firstPts = -1L
    private var written = 0

    init {
        val fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, (w * h * 4).coerceIn(1_000_000, 8_000_000))
            setInteger(MediaFormat.KEY_FRAME_RATE, 20)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        surface = codec.createInputSurface()
        codec.start()
    }

    fun addFrame(bmp: Bitmap) {
        val canvas = surface.lockCanvas(null)
        canvas.drawBitmap(bmp, null, dst, null)
        surface.unlockCanvasAndPost(canvas)
        drain(false)
    }

    /** Zamyka plik. Zwraca false, gdy nic nie zapisano. */
    fun finish(): Boolean {
        codec.signalEndOfInputStream()
        drain(true)
        release()
        return written > 0
    }

    fun abort() = release()

    private fun release() {
        runCatching { codec.stop() }
        runCatching { codec.release() }
        runCatching { surface.release() }
        runCatching { if (muxerStarted) muxer.stop() }
        runCatching { muxer.release() }
        muxerStarted = false
    }

    private fun drain(untilEnd: Boolean) {
        var idle = 0
        while (true) {
            val i = codec.dequeueOutputBuffer(info, if (untilEnd) 10_000 else 0)
            when {
                i == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!untilEnd || ++idle > 300) return
                }
                i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }
                i >= 0 -> {
                    val data = codec.getOutputBuffer(i)
                    val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (data != null && info.size > 0 && !isConfig && muxerStarted) {
                        if (firstPts < 0) firstPts = info.presentationTimeUs
                        info.presentationTimeUs -= firstPts
                        data.position(info.offset)
                        data.limit(info.offset + info.size)
                        muxer.writeSampleData(track, data, info)
                        written++
                    }
                    codec.releaseOutputBuffer(i, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }
}
