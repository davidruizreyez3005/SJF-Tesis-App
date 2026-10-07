package mx.sjf.tesis.data.util

import mx.sjf.tesis.data.model.Tesis
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Precedentes y votos con textos REALES de la API de sjf2.scjn.gob.mx, ya
 * limpios como los deja parseTesis (párrafos separados por salto de línea).
 */
class PrecedentesTest {

    // Registro 172000 (jurisprudencia por reiteración, 9a. Época): cinco asuntos.
    private val reiteracion = listOf(
        "Revisión contencioso administrativa 70/2005. Directora Ejecutiva de Servicios Jurídicos de la Secretaría de Desarrollo Urbano y Vivienda del Gobierno del Distrito Federal. 17 de agosto de 2005. Unanimidad de votos. Ponente: Alberto Pérez Dayán. Secretaria: Amelia Vega Carrillo.",
        "Revisión contencioso administrativa 110/2005. Jefe Delegacional, Director General Jurídico y de  Gobierno y Subdirector de Calificación de Infracciones, autoridades dependientes del Gobierno del Distrito Federal en la Delegación Tlalpan. 3 de noviembre de 2005. Unanimidad de votos. Ponente: Alberto Pérez Dayán. Secretaria: Amelia Vega Carrillo.",
        "Revisión contencioso administrativa 8/2007. Director General Jurídico y de Gobierno en la Delegación Xochimilco y otras. 7 de febrero de 2007. Unanimidad de votos. Ponente: Adela Domínguez Salazar. Secretaria: Aurora del Carmen Muñoz García.",
        "Revisión contencioso administrativa 14/2007. Director General Jurídico y de Gobierno en Tláhuac. 14 de marzo de 2007. Unanimidad de votos. Ponente: Alberto Pérez Dayán. Secretaria: Laura Iris Porras Espinosa.",
        "Revisión contencioso administrativa 34/2007. Francisco Javier Álvarez Rojas, autorizado de las autoridades demandadas pertenecientes a la Delegación Tlalpan del Gobierno del Distrito Federal. 16 de mayo de 2007. Unanimidad de votos. Ponente: Alberto Pérez Dayán. Secretaria: Irma Gómez Rodríguez."
    ).joinToString("\n")

    // Registro 190955 (tesis aislada del Pleno, 9a. Época): asunto + aprobación + nota.
    private val conNotas = listOf(
        "Amparo en revisión 3066/98. 10 de agosto de 1999. Unanimidad de diez votos. Ausente: José Vicente Aguinaco Alemán. Ponente: José de Jesús Gudiño Pelayo. Secretario: Ramiro Rodríguez Pérez.",
        "El Tribunal Pleno, en su sesión privada celebrada hoy dos de octubre en curso, aprobó, con el número CLXV/2000, la tesis aislada que antecede; y determinó que la votación es idónea para integrar tesis jurisprudencial. México, Distrito Federal, a dos de octubre de dos mil.",
        "Nota: Esta tesis interrumpe la tesis P. XLIV/98, que aparece publicada en el Semanario Judicial de la Federación y su Gaceta, Novena Época, Tomo VII, mayo de 1998, página 7."
    ).joinToString("\n")

    // Registro 2019000 (contradicción de tesis, Plenos de Circuito, 10a. Época).
    private val contradiccion = listOf(
        "Contradicción de tesis 5/2018. Entre las sustentadas por el Cuarto Tribunal Colegiado de Circuito del Centro Auxiliar de la Tercera Región, con residencia en Guadalajara, Jalisco y el Sexto Tribunal Colegiado en Materia Administrativa del Tercer Circuito. 24 de septiembre de 2018. Unanimidad de seis votos. Ponente: Hugo Gómez Ávila. Secretaria: Claudia de Anda García.",
        "Criterios contendientes:",
        "El sustentado por el Cuarto Tribunal Colegiado de Circuito del Centro Auxiliar de la Tercera Región, con residencia en Guadalajara, Jalisco, al resolver el amparo directo 486/2017."
    ).joinToString("\n")

