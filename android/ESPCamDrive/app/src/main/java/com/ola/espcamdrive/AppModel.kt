package com.ola.espcamdrive

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class Item(
    val id: UUID = UUID.randomUUID(),
    val file: File,
    val isVideo: Boolean,
    val uploaded: Boolean = false,
    val thumb: Bitmap? = null,
)

/** Cały stan aplikacji. Żyje w obiekcie Application, więc pętla przetrwa zamknięcie ekranu. */
class AppModel(private val app: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Ogólne
    var items by mutableStateOf<List<Item>>(emptyList()); private set
    var status by mutableStateOf("Skonfiguruj kamerę i konto Google (ikona ⚙︎)"); private set
    var statusIsError by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var recording by mutableStateOf(false); private set
    var elapsed by mutableStateOf(0); private set
    var lastImage by mutableStateOf<Bitmap?>(null); private set

    // Tryb cykliczny
    var autoRunning by mutableStateOf(false); private set
    var autoStart by mutableStateOf<Long?>(null); private set
    var autoEnd by mutableStateOf<Long?>(null); private set
    var captured by mutableStateOf(0); private set
    var uploaded by mutableStateOf(0); private set
    var failed by mutableStateOf(0); private set
    var pending by mutableStateOf(0); private set

    val auth = GoogleAuth(app)
    private val drive = DriveUploader(auth, scope)
    private var autoJob: Job? = null
    private var stopFlag: StopFlag? = null
    private var cameraErrors = 0
    private var uploadErrorShown = false
    private var runningText = ""

    private fun setStatus(text: String, error: Boolean = false) {
        status = text
        statusIsError = error
    }

    // region Google

    suspend fun signIn(activity: Activity) {
        try {
            auth.signIn(activity)
            setStatus("Zalogowano do Google")
        } catch (e: Exception) {
            setStatus("Logowanie: ${e.pl}", error = true)
        }
    }

    // endregion

    // region Zdjęcie / film (ręcznie)

    suspend fun photo(host: String, autoUpload: Boolean, folder: String) {
        if (busy) return
        busy = true
        try {
            setStatus("Pobieram zdjęcie…")
            val data = CameraClient(host).captureJPEG()
            val file = newFile("IMG", "jpg")
            withContext(Dispatchers.IO) { file.writeBytes(data) }
            lastImage = decode(data, 800)
            val item = Item(file = file, isVideo = false, thumb = decode(data, 120))
            insert(item)
            setStatus("Zdjęcie zapisane")
            if (autoUpload) uploadItem(item.id, folder)
        } catch (e: Exception) {
            setStatus("Błąd: ${e.pl}", error = true)
        } finally {
            busy = false
        }
    }

    /** Nagrywa, dopóki nie wywołasz stopRecording(). */
    suspend fun startRecording(host: String, autoUpload: Boolean, folder: String) {
        if (busy) return
        busy = true
        recording = true
        elapsed = 0
        val flag = StopFlag()
        stopFlag = flag
        val started = System.currentTimeMillis()
        val ticker = scope.launch {
            while (isActive) {
                delay(1000)
                elapsed = ((System.currentTimeMillis() - started) / 1000).toInt()
            }
        }
        setStatus("Nagrywam… (naciśnij Zakończ)")
        try {
            val file = newFile("VID", "mp4")
            MJPEGRecorder.record(CameraClient(host), stop = flag, to = file)
            ticker.cancel()
            recording = false
            stopFlag = null
            val item = Item(file = file, isVideo = true)
            insert(item)
            setStatus("Film zapisany")
            if (autoUpload) uploadItem(item.id, folder)
        } catch (e: Exception) {
            ticker.cancel()
            recording = false
            stopFlag = null
            setStatus("Błąd: ${e.pl}", error = true)
        } finally {
            busy = false
        }
    }

    fun stopRecording() {
        stopFlag?.set()
        setStatus("Kończę nagrywanie…")
    }

    private fun insert(item: Item) {
        items = (listOf(item) + items).take(50)
    }

    // endregion

    // region Wysyłka (ręczna)

    suspend fun uploadItem(id: UUID, folder: String) {
        val item = items.firstOrNull { it.id == id } ?: return
        setStatus("Wysyłam ${item.file.name}…")
        try {
            drive.upload(item.file, if (item.isVideo) "video/mp4" else "image/jpeg", folder)
            items = items.map { if (it.id == id) it.copy(uploaded = true) else it }
            setStatus("Wysłano na Dysk: ${item.file.name}")
        } catch (e: Exception) {
            setStatus("Błąd wysyłki: ${e.pl}", error = true)
        }
    }

    // endregion

    // region Tryb cykliczny

    /**
     * Zdjęcia robione ze stałą częstotliwością. Wysyłka działa równolegle w tle,
     * więc wolne łącze nie opóźnia kolejnych zdjęć.
     */
    fun startAuto(host: String, interval: Double, folder: String, deleteAfter: Boolean) {
        if (autoJob != null) return
        captured = 0; uploaded = 0; failed = 0; pending = 0
        cameraErrors = 0
        uploadErrorShown = false
        autoStart = System.currentTimeMillis()
        autoEnd = null
        autoRunning = true
        runningText = "Cykl działa: zdjęcie co ${formatInterval(interval)}"

        KeepAliveService.start(app, runningText)   // usługa w tle utrzymuje aplikację przy życiu po zgaszeniu ekranu
        setStatus(runningText)

        val camera = CameraClient(host)
        autoJob = scope.launch {
            val step = (interval * 1000).toLong().coerceAtLeast(1)
            var next = System.nanoTime()
            while (isActive) {
                next += step * 1_000_000
                autoShot(camera, folder, deleteAfter)
                val now = System.nanoTime()
                if (now > next) next = now   // spóźnione klatki pomijamy, nie nadganiamy
                delay((next - now) / 1_000_000)
            }
        }
    }

    fun stopAuto() {
        autoJob?.cancel()
        autoJob = null
        autoRunning = false
        autoEnd = System.currentTimeMillis()
        KeepAliveService.stop(app)
        setStatus("Zatrzymano")
    }

    private suspend fun autoShot(camera: CameraClient, folder: String, deleteAfter: Boolean) {
        try {
            val data = camera.captureJPEG(timeoutSec = 5)
            if (!autoRunning) return
            val file = newFile("IMG", "jpg")
            withContext(Dispatchers.IO) { file.writeBytes(data) }
            captured += 1
            lastImage = decode(data, 800)
            if (cameraErrors > 0) {
                cameraErrors = 0
                setStatus(runningText)
            }
            pending += 1
            scope.launch { autoUpload(file, folder, deleteAfter) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!autoRunning) return
            cameraErrors += 1
            failed += 1
            setStatus("Kamera nie odpowiada ($cameraErrors z rzędu): ${e.pl}", error = true)
        }
    }

    private suspend fun autoUpload(file: File, folder: String, deleteAfter: Boolean) {
        try {
            drive.upload(file, "image/jpeg", folder)
            uploaded += 1
            if (deleteAfter) file.delete()
            if (uploadErrorShown && cameraErrors == 0) {
                uploadErrorShown = false
                if (autoRunning) setStatus(runningText)
            }
        } catch (e: Exception) {
            // Plik zostaje na telefonie (Android/data/com.ola.espcamdrive/files/ESPCam).
            failed += 1
            uploadErrorShown = true
            setStatus("Błąd wysyłki: ${e.pl}", error = true)
        }
        pending -= 1
    }

    // endregion

    // region Pliki

    private val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)   // z milisekundami: przy 0,5 s nazwy muszą być unikalne

    private fun newFile(prefix: String, ext: String): File {
        val dir = File(app.getExternalFilesDir(null) ?: app.filesDir, "ESPCam").apply { mkdirs() }
        return File(dir, "${prefix}_${synchronized(stamp) { stamp.format(Date()) }}.$ext")
    }

    /** Dekoduje JPEG do miniatury o boku nie większym niż [max] px. */
    private suspend fun decode(data: ByteArray, max: Int): Bitmap? = withContext(Dispatchers.Default) {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, o)
        var s = 1
        while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= max) s *= 2
        BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = s })
    }

    // endregion
}
