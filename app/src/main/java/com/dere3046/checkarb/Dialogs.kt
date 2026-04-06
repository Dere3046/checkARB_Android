// Dialogs.kt
package com.dere3046.checkarb

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

private val DIALOG_CORNER_RADIUS = 8.dp
private val BUTTON_CORNER_RADIUS = 4.dp
private val DIALOG_WIDTH_FRACTION = 0.9f

@Composable
fun TargetDeviceDialog(
    availableDevices: List<String>,
    currentDevice: String?,
    onSelect: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(DIALOG_WIDTH_FRACTION),
            shape = RoundedCornerShape(DIALOG_CORNER_RADIUS),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.select_target_device),
                    style = MaterialTheme.typography.titleMedium
                )
                HorizontalDivider()

                if (availableDevices.isEmpty()) {
                    Text(stringResource(R.string.no_device_found))
                } else {
                    availableDevices.forEach { device ->
                        Button(
                            onClick = { onSelect(device) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (device == currentDevice) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(BUTTON_CORNER_RADIUS)
                        ) {
                            Text(device)
                        }
                    }
                }

                Button(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(BUTTON_CORNER_RADIUS),
                    enabled = currentDevice != null
                ) {
                    Text(stringResource(R.string.reset_to_default))
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(BUTTON_CORNER_RADIUS)
                ) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    context: Context,
    hasRoot: Boolean,
    onShowLicenses: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val repository = remember { SettingsRepository(context) }
    var extractionTool by remember { mutableStateOf("cat") }
    var showAboutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        repository.extractionToolFlow.collect { extractionTool = it }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(DIALOG_WIDTH_FRACTION),
            shape = RoundedCornerShape(DIALOG_CORNER_RADIUS),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings),
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(onClick = { showAboutDialog = true }) {
                        Text(stringResource(R.string.about_title))
                    }
                }

                HorizontalDivider()

                if (!hasRoot) {
                    Text(
                        text = "⚠ No ROOT access - Some features unavailable",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Text(stringResource(R.string.extraction_tool))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch { repository.setExtractionTool("cat") }
                            extractionTool = "cat"
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (extractionTool == "cat") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(BUTTON_CORNER_RADIUS)
                    ) {
                        Text(stringResource(R.string.cat))
                    }
                    Button(
                        onClick = {
                            scope.launch { repository.setExtractionTool("dd") }
                            extractionTool = "dd"
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (extractionTool == "dd") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(BUTTON_CORNER_RADIUS)
                    ) {
                        Text(stringResource(R.string.dd))
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(BUTTON_CORNER_RADIUS)
                ) {
                    Text(stringResource(R.string.close))
                }

                if (onShowLicenses != null) {
                    OutlinedButton(
                        onClick = onShowLicenses,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(BUTTON_CORNER_RADIUS)
                    ) {
                        Text("Open Source Licenses")
                    }
                }
            }
        }
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.author, "dere3046"))
                Text(stringResource(R.string.testers, "小初大王, 15, Stillrain001"))
                TextButton(onClick = {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/Dere3046/checkARB_Android")))
                }) {
                    Text(stringResource(R.string.project_github))
                }
                TextButton(onClick = {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/Dere3046/arb_inspector_next")))
                }) {
                    Text(stringResource(R.string.arb_inspector_github))
                }
                TextButton(onClick = {
                    try {
                        val input = context.assets.open("licenses.html")
                        val tempFile = File(context.filesDir, "licenses.html")
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                        val intent = androidx.browser.customtabs.CustomTabsIntent.Builder()
                            .setShowTitle(true)
                            .build()
                        intent.launchUrl(context, android.net.Uri.fromFile(tempFile))
                    } catch (e: Exception) {
                        android.util.Log.e("AboutDialog", "Failed to open licenses: ${e.message}")
                    }
                }) {
                    Text(stringResource(R.string.licenses))
                }
                Text(
                    text = stringResource(R.string.reference_kernelflasher),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        }
    )
}