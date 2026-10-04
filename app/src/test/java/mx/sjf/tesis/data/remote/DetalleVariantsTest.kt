package mx.sjf.tesis.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de la construcción de URLs de detalle por variante de edición.
 *
 * La lógica de [DetalleVariants.url] es la que en v2.2.0 vivía dentro de
 * pedirDetalle y siempre añadía `isSemanal=true`; esa única variante dejaba
 * sin texto completo a las tesis de la Gaceta impresa (p. ej. ius 190955).
 */
class DetalleVariantsTest {

    private val base =
        "https://sjf2.scjn.gob.mx/services/sjftesismicroservice/api/public/tesis/%IUS%"

    @Test
    fun `variante semanal agrega isSemanal true y hostName`() {
        val url = DetalleVariants.url(base, 190955L, DetalleVariants.SEMANAL)
        assertEquals(
            "https://sjf2.scjn.gob.mx/services/sjftesismicroservice/api/public/tesis/190955" +
                "?isSemanal=true&hostName=https://sjf2.scjn.gob.mx",
            url
        )
    }

    @Test
    fun `variante plana no agrega isSemanal`() {
        val url = DetalleVariants.url(base, 190955L, DetalleVariants.PLANA)
        assertEquals(
            "https://sjf2.scjn.gob.mx/services/sjftesismicroservice/api/public/tesis/190955" +
                "?hostName=https://sjf2.scjn.gob.mx",
            url
        )
        assertFalse(url.contains("isSemanal"))
    }

    @Test
    fun `variante gaceta agrega isGaceta true`() {
        val url = DetalleVariants.url(base, 190955L, DetalleVariants.GACETA)
        assertEquals(
            "https://sjf2.scjn.gob.mx/services/sjftesismicroservice/api/public/tesis/190955" +
                "?isGaceta=true&hostName=https://sjf2.scjn.gob.mx",
            url
        )
        assertFalse(url.contains("isSemanal"))
    }

    @Test
    fun `variante no semanal agrega isSemanal false`() {
        val url = DetalleVariants.url(base, 2004415L, DetalleVariants.NO_SEMANAL)
        assertEquals(
            "https://sjf2.scjn.gob.mx/services/sjftesismicroservice/api/public/tesis/2004415" +
                "?isSemanal=false&hostName=https://sjf2.scjn.gob.mx",
            url
        )
    }

    @Test
    fun `el marcador de registro se sustituye completo`() {
        val url = DetalleVariants.url(base, 2032514L, DetalleVariants.SEMANAL)
        assertTrue(url.contains("/tesis/2032514?"))
        assertFalse(url.contains("%IUS%"))
    }

    @Test
    fun `plantillas con query previo usan ampersand y no duplican`() {
        val conQuery = base.replace("%IUS%", "190955") + "?isSemanal=true"
        val url = DetalleVariants.url(conQuery, 190955L, DetalleVariants.SEMANAL)
        // isSemanal ya viene en la plantilla: no se agrega dos veces.
        assertEquals(1, Regex("isSemanal=").findAll(url).count())
        // hostName se añade con & porque ya hay query.
        assertTrue(url.endsWith("&hostName=https://sjf2.scjn.gob.mx"))
    }

    @Test
    fun `hostName sin protocolo se normaliza a https`() {
        val conHost = "https://sjf2.scjn.gob.mx/services/sjftesismicroservice/api/public/tesis/%IUS%" +
            "?isSemanal=true&hostName=sjf2.scjn.gob.mx"
        val url = DetalleVariants.url(conHost, 190955L, DetalleVariants.PLANA)
        assertTrue(url.contains("hostName=https://sjf2.scjn.gob.mx"))
        assertFalse(url.endsWith("hostName=sjf2.scjn.gob.mx"))
    }

    @Test
    fun `plantilla de microservicio ajeno no recibe parametros de edicion`() {
        // Las plantillas de ejecutorias/votos/acuerdos (2 segmentos, sin
        // variante) deben quedar solo con hostName.
        val ejecutorias =
            "https://sjf2.scjn.gob.mx/services/sjfejecutoriamicroservice/api/public/ejecutorias/%IUS%"
        val url = DetalleVariants.url(ejecutorias, 24820L, DetalleVariants.PLANA)
        assertEquals(
            "https://sjf2.scjn.gob.mx/services/sjfejecutoriamicroservice/api/public/ejecutorias/24820" +
                "?hostName=https://sjf2.scjn.gob.mx",
            url
        )
    }

    @Test
    fun `orden de variantes inicia con semanal y termina con no semanal`() {
        assertEquals(
            listOf("S", "P", "G", "F"),
            DetalleVariants.ORDEN
        )
        // La primera variante debe ser la semanal: es la que sirve para la
        // mayoría de las tesis recientes (una sola petición en el caso común).
        assertEquals(DetalleVariants.SEMANAL, DetalleVariants.ORDEN.first())
    }

    @Test
    fun `etiquetas legibles para el registro de red`() {
        assertEquals("semanal", DetalleVariants.etiqueta(DetalleVariants.SEMANAL))
        assertEquals("sin edición", DetalleVariants.etiqueta(DetalleVariants.PLANA))
        assertEquals("gaceta", DetalleVariants.etiqueta(DetalleVariants.GACETA))
        assertEquals("no semanal", DetalleVariants.etiqueta(DetalleVariants.NO_SEMANAL))
        assertEquals("estándar", DetalleVariants.etiqueta("X"))
    }
}
