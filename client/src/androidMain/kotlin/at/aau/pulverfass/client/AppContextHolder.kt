package at.aau.pulverfass.client

import android.annotation.SuppressLint
import android.content.Context

/**
 * Hält den Application-Context für androidMain-actuals, die aus common code
 * ohne Context-Parameter aufgerufen werden. Wird in MainActivity.onCreate
 * gesetzt, bevor die Composition startet.
 */
@SuppressLint("StaticFieldLeak")
object AppContextHolder {
    lateinit var context: Context
}
