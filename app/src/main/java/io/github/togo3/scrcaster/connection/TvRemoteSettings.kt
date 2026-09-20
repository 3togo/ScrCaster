package io.github.togo3.scrcaster.connection

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.togo3.scrcaster.rememberTvRemoteAccess
import io.github.togo3.scrcaster.TvRemoteAccess
import io.github.togo3.scrcaster.R

@Composable
internal fun TvRemoteSettings(modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val context = LocalContext.current
    val access = rememberTvRemoteAccess()
    val enabled = access != TvRemoteAccess.DISABLED
    Text(stringResource(access.message),
        color = if (access == TvRemoteAccess.ACTIVE) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.error)
    ActionButton(stringResource(if (enabled) R.string.tv_remote_controls_manage else R.string.tv_remote_controls_enable), modifier) {
        onOpen()
        try {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.tv_remote_settings_unavailable, Toast.LENGTH_LONG).show()
            try {
                context.startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: ActivityNotFoundException) {
                // Some TV firmware exposes neither standard settings action; keep the app open.
            }
        }
    }
    Text(stringResource(R.string.tv_remote_controls_help), style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}
