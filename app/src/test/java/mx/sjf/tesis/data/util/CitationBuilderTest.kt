package mx.sjf.tesis.data.util

import mx.sjf.tesis.data.model.CitationStyle
import mx.sjf.tesis.data.model.Tesis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del constructor de citas. Las muestras reproducen registros REALES
 * tal como los entrega la API de sjf2.scjn.gob.mx (ya pasados por parseTesis),
 * uno por cada forma de publicación:
 *
 *  - 2032690: jurisprudencia del Pleno, 12a. Época, semanario electrónico
 *    (sin página; se cita por fecha y hora de publicación).
 *  - 2004236: tesis aislada de T.C.C., 10a. Época, Gaceta impresa
 *    (libro, tomo y página).
 *  - 172000: jurisprudencia de T.C.C., 9a. Época, Gaceta impresa (tomo y página).
 */
class CitationBuilderTest {

    private val pleno12a = Tesis(
        registro = 2032690L,
        rubro = "ASISTENCIA CONSULAR A VÍCTIMAS EXTRANJERAS EN EL PROCEDIMIENTO PENAL. SU OMISIÓN NO GENERA AUTOMÁTICAMENTE LA ILICITUD NI LA EXCLUSIÓN DE SUS DECLARACIONES, SINO QUE EXIGE UN ANÁLISIS CASUÍSTICO SOBRE SU INCIDENCIA REAL, DIRECTA Y SUSTANCIAL EN EL DEBIDO PROCESO.",
        texto = "Hechos: En un proceso penal seguido por los delitos de delincuencia organizada y secuestro…",
        epoca = "Duodécima Época",
        instancia = "Pleno",
        tipo = "Jurisprudencia",
        materia = "Constitucional, Penal",
        fecha = "2 de octubre del 2026",
        precedente = "Amparo directo en revisión 6627/2025. 25 de marzo de 2026.",
        clave = "P./J. 191/2026 (12a.)",
        fuente = "Semanario Judicial de la Federación",
        volumen = "Libro 14, Octubre de 2026",
        publicacion = "Esta tesis se publicó el viernes 02 de octubre de 2026 a las 10:12 horas en el Semanario Judicial de la Federación y, por ende, se considera de aplicación obligatoria a partir del lunes 05 de octubre de 2026, para los efectos previstos en el punto octavo del Acuerdo General Plenario 7/2025 (12a.)."
    )

    private val tcc10a = Tesis(
        registro = 2004236L,
        rubro = "IMPEDIMENTO MANIFESTADO POR UN JUEZ DE DISTRITO O MAGISTRADO DE CIRCUITO. NO PROCEDE TRATÁNDOSE DE LA SUSPENSIÓN DE OFICIO.",
        texto = "El artículo 53 de la Ley de Amparo establece que el que se excuse deberá…",
        epoca = "Décima Época",
        instancia = "Tribunales Colegiados de Circuito",
        tipo = "Tesis Aislada",
        materia = "Común",
        fecha = "agosto del 2013",
        precedente = "Impedimento 2/2013. Magistrado Hugo Sahuer Hernández. 19 de abril de 2013.",
        clave = "XI.1o.A.T.8 K (10a.)",
        fuente = "Semanario Judicial de la Federación y su Gaceta",
        volumen = "Libro XXIII, Agosto de 2013",
        tomo = "Tomo 3",
        pagina = "1659"
    )

    private val tcc9a = Tesis(
        registro = 172000L,
        rubro = "JUICIO CONTENCIOSO ADMINISTRATIVO. TRATÁNDOSE DE ACTIVIDADES REGLAMENTADAS (LEGISLACIÓN DEL DISTRITO FEDERAL).",
        epoca = "Novena Época",
        instancia = "Tribunales Colegiados de Circuito",
        tipo = "Jurisprudencia",
        materia = "Administrativa",
        clave = "I.7o.A. J/36",
        fuente = "Semanario Judicial de la Federación y su Gaceta",
        volumen = "Tomo XXVI, Julio de 2007",
        pagina = "2331"
    )

    // ── Cita jurídica (escritos) ──

