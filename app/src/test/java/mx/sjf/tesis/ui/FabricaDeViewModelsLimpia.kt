package mx.sjf.tesis.ui

import androidx.lifecycle.ViewModelProvider
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * AndroidViewModelFactory guarda en un campo estático la primera Application
 * que ve. En la app hay una sola, pero Robolectric crea una por prueba: sin
 * esta regla, los ViewModels de las pruebas siguientes usarían la Application
 * (y los datos) de la primera, y el orden de las pruebas cambiaría el resultado.
 */
class FabricaDeViewModelsLimpia : TestWatcher() {
    override fun starting(description: Description) {
        ViewModelProvider.AndroidViewModelFactory::class.java.getDeclaredField("sInstance")
            .apply { isAccessible = true }
            .set(null, null)
    }
}
