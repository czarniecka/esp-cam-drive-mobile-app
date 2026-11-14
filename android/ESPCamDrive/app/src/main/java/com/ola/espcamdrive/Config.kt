package com.ola.espcamdrive

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object Config {
    /** Wklej tutaj Client ID z Google Cloud Console (ten sam co w wersji iOS albo klient typu Android). */
    const val googleClientID = "WKLEJ_TUTAJ_CLIENT_ID.apps.googleusercontent.com"

    const val scope = "https://www.googleapis.com/auth/drive.file"

    /** Schemat przekierowania Google = odwrócony Client ID. */
    val redirectScheme: String
        get() = googleClientID.split(".").reversed().joinToString(".")
    val redirectURI: String get() = "$redirectScheme:/oauth2redirect"
}

class AppError(message: String) : Exception(message)

/** Czytelny, polski opis błędu (odpowiednik localizedDescription). */
val Throwable.pl: String
    get() = when (this) {
        is AppError -> message ?: "Błąd"
        is SocketTimeoutException -> "Przekroczono limit czasu"
        is UnknownHostException -> "Nie można znaleźć serwera"
        is IOException -> message ?: "Błąd połączenia"
        else -> message ?: toString()
    }