    @Test
    fun reiteracion_cincoAsuntosNumerables() {
        val d = dividirPrecedentes(reiteracion)
        assertEquals(5, d.asuntos.size)
        assertEquals(
            listOf(
                "Revisión contencioso administrativa 70/2005",
                "Revisión contencioso administrativa 110/2005",
                "Revisión contencioso administrativa 8/2007",
                "Revisión contencioso administrativa 14/2007",
                "Revisión contencioso administrativa 34/2007"
            ),
            d.asuntos.map { it.asunto }
        )
        assertTrue(d.asuntos[0].detalle.startsWith("Directora Ejecutiva de Servicios Jurídicos"))
        assertTrue(d.asuntos[0].detalle.endsWith("Secretaria: Amelia Vega Carrillo."))
        // Se normalizan los espacios dobles del texto oficial.
        assertTrue(d.asuntos[1].detalle.contains("Jurídico y de Gobierno"))
        assertTrue(d.notas.isEmpty())
    }

    @Test
    fun aprobacionYNotasSeSeparanDelAsunto() {
        val d = dividirPrecedentes(conNotas)
        assertEquals(listOf("Amparo en revisión 3066/98"), d.asuntos.map { it.asunto })
        assertTrue(d.asuntos[0].detalle.startsWith("10 de agosto de 1999. Unanimidad de diez votos."))
        assertEquals(2, d.notas.size)
        assertTrue(d.notas[0].startsWith("El Tribunal Pleno, en su sesión privada"))
        assertTrue(d.notas[1].startsWith("Nota: Esta tesis interrumpe la tesis P. XLIV/98"))
    }

    @Test
    fun contradiccion_criteriosContendientesSonNotas() {
        val d = dividirPrecedentes(contradiccion)
        assertEquals(listOf("Contradicción de tesis 5/2018"), d.asuntos.map { it.asunto })
        assertEquals(listOf("Criterios contendientes:", d.notas[1]), d.notas)
        assertTrue(d.notas[1].startsWith("El sustentado por el Cuarto Tribunal"))
    }

    @Test
    fun ningunTextoSePierde() {
        for (texto in listOf(reiteracion, conNotas, contradiccion)) {
            val d = dividirPrecedentes(texto)
            val reconstruido = d.asuntos.joinToString(" ") { "${it.asunto}. ${it.detalle}" } + " " + d.notas.joinToString(" ")
            val palabras = { s: String -> s.split(Regex("\\s+")).filter { it.isNotBlank() }.sorted() }
            assertEquals(palabras(texto), palabras(reconstruido))
        }
    }

    @Test
    fun voto_tituloYTipoDelPrimerParrafo() {
        val texto = "<p>Voto particular que formula la Ministra Lenia Batres Guadarrama relativo al amparo directo en revisión 6627/2025.</p><br><p>Durante la sesión celebrada el veinticinco de marzo de dos mil veintiséis, el Pleno de esta Suprema Corte…</p>"
        assertEquals(
            "Voto particular que formula la Ministra Lenia Batres Guadarrama relativo al amparo directo en revisión 6627/2025" to "particular",
            tituloYTipoDeVoto(texto)
        )
        assertEquals("concurrente", tituloYTipoDeVoto("<p>Voto concurrente que formula el Ministro X.</p>").second)
        assertEquals("aclaratorio", tituloYTipoDeVoto("Voto aclaratorio que formula la Ministra Y.").second)
        assertEquals("" , tituloYTipoDeVoto("Texto sin encabezado de voto.").second)
        assertEquals("Voto" to "", tituloYTipoDeVoto(""))
    }

    @Test
    fun votosSobrevivenAlGuardar() {
        val t = Tesis(registro = 2032690, rubro = "R", votos = listOf(47724, 47725, 47726))
        val ida = Tesis.fromJson(JSONObject(t.toJson().toString()))
        assertEquals(listOf(47724L, 47725L, 47726L), ida.votos)
        // Tesis guardadas por versiones anteriores (sin el campo) cargan sin votos.
        assertEquals(emptyList<Long>(), Tesis.fromJson(JSONObject("""{"ius":1,"rubro":"R"}""")).votos)
    }

    @Test
    fun autorDeVoto_quitaElTipoRepetido() {
        assertEquals("Ministro Sergio Salvador Aguirre Anguiano", autorDeVoto("Voto paralelo del Ministro Sergio Salvador Aguirre Anguiano"))
        assertEquals("Ministra Norma Lucía Piña Hernández", autorDeVoto("Voto concurrente que formula la Ministra Norma Lucía Piña Hernández"))
        assertEquals("Ministro Juan N. Silva Meza", autorDeVoto("Voto particular del Ministro Juan N. Silva Meza"))
        // Sin el patrón, el título queda intacto.
        assertEquals("Voto", autorDeVoto("Voto"))
        assertEquals("Engrose del asunto", autorDeVoto("Engrose del asunto"))
    }
}
