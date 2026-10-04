package mx.sjf.tesis

import mx.sjf.tesis.data.model.Tesis
import mx.sjf.tesis.data.util.FechaLegible
import mx.sjf.tesis.data.util.epochAFechaLegible
import mx.sjf.tesis.data.util.epochPlausibleMs
import mx.sjf.tesis.data.util.extraerFecha
import mx.sjf.tesis.data.util.formatDateCompact
import mx.sjf.tesis.data.util.formatDateLong
import mx.sjf.tesis.data.util.parseFecha
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del motor de fechas (TextUtils.kt).
 *
 * Cubre los formatos que la API del SJF realmente entrega según la edición del
 * Semanario: fecha humana en español, ISO, dd/MM/yyyy, epoch (segundos y
 * milisegundos), mes-año de la Gaceta impresa («Febrero de 2015»), mes-año
 * numérico («09/2013») y año suelto.
 */
class FechaUtilsTest {

    // ── formatDateLong: el formato pedido «(día) de (mes) del (año)» ──

    @Test
    fun `formato largo dia de mes del anio`() {
        assertEquals("26 de enero del 2018", formatDateLong("26 de enero de 2018"))
        assertEquals("26 de enero del 2018", formatDateLong("26 de enero del 2018"))
        assertEquals("26 de enero del 2018", formatDateLong("26 de Enero de 2018"))
        assertEquals("5 de marzo del 2021", formatDateLong("5 de marzo de 2021"))
    }

    @Test
    fun `formato largo desde iso`() {
        assertEquals("26 de enero del 2018", formatDateLong("2018-01-26"))
        assertEquals("26 de enero del 2018", formatDateLong("2018-01-26T00:00:00Z"))
        assertEquals("26 de enero del 2018", formatDateLong("2018/01/26"))
    }

    @Test
    fun `formato largo desde numerico`() {
        assertEquals("26 de enero del 2018", formatDateLong("26/01/2018"))
        assertEquals("26 de enero del 2018", formatDateLong("26-01-2018"))
    }

    @Test
    fun `formato largo desde mes anio numerico`() {
        // Regresión v2.2.3: «09/2013» se mostraba crudo en las tarjetas porque
        // ningún patrón lo reconocía. Es mes-año de la API de búsqueda (Gaceta):
        // se interpreta sin fabricar día.
        assertEquals("septiembre del 2013", formatDateLong("09/2013"))
        assertEquals("septiembre del 2013", formatDateLong("9-2013"))
        assertEquals("enero del 2014", formatDateLong("1/2014"))
    }

    @Test
    fun `mes anio numerico invalido no inventa fecha`() {
        // «13/2013» no es un mes válido: se muestra tal cual, sin inventar.
        assertEquals("13/2013", formatDateLong("13/2013"))
        assertEquals("99/2013", formatDateLong("99/2013"))
    }

    @Test
    fun `casos de la captura del usuario - tarjetas de vista previa`() {
        // Captura 20260827_182717: las tarjetas mostraban «10/01/2014» y
        // «09/2013»; deben verse en el formato «(día) de (mes) del (año)».
        assertEquals("10 de enero del 2014", formatDateLong("10/01/2014"))
        assertEquals("septiembre del 2013", formatDateLong("09/2013"))
    }

    @Test
    fun `formato largo desde epoch`() {
        assertEquals("26 de enero del 2018", formatDateLong("1516924800000")) // ms
        assertEquals("1 de febrero del 2015", formatDateLong("1422748800"))   // segundos
    }

    @Test
    fun `fecha previa a 2001 no se descarta`() {
        // Regresión: el filtro anterior (ms > 1e12) tiraba toda fecha < 2001.
        assertEquals("15 de enero del 2000", formatDateLong("947894400000"))
        assertEquals("15 de enero del 2000", formatDateLong("947894400"))
    }

    @Test
    fun `solo mes y anio - formato gaceta`() {
        assertEquals("febrero del 2015", formatDateLong("Febrero de 2015"))
        assertEquals("febrero del 2015", formatDateLong("febrero del 2015"))
        assertEquals("septiembre del 1937", formatDateLong("Setiembre de 1937"))
    }

    @Test
    fun `anio suelto y casos vacios`() {
        assertEquals("2018", formatDateLong("2018"))
        assertEquals("Sin fecha", formatDateLong(""))
        assertEquals("Sin fecha", formatDateLong("   "))
        assertEquals("fecha desconocida", formatDateLong("fecha desconocida"))
    }

    // ── formatDateCompact ──

