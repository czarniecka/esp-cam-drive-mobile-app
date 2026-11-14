package com.ola.espcamdrive

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val model get() = (application as EspApp).model
    private var leftApp = false

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                ContentScreen(
                    model = model,
                    activity = this,
                    ensureNotifications = ::ensureNotifications,
                )
            }
        }
    }

    /** Na Androidzie 13+ powiadomienie usługi wymaga zgody (sama usługa działa i bez niej). */
    private fun ensureNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Powrót z logowania Google: com.googleusercontent.apps.<id>:/oauth2redirect?code=...
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val data = intent.data
        if (data != null && data.scheme == Config.redirectScheme) {
            model.auth.pending?.complete(data)
        }
    }

    override fun onPause() {
        super.onPause()
        leftApp = true
    }

    override fun onResume() {
        super.onResume()
        // Wróciliśmy z przeglądarki bez przekierowania = użytkownik zamknął logowanie.
        if (leftApp && model.auth.pending != null) {
            Handler(Looper.getMainLooper()).postDelayed({
                model.auth.pending?.completeExceptionally(AppError("Logowanie przerwane"))
            }, 600)
        }
        leftApp = false
    }
}
