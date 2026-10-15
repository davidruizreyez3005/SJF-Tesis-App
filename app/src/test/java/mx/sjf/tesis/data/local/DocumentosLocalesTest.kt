package mx.sjf.tesis.data.local

import mx.sjf.tesis.data.model.Ejecutoria
import mx.sjf.tesis.data.model.Voto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DocumentosLocalesTest {
    @get:Rule val carpeta = TemporaryFolder()

    private val ejecutoria = Ejecutoria(
        19501, "Solicitud de sustitución de jurisprudencia 2/2005-PL", "EXTRADICIÓN.", "Novena Época", "Pleno",
        "Semanario Judicial de la Federación y su Gaceta", "Tomo XXIII, Mayo de 2006", "", "339", "", "<p>Texto «largo» con acentos: ñ, ü.</p>"
    )
    private val voto = Voto(20582, "Voto paralelo del Ministro X", "paralelo", "<p>Texto</p>", "viernes 26 de mayo de 2006")

    @Test
    fun guardaYLeeSinPerderDatos() {
        val docs = DocumentosLocales(carpeta.root)
        docs.guardar(ejecutoria)
        docs.guardar(voto)
        assertEquals(ejecutoria, docs.ejecutoria(19501))
        assertEquals(voto, docs.voto(20582))
        assertNull(docs.ejecutoria(1))
        assertTrue(docs.tamano() > 0)
    }

    @Test
    fun conservarSoloBorraLoQueYaNoEstaGuardado() {
        val docs = DocumentosLocales(carpeta.root)
        docs.guardar(ejecutoria)
        docs.guardar(ejecutoria.copy(registro = 2))
        docs.guardar(voto)
        docs.conservarSolo(ejecutorias = setOf(19501), votos = emptySet())
        assertTrue(docs.tieneEjecutoria(19501))
        assertFalse(docs.tieneEjecutoria(2))
        assertFalse(docs.tieneVoto(20582))
    }

    @Test
    fun unArchivoDanadoSeIgnora() {
        val docs = DocumentosLocales(carpeta.root)
        java.io.File(carpeta.root, "voto_7.json").writeText("{no es json")
        assertNull(docs.voto(7))
    }
}
