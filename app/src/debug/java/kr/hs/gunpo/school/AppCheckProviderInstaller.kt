package kr.hs.gunpo.school

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

object AppCheckProviderInstaller {
    fun install() {
        Firebase.appCheck.installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance(),
        )
        Firebase.appCheck.getAppCheckToken(false)
            .addOnSuccessListener { Log.i("GunpoAppCheck", "Debug App Check token issued") }
            .addOnFailureListener { Log.e("GunpoAppCheck", "Debug App Check token request failed", it) }
    }
}
