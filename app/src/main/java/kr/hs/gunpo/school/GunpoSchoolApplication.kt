package kr.hs.gunpo.school

import android.app.Application
import com.google.firebase.FirebaseApp

class GunpoSchoolApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Remove obsolete on-device model files while preserving the student's settings and cache.
        listOf("ai-models", "ai-cache", "ai-packages", "ai-runtime").forEach { directory ->
            filesDir.resolve(directory).deleteRecursively()
        }

        if (!BuildConfig.FIREBASE_CONFIGURED) return
        if (FirebaseApp.initializeApp(this) != null) AppCheckProviderInstaller.install()
    }
}