    @Test
    fun legal_semanarioElectronico_citaFechaYHoraDePublicacion() {
        val cita = CitationBuilder.legalFormat(pleno12a)
        assertTrue(cita.startsWith(
            "Lo anterior encuentra sustento en la jurisprudencia P./J. 191/2026 (12a.), " +
                "emitida por el Pleno de la Suprema Corte de Justicia de la Nación, " +
                "publicada en el Semanario Judicial de la Federación, Duodécima Época, " +
                "el viernes 2 de octubre de 2026 a las 10:12 horas, " +
                "registro digital 2032690, de rubro y texto siguientes:"
        ))
        assertTrue(cita.contains("\n\n${pleno12a.rubro}\n\nHechos:"))
        assertTrue(cita.endsWith("Amparo directo en revisión 6627/2025. 25 de marzo de 2026."))
    }

    @Test
    fun legal_gaceta_citaLibroTomoYPagina() {
        val cita = CitationBuilder.legalFormat(tcc10a)
        assertTrue(cita.startsWith(
            "Lo anterior encuentra sustento en la tesis aislada XI.1o.A.T.8 K (10a.), " +
                "emitida por un Tribunal Colegiado de Circuito, " +
                "publicada en el Semanario Judicial de la Federación y su Gaceta, Décima Época, " +
                "Libro XXIII, agosto de 2013, Tomo 3, página 1659, " +
                "registro digital 2004236, de rubro y texto siguientes:"
        ))
    }

    @Test
    fun legal_salaUsaArticuloFemenino() {
        val cita = CitationBuilder.legalFormat(pleno12a.copy(instancia = "Primera Sala"))
        assertTrue(cita.contains("emitida por la Primera Sala de la Suprema Corte de Justicia de la Nación,"))
    }

    // ── Ficha oficial ──

    @Test
    fun ficha_mismosCamposYOrdenQueElSemanario() {
        val ficha = CitationBuilder.scjnFormat(tcc10a)
        assertTrue(ficha.startsWith(
            "Registro digital: 2004236\n" +
                "Tesis: XI.1o.A.T.8 K (10a.)\n" +
                "Décima Época\n" +
                "Tipo: Aislada\n" +
                "Instancia: Tribunales Colegiados de Circuito\n" +
                "Materia(s): Común\n" +
                "Fuente: Semanario Judicial de la Federación y su Gaceta. Libro XXIII, agosto de 2013, Tomo 3, página 1659\n\n" +
                tcc10a.rubro
        ))
        assertTrue(ficha.endsWith("https://sjf2.scjn.gob.mx/detalle/tesis/2004236"))
    }

    @Test
    fun ficha_semanarioIncluyeNotaDePublicacion() {
        val ficha = CitationBuilder.scjnFormat(pleno12a)
        assertTrue(ficha.contains("Tipo: Jurisprudencia\n"))
        assertTrue(ficha.contains("Esta tesis se publicó el viernes 02 de octubre de 2026 a las 10:12 horas"))
    }

    // ── Académica (criterios editoriales de la SCJN) ──

    @Test
    fun academica_novenaEpoca_comoEjemploDeLaScjn() {
        assertEquals(
            "Tesis [J.]: I.7o.A. J/36, T.C.C., Semanario Judicial de la Federación y su Gaceta, " +
                "Novena Época, tomo XXVI, julio de 2007, p. 2331. Reg. digital 172000.",
            CitationBuilder.academicFormat(tcc9a)
        )
    }

    @Test
    fun academica_soloDigitalSinPagina() {
        assertEquals(
            "Tesis [J.]: P./J. 191/2026 (12a.), Semanario Judicial de la Federación, " +
                "Duodécima Época, libro 14, octubre de 2026, s. p. Reg. digital 2032690.",
            CitationBuilder.academicFormat(pleno12a)
        )
    }

    // ── Cita breve ──

    @Test
    fun breve_unaLineaConClaveRubroYLocalizacion() {
        val cita = CitationBuilder.plainFormat(tcc10a)
        assertEquals(
            "Tesis aislada XI.1o.A.T.8 K (10a.), de rubro: \"IMPEDIMENTO MANIFESTADO POR UN JUEZ DE DISTRITO O MAGISTRADO DE CIRCUITO. " +
                "NO PROCEDE TRATÁNDOSE DE LA SUSPENSIÓN DE OFICIO\", Semanario Judicial de la Federación y su Gaceta, " +
                "Décima Época, Libro XXIII, agosto de 2013, Tomo 3, página 1659, registro digital 2004236.",
            cita
        )
        assertFalse(cita.contains("\n"))
    }

    @Test
    fun breve_semanarioDiceFechaDePublicacion() {
        val cita = CitationBuilder.plainFormat(pleno12a)
        assertTrue(cita.endsWith(
            "Semanario Judicial de la Federación, Duodécima Época, publicada el viernes 2 de octubre de 2026 a las 10:12 horas, registro digital 2032690."
        ))
    }

