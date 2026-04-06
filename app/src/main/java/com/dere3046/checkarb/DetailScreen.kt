package com.dere3046.checkarb

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

private val CARD_CORNER_RADIUS = 4.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    slotInfo: MainViewModel.SlotInfo,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onSelectDevice: (String?) -> Unit,
    availableDevices: List<String>,
    currentDevicePath: String?
) {
    var showDeviceDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
    Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CARD_CORNER_RADIUS),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.basic_info), style = MaterialTheme.typography.titleMedium)
                InfoRow(stringResource(R.string.arb_value), slotInfo.arbValue?.toString() ?: "-")
                InfoRow(stringResource(R.string.device_path), slotInfo.devicePath ?: "-")
                InfoRow(stringResource(R.string.real_device_path), slotInfo.realPath ?: "-")
                slotInfo.error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (slotInfo.debugMessages.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(CARD_CORNER_RADIUS),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(R.string.debug_messages), style = MaterialTheme.typography.titleMedium)
                    Column {
                        slotInfo.debugMessages.forEach { msg ->
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        if (showDeviceDialog) {
            TargetDeviceDialog(
                availableDevices = availableDevices,
                currentDevice = currentDevicePath,
                onSelect = { path ->
                    onSelectDevice(path)
                    showDeviceDialog = false
                },
                onReset = {
                    onSelectDevice(null)
                    showDeviceDialog = false
                },
                onDismiss = { showDeviceDialog = false }
            )
        }
    }
}
 
