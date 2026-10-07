package mx.sjf.tesis.data.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ArchivosTest {
    @Test
    fun describeLosArchivosDeLaApp() {
        assertEquals(ArchivoDescrito(TipoArchivo.TESIS, "P-J-2-2006 · Registro 175940"), describirArchivo("Tesis_P-J-2-2006_175940.pdf"))
        assertEquals(ArchivoDescrito(TipoArchivo.TESIS, "Registro digital 2032690"), describirArchivo("Tesis_2032690.pdf"))
        assertEquals(ArchivoDescrito(TipoArchivo.EJECUTORIA, "Registro digital 19501"), describirArchivo("Ejecutoria_19501.pdf"))
        assertEquals(ArchivoDescrito(TipoArchivo.VOTO, "Registro digital 20582"), describirArchivo("Voto_20582.pdf"))
        assertEquals(ArchivoDescrito(TipoArchivo.EXPEDIENTE, "P-J-2-2006 · Registro 175940"), describirArchivo("Expediente_P-J-2-2006_175940.pdf"))
        assertEquals(ArchivoDescrito(TipoArchivo.DOCUMENTOS, "P-J-2-2006 · Registro 175940"), describirArchivo("Tesis_P-J-2-2006_175940_documentos.zip"))
        assertEquals(TipoArchivo.COMPILACION, describirArchivo("SJF_Tesis_Compilacion_20261007_101500.pdf").tipo)
        assertEquals(TipoArchivo.LOTE, describirArchivo("SJF_Tesis_20261007_101500.zip").tipo)
        assertEquals(ArchivoDescrito(TipoArchivo.OTRO, "otro.pdf"), describirArchivo("otro.pdf"))
        // Duplicados que agrega el sistema («Voto_20582 (1).pdf») no rompen la descripción.
        assertEquals(TipoArchivo.VOTO, describirArchivo("Voto_20582 (1).pdf").tipo)
    }
}