    // ── Datos incompletos (tesis guardadas con versiones anteriores) ──

    @Test
    fun sinDatosOficiales_degradaSinInventar() {
        val vieja = Tesis(
            registro = 2018045L, rubro = "RUBRO.", epoca = "10a. Época",
            instancia = "Primera Sala", tipo = "Jurisprudencia", fecha = "18 de mayo de 2018"
        )
        assertEquals(
            "Jurisprudencia, de rubro: \"RUBRO\", Semanario Judicial de la Federación, Décima Época, " +
                "18 de mayo de 2018, registro digital 2018045.",
            CitationBuilder.plainFormat(vieja)
        )
        assertTrue(CitationBuilder.academicFormat(vieja).startsWith("Tesis [J.]: s. n.,"))
    }

    // ── Piezas ──

    @Test
    fun notaDePublicacion_extraeFechas() {
        assertEquals("viernes 2 de octubre de 2026 a las 10:12 horas", CitationBuilder.fechaDePublicacion(pleno12a.publicacion))
        assertEquals("lunes 5 de octubre de 2026", CitationBuilder.obligatoriaDesde(pleno12a.publicacion))
        assertEquals(
            "18 de marzo de 2025",
            CitationBuilder.obligatoriaDesde(
                "Esta tesis se publicó el viernes 14 de marzo de 2025 a las 10:17 horas en el Semanario Judicial de la " +
                    "Federación y, por ende, se considera de aplicación obligatoria a partir del día hábil siguiente, " +
                    "18 de marzo de 2025, para los efectos previstos en el punto noveno del Acuerdo General Plenario 1/2021."
            )
        )
        assertEquals(null, CitationBuilder.fechaDePublicacion(""))
    }

    @Test
    fun emisor_porInstancia() {
        assertEquals("un Pleno de Circuito", CitationBuilder.emisor("Plenos de Circuito"))
        assertEquals("un Pleno Regional", CitationBuilder.emisor("Plenos Regionales"))
        assertEquals("la Segunda Sala de la Suprema Corte de Justicia de la Nación", CitationBuilder.emisor("Segunda Sala"))
        assertEquals("la Suprema Corte de Justicia de la Nación", CitationBuilder.emisor(""))
    }

    @Test
    fun epoca_abreviadaSeExpande() {
        assertEquals("Undécima Época", CitationBuilder.epocaCompleta("11a. Época"))
        assertEquals("Novena Época", CitationBuilder.epocaCompleta("Novena Época"))
    }

    // ── HTML ──

    @Test
    fun html_escapaYEnlazaAlRegistro() {
        val cita = CitationBuilder.htmlFormat(tcc10a.copy(rubro = "AMPARO & <PROTECCIÓN> \"DIRECTO\""))
        assertTrue(cita.contains("&amp;"))
        assertTrue(cita.contains("&lt;PROTECCIÓN&gt;"))
        assertTrue(cita.contains("&quot;DIRECTO&quot;"))
        assertFalse(cita.contains("<PROTECCIÓN>"))
        assertTrue(cita.contains("<a href=\"https://sjf2.scjn.gob.mx/detalle/tesis/2004236\""))
    }

    // ── Dispatcher y lote ──

    @Test
    fun build_despachaAlEstiloCorrecto() {
        for (t in listOf(pleno12a, tcc10a)) {
            assertEquals(CitationBuilder.legalFormat(t), CitationBuilder.build(t, CitationStyle.LEGAL))
            assertEquals(CitationBuilder.scjnFormat(t), CitationBuilder.build(t, CitationStyle.SCJN))
            assertEquals(CitationBuilder.academicFormat(t), CitationBuilder.build(t, CitationStyle.ACADEMIC))
            assertEquals(CitationBuilder.plainFormat(t), CitationBuilder.build(t, CitationStyle.PLAIN))
            assertEquals(CitationBuilder.htmlFormat(t), CitationBuilder.build(t, CitationStyle.HTML))
        }
    }

    @Test
    fun buildBatch_separaConLineaDivisoria() {
        val lote = CitationBuilder.buildBatch(listOf(pleno12a, tcc10a), CitationStyle.SCJN)
        assertTrue(lote.contains("———"))
        assertTrue(lote.contains("Registro digital: 2032690"))
        assertTrue(lote.contains("Registro digital: 2004236"))
    }
}
