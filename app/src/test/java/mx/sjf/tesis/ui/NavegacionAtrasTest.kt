package mx.sjf.tesis.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import mx.sjf.tesis.MainActivity
import mx.sjf.tesis.viewmodel.AppViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** El botón «atrás» recorre la app de lo particular a lo general y solo sale tras confirmar. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class NavegacionAtrasTest {

    // La fábrica se limpia antes de que la regla de Compose abra la actividad.
    @get:Rule(order = 0) val fabrica = FabricaDeViewModelsLimpia()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    private val vm get() = ViewModelProvider(rule.activity)[AppViewModel::class.java]

    private fun atras() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.mainClock.advanceTimeBy(100)
    }

    private fun inicio() {
        rule.mainClock.autoAdvance = false   // el plazo para salir depende del reloj
        rule.mainClock.advanceTimeBy(4_000)  // pasa la pantalla de bienvenida
    }

    @Test
    fun desdeUnaBusqueda_vuelveAlInicioYDespuesPideConfirmar() {
        inicio()
        rule.onNodeWithText("Rubro, texto o registro").performTextInput("amparo")
        rule.mainClock.advanceTimeBy(100)
        assertEquals("amparo", vm.search.value.query)

        atras()   // resultados → inicio
        assertEquals("", vm.search.value.query)
        assertFalse(rule.activity.isFinishing)

        atras()   // inicio → aviso, no sale
        assertFalse(rule.activity.isFinishing)

        atras()   // segundo «atrás» dentro del plazo → sale
        assertTrue(rule.activity.isFinishing)
    }

    @Test
    fun elAvisoDeSalidaCaduca() {
        inicio()
        atras()
        rule.mainClock.advanceTimeBy(3_000)   // pasa el plazo: hay que confirmar de nuevo
        atras()
        assertFalse(rule.activity.isFinishing)
    }

    @Test
    fun desdeOtraPestana_vuelveABuscar() {
        rule.mainClock.advanceTimeBy(4_000)
        rule.onAllNodesWithText("Ajustes")[0].performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Rubro, texto o registro").assertDoesNotExist()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithText("Rubro, texto o registro").assertExists()
        assertFalse(rule.activity.isFinishing)
    }
}
