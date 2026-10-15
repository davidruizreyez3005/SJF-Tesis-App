package mx.sjf.tesis.data.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** Casos tomados de la ejecutoria 19501 (saltos sueltos dentro de rubros citados). */
class RenglonesTest {
    @Test
    fun uneLosCortesAMediaFrase() {
        assertEquals(
            "PROHIBIDA POR EL ARTÍCULO 22 DE LA CONSTITUCIÓN DE LOS ESTADOS UNIDOS MEXICANOS",
            unirRenglones("PROHIBIDA POR EL ARTÍCULO 22 DE LA\nCONSTITUCIÓN DE LOS ESTADOS UNIDOS MEXICANOS")
        )
        assertEquals(
            "\"‘JURISPRUDENCIA DE LA SUPREMA CORTE DE JUSTICIA. PREVIAMENTE A LA SOLICITUD DE SU MODIFICACIÓN",
            unirRenglones("\"‘JURISPRUDENCIA DE LA SUPREMA CORTE DE JUSTICIA. PREVIAMENTE A LA \n  SOLICITUD DE SU MODIFICACIÓN")
        )
        assertEquals(
            "NO SE DEFINE POR SU CALIDAD DE PROCESADO O SENTENCIADO.-El instituto",
            unirRenglones("NO SE DEFINE POR SU CALIDAD DE PROCESADO O\nSENTENCIADO.-El instituto")
        )
    }

    @Test
    fun conservaLosSaltosConSentido() {
        // Fin de oración (también con comillas o paréntesis de cierre).
        assertEquals("Primero.\nSegundo", unirRenglones("Primero.\nSegundo"))
        assertEquals("los siguientes:\nI. Uno", unirRenglones("los siguientes:\nI. Uno"))
        assertEquals("(página 84).\"\nEl primero", unirRenglones("(página 84).\"\nEl primero"))
        // Incisos y listas.
        assertEquals("se constriñe\na) Que tenga\nb) Que sea", unirRenglones("se constriñe\na) Que tenga\nb) Que sea"))
        assertEquals("los requisitos\n– el primero", unirRenglones("los requisitos\n– el primero"))
    }

    @Test
    fun parrafosDeUnDocumentoHtml() {
        assertEquals(
            listOf("CONSTITUYE UNA PENA PROHIBIDA POR EL ARTÍCULO 22 DE LA CONSTITUCIÓN.", "RESULTANDO:"),
            parrafosPlanos("<p>CONSTITUYE UNA PENA PROHIBIDA POR EL ARTÍCULO 22 DE LA<br>CONSTITUCIÓN.</p><p>RESULTANDO:</p>")
        )
    }
}
