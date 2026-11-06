package com.gaiascanner.app.ui.info

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gaiascanner.app.AppInfo
import com.gaiascanner.app.R

/** Feature request / bug report: Telegram first, then email (owner decision, 2026-11-05). */
@Composable
fun FeedbackDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_feedback)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.feedback_body))
                Button(onClick = { Support.openTelegram(context); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.feedback_telegram, AppInfo.TELEGRAM_HANDLE))
                }
                OutlinedButton(onClick = { Support.sendEmail(context); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.feedback_email))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
