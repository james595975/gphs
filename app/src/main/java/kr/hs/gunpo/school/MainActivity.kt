package kr.hs.gunpo.school

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.hs.gunpo.school.ui.GunpoSchoolApp
import kr.hs.gunpo.school.ui.theme.GunpoSchoolTheme

class MainActivity : ComponentActivity() {
    private var launchUri by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchUri = intent?.dataString
        enableEdgeToEdge()
        setContent {
            GunpoSchoolTheme {
                val viewModel: MainViewModel = viewModel()
                GunpoSchoolApp(viewModel, intent?.data?.host, launchUri)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchUri = intent.dataString
    }
}
