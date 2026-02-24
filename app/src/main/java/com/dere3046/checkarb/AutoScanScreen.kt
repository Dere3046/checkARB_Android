package com.dere3046.checkarb

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoScanScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: AutoScanViewModel = viewModel()

    val deviceCandidates by viewModel.deviceCandidates.collectAsState()
    val selectedDevicePath by viewModel.selectedDevicePath.collectAsState()
    val isLoadingCandidates by viewModel.isLoadingCandidates.collectAsState()
    val scanResult by viewModel.scanResult.collectAsState()
    val lastArb by viewModel.lastArb.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val showResultDialog by viewModel.showResultDialog.collectAsState()

    var showParamSettings by remember { mutableStateOf(false) }
    var customDebug by remember { mutableStateOf(true) }
    var customBlock by remember { mutableStateOf(false) }
    var customTool by remember { mutableStateOf("cat") }

    var expanded by remember { mutableStateOf(false) }
    var showRecreateDialog by remember { mutableStateOf(false) }
    var pendingRecreate: AutoScanPreparation.NeedRecreate? by remember { mutableStateOf(null) }

    LaunchedEffect(Unit) {
        viewModel.findTargetDevice()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.auto_scan)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Device selection
            if (deviceCandidates.size > 1) {
                Text(stringResource(R.string.select_device), style = MaterialTheme.typography.titleMedium)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedDevicePath ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.choose_from_list)) },
                        trailingIcon = { IconButton(onClick = { expanded = true }) { Icon(Icons.Default.Settings, null) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        deviceCandidates.forEach { path ->
                            DropdownMenuItem(
                                text = { Text(path) },
                                onClick = {
                                    viewModel.selectDevice(path)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            } else if (deviceCandidates.size == 1) {
                Text(
                    text = "${stringResource(R.string.target_device)}: ${deviceCandidates.first()}",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else if (!isLoadingCandidates && deviceCandidates.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_device_found),
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Custom parameters toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { showParamSettings = !showParamSettings }
            ) {
                Checkbox(checked = showParamSettings, onCheckedChange = null)
                Text(stringResource(R.string.custom_parameters))
            }

            // Custom parameters
            if (showParamSettings) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = customDebug, onCheckedChange = { customDebug = it })
                        Text(stringResource(R.string.debug_mode))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = customBlock, onCheckedChange = { customBlock = it })
                        Text(stringResource(R.string.block_mode))
                    }
                    Text(stringResource(R.string.extraction_tool))
                    Row {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            RadioButton(selected = customTool == "cat", onClick = { customTool = "cat" })
                            Text(stringResource(R.string.cat), modifier = Modifier.padding(start = 4.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            RadioButton(selected = customTool == "dd", onClick = { customTool = "dd" })
                            Text(stringResource(R.string.dd), modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Start scan button
            Button(
                onClick = {
                    scope.launch {
                        val preparation = viewModel.prepareScan()
                        when (preparation) {
                            is AutoScanPreparation.Ready -> {
                                val useDefault = !showParamSettings
                                if (showParamSettings) {
                                    viewModel.setDebugMode(customDebug)
                                    viewModel.setBlockMode(customBlock)
                                    viewModel.setExtractionTool(customTool)
                                }
                                viewModel.executeScan(context, useDefaultParams = useDefault)
                            }
                            is AutoScanPreparation.NeedRecreate -> {
                                pendingRecreate = preparation
                                showRecreateDialog = true
                            }
                            is AutoScanPreparation.Error -> {
                                viewModel.reportError("Preparation error: ${preparation.message}")
                            }
                        }
                    }
                },
                enabled = selectedDevicePath != null && !isScanning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isScanning) stringResource(R.string.scanning) else stringResource(R.string.start_scan))
            }
        }
    }

    // Result dialog
    if (showResultDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissResultDialog() },
            title = { Text(stringResource(R.string.scan_result_title)) },
            text = {
                SelectionContainer {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .then(if (scanResult.length > 500) Modifier.height(300.dp) else Modifier)
                    ) {
                        item {
                            Text(
                                text = scanResult.ifEmpty { stringResource(R.string.output_placeholder) },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (lastArb != null) {
                            item {
                                Spacer(modifier = Modifier.padding(4.dp))
                                Text(
                                    text = if (lastArb!! >= 1)
                                        stringResource(R.string.arb_enabled)
                                    else
                                        stringResource(R.string.arb_disabled),
                                    color = if (lastArb!! >= 1)
                                        androidx.compose.ui.graphics.Color.Red
                                    else
                                        androidx.compose.ui.graphics.Color.Green,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissResultDialog() }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    // Recreate device node dialog
    if (showRecreateDialog && pendingRecreate != null) {
        AlertDialog(
            onDismissRequest = { showRecreateDialog = false },
            title = { Text(stringResource(R.string.device_node_missing_title)) },
            text = { Text(stringResource(R.string.device_node_missing_text, pendingRecreate!!.devicePath)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRecreateDialog = false
                        scope.launch {
                            val recreate = pendingRecreate!!
                            val success = viewModel.recreateDeviceNode(recreate.devicePath, recreate.major, recreate.minor)
                            if (success) {
                                val useDefault = !showParamSettings
                                viewModel.executeScan(context, useDefaultParams = useDefault, recreatedPath = recreate.devicePath)
                            } else {
                                viewModel.reportError(context.getString(R.string.recreate_failed))
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.recreate))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecreateDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}