package mx.sjf.tesis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import mx.sjf.tesis.data.model.ThemeMode
import mx.sjf.tesis.data.util.enlaceSjf
import mx.sjf.tesis.ui.navigation.SjfApp
import mx.sjf.tesis.ui.theme.SjfTheme
import mx.sjf.tesis.viewmodel.AppViewModel

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    // Registro de permiso de escritura para Android 9 y anteriores.
    // Android 10+ usa MediaStore y no requiere este permiso.
    private val writePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // El resultado se maneja implícitamente — el usuario verá un aviso
        // cuando intente exportar si no tiene permiso.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Solicitar permiso de escritura en Android 9 y anteriores al iniciar.
        requestStoragePermissionIfNeeded()

        // Al recrearse (p. ej. al girar) el enlace ya se atendió.
        if (savedInstanceState == null) atenderEnlace(intent)

        setContent {
            val settings by vm.settings.collectAsState()
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            LaunchedEffect(dark) {
                window.statusBarColor = if (dark) 0xFF0D1B2A.toInt() else 0xFFF4F6F9.toInt()
                window.navigationBarColor = if (dark) 0xFF14273E.toInt() else 0xFFFFFFFF.toInt()
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            SjfTheme(darkTheme = dark, fontScale = settings.fontScale) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SjfApp(vm)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        atenderEnlace(intent)
    }

    /**
     * Un enlace del Semanario (abierto o compartido) muestra su documento.
     * Otro texto compartido se busca.
     */
    private fun atenderEnlace(intent: Intent?) {
        val texto = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        }?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val enlace = enlaceSjf(texto)
        when {
            enlace != null -> vm.abrirEnlace(enlace)
            intent?.action == Intent.ACTION_SEND -> vm.buscarTextoCompartido(texto)
        }
    }

    /**
     * Solicita WRITE_EXTERNAL_STORAGE en Android 9 y anteriores.
     * Android 10+ no lo necesita (MediaStore API).
     */
    private fun requestStoragePermissionIfNeeded() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }
}
