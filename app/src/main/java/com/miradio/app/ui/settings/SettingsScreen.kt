package com.miradio.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.miradio.app.BuildConfig
import com.miradio.app.R
import com.miradio.app.domain.model.ThemeMode
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = null) }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column {
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
                ThemeOption(ThemeMode.SYSTEM, R.string.settings_theme_system, state.themeMode, viewModel::onThemeModeChange)
                ThemeOption(ThemeMode.LIGHT, R.string.settings_theme_light, state.themeMode, viewModel::onThemeModeChange)
                ThemeOption(ThemeMode.DARK, R.string.settings_theme_dark, state.themeMode, viewModel::onThemeModeChange)
            }

            Divider()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_remote_catalog), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = state.remoteCatalogUrl,
                    onValueChange = viewModel::onRemoteUrlChange,
                    label = { Text(stringResource(R.string.settings_remote_catalog_url)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Button(onClick = viewModel::refreshRemoteCatalog, enabled = !state.isSyncing) {
                    if (state.isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                    }
                    Text(stringResource(R.string.settings_remote_catalog_refresh))
                }
                val lastSyncText = state.lastSyncMillis?.let {
                    DateFormat.getDateTimeInstance().format(Date(it))
                } ?: stringResource(R.string.settings_remote_catalog_never)
                Text(
                    text = stringResource(R.string.settings_remote_catalog_last_sync, lastSyncText),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.syncMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.syncFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Divider()

            Column {
                Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ThemeOption(
    mode: ThemeMode,
    labelRes: Int,
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected == mode, onClick = { onSelect(mode) })
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected == mode, onClick = { onSelect(mode) })
        Text(stringResource(labelRes), modifier = Modifier.padding(start = 8.dp))
    }
}
