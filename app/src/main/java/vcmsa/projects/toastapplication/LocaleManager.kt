package vcmsa.projects.toastapplication

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LocaleManager {
    private const val PREFS_NAME = "AppSettings"
    private const val KEY_APP_LANGUAGE = "App_Language"

    fun saveLanguage(context: Context, languageTag: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_APP_LANGUAGE, languageTag)
            .apply()
    }

    fun getSavedLanguage(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_APP_LANGUAGE, "en") ?: "en"
    }

    fun applySavedLocale(context: Context) {
        val lang = getSavedLanguage(context)
        applyLanguageTag(lang)
    }

    fun applyLanguageTag(languageTag: String) {
        val locales = LocaleListCompat.forLanguageTags(languageTag)
        AppCompatDelegate.setApplicationLocales(locales)
    }
}


