package mx.sjf.tesis.data.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import mx.sjf.tesis.core.Constants
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Helper para guardar archivos en una carpeta pública visible en el administrador
 * de archivos de Android (Documents/SJF_Tesis/).
 *
 * - Android 10+ (API 29+): usa MediaStore API (no requiere permisos de almacenamiento)
 * - Android 9 y anteriores: usa escritura directa en Environment.getExternalStoragePublicDirectory
 *   (requiere WRITE_EXTERNAL_STORAGE, solicitado en tiempo de ejecución)
 *
 * El directorio público es: Documents/SJF_Tesis/
 * Visible en: Files app → Documents → SJF_Tesis
 * Ruta completa típica: /storage/emulated/0/Documents/SJF_Tesis/
 */
object PdfStorage {

    const val PUBLIC_DIR_NAME = "SJF_Tesis"

    /**
     * Guarda un archivo (PDF o ZIP) en Documents/SJF_Tesis/.
     * Devuelve la URI pública del archivo creado (para compartir/abrir).
     *
     * @param context Contexto de la aplicación
     * @param fileName Nombre del archivo (ej: "Tesis_2032514.pdf")
     * @param mimeType Tipo MIME (ej: "application/pdf", "application/zip")
     * @param writer Callback que escribe el contenido en el OutputStream
     * @return Result con la URI pública del archivo
     */
    fun saveToPublicDocuments(
        context: Context,
        fileName: String,
        mimeType: String,
        writer: (OutputStream) -> Unit
    ): Result<Uri> {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(context, fileName, mimeType, writer)
            } else {
                saveViaDirectFile(context, fileName, mimeType, writer)
            }
        }
    }

    /**
     * Android 10+ (API 29+): usa MediaStore para crear el archivo en el directorio
     * público Documents/SJF_Tesis/ sin necesidad de permisos de almacenamiento.
     */
    private fun saveViaMediaStore(
        context: Context,
        fileName: String,
        mimeType: String,
        writer: (OutputStream) -> Unit
    ): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            // Subcarpeta dentro de Documents/
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/$PUBLIC_DIR_NAME")
            // Para que el archivo sea permanente (no se borre al limpiar caché)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        // Usar la colección genérica de archivos externos — funciona para PDF y ZIP.
        val collection = MediaStore.Files.getContentUri("external")

        // Primero borrar si ya existe un archivo con el mismo nombre
        deleteExisting(resolver, fileName)

        val uri = resolver.insert(collection, values)
            ?: throw java.io.IOException("No se pudo crear el archivo en MediaStore")

        resolver.openOutputStream(uri)?.use { os ->
            writer(os)
            os.flush()
        } ?: throw java.io.IOException("No se pudo abrir OutputStream para $uri")

        // Marcar como completado (Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val finalValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(uri, finalValues, null, null)
        }

        return uri
    }

    /**
     * Android 9 y anteriores: escribe directamente en
     * Environment.getExternalStoragePublicDirectory(Documents)/SJF_Tesis/.
     * Requiere WRITE_EXTERNAL_STORAGE (solicitado en runtime).
     */
    private fun saveViaDirectFile(
        context: Context,
        fileName: String,
        mimeType: String,
        writer: (OutputStream) -> Unit
    ): Uri {
        val docsDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            PUBLIC_DIR_NAME
        )
        if (!docsDir.exists()) docsDir.mkdirs()

        val file = File(docsDir, fileName)
        FileOutputStream(file).use { fos ->
            writer(fos)
            fos.flush()
        }

        // Devolver URI via FileProvider para compartir/abrir
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /**
     * Elimina un archivo existente con el mismo nombre en Documents/SJF_Tesis/.
     * Útil para sobrescribir PDFs sin duplicar.
     */
    private fun deleteExisting(resolver: android.content.ContentResolver, fileName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        try {
            val collection = MediaStore.Files.getContentUri("external")
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf(fileName, "%$PUBLIC_DIR_NAME%")
            resolver.delete(collection, selection, selectionArgs)
        } catch (e: Exception) {
            // Ignorar — si no se puede borrar, el insert creará un (2), (3), etc.
        }
    }

    /**
     * Devuelve la ruta legible por el usuario donde se guardarán los archivos.
     * Ej: "Documents/SJF_Tesis/" o "/storage/emulated/0/Documents/SJF_Tesis/"
     */
    fun publicPathDisplay(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "Documents/$PUBLIC_DIR_NAME/"
        } else {
            "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)}/$PUBLIC_DIR_NAME/"
        }
    }

    /**
     * Verifica si tenemos permiso para escribir en el almacenamiento público.
     * - Android 10+: siempre true (MediaStore no requiere permisos)
     * - Android 9-: verifica WRITE_EXTERNAL_STORAGE
     */
    fun hasWritePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            true
        } else {
            android.content.pm.PackageManager.PERMISSION_GRANTED ==
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
        }
    }
}
