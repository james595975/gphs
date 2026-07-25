package kr.hs.gunpo.school

import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

object AppCheckProviderInstaller {
    fun install() {
        Firebase.appCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance(),
        )
    }
}
