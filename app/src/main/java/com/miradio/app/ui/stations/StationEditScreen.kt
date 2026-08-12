package com.miradio.app.ui.stations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.miradio.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationEditScreen(
    stationId: String?,
    onBack: () -> Unit,
    viewModel: StationEditViewModel = viewModel(factory = StationEditViewModel.factory(stationId)),
) {
    val state by viewModel.state.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved, state.deleted) {
        if (state.saved || state.deleted) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isEditing) R.string.stations_edit_title else R.string.stations_add_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.stations_field_name)) },
                isError = state.nameError,
                supportingText = { if (state.nameError) Text(stringResource(R.string.stations_validation_required)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = state.city,
                onValueChange = viewModel::onCityChange,
                label = { Text(stringResource(R.string.stations_field_city)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = state.streamUrl,
                onValueChange = viewModel::onStreamUrlChange,
                label = { Text(stringResource(R.string.stations_field_stream_url)) },
                isError = state.urlError,
                supportingText = {
                    Text(
                        when {
                            state.urlError -> stringResource(R.string.stations_validation_url)
                            state.urlCheckState == UrlCheckState.CHECKING -> stringResource(R.string.stations_checking_url)
                            state.urlCheckState == UrlCheckState.OK -> stringResource(R.string.stations_url_ok)
                            state.urlCheckState == UrlCheckState.FAILED -> stringResource(R.string.stations_url_failed)
                            else -> ""
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedButton(onClick = viewModel::checkStreamUrl, enabled = state.urlCheckState != UrlCheckState.CHECKING) {
                if (state.urlCheckState == UrlCheckState.CHECKING) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.stations_check_url_button))
                }
            }
            OutlinedTextField(
                value = state.logoUrl,
                onValueChange = viewModel::onLogoUrlChange,
                label = { Text(stringResource(R.string.stations_field_logo_url)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = state.category,
                onValueChange = viewModel::onCategoryChange,
                label = { Text(stringResource(R.string.stations_field_category)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text(stringResource(R.string.stations_field_description)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
            )

            Button(
                onClick = viewModel::save,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.stations_save))
            }

            if (state.isEditing) {
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.stations_delete))
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.stations_delete)) },
            text = { Text(stringResource(R.string.stations_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; viewModel.delete() }) {
                    Text(stringResource(R.string.stations_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") }
            },
        )
    }
}
