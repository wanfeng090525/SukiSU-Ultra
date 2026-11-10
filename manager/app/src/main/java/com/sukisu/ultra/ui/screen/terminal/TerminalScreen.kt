package com.sukisu.ultra.ui.screen.terminal

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sukisu.ultra.R
import kotlinx.coroutines.delay

private enum class TerminalTab { Script, Processes }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    onBack: () -> Unit,
    viewModel: TerminalViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(TerminalTab.Script) }
    var autoRefresh by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var details by remember { mutableStateOf<TerminalProcess?>(null) }
    var confirmStop by remember { mutableStateOf<TerminalProcess?>(null) }
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let(viewModel::loadScript)
    }

    LaunchedEffect(tab, autoRefresh) {
        if (tab == TerminalTab.Processes) {
            viewModel.refreshProcesses()
            while (autoRefresh) {
                delay(4_000)
                viewModel.refreshProcesses()
            }
        }
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.terminal_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(id = R.string.close),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabRow(selectedTabIndex = tab.ordinal) {
                Tab(
                    selected = tab == TerminalTab.Script,
                    onClick = { tab = TerminalTab.Script },
                    text = { Text(stringResource(id = R.string.terminal_tab_script)) },
                )
                Tab(
                    selected = tab == TerminalTab.Processes,
                    onClick = { tab = TerminalTab.Processes },
                    text = { Text(stringResource(id = R.string.terminal_tab_processes)) },
                )
            }

            when (tab) {
                TerminalTab.Script -> ScriptTab(
                    state = state,
                    onEditorChange = viewModel::updateEditor,
                    onRun = viewModel::run,
                    onStop = viewModel::stopCurrent,
                    onClear = viewModel::clearOutput,
                    onPickScript = {
                        picker.launch(
                            arrayOf(
                                "text/x-shellscript",
                                "application/x-sh",
                                "text/plain",
                            ),
                        )
                    },
                )

                TerminalTab.Processes -> ProcessTab(
                    state = state,
                    search = search,
                    onSearchChange = { search = it },
                    autoRefresh = autoRefresh,
                    onAutoRefreshChange = { autoRefresh = it },
                    onRefresh = viewModel::refreshProcesses,
                    onOpenDetails = { details = it },
                    onStopProcess = { confirmStop = it },
                )
            }
        }
    }

    details?.let { process ->
        ProcessDetailsDialog(
            process = process,
            onTerminate = {
                viewModel.killProcess(process.pid, force = false)
                details = null
            },
            onForceStop = {
                viewModel.killProcess(process.pid, force = true)
                details = null
            },
            onDismiss = { details = null },
        )
    }

    confirmStop?.let { process ->
        AlertDialog(
            onDismissRequest = { confirmStop = null },
            title = { Text(stringResource(id = R.string.terminal_kill_title)) },
            text = {
                Text(
                    stringResource(
                        id = R.string.terminal_kill_confirm,
                        process.pid,
                        process.name,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.killProcess(process.pid, force = false)
                    confirmStop = null
                }) {
                    Text(stringResource(id = R.string.terminal_kill_term))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        viewModel.killProcess(process.pid, force = true)
                        confirmStop = null
                    }) {
                        Text(
                            text = stringResource(id = R.string.terminal_kill_force),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    TextButton(onClick = { confirmStop = null }) {
                        Text(stringResource(id = R.string.cancel))
                    }
                }
            },
        )
    }
}

