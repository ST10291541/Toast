package vcmsa.projects.toastapplication

import android.app.Application

class ToastApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LocaleManager.applySavedLocale(this)
    }
}