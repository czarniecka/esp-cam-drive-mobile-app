package com.ola.espcamdrive

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CameraEnhance
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale

private val Green = Color(0xFF34C759)
private val Red = Color(0xFFFF3B30)
private val Orange = Color(0xFFFF9500)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        android.os.Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Ustawienia zapamiętywane między uruchomieniami (odpowiednik @AppStorage). */
private class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var host by mutableStateOf(p.getString("host", "") ?: "")
    var folder by mutableStateOf(p.getString("folder", "ESP32-CAM") ?: "ESP32-CAM")
    var autoMode by mutableStateOf(p.getBoolean("autoMode", false))
    var interval by mutableStateOf(p.getFloat("intervalSec", 1f).toDouble())
    var deleteAfter by mutableStateOf(p.getBoolean("deleteAfter", true))

    fun save() = p.edit()
        .putString("host", host).putString("folder", folder)
        .putBoolean("autoMode", autoMode).putFloat("intervalSec", interval.toFloat())
        .putBoolean("deleteAfter", deleteAfter).apply()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentScreen(model: AppModel, activity: android.app.Activity, ensureNotifications: () -> Unit) {
    val prefs = remember { Prefs(activity) }
    val scope = rememberCoroutineScope()
    var showSettings by remember { mutableStateOf(prefs.host.isBlank()) }
    val signedIn = model.auth.isSignedIn
    val hostEmpty = prefs.host.isBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ESP Cam Drive", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Ustawienia")
                    }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (hostEmpty || !signedIn) item { SetupBanner(hostEmpty, signedIn) { showSettings = true } }
            item { PreviewSection(model) }
            item { ControlsSection(model, prefs, signedIn, hostEmpty, ensureNotifications) }
            if (prefs.autoMode && (model.autoRunning || model.autoStart != null)) item { StatsSection(model) }
            if (model.items.isNotEmpty()) {
                item { SectionTitle("Ostatnie pliki") }
                items(model.items, key = { it.id }) { item ->
                    ItemRow(item) { scope.launch { model.uploadItem(item.id, prefs.folder) } }
                }
            }
        }
    }

    if (showSettings) {
        SettingsSheet(model, prefs, activity) { prefs.save(); showSettings = false }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text.uppercase(Locale.getDefault()), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
}

@Composable
private fun SetupBanner(hostEmpty: Boolean, signedIn: Boolean, onOpen: () -> Unit) {
    val text = listOfNotNull(
        if (hostEmpty) "podaj adres IP kamery" else null,
        if (signedIn) null else "zaloguj się do Google",
    ).joinToString(", ").replaceFirstChar { it.uppercase() }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Warning, null, tint = Orange, modifier = Modifier.size(18.dp))
                Text("Najpierw skonfiguruj aplikację", color = Orange, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onOpen, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text("Otwórz ustawienia")
            }
        }
    }
}

