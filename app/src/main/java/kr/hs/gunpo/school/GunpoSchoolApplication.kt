package kr.hs.gunpo.school

import android.app.Application
import com.google.firebase.FirebaseApp

class GunpoSchoolApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!BuildConfig.FIREBASE_CONFIGURED) return

        // App Check must be installed before the first Firebase AI Logic request.
        if (FirebaseApp.initializeApp(this) != null) {
            AppCheckProviderInstaller.install()
        }
    }
}
