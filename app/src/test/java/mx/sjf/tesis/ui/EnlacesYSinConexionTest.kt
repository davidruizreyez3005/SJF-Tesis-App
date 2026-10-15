package mx.sjf.tesis.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import mx.sjf.tesis.MainActivity
import mx.sjf.tesis.data.local.DocumentosLocales
import mx.sjf.tesis.data.model.Ejecutoria
import mx.sjf.tesis.data.model.Tesis
import mx.sjf.tesis.data.model.Voto
import mx.sjf.tesis.viewmodel.AppViewModel
import mx.sjf.tesis.viewmodel.DocumentosUi
import mx.sjf.tesis.viewmodel.Lectura
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Enlaces del Semanario (abiertos o compartidos) y lectura sin conexión.
 * La API se desactiva para que las pruebas no dependan de la red.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class EnlacesYSinConexionTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("sjf_tesis_store", Context.MODE_PRIVATE)

    private val ejecutoria = Ejecutoria(19501, "Solicitud de sustitución de jurisprudencia 2/2005-PL", "", "Novena Época", "Pleno",
        "Semanario Judicial de la Federación y su Gaceta", "Tomo XXIII, Mayo de 2006", "", "339", "", "<p>Texto</p>")
    private val voto = Voto(20582, "Voto paralelo del Ministro X", "paralelo", "<p>Texto</p>", "")

    @get:Rule(order = 0) val fabrica = FabricaDeViewModelsLimpia()
    @get:Rule(order = 1) val compose = createEmptyComposeRule()

    @Before
    fun sinRed() {
        prefs.edit().clear().putBoolean("apiDirect", false).commit()
    }


    private fun guardarTesis(t: Tesis) {
        prefs.edit().putString("saved", JSONArray().put(t.toJson()).toString()).commit()
    }

    private var escenario: ActivityScenario<MainActivity>? = null

    @After
    fun cerrar() {
        escenario?.close()
        escenario = null
    }

    private fun abrir(intent: Intent): AppViewModel {
        val e = ActivityScenario.launch<MainActivity>(intent)
        escenario = e
        var vm: AppViewModel? = null
        e.onActivity { vm = ViewModelProvider(it)[AppViewModel::class.java] }
        compose.waitForIdle()
        return vm!!
    }

    /** Espera (con el reloj real) a que se cumpla [condicion]: la carga corre en otros hilos. */
    private fun esperar(condicion: () -> Boolean) {
        compose.waitUntil(timeoutMillis = 5_000) { condicion() }
    }

    private fun ver(url: String) = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .setClassName(context, MainActivity::class.java.name)

    private fun compartir(texto: String) = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, texto).setClassName(context, MainActivity::class.java.name)

    @Test
    fun enlaceDeTesis_abreSuFicha() {
        val vm = abrir(ver("https://sjf2.scjn.gob.mx/detalle/tesis/175940"))
        assertEquals(175940L, vm.selected.value?.registro)
    }

    @Test
    fun enlaceAnterior_abreSuFicha() {
        val vm = abrir(ver("https://sjf.scjn.gob.mx/SJFSist/Paginas/DetalleGeneralV2.aspx?ID=2004415&Clase=DetalleTesisBL"))
        assertEquals(2004415L, vm.selected.value?.registro)
    }

    @Test
    fun enlaceDeEjecutoria_sinRedNiCopiaLocal_avisaElError() {
        val vm = abrir(ver("https://sjf2.scjn.gob.mx/detalle/ejecutoria/19501"))
        assertEquals(Lectura.Fallo(mx.sjf.tesis.viewmodel.TipoDocumento.EJECUTORIA, 19501), vm.lectura.value)
    }

    @Test
    fun enlaceDeVoto_guardadoEnElTelefono_seLeeSinRed() {
        // El voto pertenece a una tesis guardada (lo demás se limpia al iniciar).
        guardarTesis(Tesis(registro = 175940, rubro = "R", texto = "T", votos = listOf(20582)))
        DocumentosLocales(File(context.filesDir, "documentos")).guardar(voto)
        val vm = abrir(compartir("Te comparto el voto: https://sjf2.scjn.gob.mx/detalle/voto/20582"))
        esperar { vm.lectura.value is Lectura.DeVoto }
        assertEquals(Lectura.DeVoto(voto), vm.lectura.value)
    }

    @Test
    fun textoCompartidoSinEnlace_seBusca() {
        val vm = abrir(compartir("prisión   vitalicia\nextradición"))
        assertEquals("prisión vitalicia extradición", vm.search.value.query)
    }

    @Test
    fun tesisGuardada_muestraSusPrecedentesYVotosSinRed() {
        val tesis = Tesis(registro = 175940, rubro = "EXTRADICIÓN.", texto = "Texto", ejecutorias = listOf(19501), votos = listOf(20582))
        guardarTesis(tesis)
        DocumentosLocales(File(context.filesDir, "documentos")).apply { guardar(ejecutoria); guardar(voto) }

        val vm = abrir(Intent(Intent.ACTION_MAIN).setClassName(context, MainActivity::class.java.name))
        compose.runOnUiThread { vm.openDetail(vm.saved.value.first()) }
        esperar { vm.votos.value is DocumentosUi.Listos }
        assertEquals(DocumentosUi.Listos(listOf(ejecutoria)), vm.ejecutorias.value)
        assertEquals(DocumentosUi.Listos(listOf(voto)), vm.votos.value)
        esperar { 175940L in vm.sinConexion.value }
        assertTrue(175940L in vm.sinConexion.value)
    }
}
