package mx.sjf.tesis.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.sjf.tesis.ui.theme.isDark

/** Cepillo shimmer animado para skeletons de carga. */
@Composable
fun shimmerBrush(): Brush {
    val dark = isDark()
    val base = if (dark) Color(0xFF1B3A5C) else Color(0xFFE4EAF1)
    val highlight = if (dark) Color(0xFF2A537E) else Color(0xFFF5F8FC)
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "progress"
    )
    return Brush.linearGradient(
        listOf(base, highlight, base),
        Offset(-400f + 800f * progress, 0f),
        Offset(800f * progress, 400f)
    )
}

@Composable
fun ShimmerBox(modifier: Modifier, corner: Dp = 8.dp) {
    Box(modifier.clip(RoundedCornerShape(corner)).background(shimmerBrush()))
}

/** Skeleton completo de tarjeta de tesis en carga. */
@Composable
fun ResultSkeleton() {
    repeat(3) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShimmerBox(Modifier.height(18.dp).width(64.dp), 6.dp)
                    ShimmerBox(Modifier.height(18.dp).width(84.dp), 6.dp)
                    ShimmerBox(Modifier.height(18.dp).width(56.dp), 6.dp)
                }
                Spacer(Modifier.height(12.dp))
                ShimmerBox(Modifier.fillMaxWidth().height(15.dp))
                Spacer(Modifier.height(7.dp))
                ShimmerBox(Modifier.fillMaxWidth(0.75f).height(15.dp))
                Spacer(Modifier.height(12.dp))
                ShimmerBox(Modifier.fillMaxWidth().height(11.dp))
                Spacer(Modifier.height(6.dp))
                ShimmerBox(Modifier.fillMaxWidth(0.88f).height(11.dp))
                Spacer(Modifier.height(6.dp))
                ShimmerBox(Modifier.fillMaxWidth(0.5f).height(11.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** Estado vacío con ícono circular y texto explicativo. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(80.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(5.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            androidx.compose.material3.OutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Indicador de carga para operaciones largas (export PDF, etc.). */
@Composable
fun ProgressBlock(message: String, detail: String? = null) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            Modifier.size(36.dp),
            strokeWidth = 3.dp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(12.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        if (detail != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/** Caja con borde redondeado y texto monoespaciado para logs. */
@Composable
fun ConsoleBox(content: String, modifier: Modifier = Modifier, height: Dp = 240.dp) {
    val scroll = rememberScrollState()
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFF23364D), RoundedCornerShape(10.dp))
            .background(Color(0xFF0A1017))
            .height(height)
            .verticalScroll(scroll)
            .padding(10.dp)
    ) {
        if (content.isEmpty()) {
            Text(
                "Sin actividad todavía.\nEjecuta una búsqueda o el diagnóstico.",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = Color(0xFF8FA3BC)
            )
        } else {
            content.split("\n").forEach { line ->
                Text(line, fontFamily = FontFamily.Monospace, fontSize = 10.5f.sp, color = colorFor(line))
            }
        }
    }
}

private fun colorFor(line: String): Color = when {
    line.contains("✗") -> Color(0xFFF87171)
    line.contains("✓") -> Color(0xFF4ADE80)
    line.startsWith("←") || line.startsWith("→") -> Color(0xFFE8D5A0)
    else -> Color(0xFF9FB3CC)
}
