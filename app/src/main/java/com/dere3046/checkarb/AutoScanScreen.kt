// AutoScanScreen.kt
package com.dere3046.checkarb

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.dere3046.arbinspector.ArbResult
import kotlinx.coroutines.launch

private val BUTTON_CORNER_RADIUS = 4.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoScanScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val scanResult by viewModel.autoScanResult.collectAsState()
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val hasRoot by viewModel.hasRootAccess.collectAsState()
    var isScanning by remember { mutableStateOf(false) }
    var fullMode by remember { mutableStateOf(true) }
    var selectedSlot by remember { mutableStateOf("_a") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { selectedSlot = "_a" },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedSlot == "_a") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(BUTTON_CORNER_RADIUS)
            ) {
                Text(stringResource(R.string.slot_a) + if (deviceInfo.activeSlot == "_a") " [${stringResource(R.string.current)}]" else "")
            }
            Button(
                onClick = { selectedSlot = "_b" },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedSlot == "_b") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(BUTTON_CORNER_RADIUS)
            ) {
                Text(stringResource(R.string.slot_b) + if (deviceInfo.activeSlot == "_b") " [${stringResource(R.string.current)}]" else "")
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
        }

        Button(
            onClick = {
                scope.launch {
                    isScanning = true
                    android.util.Log.d("AutoScan", "Starting scan for slot $selectedSlot, fullMode: $fullMode")
                    viewModel.autoScan(selectedSlot, fullMode, true)
                    android.util.Log.d("AutoScan", "Scan completed")
                    isScanning = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(BUTTON_CORNER_RADIUS),
            enabled = hasRoot && !isScanning
        ) {
            Text(if (isScanning) stringResource(R.string.scanning) else stringResource(R.string.start_scan))
        }

        if (scanResult != null) {
            ResultCard(result = scanResult!!)
        }
    }
}
