// Components.kt
package com.dere3046.checkarb

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dere3046.arbinspector.ArbResult
import com.dere3046.checkarb.ui.components.DataCard
import com.dere3046.checkarb.ui.components.DataRow
import com.dere3046.checkarb.ui.components.ViewButton

private val TEXT_PREVIEW_LENGTH = 40
private val TOP_APP_BAR_HEIGHT = 56.dp
private val RESULT_LIST_MAX_HEIGHT = 200.dp
private val CARD_CORNER_RADIUS = 4.dp
private val MASK_ALPHA = 0.6f
private val DIALOG_WIDTH_FRACTION = 0.9f

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.3f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ExpandableInfoRow(label: String, fullText: String, maxLines: Int = 1) {
    var expanded by remember { mutableStateOf(false) }
    val displayText = if (expanded) fullText else fullText.take(TEXT_PREVIEW_LENGTH) + if (fullText.length > TEXT_PREVIEW_LENGTH) "..." else ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.3f)
        )
        Text(
            text = displayText,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.7f),
            maxLines = if (expanded) Int.MAX_VALUE else maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SlotCard(
    title: String,
    data: MainViewModel.SlotInfo,
    isScanning: Boolean,
    isActive: Boolean,
    hasRoot: Boolean,
    onDetail: () -> Unit
) {
    DataCard(
        title = "$title ${if (isActive) "[${stringResource(R.string.current)}]" else ""}",
        button = {
            if (!isScanning) {
                ViewButton {
                    onDetail()
                }
            }
        }
    ) {
        val cardWidth = remember { mutableIntStateOf(0) }
        
        if (isScanning) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.height(24.dp))
            }
        } else {
            val arbValue = data.arbValue?.toString() ?: stringResource(R.string.loading)
            val arbColor = if (data.arbValue != null) {
                if (data.arbValue >= 1) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            } else {
                Color.Unspecified
            }
            DataRow(
                label = stringResource(R.string.arb_value),
                value = arbValue,
                valueColor = arbColor,
                mutableMaxWidth = cardWidth
            )
            DataRow(
                label = stringResource(R.string.device_path),
                value = data.devicePath ?: stringResource(R.string.loading),
                mutableMaxWidth = cardWidth
            )

            data.error?.let {
                Spacer(Modifier.height(4.dp))
                Row {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            if (data.arbValue != null) {
                Spacer(Modifier.height(4.dp))
                Row {
                    val warningText = if (data.arbValue >= 1) {
                        stringResource(R.string.arb_fused_warning)
                    } else {
                        stringResource(R.string.arb_safe)
                    }
                    val warningColor = if (data.arbValue >= 1) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                    Text(
                        text = warningText,
                        color = warningColor,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        
        Spacer(Modifier.height(8.dp))
    }

    if (!hasRoot) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = MASK_ALPHA))
        ) {
            Text(
                text = stringResource(R.string.non_root_mode_warning),
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        }
    }
}

@Composable
fun ResultCard(result: ArbResult) {
    val errorMsg = result.error
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CARD_CORNER_RADIUS),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.scan_result_title), style = MaterialTheme.typography.titleMedium)

            if (errorMsg != null) {
                Text(
                    text = stringResource(R.string.scan_failed, errorMsg),
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                val arbColor = if (result.arb >= 1) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
                Text(
                    text = stringResource(R.string.arb_value_label, result.arb),
                    color = arbColor
                )
                val warningText = if (result.arb >= 1) {
                    stringResource(R.string.arb_fused_warning)
                } else {
                    stringResource(R.string.arb_safe)
                }
                val warningColor = if (result.arb >= 1) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
                Text(
                    text = warningText,
                    color = warningColor,
                    style = MaterialTheme.typography.bodySmall
                )
                if (result.debugMessages.isNotEmpty()) {
                    Text(stringResource(R.string.debug_messages), style = MaterialTheme.typography.titleSmall)
                    LazyColumn(modifier = Modifier.heightIn(max = RESULT_LIST_MAX_HEIGHT)) {
                        items(result.debugMessages) { msg ->
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
    }
}