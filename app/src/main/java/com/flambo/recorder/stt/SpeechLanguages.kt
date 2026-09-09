package com.flambo.recorder.stt

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

// Lists every language the on-device recognizer reports. Falls back to a
// sensible default when the details broadcast goes unanswered (some OEMs).
object SpeechLanguages {

    suspend fun fetchSupported(context: Context): List<Locale> {
        val tags = runCatching { queryDetails(context) }.getOrNull().orEmpty()
        val locales = tags.mapNotNull { runCatching { Locale.forLanguageTag(it) }.getOrNull() }
            .filter { it.language.isNotBlank() }
            .distinctBy { it.toLanguageTag() }
        if (locales.isNotEmpty()) return locales.sortedBy { displayName(it) }
        return listOf(Locale.getDefault(), Locale.ENGLISH, Locale("es"), Locale("fr"), Locale("de"), Locale("ar"))
            .distinctBy { it.toLanguageTag() }
    }

    private suspend fun queryDetails(context: Context): List<String>? =
        suspendCancellableCoroutine { cont ->
            try {
                val details = RecognizerIntent.getVoiceDetailsIntent(context)
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(c: Context?, intent: Intent?) {
                        val list = getResultExtras(true)
                            ?.getStringArrayList(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES)
                        if (cont.isActive) cont.resume(list)
                    }
                }
                context.sendOrderedBroadcast(details, null, receiver, null, Activity.RESULT_OK, null, null)
                Handler(Looper.getMainLooper()).postDelayed({
                    if (cont.isActive) cont.resume(null)
                }, 2500)
            } catch (_: Exception) {
                if (cont.isActive) cont.resume(null)
            }
        }

    fun displayName(locale: Locale): String =
        runCatching {
            locale.getDisplayName(locale).replaceFirstChar { it.uppercaseChar() }
        }.getOrDefault(locale.toLanguageTag())
}
