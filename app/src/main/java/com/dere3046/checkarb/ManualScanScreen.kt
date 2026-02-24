package com.dere3046.checkarb

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.dere3046.arbinspector.ArbResult
import com.dere3046.checkarb.data.SettingsRepository
import com.dere3046.checkarb.log.LogManager
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.launch
import java.io.PrintWriter
import java.io.StringWriter

enum class FileSelectionMode { SAF, DIRECT_PATH, DEV_BLOCK_LIST }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualScanScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepo = remember { SettingsRepository(context) }
    val viewModel: ManualScanViewModel = viewModel()

    val workDir by viewModel.workDir.collectAsState()
    val workDirEditable by viewModel.workDirEditable.collectAsState()
    val debugMode by viewModel.debugMode.collectAsState()
    val blockMode by viewModel.blockMode.collectAsState()
    val fileMode by viewModel.fileMode.collectAsState()
    val selectedSafUri by viewModel.selectedSafUri.collectAsState()
    val directPath by viewModel.directPath.collectAsState()
    val directRead by viewModel.directRead.collectAsState()
    val devBlockCandidates by viewModel.devBlockCandidates.collectAsState()
    val selectedDevBlockPath by viewModel.selectedDevBlockPath.collectAsState()
    val isLoadingCandidates by viewModel.isLoadingCandidates.collectAsState()
    val advancedMode by viewModel.advancedMode.collectAsState()
    val hashScanMax by viewModel.hashScanMax.collectAsState()
    val maxSegmentSize by viewModel.maxSegmentSize.collectAsState()
    val minVersion by viewModel.minVersion.collectAsState()
    val maxVersion by viewModel.maxVersion.collectAsState()
    val maxCommonSz by viewModel.maxCommonSz.collectAsState()
    val maxQtiSz by viewModel.maxQtiSz.collectAsState()
    val maxOemSz by viewModel.maxOemSz.collectAsState()
    val maxHashTblSz by viewModel.maxHashTblSz.collectAsState()
    val maxArb by viewModel.maxArb.collectAsState()

    val workMode by settingsRepo.workModeFlow.collectAsState(initial = WorkMode.NON_ROOT)
    val extractionTool by settingsRepo.extractionToolFlow.collectAsState(initial = "cat")

    var outputText by remember { mutableStateOf("") }

    var showRecreateDialog by remember { mutableStateOf(false) }
    var pendingRecreate: ScanPreparation.NeedRecreate? by remember { mutableStateOf(null) }

    LaunchedEffect(Unit) {
        viewModel.initializeWorkDir(context)
    }

    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.setSelectedSafUri(it.toString())
            LogManager.i("ManualScan", "SAF file selected: $it")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                viewModel.importParameters(context, it)
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            scope.launch {
                viewModel.exportParameters(context, it)
            }
        }
    }

    fun startScanAfterRecreate(recreatedPath: String? = null) {
        scope.launch {
            try {
                val result = viewModel.executeScan(context, extractionTool, recreatedPath)
                if (result != null) {
                    if (result.error != null) {
                        outputText = buildString {
                            appendLine("${context.getString(R.string.scan_failed)}: ${result.error}")
                            if (result.debugMessages.isNotEmpty()) {
                                appendLine("\n${context.getString(R.string.debug_messages)}:")
                                result.debugMessages.forEach { appendLine(it) }
                            }
                        }
                        LogManager.w("ManualScan", "Scan failed with error: ${result.error}")
                    } else {
                        outputText = buildString {
                            appendLine("${context.getString(R.string.major)}: ${result.major}")
                            appendLine("${context.getString(R.string.minor)}: ${result.minor}")
                            appendLine("ARB: ${result.arb}")
                            if (result.debugMessages.isNotEmpty()) {
                                appendLine("\n${context.getString(R.string.debug_messages)}:")
                                result.debugMessages.forEach { appendLine(it) }
                            }
                        }
                        LogManager.i("ManualScan", "Scan completed, ARB=${result.arb}")
                    }
                } else {
                    outputText = context.getString(R.string.scan_failed_detail)
                    LogManager.w("ManualScan", "Scan returned null result")
                }
            } catch (e: Exception) {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                outputText = sw.toString()
                LogManager.e("ManualScan", "Scan exception", e)
            } finally {
                recreatedPath?.let {
                    scope.launch {
                        Shell.cmd("rm -f $it").exec()
                        LogManager.i("ManualScan", "Removed recreated device node: $it")
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.manual_scan)) },
                navigationIcon = {
                    IconButton(onClick = {
                        navController.popBackStack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.padding(0.dp)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Work directory
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = workDir,
                        onValueChange = { if (workDirEditable) viewModel.setWorkDir(it) },
                        label = { Text(stringResource(R.string.work_directory)) },
                        enabled = workDirEditable,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    ClickableCheckboxRow(
                        checked = workDirEditable,
                        onCheckedChange = { viewModel.setWorkDirEditable(it) },
                        text = stringResource(R.string.editable)
                    )
                }
            }

            // Debug and block mode
            item {
                Row {
                    ClickableCheckboxRow(
                        checked = debugMode,
                        onCheckedChange = { viewModel.setDebugMode(it) },
                        text = stringResource(R.string.debug_mode),
                        modifier = Modifier.weight(1f)
                    )
                    ClickableCheckboxRow(
                        checked = blockMode,
                        onCheckedChange = { viewModel.setBlockMode(it) },
                        text = stringResource(R.string.block_mode),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // File selection mode
            item {
                Text(
                    text = stringResource(R.string.file_selection_mode),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            item {
                Column {
                    FileModeOption(
                        text = stringResource(R.string.saf_mode),
                        selected = fileMode == FileSelectionMode.SAF,
                        enabled = true,
                        onClick = { viewModel.setFileMode(FileSelectionMode.SAF) }
                    )
                    FileModeOption(
                        text = stringResource(R.string.direct_path),
                        selected = fileMode == FileSelectionMode.DIRECT_PATH,
                        enabled = workMode == WorkMode.ROOT,
                        onClick = { viewModel.setFileMode(FileSelectionMode.DIRECT_PATH) }
                    )
                    FileModeOption(
                        text = stringResource(R.string.dev_block_list),
                        selected = fileMode == FileSelectionMode.DEV_BLOCK_LIST,
                        enabled = workMode == WorkMode.ROOT,
                        onClick = { viewModel.setFileMode(FileSelectionMode.DEV_BLOCK_LIST) }
                    )
                }

                if (workMode != WorkMode.ROOT && fileMode != FileSelectionMode.SAF) {
                    Text(
                        text = stringResource(R.string.no_root_mode_warning),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // File selection UI based on mode
            item {
                when (fileMode) {
                    FileSelectionMode.SAF -> {
                        Button(onClick = { safLauncher.launch("*/*") }) {
                            Text(stringResource(R.string.select_file))
                        }
                        if (!selectedSafUri.isNullOrEmpty()) {
                            Text("URI: $selectedSafUri")
                        }
                    }
                    FileSelectionMode.DIRECT_PATH -> {
                        OutlinedTextField(
                            value = directPath,
                            onValueChange = { viewModel.setDirectPath(it) },
                            label = { Text(stringResource(R.string.input_path_hint)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        ClickableCheckboxRow(
                            checked = directRead,
                            onCheckedChange = { viewModel.setDirectRead(it) },
                            text = stringResource(R.string.direct_read)
                        )
                    }
                    FileSelectionMode.DEV_BLOCK_LIST -> {
                        if (devBlockCandidates.isEmpty()) {
                            LaunchedEffect(Unit) {
                                viewModel.loadDevBlockCandidates()
                            }
                        }
                        if (isLoadingCandidates) {
                            Text(stringResource(R.string.loading))
                        } else {
                            var expanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = expanded,
                                onExpandedChange = { expanded = !expanded }
                            ) {
                                OutlinedTextField(
                                    value = selectedDevBlockPath ?: "",
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                    label = { Text(stringResource(R.string.choose_from_list)) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor()
                                )
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    devBlockCandidates.forEach { path ->
                                        DropdownMenuItem(
                                            text = { Text(path) },
                                            onClick = {
                                                viewModel.setSelectedDevBlockPath(path)
                                                expanded = false
                                                LogManager.i("ManualScan", "Dev block selected: $path")
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        ClickableCheckboxRow(
                            checked = directRead,
                            onCheckedChange = { viewModel.setDirectRead(it) },
                            text = stringResource(R.string.direct_read)
                        )
                    }
                }
            }

            // Advanced mode toggle
            item {
                ClickableCheckboxRow(
                    checked = advancedMode,
                    onCheckedChange = { viewModel.setAdvancedMode(it) },
                    text = stringResource(R.string.advanced_mode)
                )
            }

            // Advanced parameters
            if (advancedMode) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.custom_parameters),
                                style = MaterialTheme.typography.titleSmall
                            )
                            ParameterInput(
                                label = stringResource(R.string.hash_scan_max),
                                value = hashScanMax,
                                onValueChange = { viewModel.setHashScanMax(it) }
                            )
                            ParameterInput(
                                label = stringResource(R.string.max_segment_size),
                                value = maxSegmentSize,
                                onValueChange = { viewModel.setMaxSegmentSize(it) }
                            )
                            ParameterInput(
                                label = stringResource(R.string.min_version),
                                value = minVersion,
                                onValueChange = { viewModel.setMinVersion(it) }
                            )
                            ParameterInput(
                                label = stringResource(R.string.max_version),
                                value = maxVersion,
                                onValueChange = { viewModel.setMaxVersion(it) }
                            )
                            ParameterInput(
                                label = stringResource(R.string.max_common_sz),
                                value = maxCommonSz,
                                onValueChange = { viewModel.setMaxCommonSz(it) }
                            )
                            ParameterInput(
                                label = stringResource(R.string.max_qti_sz),
                                value = maxQtiSz,
                                onValueChange = { viewModel.setMaxQtiSz(it) }
                            )
                            ParameterInput(
                                label = stringResource(R.string.max_oem_sz),
                                value = maxOemSz,
                                onValueChange = { viewModel.setMaxOemSz(it) }
                            )
                            ParameterInput(
                                label = stringResource(R.string.max_hash_tbl_sz),
                                value = maxHashTblSz,
                                onValueChange = { viewModel.setMaxHashTblSz(it) }
                            )
                            ParameterInput(
                                label = stringResource(R.string.max_arb),
                                value = maxArb,
                                onValueChange = { viewModel.setMaxArb(it) }
                            )
                            Row {
                                Button(
                                    onClick = { importLauncher.launch("application/json") },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(R.string.import_params))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { exportLauncher.launch("arb_config.json") },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(R.string.export_params))
                                }
                            }
                        }
                    }
                }
            }

            // Extraction tool selection
            item {
                Text(
                    text = stringResource(R.string.extraction_tool),
                    style = MaterialTheme.typography.titleMedium
                )
                Row {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        RadioButton(
                            selected = extractionTool == "cat",
                            onClick = { scope.launch { settingsRepo.setExtractionTool("cat") } }
                        )
                        Text(stringResource(R.string.cat), modifier = Modifier.padding(start = 8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        RadioButton(
                            selected = extractionTool == "dd",
                            onClick = { scope.launch { settingsRepo.setExtractionTool("dd") } }
                        )
                        Text(stringResource(R.string.dd), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            // Start scan button
            item {
                Button(
                    onClick = {
                        scope.launch {
                            outputText = context.getString(R.string.scanning)
                            val preparation = viewModel.prepareScan(context, extractionTool)
                            when (preparation) {
                                is ScanPreparation.Ready -> {
                                    startScanAfterRecreate()
                                }
                                is ScanPreparation.NeedRecreate -> {
                                    pendingRecreate = preparation
                                    showRecreateDialog = true
                                }
                                is ScanPreparation.Error -> {
                                    outputText = "Preparation error: ${preparation.message}"
                                    LogManager.e("ManualScan", "Preparation error: ${preparation.message}")
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.start_scan))
                }
            }

            // Output box (adaptive height)
            item {
                Surface(
                    tonalElevation = 2.dp,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SelectionContainer {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Text(
                                text = outputText.ifEmpty { stringResource(R.string.output_placeholder) },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialog for user confirmation to recreate device node before scan
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
                                startScanAfterRecreate(recreate.devicePath)
                            } else {
                                outputText = context.getString(R.string.recreate_failed)
                                LogManager.e("ManualScan", "Failed to recreate device node ${recreate.devicePath}")
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

@Composable
fun FileModeOption(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(enabled = enabled) { onClick() }
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled
        )
        Text(
            text = text,
            modifier = Modifier.padding(start = 8.dp),
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    }
}

@Composable
fun ClickableCheckboxRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clickable(enabled = enabled) { onCheckedChange(!checked) }
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled
        )
        Text(
            text = text,
            modifier = Modifier.padding(start = 8.dp),
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    }
}

@Composable
fun ParameterInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}