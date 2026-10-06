package mx.sjf.tesis.data.model

import mx.sjf.tesis.data.remote.SjfApi
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** «Precedente(s) de la tesis» con datos reales de la API (ejecutorias 19501 y 34279). */
class EjecutoriaTest {

    private fun ejecutoria(epoca: String, instancia: String, fuente: String, volumen: String, tomo: String = "", pagina: String = "") =
        Ejecutoria(19501, "", "", epoca, instancia, fuente, volumen, tomo, pagina, "", "")

    @Test
    fun localizacion_gaceta_igualQueElSemanario() {
        // P./J. 2/2006 (registro 175940) → «1. 19501 Novena Época. Pleno. …»
        assertEquals(
            "Novena Época. Pleno. Semanario Judicial de la Federación y su Gaceta, Tomo XXIII, Mayo de 2006, Pág. 339.",
            ejecutoria("Novena Época", "Pleno", "Semanario Judicial de la Federación y su Gaceta", "Tomo XXIII, Mayo de 2006", pagina = "339").localizacion
        )
    }

    @Test
    fun localizacion_semanarioElectronico_sinPagina() {
        assertEquals(
            "Duodécima Época. Pleno. Semanario Judicial de la Federación, Libro 14, Octubre de 2026.",
            ejecutoria("Duodécima Época", "Pleno", "Semanario Judicial de la Federación", "Libro 14, Octubre de 2026").localizacion
        )
    }

    @Test
    fun localizacion_conTomoDeLaDecimaEpoca() {
        assertEquals(
            "Décima Época. Tribunales Colegiados de Circuito. Semanario Judicial de la Federación y su Gaceta, Libro XXIII, Agosto de 2013, Tomo 3, Pág. 1659.",
            ejecutoria("Décima Época", "Tribunales Colegiados de Circuito", "Semanario Judicial de la Federación y su Gaceta",
                "Libro XXIII, Agosto de 2013", tomo = "Tomo 3", pagina = "1659").localizacion
        )
    }

    @Test
    fun urlsOficiales() {
        assertEquals("https://sjf2.scjn.gob.mx/detalle/ejecutoria/19501", ejecutoria("", "", "", "").urlDetalle)
        assertEquals("https://sjf2.scjn.gob.mx/detalle/voto/47724", Voto(47724, "", "", "", "").urlDetalle)
    }

    @Test
    fun tipoAsunto_mayusculasALegible() {
        assertEquals("Solicitud de sustitución de jurisprudencia 2/2005-PL",
            SjfApi.tipoAsuntoLegible("SOLICITUD DE SUSTITUCIÓN DE JURISPRUDENCIA 2/2005-PL"))
        assertEquals("Amparo directo en revisión 6627/2025", SjfApi.tipoAsuntoLegible("AMPARO DIRECTO EN REVISIÓN 6627/2025"))
        // Si ya viene en altas y bajas, se respeta.
        assertEquals("Amparo directo 426/2022", SjfApi.tipoAsuntoLegible("Amparo directo 426/2022"))
    }

    @Test
    fun ejecutoriasSobrevivenAlGuardar() {
        val t = Tesis(registro = 175940, rubro = "R", ejecutorias = listOf(19501), votos = listOf(20582, 20583, 20584))
        val ida = Tesis.fromJson(JSONObject(t.toJson().toString()))
        assertEquals(listOf(19501L), ida.ejecutorias)
        assertEquals(listOf(20582L, 20583L, 20584L), ida.votos)
    }
}
