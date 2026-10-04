package mx.sjf.tesis.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Contenedor con pull-to-refresh construido sobre nestedScroll, sin
 * dependencias adicionales (material3 1.2 aún no incluye el componente
 * oficial de PullToRefresh).
 *
 * Comportamiento:
 *  - Arrastrar hacia abajo con la lista arriba del todo hace crecer un
 *    indicador circular flotante (con amortiguación del 60%).
 *  - Al superar el umbral y soltar, se dispara [onRefresh] y el indicador
 *    gira como progreso indeterminado mientras [isRefreshing] sea true.
 *  - Arrastrar hacia arriba con el indicador visible lo colapsa antes de
 *    permitir el scroll del contenido.
 */
@Composable
fun PullToRefreshLayout(
    isRefreshing: Boolean,
    enabled: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { 72.dp.toPx() }
    var pullDistance by remember { mutableFloatStateOf(0f) }

    // Siempre leer los valores más recientes desde el connection (que se crea
    // una sola vez), evitando capturas obsoletas tras la recomposición.
    val currentOnRefresh by rememberUpdatedState(onRefresh)
    val currentRefreshing by rememberUpdatedState(isRefreshing)
    val currentEnabled by rememberUpdatedState(enabled)

    // Posición del indicador: mientras arrastra sigue al dedo; al soltar anima
    // a la posición de refresco o vuelve a ocultarse.
    val indicatorOffset by animateFloatAsState(
        targetValue = if (isRefreshing) thresholdPx else pullDistance,
        animationSpec = tween(durationMillis = 220),
        label = "pullIndicatorOffset"
    )

    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!currentEnabled || currentRefreshing) return Offset.Zero
                // Dedo hacia arriba con el indicador visible: colapsarlo primero.
                if (available.y < 0f && pullDistance > 0f) {
                    val previous = pullDistance
                    pullDistance = (pullDistance + available.y).coerceAtLeast(0f)
                    return Offset(0f, pullDistance - previous)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (!currentEnabled || currentRefreshing) return Offset.Zero
                if (source == NestedScrollSource.Drag && available.y > 0f) {
                    // Overscroll en la parte superior: hacer crecer el indicador
                    // con amortiguación para que se sienta natural.
                    val previous = pullDistance
                    pullDistance = (pullDistance + available.y * 0.6f).coerceAtMost(thresholdPx * 1.15f)
                    return Offset(0f, pullDistance - previous)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!currentEnabled || currentRefreshing) return Velocity.Zero
                val triggered = pullDistance >= thresholdPx
                pullDistance = 0f
                if (triggered) currentOnRefresh()
                return Velocity.Zero
            }
        }
    }

    Box(modifier.nestedScroll(connection)) {
        content()
        if (indicatorOffset > 0.5f) {
            val progress = (indicatorOffset / thresholdPx).coerceIn(0f, 1f)
            Surface(
                shape = CircleShape,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(0, (-48.dp.toPx() + indicatorOffset).roundToInt()) }
                    .size(42.dp)
                    .graphicsLayer {
                        alpha = progress
                        scaleX = 0.6f + 0.4f * progress
                        scaleY = 0.6f + 0.4f * progress
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = "Actualizar resultados",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(22.dp)
                                .graphicsLayer { rotationZ = progress * 180f }
                        )
                    }
                }
            }
        }
    }
}
