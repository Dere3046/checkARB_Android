package com.dere3046.checkarb

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dere3046.checkarb.log.LogEntry
import com.dere3046.checkarb.log.LogManager
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(navController: NavController) {
    var logs by remember { mutableStateOf(LogManager.getAll()) }
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            logs = LogManager.getAll()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.logs)) },
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
        SelectionContainer {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(8.dp)
            ) {
                items(logs, key = { it.hashCode() }) { entry ->
                    LogEntryItem(entry, dateFormat)
                }
            }
        }
    }
}

@Composable
fun LogEntryItem(entry: LogEntry, dateFormat: SimpleDateFormat) {
    val color = when (entry.level) {
        LogEntry.Level.VERBOSE -> Color.Gray
        LogEntry.Level.DEBUG -> Color.Blue
        LogEntry.Level.INFO -> Color.Green
        LogEntry.Level.WARN -> Color.Yellow
        LogEntry.Level.ERROR -> Color.Red
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Box(modifier = Modifier.padding(8.dp)) {
            Text(
                text = "${dateFormat.format(entry.timestamp)} ${entry.level}/${entry.tag}: ${entry.message}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}