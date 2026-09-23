package io.github.openquesttuner.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.openquesttuner.R
import io.github.openquesttuner.core.ConnectionMethod
import io.github.openquesttuner.core.ConnectionState
import io.github.openquesttuner.core.FailureReason

/** Puce d'état de la connexion, toujours visible dans la barre du haut ; ouvre l'écran Connexion. */
@Composable
fun ConnectionBadge(state: ConnectionState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AssistChip(
        onClick = onClick,
        label = { Text(stringResource(state.labelRes())) },
        leadingIcon = {
            Box(Modifier.size(10.dp).background(state.dotColor(), CircleShape))
        },
        modifier = modifier.heightIn(min = 48.dp),
    )
}

@StringRes
fun ConnectionState.labelRes(): Int = when (this) {
    ConnectionState.Disconnected -> R.string.state_disconnected
    ConnectionState.Pairing -> R.string.state_pairing
    is ConnectionState.Connecting -> R.string.state_connecting
    is ConnectionState.Connected -> when (method) {
        ConnectionMethod.WIRELESS -> R.string.state_connected_wireless
        ConnectionMethod.PC -> R.string.state_connected_pc
    }
    is ConnectionState.Failed -> R.string.state_failed
}

/** Cause et action suggérée pour chaque échec (FR-004). */
@StringRes
fun FailureReason.messageRes(): Int = when (this) {
    FailureReason.PORT_CLOSED -> R.string.failure_port_closed
    FailureReason.NOT_AUTHORIZED -> R.string.failure_not_authorized
    FailureReason.PAIRING_REQUIRED -> R.string.failure_pairing_required
    FailureReason.PAIRING_CODE_REJECTED -> R.string.failure_pairing_code_rejected
    FailureReason.SERVICE_NOT_FOUND -> R.string.failure_service_not_found
    FailureReason.UNKNOWN -> R.string.failure_unknown
}

val ConnectionState.isBusy: Boolean
    get() = this is ConnectionState.Pairing || this is ConnectionState.Connecting

private fun ConnectionState.dotColor(): Color = when (this) {
    is ConnectionState.Connected -> Color(0xFF6DD58C)
    ConnectionState.Pairing, is ConnectionState.Connecting -> Color(0xFFFFB870)
    ConnectionState.Disconnected -> Color(0xFF8A9199)
    is ConnectionState.Failed -> Color(0xFFFFB4AB)
}
