package com.gaiascanner.app.ui.info

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gaiascanner.app.AppInfo
import com.gaiascanner.app.BuildConfigInfo
import com.gaiascanner.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember { BuildConfigInfo.versionName(context) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_about)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(AppInfo.APP_NAME, style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.about_version, version), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.about_body))
            HorizontalDivider()
            Text(stringResource(R.string.about_contact_title), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { Support.openTelegram(context) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.feedback_telegram, AppInfo.TELEGRAM_HANDLE))
            }
            OutlinedButton(onClick = { Support.sendEmail(context) }, modifier = Modifier.fillMaxWidth()) {
                Text(AppInfo.SUPPORT_EMAIL)
            }
            HorizontalDivider()
            Text(stringResource(R.string.about_licenses_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.about_licenses_body), style = MaterialTheme.typography.bodySmall)
        }
    }
}
