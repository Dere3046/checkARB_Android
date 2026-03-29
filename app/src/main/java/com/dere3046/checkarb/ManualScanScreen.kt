// ManualScanScreen.kt
package com.dere3046.checkarb

import android.net.Uri
import android.widget.Toast
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dere3046.arbinspector.ArbResult
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.launch

enum class FileSelectionMode {
    SAF,
    DIRECT_PATH,
    DEV_BLOCK_LIST
}

private val BUTTON_CORNER_RADIUS = 4.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualScanScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasRoot by viewModel.hasRootAccess.collectAsState()
    var fileMode by remember { mutableStateOf(FileSelectionMode.SAF) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var directPath by remember { mutableStateOf("") }
    var selectedDevBlockPath by remember { mutableStateOf<String?>(null) }
    var devBlockCandidates by remember { mutableStateOf<List<String>>(emptyList()) }
    var scanResult by remember { mutableStateOf<ArbResult?>(null) }
    var isScanning by remember { mutableStateOf(false) }
    var fullMode by remember { mutableStateOf(false) }
    var debugMode by remember { mutableStateOf(true) }
    var workDir by remember { mutableStateOf(context.filesDir.absolutePath) }
    var workDirEditable by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (hasRoot) {
            val result = Shell.cmd("find /dev/block -name '*xbl_config*' 2>/dev/null").exec()
            devBlockCandidates = result.out.filter { line -> line.isNotBlank() }
            android.util.Log.d("ManualScan", "Found devices: $devBlockCandidates")
        }
    }

    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        selectedUri = uri
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
    OutlinedTextField(
            value = workDir,
            onValueChange = { if (workDirEditable) workDir = it },
            label = { Text(stringResource(R.string.work_directory)) },
            enabled = workDirEditable,
            modifier = Modifier.fillMaxWidth()
        )
        Row {
            TextButton(onClick = { workDirEditable = !workDirEditable }) {
                Text(if (workDirEditable) stringResource(R.string.editable_on) else stringResource(R.string.editable_off))
            }
        }

        Text(
            text = stringResource(R.string.file_selection_mode),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = fileMode == FileSelectionMode.SAF,
                    onClick = { fileMode = FileSelectionMode.SAF }
                )
                Text(stringResource(R.string.saf_mode), modifier = Modifier.padding(start = 8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = fileMode == FileSelectionMode.DIRECT_PATH,
                    onClick = { fileMode = FileSelectionMode.DIRECT_PATH },
                    enabled = hasRoot
                )
                Text(
                    stringResource(R.string.direct_path),
                    modifier = Modifier.padding(start = 8.dp),
                    color = if (hasRoot) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = fileMode == FileSelectionMode.DEV_BLOCK_LIST,
                    onClick = { fileMode = FileSelectionMode.DEV_BLOCK_LIST },
                    enabled = hasRoot
                )
                Text(
                    stringResource(R.string.dev_block_list),
                    modifier = Modifier.padding(start = 8.dp),
                    color = if (hasRoot) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        when (fileMode) {
            FileSelectionMode.SAF -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { safLauncher.launch("*/*") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(BUTTON_CORNER_RADIUS)
                    ) {
                        Text(stringResource(R.string.select_file))
                    }
                }
                selectedUri?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(text = it.toString(), style = MaterialTheme.typography.bodySmall)
                }
            }
            FileSelectionMode.DIRECT_PATH -> {
                OutlinedTextField(
                    value = directPath,
                    onValueChange = { directPath = it },
                    label = { Text(stringResource(R.string.input_path_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            FileSelectionMode.DEV_BLOCK_LIST -> {
                LaunchedEffect(hasRoot) {
                    if (hasRoot) {
                        val result = Shell.cmd("find /dev/block -name '*xbl_config*' 2>/dev/null").exec()
                        devBlockCandidates = result.out.filter { line -> line.isNotBlank() }
                        android.util.Log.d("ManualScan", "Found devices: $devBlockCandidates")
                    }
                }

                if (!hasRoot) {
                    fileMode = FileSelectionMode.SAF
                } else if (devBlockCandidates.isEmpty()) {
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
                                        selectedDevBlockPath = path
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { fullMode = !fullMode },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (fullMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(BUTTON_CORNER_RADIUS)
            ) {
                Text(if (fullMode) stringResource(R.string.full_mode) else stringResource(R.string.quick_mode))
            }
            Button(
                onClick = { debugMode = !debugMode },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (debugMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(BUTTON_CORNER_RADIUS)
            ) {
                Text(stringResource(R.string.debug_mode))
            }
        }

        Button(
            onClick = {
                val path = when (fileMode) {
                    FileSelectionMode.SAF -> selectedUri?.toString()
                    FileSelectionMode.DIRECT_PATH -> directPath.takeIf { it.isNotBlank() }
                    FileSelectionMode.DEV_BLOCK_LIST -> selectedDevBlockPath
                }
                if (path.isNullOrEmpty()) {
                    Toast.makeText(context, R.string.select_file_first, Toast.LENGTH_SHORT).show()
                    return@Button
                }
                scope.launch {
                    isScanning = true
                    val result = viewModel.scanFile(path, fullMode, debugMode, workDir)
                    scanResult = result
                    isScanning = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(BUTTON_CORNER_RADIUS),
            enabled = !isScanning
        ) {
            Text(if (isScanning) stringResource(R.string.scanning) else stringResource(R.string.start_scan))
        }

        if (scanResult != null) {
            ResultCard(result = scanResult!!)
        }
    }
}
