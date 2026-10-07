package mx.sjf.tesis.data.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas de utilidades de texto: fechas, acentos, nombres de archivo, tipos. */
class TextUtilsTest {

    // ── foldAccents ──

    @Test
    fun foldAccents_eliminaDiacriticos() {
        assertEquals("AEIOUnu", "ÁÉÍÓÚñü".foldAccents())
        assertEquals("amparo", "ámparo".foldAccents())
        assertEquals("sin cambios", "sin cambios".foldAccents())
    }

    // ── countOccurrences ──

    @Test
    fun countOccurrences_cuentaAparicionesNoSolapadas() {
        assertEquals(2, "amparo amparo".countOccurrences("amparo"))
        assertEquals(1, "aaa".countOccurrences("aa")) // no solapa
        assertEquals(0, "tesis".countOccurrences("voto"))
        assertEquals(3, "a b a b a".countOccurrences("a"))
    }

    // ── parseFecha ──

    @Test
    fun parseFecha_formatoLargoEnEspanol() {
        val ts = parseFecha("18 de mayo de 2018")
        assertTrue("debe parsear fecha larga es-MX", ts != 0L)
        // Round-trip independiente de la zona horaria del JVM de pruebas.
        // Formato v2.2.2: «(día) de (mes) del (año)».
        assertEquals("18 de mayo de 2018", formatDateLong("18 de mayo de 2018"))
    }

    @Test
    fun parseFecha_formatosNumericosEISO() {
        assertTrue(parseFecha("18/05/2018") != 0L)
        assertTrue(parseFecha("2018-05-18") != 0L)
        assertTrue(parseFecha("2018/05/18") != 0L)
    }

    @Test
    fun parseFecha_entradasInvalidasDevuelvenCero() {
        assertEquals(0L, parseFecha(""))
        assertEquals(0L, parseFecha("fecha imposible"))
        assertEquals(0L, parseFecha("99 de nubre de 9999"))
    }

    // ── formatDateLong / formatDateCompact ──

    @Test
    fun formatDateLong_formateaFechaLarga() {
        assertEquals("18 de mayo de 2018", formatDateLong("18/05/2018"))
    }

    @Test
    fun formatDateLong_fechaVaciaSinFecha() {
        assertEquals("Sin fecha", formatDateLong(""))
    }

    @Test
    fun formatDateLong_textoIrreconocibleSeDevuelveTalCual() {
        assertEquals("publicación próxima", formatDateLong("publicación próxima"))
    }

    @Test
    fun formatDateCompact_formateaDDMMYYYY() {
        assertEquals("18/05/2018", formatDateCompact("18 de mayo de 2018"))
    }

    // ── extractYear ──

    @Test
    fun extractYear_encuentraCuatroDigitos() {
        assertEquals("2018", extractYear("Tesis publicada en 2018 por la SCJN"))
        assertEquals("1995", extractYear("Semanario 1995"))
    }

    @Test
    fun extractYear_sinAnioDevuelveVacio() {
        assertEquals("", extractYear("sin año"))
        assertEquals("", extractYear(""))
    }

    // ── tipoGrupo ──

    @Test
    fun tipoGrupo_mapeaJurisprudenciaYTesis() {
        assertEquals("J", tipoGrupo("Jurisprudencia"))
        assertEquals("J", tipoGrupo("jurisprudencia por reiteración"))
        assertEquals("T", tipoGrupo("Tesis Aislada"))
        assertEquals("T", tipoGrupo("tesis"))
        assertEquals("", tipoGrupo("Ejecutoria"))
    }

    // ── sanitizeFileName ──

    @Test
    fun sanitizeFileName_reemplazaCaracteresProhibidos() {
        assertEquals("Tesis_35_amparo_", sanitizeFileName("Tesis:35*amparo?"))
        assertEquals("a_b_c_d_e_f_g_h", sanitizeFileName("a/b\\c:d\"e<f>g|h"))
    }

    @Test
    fun sanitizeFileName_truncaASesentaCaracteres() {
        val largo = "a".repeat(120)
        assertEquals(60, sanitizeFileName(largo).length)
    }

    @Test
    fun sanitizeFileName_vacioDevuelveTesis() {
        assertEquals("tesis", sanitizeFileName("   "))
    }

    // ── truncateWords ──

    @Test
    fun truncateWords_cortaEnLimiteDePalabra() {
        val texto = "principio de presunción de inocencia y sus alcances procesales"
        val corto = truncateWords(texto, 20)
        assertTrue(corto.length <= 21)
        assertTrue(corto.endsWith("…"))
        assertTrue(corto.startsWith("principio de"))
    }

    @Test
    fun truncateWords_textoCortoQuedaIgual() {
        assertEquals("amparo", truncateWords("amparo", 20))
    }
}
