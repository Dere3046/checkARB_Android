package com.dere3046.checkarb

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

private val BUTTON_CORNER_RADIUS = 4.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XblExtractorScreen(
    onBack: () -> Unit,
    viewModel: XblExtractorViewModel
) {
    val context = LocalContext.current
    val isProcessing by viewModel.isProcessing.collectAsState()
    val status by viewModel.status.collectAsState()
    val error by viewModel.error.collectAsState()
    val foundFiles by viewModel.foundFiles.collectAsState()
    val selectedFiles by viewModel.selectedFiles.collectAsState()
    val scanResults by viewModel.scanResults.collectAsState()
    val archiveInfo by viewModel.archiveInfo.collectAsState()

    var inputMode by remember { mutableStateOf(InputMode.LOCAL) }
    var remoteUrl by remember { mutableStateOf("") }
    var debugMode by remember { mutableStateOf(true) }
    var showResults by remember { mutableStateOf(false) }

    val zipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.scanLocalZip(it) }
    }

    val dirLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { viewModel.setSaveDirUri(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = remoteUrl,
            onValueChange = { remoteUrl = it },
            label = { Text(stringResource(R.string.rom_url_hint)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = inputMode == InputMode.REMOTE && !isProcessing
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { inputMode = InputMode.LOCAL },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(BUTTON_CORNER_RADIUS),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (inputMode == InputMode.LOCAL) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                ),
                enabled = !isProcessing
            ) {
                Text(stringResource(R.string.local_zip))
            }
            Button(
                onClick = { inputMode = InputMode.REMOTE },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(BUTTON_CORNER_RADIUS),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (inputMode == InputMode.REMOTE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                ),
                enabled = !isProcessing
            ) {
                Text(stringResource(R.string.remote_url))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { zipLauncher.launch("application/zip") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(BUTTON_CORNER_RADIUS),
                enabled = inputMode == InputMode.LOCAL && !isProcessing
            ) {
                Text(stringResource(R.string.select_zip))
            }
            Button(
                onClick = { if (remoteUrl.isNotBlank()) viewModel.scanRemoteZip(remoteUrl) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(BUTTON_CORNER_RADIUS),
                enabled = inputMode == InputMode.REMOTE && remoteUrl.isNotBlank() && !isProcessing
            ) {
                Text(stringResource(R.string.scan_remote))
            }
        }

        OutlinedButton(
            onClick = { dirLauncher.launch(null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(BUTTON_CORNER_RADIUS)
        ) {
            Text(stringResource(R.string.select_save_folder))
        }

        archiveInfo?.let { info ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(BUTTON_CORNER_RADIUS),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.archive_info),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    InfoRow(stringResource(R.string.file_name), info.fileName)
                    InfoRow(stringResource(R.string.file_size), formatSize(info.fileSize))
                }
            }
        }

        if (foundFiles.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = { viewModel.selectAll() }) { Text(stringResource(R.string.select_all)) }
                TextButton(onClick = { viewModel.clearSelection() }) { Text(stringResource(R.string.clear_selection)) }
            }

            foundFiles.forEachIndexed { index, file ->
                val isSelected = selectedFiles.contains(index)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(BUTTON_CORNER_RADIUS),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    ),
                    onClick = { viewModel.toggleFileSelection(index) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${file.source} - ${formatSize(file.size)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Checkbox(checked = isSelected, onCheckedChange = { viewModel.toggleFileSelection(index) })
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showResults = false; viewModel.extractAndScanSelected(fullMode = true, debug = debugMode) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(BUTTON_CORNER_RADIUS),
                    enabled = selectedFiles.isNotEmpty() && !isProcessing
                ) {
                    Text(stringResource(R.string.scan_selected))
                }
                Button(
                    onClick = { viewModel.saveSelectedFiles() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(BUTTON_CORNER_RADIUS),
                    enabled = selectedFiles.isNotEmpty() && !isProcessing
                ) {
                    Text(stringResource(R.string.save_selected))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(checked = debugMode, onCheckedChange = { debugMode = it })
                Text(stringResource(R.string.debug_mode), modifier = Modifier.align(Alignment.CenterVertically))
                Text(" (Full mode always enabled)", style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.CenterVertically))
            }
        }

        status?.let {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Text(text = it, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }

        error?.let {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(text = it, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        if (scanResults.isNotEmpty() && !showResults) {
            Button(
                onClick = { showResults = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(BUTTON_CORNER_RADIUS)
            ) {
                Text(stringResource(R.string.view_results, scanResults.size))
            }
        }

        if (showResults) {
            scanResults.forEach { result ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(BUTTON_CORNER_RADIUS)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = result.fileName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        if (result.result?.error != null) {
                            Text(text = "Error: ${result.result.error}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        } else {
                            val arb = result.result?.arb ?: -1
                            val arbColor = if (arb >= 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            Text(
                                text = "ARB: $arb (${if (arb >= 1) "Fused" else "Safe"})",
                                color = arbColor,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(text = "Version: ${result.result?.major}.${result.result?.minor}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

private fun formatSize(size: Long): String {
    val gb = size.toDouble() / (1024 * 1024 * 1024)
    val mb = size.toDouble() / (1024 * 1024)
    return if (gb >= 1.0) {
        "%.2f GB".format(gb)
    } else {
        "%.2f MB".format(mb)
    }
}

enum class InputMode { LOCAL, REMOTE }