@Composable
private fun ScriptTab(
    state: TerminalUiState,
    onEditorChange: (String) -> Unit,
    onRun: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    onPickScript: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val statusSuffix = when {
                state.running -> stringResource(
                    R.string.terminal_running_pid,
                    state.currentPid ?: 0,
                )

                state.exitCode != null -> stringResource(
                    R.string.terminal_exit_code,
                    state.exitCode,
                )

                else -> null
            }
            Text(
                text = buildString {
                    append(state.scriptName)
                    if (statusSuffix != null) {
                        append(" · ")
                        append(statusSuffix)
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (state.running) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
            }
        }

        OutlinedTextField(
            value = state.editor,
            onValueChange = onEditorChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            label = { Text(stringResource(id = R.string.terminal_editor_label)) },
            enabled = !state.running,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(onClick = onPickScript, enabled = !state.running) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(id = R.string.terminal_pick_script))
            }
            Spacer(Modifier.weight(1f))
            if (state.running) {
                Button(onClick = onStop) {
                    Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(id = R.string.terminal_stop))
                }
            } else {
                Button(onClick = onRun) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(id = R.string.terminal_run))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(id = R.string.terminal_output),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClear) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(id = R.string.terminal_clear),
                )
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            if (state.output.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(id = R.string.terminal_no_output),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val scrollState = rememberScrollState()
                LaunchedEffect(state.output.length) {
                    scrollState.scrollTo(scrollState.maxValue)
                }
                SelectionContainer {
                    Text(
                        text = state.output,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(12.dp),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                    )
                }
            }
        }

        if (state.history.isNotEmpty()) {
            Text(
                text = stringResource(id = R.string.terminal_history),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                state.history.takeLast(3).reversed().forEach { run ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = run.name,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = when {
                                run.pid != null && run.finishedAt == null -> "PID ${run.pid}"
                                run.stopped -> stringResource(R.string.terminal_stopped)
                                run.exitCode != null -> "exit ${run.exitCode}"
                                else -> ""
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcessTab(
    state: TerminalUiState,
    search: String,
    onSearchChange: (String) -> Unit,
    autoRefresh: Boolean,
    onAutoRefreshChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onOpenDetails: (TerminalProcess) -> Unit,
    onStopProcess: (TerminalProcess) -> Unit,
) {
    val query = search.trim().lowercase()
    val filtered = remember(state.processes, query) {
        if (query.isEmpty()) {
            state.processes
        } else {
            state.processes.filter { process ->
                process.name.lowercase().contains(query) ||
                    process.command.lowercase().contains(query) ||
                    process.user.lowercase().contains(query) ||
                    process.pid.toString().contains(query)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        OutlinedTextField(
            value = search,
            onValueChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            placeholder = { Text(stringResource(id = R.string.terminal_process_search)) },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(id = R.string.terminal_process_count, filtered.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(id = R.string.terminal_auto_refresh),
                style = MaterialTheme.typography.bodySmall,
            )
            Switch(
                checked = autoRefresh,
                onCheckedChange = onAutoRefreshChange,
                modifier = Modifier.padding(start = 6.dp),
            )
            IconButton(onClick = onRefresh, enabled = !state.processLoading) {
                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = stringResource(id = R.string.terminal_process_refresh),
                )
            }
        }
        if (state.processLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        state.processError?.let { error ->
            Text(
                text = stringResource(id = R.string.terminal_process_error, error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        HorizontalDivider()
        if (filtered.isEmpty() && !state.processLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(id = R.string.terminal_process_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(filtered, key = { it.pid }) { process ->
                    ProcessRow(
                        process = process,
                        onClick = { onOpenDetails(process) },
                        onStop = { onStopProcess(process) },
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun ProcessRow(
    process: TerminalProcess,
    onClick: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = process.pid.toString(),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.size(width = 56.dp, height = 20.dp),
            color = MaterialTheme.colorScheme.primary,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        ) {
            Text(
                text = process.name.ifBlank { process.command },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${process.user} · ${process.state}" +
                    if (process.isSelf) " · ${stringResource(R.string.terminal_self)}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        TextButton(onClick = onClick) {
            Text(stringResource(id = R.string.terminal_details))
        }
        IconButton(onClick = onStop, enabled = !process.isSelf) {
            Icon(
                Icons.Rounded.StopCircle,
                contentDescription = stringResource(id = R.string.terminal_kill_title),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

@Composable
private fun ProcessDetailsDialog(
    process: TerminalProcess,
    onTerminate: () -> Unit,
    onForceStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.terminal_process_details)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DetailLine(stringResource(id = R.string.terminal_pid), process.pid.toString())
                DetailLine(stringResource(id = R.string.terminal_ppid), process.ppid.toString())
                DetailLine(stringResource(id = R.string.terminal_user), process.user)
                DetailLine(stringResource(id = R.string.terminal_state), process.state)
                DetailLine(stringResource(id = R.string.terminal_command), process.command)
            }
        },
        confirmButton = {
            TextButton(onClick = onTerminate) {
                Text(stringResource(id = R.string.terminal_kill_term))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onForceStop) {
                    Text(
                        text = stringResource(id = R.string.terminal_kill_force),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(id = R.string.close))
                }
            }
        },
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(width = 72.dp, height = 20.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.weight(1f),
        )
    }
}
