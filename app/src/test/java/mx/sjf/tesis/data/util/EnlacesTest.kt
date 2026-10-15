package mx.sjf.tesis.data.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EnlacesTest {
    @Test
    fun enlacesActuales() {
        assertEquals(EnlaceSjf.DeTesis(175940), enlaceSjf("https://sjf2.scjn.gob.mx/detalle/tesis/175940"))
        assertEquals(EnlaceSjf.DeEjecutoria(19501), enlaceSjf("https://sjf2.scjn.gob.mx/detalle/ejecutoria/19501"))
        assertEquals(EnlaceSjf.DeVoto(20582), enlaceSjf("http://SJF2.scjn.gob.mx/detalle/voto/20582?x=1#y"))
    }

    @Test
    fun enlacesDelSistemaAnterior() {
        assertEquals(
            EnlaceSjf.DeTesis(175940),
            enlaceSjf("https://sjf.scjn.gob.mx/SJFSist/Paginas/DetalleGeneralV2.aspx?ID=175940&Clase=DetalleTesisBL")
        )
        assertEquals(
            EnlaceSjf.DeEjecutoria(19501),
            enlaceSjf("https://sjf.scjn.gob.mx/SJFSist/paginas/DetalleGeneralV2.aspx?Clase=DetalleTesisEjecutorias&ID=19501&Hit=1")
        )
        assertEquals(
            EnlaceSjf.DeVoto(20582),
            enlaceSjf("https://sjf.scjn.gob.mx/SJFSist/Paginas/DetalleGeneralV2.aspx?id=20582&Clase=VotosDetalleBL")
        )
    }

    @Test
    fun enlaceDentroDeUnTextoCompartido() {
        assertEquals(
            EnlaceSjf.DeTesis(2032690),
            enlaceSjf("Mira esta tesis:\nSemanario Judicial de la Federación https://sjf2.scjn.gob.mx/detalle/tesis/2032690 saludos")
        )
    }

    @Test
    fun sinEnlace() {
        assertNull(enlaceSjf("extradición prisión vitalicia"))
        assertNull(enlaceSjf("175940"))
        assertNull(enlaceSjf("https://www.scjn.gob.mx/detalle/tesis/175940"))
        assertNull(enlaceSjf("https://sjf.scjn.gob.mx/SJFSist/Paginas/DetalleGeneralV2.aspx?Clase=DetalleTesisBL"))
    }
}