    @Test
    fun `formato compacto`() {
        assertEquals("26/01/2018", formatDateCompact("26 de enero del 2018"))
        assertEquals("26/01/2018", formatDateCompact("2018-01-26"))
        assertEquals("02/2015", formatDateCompact("Febrero de 2015"))
        assertEquals("09/2013", formatDateCompact("09/2013"))
        assertEquals("2018", formatDateCompact("2018"))
        assertEquals("s/f", formatDateCompact(""))
    }

    // ── extraerFecha: búsqueda dentro de publicación / localización ──

    @Test
    fun `extrae mes y anio de la localizacion de gaceta`() {
        val f = extraerFecha("S.J.F. y su Gaceta; Décima Época; Primera Sala; Libro 19, Febrero de 2015, Tomo I")
        assertEquals(2, f?.mes)
        assertEquals(2015, f?.anio)
        assertEquals(null, f?.dia)
        assertEquals("febrero del 2015", f?.legible())
    }

    @Test
    fun `extrae fecha completa de la publicacion`() {
        val f = extraerFecha("Aprobada por la Primera Sala en sesión de 26 de enero de 2018, por unanimidad")
        assertEquals("26 de enero del 2018", f?.legible())
    }

    @Test
    fun `no inventa fechas`() {
        assertNull(extraerFecha("Semanario Judicial de la Federación, Décima Época, Pleno"))
        assertNull(extraerFecha(""))
    }

    @Test
    fun `prefiere fecha completa sobre mes y anio`() {
        val f = extraerFecha("Tomo I, 15 de marzo de 2016 y Gaceta de abril de 2016")
        assertEquals("15 de marzo del 2016", f?.legible())
    }

    // ── Epoch ──

    @Test
    fun `epoch en segundos y milisegundos`() {
        assertEquals(FechaLegible(1, 2, 2015).legible(), epochAFechaLegible(1422748800L)?.legible())
        assertEquals(FechaLegible(1, 2, 2015).legible(), epochAFechaLegible(1422748800000L)?.legible())
        assertEquals(FechaLegible(15, 9, 1937).legible(), epochAFechaLegible(-1019174400000L)?.legible())
    }

    @Test
    fun `epoch implausible se rechaza`() {
        assertNull(epochPlausibleMs(0L))
        assertNull(epochPlausibleMs(99_999_999_999_999L))
        assertNull(epochAFechaLegible(99_999_999_999_999L))
    }

    // ── Orden cronológico (ordenar por RECIENTES) ──

    @Test
    fun `orden cronologico`() {
        val reciente = parseFecha("febrero de 2015")
        val completa = parseFecha("26 de enero del 2015")
        val viejo = parseFecha("diciembre de 2014")
        assertTrue(reciente > completa)
        assertTrue(completa > viejo)
        assertTrue(viejo > 0L)
        assertEquals(0L, parseFecha(""))
    }

    // ── Tesis.conMetadatosDe: relleno de metadatos del resultado de búsqueda ──

    @Test
    fun `conMetadatosDe hereda fecha del resultado de busqueda`() {
        val deBusqueda = Tesis(registro = 2004415, rubro = "EXTRADICIÓN", fecha = "26 de enero del 2018", epoca = "Décima Época")
        val deDetalle = Tesis(registro = 2004415, rubro = "EXTRADICIÓN", texto = "El texto completo…")
        val completa = deDetalle.conMetadatosDe(deBusqueda)
        assertEquals("26 de enero del 2018", completa.fecha)
        assertEquals("Décima Época", completa.epoca)
        assertEquals("El texto completo…", completa.texto)
    }

    @Test
    fun `conMetadatosDe conserva lo que ya trae el detalle`() {
        val deBusqueda = Tesis(registro = 1, fecha = "1 de enero del 2000", epoca = "Novena Época")
        val deDetalle = Tesis(registro = 1, fecha = "15 de marzo del 2016", epoca = "Décima Época", texto = "…")
        val completa = deDetalle.conMetadatosDe(deBusqueda)
        assertEquals("15 de marzo del 2016", completa.fecha)
        assertEquals("Décima Época", completa.epoca)
    }

    @Test
    fun `conMetadatosDe nunca hereda texto ni registro`() {
        val deBusqueda = Tesis(registro = 2, texto = "texto de búsqueda")
        val deDetalle = Tesis(registro = 1)
        val completa = deDetalle.conMetadatosDe(deBusqueda)
        assertEquals("", completa.texto)
        assertEquals(1L, completa.registro)
    }
}
