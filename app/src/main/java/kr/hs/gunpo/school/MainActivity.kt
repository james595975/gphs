package kr.hs.gunpo.school

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.hs.gunpo.school.ui.GunpoSchoolApp
import kr.hs.gunpo.school.ui.theme.GunpoSchoolTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GunpoSchoolTheme {
                val viewModel: MainViewModel = viewModel()
                GunpoSchoolApp(viewModel, intent?.data?.host)
            }
        }
    }
}
