package com.dere3046.checkarb

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.dere3046.checkarb.data.SettingsRepository
import com.dere3046.checkarb.log.LogManager
import com.dere3046.checkarb.ui.settings.SettingsViewModel
import com.dere3046.checkarb.ui.theme.CheckARBTheme
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

data class AboutInfo(
    val author: String = "dere3046",
    val testers: String = "小初大王, 15, Stillrain001",
    val projectUrl: String = "https://github.com/Dere3046/checkARB_Android",
    val arbInspectorUrl: String = "https://github.com/Dere3046/arb_inspector",
    val license: String = "MIT"
)

enum class WorkMode {
    ROOT,
    NON_ROOT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    navController: NavController? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showAboutDialog by remember { mutableStateOf(false) }
    var hasRoot by remember { mutableStateOf(false) }

    val repository = remember { SettingsRepository(context) }
    val viewModel: SettingsViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return SettingsViewModel(repository) as T
            }
        }
    )
    val workMode by viewModel.workMode.collectAsState()

    LaunchedEffect(Unit) {
        val rootAvailable = try {
            withContext(Dispatchers.IO) {
                Shell.getShell().isRoot
            }
        } catch (e: IOException) {
            false
        } catch (e: SecurityException) {
            false
        }
        hasRoot = rootAvailable
    }

    var logBufferSize by remember { mutableStateOf(LogManager.getBufferSize()) }
    var logEnabled by remember { mutableStateOf(LogManager.isEnabled()) }

    LaunchedEffect(Unit) {
        repository.logBufferSizeFlow.collect { size ->
            logBufferSize = size
            LogManager.setBufferSize(size)
        }
        repository.logEnabledFlow.collect { enabled ->
            logEnabled = enabled
            LogManager.setEnabled(enabled)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let {
            scope.launch {
                val success = LogManager.exportToFile(context, it)
                val msg = if (success) R.string.logs_export_success else R.string.logs_export_failed
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = { showAboutDialog = true }) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = stringResource(R.string.about_title)
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.work_mode),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = workMode == WorkMode.ROOT,
                        onClick = {
                            if (hasRoot) {
                                viewModel.setWorkMode(WorkMode.ROOT)
                            }
                        },
                        enabled = hasRoot
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.root_mode),
                        modifier = Modifier.weight(1f),
                        color = if (hasRoot) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                    if (!hasRoot) {
                        Text(
                            text = stringResource(R.string.unavailable),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = workMode == WorkMode.NON_ROOT,
                        onClick = { viewModel.setWorkMode(WorkMode.NON_ROOT) },
                        enabled = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.non_root_mode),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.current_selection,
                        if (workMode == WorkMode.ROOT)
                            stringResource(R.string.root_mode)
                        else
                            stringResource(R.string.non_root_mode)
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            item {
                Text(
                    text = stringResource(R.string.extraction_tool),
                    style = MaterialTheme.typography.titleMedium
                )
                val extractionTool by repository.extractionToolFlow.collectAsState(initial = "cat")
                Row {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        RadioButton(
                            selected = extractionTool == "cat",
                            onClick = { scope.launch { repository.setExtractionTool("cat") } }
                        )
                        Text(stringResource(R.string.cat), modifier = Modifier.padding(start = 8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        RadioButton(
                            selected = extractionTool == "dd",
                            onClick = { scope.launch { repository.setExtractionTool("dd") } }
                        )
                        Text(stringResource(R.string.dd), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.log_settings),
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.enable_logging),
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = logEnabled,
                        onCheckedChange = { enabled ->
                            logEnabled = enabled
                            scope.launch { repository.setLogEnabled(enabled) }
                            LogManager.setEnabled(enabled)
                        }
                    )
                }
                if (logEnabled) {
                    Text(
                        text = stringResource(R.string.log_buffer_size),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Slider(
                        value = logBufferSize.toFloat(),
                        onValueChange = { newValue ->
                            logBufferSize = newValue.toInt()
                            LogManager.setBufferSize(logBufferSize)
                            scope.launch { repository.setLogBufferSize(logBufferSize) }
                        },
                        valueRange = 50f..3000f,
                        steps = 59,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(R.string.current_buffer_size, logBufferSize),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { exportLauncher.launch("logs_${System.currentTimeMillis()}.txt") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = logEnabled
                    ) {
                        Text(stringResource(R.string.export_logs))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    navController?.let {
                        Button(
                            onClick = { navController.navigate("logs") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = logEnabled
                        ) {
                            Text(stringResource(R.string.view_logs))
                        }
                    }
                }
            }
        }
    }

    if (showAboutDialog) {
        AboutDialog(
            onDismiss = { showAboutDialog = false },
            info = AboutInfo()
        )
    }
}

@Composable
fun AboutDialog(
    onDismiss: () -> Unit,
    info: AboutInfo
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.author, info.author))
                Text(stringResource(R.string.testers, info.testers))
                ClickableTextWithLink(
                    text = stringResource(R.string.project_github),
                    url = info.projectUrl,
                    onOpenUrl = { url ->
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                )
                ClickableTextWithLink(
                    text = stringResource(R.string.arb_inspector_github),
                    url = info.arbInspectorUrl,
                    onOpenUrl = { url ->
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                )
                Text(stringResource(R.string.license, info.license))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        }
    )
}

@Composable
fun ClickableTextWithLink(
    text: String,
    url: String,
    onOpenUrl: (String) -> Unit
) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable { onOpenUrl(url) }
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewSettingsScreen() {
    CheckARBTheme { SettingsScreen() }
}