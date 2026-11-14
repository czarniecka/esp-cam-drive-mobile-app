package com.ola.espcamdrive

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Odpowiednik Keychain: wartość szyfrowana kluczem z Android Keystore, zapisana w prefsach. */
object SecureStore {
    private const val ALIAS = "espcamdrive_key"
    private fun prefs(c: Context) = c.getSharedPreferences("secure", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    fun set(c: Context, name: String, value: String?) {
        if (value == null) { prefs(c).edit().remove(name).apply(); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val blob = cipher.iv + cipher.doFinal(value.toByteArray())
        prefs(c).edit().putString(name, Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
    }

    fun get(c: Context, name: String): String? = runCatching {
        val blob = Base64.decode(prefs(c).getString(name, null) ?: return null, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob, 0, 12))
        String(cipher.doFinal(blob, 12, blob.size - 12))
    }.getOrNull()
}

private fun ByteArray.base64URL(): String =
    Base64.encodeToString(this, Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)

private class TokenResponse(val accessToken: String, val expiresIn: Int, val refreshToken: String?)

/** Logowanie do Google (OAuth 2.0 + PKCE), bez żadnych zewnętrznych bibliotek poza Custom Tabs. */
class GoogleAuth(private val ctx: Context) {
    var isSignedIn by mutableStateOf(SecureStore.get(ctx, "refresh") != null)
        private set

    private val lock = Any()
    private var accessToken: String? = null
    private var expiry = 0L

    /** Czeka na przekierowanie z przeglądarki; uzupełnia je MainActivity. */
    @Volatile var pending: CompletableDeferred<Uri>? = null

    private fun cachedToken(): String? = synchronized(lock) {
        accessToken?.takeIf { expiry > System.currentTimeMillis() + 60_000 }
    }

    private fun store(token: String, expiresIn: Int) = synchronized(lock) {
        accessToken = token
        expiry = System.currentTimeMillis() + expiresIn * 1000L
    }

    suspend fun signIn(activity: Activity) {
        if (Config.googleClientID.contains("WKLEJ_TUTAJ")) {
            throw AppError("Wpisz Client ID z Google Cloud w pliku Config.kt")
        }
        val verifier = randomString(64)
        val challenge = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()).base64URL()

        val url = Uri.parse("https://accounts.google.com/o/oauth2/v2/auth").buildUpon()
            .appendQueryParameter("client_id", Config.googleClientID)
            .appendQueryParameter("redirect_uri", Config.redirectURI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", Config.scope)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("access_type", "offline")
            .appendQueryParameter("prompt", "consent")
            .build()

        val deferred = CompletableDeferred<Uri>()
        pending = deferred
        CustomTabsIntent.Builder().build().launchUrl(activity, url)
        val callback = try { deferred.await() } finally { pending = null }

        callback.getQueryParameter("error")?.let { throw AppError("Logowanie przerwane") }
        val code = callback.getQueryParameter("code")
            ?: throw AppError("Google nie zwróciło kodu logowania")

        val r = tokenRequest(
            mapOf(
                "client_id" to Config.googleClientID,
                "code" to code,
                "code_verifier" to verifier,
                "redirect_uri" to Config.redirectURI,
                "grant_type" to "authorization_code",
            )
        )
        store(r.accessToken, r.expiresIn)
        val rt = r.refreshToken ?: throw AppError("Brak refresh tokenu od Google")
        SecureStore.set(ctx, "refresh", rt)
        isSignedIn = true
    }

    fun signOut() {
        SecureStore.set(ctx, "refresh", null)
        synchronized(lock) { accessToken = null; expiry = 0 }
        isSignedIn = false
    }

    suspend fun validAccessToken(): String {
        cachedToken()?.let { return it }
        val rt = SecureStore.get(ctx, "refresh") ?: throw AppError("Zaloguj się do Google")
        try {
            val r = tokenRequest(
                mapOf(
                    "client_id" to Config.googleClientID,
                    "refresh_token" to rt,
                    "grant_type" to "refresh_token",
                )
            )
            store(r.accessToken, r.expiresIn)
            return r.accessToken
        } catch (e: Exception) {
            signOut()
            throw AppError("Sesja Google wygasła, zaloguj się ponownie")
        }
    }

    private suspend fun tokenRequest(params: Map<String, String>): TokenResponse = withContext(Dispatchers.IO) {
        val conn = URL("https://oauth2.googleapis.com/token").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            val body = params.entries.joinToString("&") {
                "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}"
            }
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val text = (if (code == 200) conn.inputStream else conn.errorStream)
                ?.use { String(it.readBytes()) } ?: ""
            if (code != 200) throw AppError("Google: " + text.take(200))
            val j = JSONObject(text)
            TokenResponse(
                j.getString("access_token"),
                j.getInt("expires_in"),
                j.optString("refresh_token").ifEmpty { null },
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun randomString(n: Int): String =
        ByteArray(n).also { SecureRandom().nextBytes(it) }.base64URL()
}
