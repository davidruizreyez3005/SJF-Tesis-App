package mx.sjf.tesis.ui.screens.downloads

import android.app.Activity
import android.app.RecoverableSecurityException
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.sjf.tesis.ui.theme.NavyBg
import mx.sjf.tesis.viewmodel.AppViewModel

import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mx.sjf.tesis.data.util.FileSharer
import mx.sjf.tesis.ui.components.EmptyState
import mx.sjf.tesis.ui.theme.Gold
import mx.sjf.tesis.ui.theme.GoldLight
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Representación de un archivo descargado (PDF o ZIP). */
data class DownloadedFile(
    val uri: Uri,
    val name: String,
    val size: Long,
    val dateAdded: Long,
    val mimeType: String
)

@Composable
fun DownloadsScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val files = remember { mutableStateListOf<DownloadedFile>() }
    var loaded by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<DownloadedFile?>(null) }
    // Archivo cuyo borrado espera la confirmación del sistema (Android 10+).
    var awaitingSystem by remember { mutableStateOf<DownloadedFile?>(null) }

    // Cargar archivos de MediaStore cada vez que la pantalla aparece.
    LaunchedEffect(Unit) {
        val found = withContext(Dispatchers.IO) { queryDownloadedFiles(context) }
        files.clear()
        files.addAll(found)
        loaded = true
    }

    val systemDeleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val file = awaitingSystem
        awaitingSystem = null
        if (result.resultCode == Activity.RESULT_OK && file != null) {
            // En Android 11+ el sistema ya borró el archivo; en Android 10 solo
            // concedió el permiso y hay que repetir el borrado.
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                runCatching { context.contentResolver.delete(file.uri, null, null) }
            }
            files.remove(file)
            vm.notify("Archivo eliminado")
        }
    }

    fun delete(file: DownloadedFile) {
        try {
            context.contentResolver.delete(file.uri, null, null)
            files.remove(file)
            vm.notify("Archivo eliminado")
        } catch (e: SecurityException) {
            // Archivos creados por una instalación anterior de la app: Android
            // pide confirmación explícita del usuario para borrarlos.
            val sender = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                    MediaStore.createDeleteRequest(context.contentResolver, listOf(file.uri)).intentSender
                Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && e is RecoverableSecurityException ->
                    e.userAction.actionIntent.intentSender
                else -> null
            }
            if (sender != null) {
                awaitingSystem = file
                systemDeleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
            } else {
                vm.notify("No se pudo eliminar el archivo")
            }
        } catch (e: Exception) {
            vm.notify("No se pudo eliminar el archivo")
        }
    }

    pendingDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("¿Eliminar archivo?") },
            text = { Text("«${file.name}» se borrará de Documents/SJF_Tesis. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = { pendingDelete = null; delete(file) }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") }
            }
        )
    }

    if (loaded && files.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.FolderOff,
            title = "Sin descargas",
            subtitle = "Los PDF y ZIP que generes aparecerán aquí y en la carpeta Documents/SJF_Tesis de tu teléfono."
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    if (files.size == 1) "1 archivo · Documents/SJF_Tesis" else "${files.size} archivos · Documents/SJF_Tesis",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
            }
            items(files, key = { it.uri.toString() }) { file ->
                DownloadedFileCard(
                    file = file,
                    onOpen = {
                        val intent = FileSharer.buildViewIntent(file.uri, file.mimeType)
                        runCatching { context.startActivity(intent) }
                            .onFailure { vm.notify("No hay una app instalada para abrir este archivo") }
                    },
                    onShare = {
                        val intent = FileSharer.buildShareIntent(file.uri, file.mimeType)
                        runCatching { context.startActivity(Intent.createChooser(intent, "Compartir ${file.name}")) }
                            .onFailure { vm.notify("No se pudo compartir el archivo") }
                    },
                    onDelete = { pendingDelete = file }
                )
            }
        }
    }
}

@Composable
private fun DownloadedFileCard(
    file: DownloadedFile,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.verticalGradient(listOf(Gold, GoldLight))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (file.mimeType == "application/zip") Icons.Outlined.FolderZip else Icons.Outlined.PictureAsPdf,
                    contentDescription = null,
                    tint = NavyBg,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    file.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Row {
                    Text(
                        formatFileSize(file.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        formatDate(file.dateAdded),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onShare) {
                Icon(Icons.Outlined.Share, "Compartir", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            IconButton(onDelete) {
                Icon(Icons.Outlined.Delete, "Eliminar", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/**
 * Consulta MediaStore para listar archivos PDF y ZIP en Documents/SJF_Tesis/.
 * En Android 10+ filtra por RELATIVE_PATH; en Android 9- filtra por prefijo
 * del nombre (Tesis_, Ejecutoria_, Voto_, Expediente_ o SJF_Tesis_).
 */
private fun queryDownloadedFiles(context: android.content.Context): List<DownloadedFile> {
    val results = mutableListOf<DownloadedFile>()
    val collection = MediaStore.Files.getContentUri("external")
    val projection = arrayOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.SIZE,
        MediaStore.MediaColumns.DATE_ADDED,
        MediaStore.MediaColumns.MIME_TYPE
    )
    val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

    // Intento 1: filtrar por RELATIVE_PATH (Android 10+)
    runCatching {
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND (${MediaStore.MediaColumns.MIME_TYPE} = ? OR ${MediaStore.MediaColumns.MIME_TYPE} = ?)"
        val selectionArgs = arrayOf("%SJF_Tesis%", "application/pdf", "application/zip")
        context.contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "Sin nombre"
                val size = cursor.getLong(sizeCol)
                val dateAdded = cursor.getLong(dateCol) * 1000L
                val mimeType = cursor.getString(mimeCol) ?: "application/octet-stream"
                val uri = Uri.withAppendedPath(collection, id.toString())
                results += DownloadedFile(uri, name, size, dateAdded, mimeType)
            }
        }
    }

    // Intento 2 (fallback Android 9-): listar todos los PDF/ZIP y filtrar por nombre
    if (results.isEmpty()) {
        runCatching {
            val selection = "${MediaStore.MediaColumns.MIME_TYPE} = ? OR ${MediaStore.MediaColumns.MIME_TYPE} = ?"
            val selectionArgs = arrayOf("application/pdf", "application/zip")
            context.contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol) ?: continue
                    if (PREFIJOS_PROPIOS.none(name::startsWith)) continue
                    val id = cursor.getLong(idCol)
                    val size = cursor.getLong(sizeCol)
                    val dateAdded = cursor.getLong(dateCol) * 1000L
                    val mimeType = cursor.getString(mimeCol) ?: "application/octet-stream"
                    val uri = Uri.withAppendedPath(collection, id.toString())
                    results += DownloadedFile(uri, name, size, dateAdded, mimeType)
                }
            }
        }
    }

    return results
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    if (bytes < 1024 * 1024) return "${bytes / 1024} KB"
    return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}

private fun formatDate(timestamp: Long): String {
    if (timestamp == 0L) return ""
    return SimpleDateFormat("d MMM yyyy · HH:mm", Locale("es", "MX")).format(Date(timestamp))
}

/** Prefijos de los archivos que genera la app (ver [mx.sjf.tesis.pdf.DocumentosPdf]). */
private val PREFIJOS_PROPIOS = listOf("Tesis_", "Ejecutoria_", "Voto_", "Expediente_", "SJF_Tesis_")
