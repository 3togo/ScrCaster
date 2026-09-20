package io.github.togo3.scrcaster.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import io.github.togo3.scrcaster.R
import io.github.togo3.scrcaster.connection.ConnectionStatus
import io.github.togo3.scrcaster.connection.QrPairingUiState
import io.github.togo3.scrcaster.constants.UiSpacing
import io.github.togo3.scrcaster.util.QrCodeEncoder
import io.github.togo3.scrcaster.util.QrMatrix
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.layout.DialogDefaults
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@Composable
internal fun QrPairingDialog(
    state: QrPairingUiState,
    onDismiss: () -> Unit,
) {
    val view = LocalView.current
    val matrix = remember(state.payload) {
        state.payload.takeIf { it.isNotBlank() }
            ?.let { payload -> runCatching { QrCodeEncoder.encode(payload) }.getOrNull() }
    }
    val statusText = statusString(state.status)

    DisposableEffect(state.active) {
        if (state.active) view.keepScreenOn = true
        onDispose {
            if (state.active) view.keepScreenOn = false
        }
    }

    OverlayDialog(
        show = state.active,
        title = stringResource(R.string.device_pair_qr_title),
        summary = statusText,
        defaultWindowInsetsPadding = false,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentVertical),
        ) {
            if (matrix != null) {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    val windowHeight = LocalWindowInfo.current.containerDpSize.height
                    val side = minOf(maxWidth, maxHeight, windowHeight * QR_MAX_HEIGHT_FRACTION)
                    QrCodeCanvas(
                        matrix = matrix,
                        modifier = Modifier.size(side),
                    )
                }
            }
            Text(
                text = stringResource(R.string.device_pair_qr_desc),
                style = MiuixTheme.textStyles.footnote1,
                color = DialogDefaults.summaryColor(),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun statusString(status: ConnectionStatus): String = when (status) {
    ConnectionStatus.READY -> stringResource(R.string.device_pair_qr_waiting)
    ConnectionStatus.PAIRING -> stringResource(R.string.device_pair_qr_pairing)
    ConnectionStatus.FINDING_PORT -> stringResource(R.string.device_pair_qr_finding_port)
    ConnectionStatus.QR_TIMEOUT -> stringResource(R.string.device_pair_qr_timeout)
    ConnectionStatus.PAIR_FAILED -> stringResource(R.string.vm_pairing_failed)
    ConnectionStatus.PAIRED -> stringResource(R.string.device_pair_qr_connected)
    else -> stringResource(R.string.device_pair_qr_waiting)
}

@Composable
private fun QrCodeCanvas(
    matrix: QrMatrix,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val quiet = QrCodeEncoder.RENDER_QUIET_ZONE
        val total = matrix.size + quiet * 2
        val modulePx = size.minDimension / total
        drawRoundRect(
            color = QR_LIGHT,
            size = Size(total * modulePx, total * modulePx),
            cornerRadius = CornerRadius(modulePx * QUIET_CORNER_MODULES),
        )
        for (y in 0 until matrix.size) {
            for (x in 0 until matrix.size) {
                if (!matrix[x, y]) continue
                val left = ((x + quiet) * modulePx).roundToInt().toFloat()
                val top = ((y + quiet) * modulePx).roundToInt().toFloat()
                val right = ((x + quiet + 1) * modulePx).roundToInt().toFloat()
                val bottom = ((y + quiet + 1) * modulePx).roundToInt().toFloat()
                drawRect(
                    color = QR_DARK,
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                )
            }
        }
    }
}

private val QR_LIGHT = Color.White
private val QR_DARK = Color.Black

private const val QR_MAX_HEIGHT_FRACTION = 0.6f

private const val QUIET_CORNER_MODULES = 1f