// ArbDatabaseScreen.kt
package com.dere3046.checkarb

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

private val BUTTON_CORNER_RADIUS = 4.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArbDatabaseScreen(
    viewModel: ArbDatabaseViewModel = viewModel(),
    onBack: () -> Unit
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val database by viewModel.database.collectAsState()
    val matchResult by viewModel.matchResult.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var showAllDevices by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.fetchDatabase()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(4.dp),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.device_info),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                InfoRow(stringResource(R.string.model), Build.MODEL)
                InfoRow(stringResource(R.string.build_number), Build.ID)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    showAllDevices = false
                    viewModel.matchCurrentDevice()
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(BUTTON_CORNER_RADIUS),
                enabled = !isLoading && database.isNotEmpty()
            ) {
                Text(stringResource(R.string.check_this_device))
            }
            Button(
                onClick = {
                    showAllDevices = true
                    viewModel.clearMatchResult()
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(BUTTON_CORNER_RADIUS),
                enabled = !isLoading && database.isNotEmpty()
            ) {
                Text(stringResource(R.string.check_other_devices))
            }
        }

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        error?.let {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = it,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        if (showAllDevices && database.isNotEmpty()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.search_devices)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(BUTTON_CORNER_RADIUS)
            )

            val filtered = if (searchQuery.isEmpty()) {
                database.entries.toList()
            } else {
                database.entries.filter { (key, value) ->
                    key.contains(searchQuery, ignoreCase = true) ||
                    value.deviceName.contains(searchQuery, ignoreCase = true)
                }
            }

            filtered.forEach { (model, data) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = data.deviceName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Model: $model",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        data.versions.forEach { (version, info) ->
                            ArbVersionRow(version, info)
                        }
                    }
                }
            }
        }

        if (!showAllDevices && matchResult != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(4.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = matchResult!!.deviceName,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Model: ${matchResult!!.model}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    matchResult!!.versions.forEach { info ->
                        ArbVersionRow("", info)
                    }
                }
            }
        }
    }
}

@Composable
fun ArbVersionRow(version: String, info: VersionInfo) {
    val arbColor = if (info.arb >= 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = if (version.isNotEmpty()) version else stringResource(R.string.version),
                style = MaterialTheme.typography.titleSmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "ARB: ${info.arb}",
                    color = arbColor,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
                Text(
                    text = "${info.major}.${info.minor}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
            }
            Text(
                text = "MD5: ${info.md5 ?: "N/A"}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            )
            Text(
                text = "${stringResource(R.string.regions)}: ${info.regions.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "${stringResource(R.string.status)}: ${info.status} | ${stringResource(R.string.first)}: ${info.firstSeen} | ${stringResource(R.string.last)}: ${info.lastChecked}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