@Composable
private fun PreviewSection(model: AppModel) {
    val statusColor = when {
        model.statusIsError -> Red
        model.autoRunning || model.recording -> Green
        else -> Color.Gray
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                val img = model.lastImage
                if (img != null) {
                    Image(img.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.CameraEnhance, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Brak podglądu", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (model.recording) {
                    Row(
                        Modifier.align(Alignment.TopStart).padding(8.dp).clip(CircleShape).background(Red)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Filled.FiberManualRecord, null, Modifier.size(10.dp), tint = Color.White)
                        Text("REC ${formatDuration(model.elapsed.toDouble())}", color = Color.White,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.padding(top = 5.dp).size(10.dp).clip(CircleShape).background(statusColor))
                Text(model.status, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ControlsSection(
    model: AppModel, prefs: Prefs, signedIn: Boolean, hostEmpty: Boolean, ensureNotifications: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val lockMode = model.autoRunning || model.recording
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Sterowanie")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val modes = listOf("Ręczny" to false, "Cykliczny" to true)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { i, (label, value) ->
                        SegmentedButton(
                            selected = prefs.autoMode == value,
                            onClick = { prefs.autoMode = value; prefs.save() },
                            enabled = !lockMode,
                            shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                        ) { Text(label) }
                    }
                }
                Text(
                    if (prefs.autoMode) "Zdjęcia robią się same w wybranym odstępie i od razu trafiają na Dysk."
                    else "Zdjęcie lub film pojawia się na liście, na Dysk wysyłasz je przyciskiem przy pliku.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (prefs.autoMode) {
                    IntervalPicker(prefs.interval, enabled = !model.autoRunning) { prefs.interval = it; prefs.save() }

                    if (prefs.interval < 1) {
                        Text(
                            "Przy takim odstępie kamera i internet muszą wyrabiać ok. ${(1 / prefs.interval).let { Math.round(it).toInt() }} zdjęcia na sekundę. Jeśli nie zdążą, spóźnione klatki zostaną pominięte.",
                            fontSize = 13.sp, color = Orange,
                        )
                    }

                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Usuń z telefonu po wysłaniu", Modifier.weight(1f))
                        Switch(prefs.deleteAfter, { prefs.deleteAfter = it; prefs.save() }, enabled = !model.autoRunning)
                    }

                    if (model.autoRunning) {
                        Button(
                            onClick = { model.stopAuto() },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Red),
                        ) {
                            Icon(Icons.Filled.Stop, null); Spacer(Modifier.width(8.dp)); Text("Zatrzymaj")
                        }
                    } else {
                        Button(
                            onClick = {
                                ensureNotifications()
                                model.startAuto(prefs.host, prefs.interval, prefs.folder, prefs.deleteAfter)
                            },
                            enabled = !(hostEmpty || !signedIn || model.busy),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Green),
                        ) {
                            Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(8.dp))
                            Text("Start: zdjęcie co ${formatInterval(prefs.interval)}")
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { scope.launch { model.photo(prefs.host, prefs.autoMode, prefs.folder) } },
                        enabled = !(model.busy || model.autoRunning || hostEmpty),
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Icon(Icons.Filled.CameraAlt, null); Spacer(Modifier.width(6.dp)); Text("Zdjęcie")
                    }
                    if (model.recording) {
                        OutlinedButton(
                            onClick = { model.stopRecording() },
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Red),
                        ) {
                            Icon(Icons.Filled.StopCircle, null); Spacer(Modifier.width(6.dp)); Text("Zakończ")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { scope.launch { model.startRecording(prefs.host, prefs.autoMode, prefs.folder) } },
                            enabled = !(model.busy || model.autoRunning || hostEmpty),
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) {
                            Icon(Icons.Filled.Videocam, null); Spacer(Modifier.width(6.dp)); Text("Nagrywaj")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IntervalPicker(value: Double, enabled: Boolean, onChange: (Double) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Odstęp", Modifier.weight(1f))
        Box {
            TextButton(onClick = { open = true }, enabled = enabled) { Text(formatInterval(value)) }
            DropdownMenu(open, { open = false }) {
                intervalOptions.forEach {
                    DropdownMenuItem(text = { Text(formatInterval(it)) }, onClick = { onChange(it); open = false })
                }
            }
        }
    }
}

@Composable
private fun StatsSection(model: AppModel) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(model.autoRunning) {
        while (model.autoRunning) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Cykl")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    StatTile("${model.captured}", "Zrobione", Modifier.weight(1f))
                    StatTile("${model.uploaded}", "Wysłane", Modifier.weight(1f))
                    StatTile("${model.pending}", "W kolejce", Modifier.weight(1f))
                    StatTile("${model.failed}", "Błędy", Modifier.weight(1f),
                        tint = if (model.failed > 0) Red else MaterialTheme.colorScheme.onSurface)
                }
                model.autoStart?.let { start ->
                    Row(Modifier.fillMaxWidth()) {
                        Text("Czas działania", Modifier.weight(1f))
                        val end = model.autoEnd ?: now
                        Text(formatDuration((end - start) / 1000.0), fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, color = tint)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ItemRow(item: Item, onUpload: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                val t = item.thumb
                if (t != null) Image(t.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(if (item.isVideo) Icons.Filled.Videocam else Icons.Filled.Image, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(item.file.name, fontSize = 13.sp, modifier = Modifier.weight(1f))
            if (item.uploaded) Icon(Icons.Filled.CloudDone, "Wysłano", tint = Green)
            else IconButton(onClick = onUpload) { Icon(Icons.Filled.CloudUpload, "Wyślij na Dysk") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(model: AppModel, prefs: Prefs, activity: android.app.Activity, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDone,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ustawienia", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onDone) { Text("Gotowe") }
            }

            SectionTitle("Kamera ESP32-CAM")
            OutlinedTextField(
                prefs.host, { prefs.host = it },
                label = { Text("Adres IP, np. 192.168.1.50") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Telefon musi być w tej samej sieci WiFi co kamera. Kamera musi działać ze szkicem Arduino CameraWebServer.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            HorizontalDivider()
            SectionTitle("Dysk Google")
            if (model.auth.isSignedIn) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = Green)
                    Spacer(Modifier.width(8.dp))
                    Text("Zalogowano", color = Green, modifier = Modifier.weight(1f))
                    TextButton(onClick = { model.auth.signOut() }) { Text("Wyloguj") }
                }
            } else {
                TextButton(onClick = { scope.launch { model.signIn(activity) } }) {
                    Icon(Icons.Filled.AccountCircle, null); Spacer(Modifier.width(8.dp)); Text("Zaloguj się do Google")
                }
            }
            OutlinedTextField(
                prefs.folder, { prefs.folder = it },
                label = { Text("Folder na Dysku") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Aplikacja widzi tylko foldery i pliki, które sama utworzyła. Jeśli folder o tej nazwie nie istnieje, zostanie założony.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (model.status.isNotEmpty()) {
                HorizontalDivider()
                SectionTitle("Status")
                Text(model.status, fontSize = 13.sp,
                    color = if (model.statusIsError) Red else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
