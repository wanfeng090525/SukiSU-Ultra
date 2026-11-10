package com.sukisu.ultra.ui.screen.terminal

import android.net.Uri
import android.os.Process
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sukisu.ultra.ksuApp
import com.sukisu.ultra.ui.util.withNewRootShell
import com.topjohnwu.superuser.CallbackList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

@Immutable
data class TerminalProcess(
    val pid: Int,
    val ppid: Int,
    val user: String,
    val state: String,
    val command: String,
    val isSelf: Boolean = false,
) {
    val name: String
        get() = command.substringBefore(' ').substringAfterLast('/').ifBlank { command }
}

@Immutable
data class TerminalRun(
    val id: Long,
    val name: String,
    val pid: Int?,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val exitCode: Int? = null,
    val stopped: Boolean = false,
)

@Immutable
data class TerminalUiState(
    val editor: String = DEFAULT_SCRIPT,
    val scriptName: String = "script.sh",
    val output: String = "",
    val running: Boolean = false,
    val currentPid: Int? = null,
    val exitCode: Int? = null,
    val processes: List<TerminalProcess> = emptyList(),
    val processLoading: Boolean = false,
    val processError: String? = null,
    val history: List<TerminalRun> = emptyList(),
    val message: String? = null,
) {
    companion object {
        const val DEFAULT_SCRIPT = "#!/system/bin/sh\n\n"
    }
}

class TerminalViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    private val outputLock = Any()
    private val output = StringBuilder()
    private var lastOutputPush = 0L
    private var flushJob: Job? = null
    private var activeRunToken = 0L
    private var stopRequested = false

    fun updateEditor(text: String) {
        _uiState.update { it.copy(editor = text) }
    }

    fun loadScript(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                ksuApp.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    val content = reader.readText()
                    val name = uri.lastPathSegment?.substringAfterLast('/') ?: "script.sh"
                    content.take(MAX_SCRIPT_LENGTH) to name
                } ?: error("empty stream")
            }
            result.onSuccess { (content, name) ->
                _uiState.update { it.copy(editor = content, scriptName = name) }
            }.onFailure { error ->
                _uiState.update { it.copy(message = error.message ?: "read failed") }
            }
        }
    }

    fun run() {
        val state = _uiState.value
        if (state.running) return
        val script = state.editor
        if (script.isBlank()) {
            _uiState.update { it.copy(message = "script is empty") }
            return
        }

        val token = ++activeRunToken
        stopRequested = false
        synchronized(outputLock) {
            output.setLength(0)
        }
        val scriptFile = File(ksuApp.filesDir, "terminal").apply { mkdirs() }
            .resolve("run-${System.currentTimeMillis()}.sh")
        runCatching { scriptFile.writeText(script) }.onFailure { error ->
            _uiState.update { it.copy(message = error.message ?: "write failed") }
            return
        }

        val runId = System.currentTimeMillis()
        _uiState.update {
            it.copy(
                output = "",
                running = true,
                exitCode = null,
                currentPid = null,
                history = it.history + TerminalRun(
                    id = runId,
                    name = state.scriptName,
                    pid = null,
                    startedAt = runId,
                ),
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            val path = scriptFile.absolutePath.replace("'", "'\\''")
            val command = buildString {
                append("cd /; printf '__PMAX_TERMINAL_PID__ %s\\n' \$\$; ")
                append("if command -v setsid >/dev/null 2>&1; then ")
                append("exec setsid sh '$path'; else exec sh '$path'; fi")
            }
            val stdout = object : CallbackList<String?>() {
                override fun onAddElement(element: String?) {
                    handleOutput(token, element.orEmpty(), stderr = false)
                }
            }
            val stderr = object : CallbackList<String?>() {
                override fun onAddElement(element: String?) {
                    handleOutput(token, element.orEmpty(), stderr = true)
                }
            }
            val result = runCatching {
                withNewRootShell {
                    newJob().add(command).to(stdout, stderr).exec()
                }
            }
            if (token != activeRunToken) return@launch

            val stopped = stopRequested
            val exitCode = result.getOrNull()?.code
            flushOutput(force = true, token = token)
            appendOutput(
                token,
                "\n[${if (stopped) "stopped" else "exit"} ${exitCode ?: result.exceptionOrNull()?.message ?: "?"}]\n",
            )
            flushOutput(force = true, token = token)

            _uiState.update { current ->
                current.copy(
                    running = false,
                    currentPid = null,
                    exitCode = if (stopped) -1 else exitCode,
                    history = current.history.map { run ->
                        if (run.id == runId) {
                            run.copy(
                                finishedAt = System.currentTimeMillis(),
                                exitCode = if (stopped) -1 else exitCode,
                                stopped = stopped,
                            )
                        } else {
                            run
                        }
                    },
                )
            }
        }
    }

    fun stopCurrent() {
        val pid = _uiState.value.currentPid ?: return
        if (!_uiState.value.running) return
        stopRequested = true
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                withNewRootShell {
                    newJob()
                        .add(
                            "kill -TERM -$pid 2>/dev/null; kill -TERM $pid 2>/dev/null; " +
                                "sleep 0.4; kill -KILL -$pid 2>/dev/null; kill -KILL $pid 2>/dev/null; true",
                        )
                        .exec()
                }
            }
        }
    }

    fun refreshProcesses() {
        if (_uiState.value.processLoading) return
        _uiState.update { it.copy(processLoading = true, processError = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                withNewRootShell {
                    val primary = newJob()
                        .add("ps -A -o PID,PPID,USER,STAT,ARGS 2>/dev/null")
                        .to(ArrayList<String>(), null)
                        .exec()
                    if (primary.out.any { it.contains("PPID") }) {
                        primary.out
                    } else {
                        newJob().add("ps -A 2>/dev/null").to(ArrayList<String>(), null).exec().out
                    }
                }
            }
            result.onSuccess { lines ->
                _uiState.update { it.copy(processes = parseProcesses(lines), processLoading = false) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(processLoading = false, processError = error.message ?: "ps failed")
                }
            }
        }
    }

    fun killProcess(pid: Int, force: Boolean) {
        if (pid <= 0) return
        viewModelScope.launch(Dispatchers.IO) {
            val signal = if (force) "KILL" else "TERM"
            val succeeded = runCatching {
                withNewRootShell {
                    newJob().add("kill -$signal $pid").exec().code == 0
                }
            }.getOrDefault(false)
            _uiState.update {
                it.copy(
                    message = if (succeeded) {
                        "signal $signal sent to $pid"
                    } else {
                        "failed to stop $pid"
                    },
                )
            }
            refreshProcesses()
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun clearOutput() {
        synchronized(outputLock) {
            output.setLength(0)
        }
        _uiState.update { it.copy(output = "", exitCode = null) }
    }

    override fun onCleared() {
        stopCurrent()
        super.onCleared()
    }

    private fun handleOutput(token: Long, element: String, stderr: Boolean) {
        if (token != activeRunToken) return
        val line = element.trimEnd('\n')
        if (line.startsWith(PID_MARKER)) {
            val pid = line.removePrefix(PID_MARKER).trim().toIntOrNull()
            if (pid != null) {
                _uiState.update { it.copy(currentPid = pid) }
            }
            return
        }
        appendOutput(token, if (stderr) "[err] $line\n" else "$line\n")
    }

    private fun appendOutput(token: Long, text: String) {
        if (token != activeRunToken) return
        synchronized(outputLock) {
            output.append(text)
            if (output.length > MAX_OUTPUT_CHARS) {
                output.delete(0, output.length - KEEP_OUTPUT_CHARS)
            }
        }
        scheduleFlush(token)
    }

    private fun scheduleFlush(token: Long) {
        val now = System.currentTimeMillis()
        if (now - lastOutputPush >= OUTPUT_PUSH_INTERVAL_MS) {
            lastOutputPush = now
            flushOutput(force = false, token = token)
            return
        }
        if (flushJob?.isActive == true) return
        flushJob = viewModelScope.launch(Dispatchers.Default) {
            delay(OUTPUT_PUSH_INTERVAL_MS)
            flushOutput(force = true, token = token)
        }
    }

    private fun flushOutput(force: Boolean, token: Long) {
        if (token != activeRunToken) return
        if (!force && System.currentTimeMillis() - lastOutputPush < OUTPUT_PUSH_INTERVAL_MS) return
        lastOutputPush = System.currentTimeMillis()
        val text = synchronized(outputLock) { output.toString() }
        _uiState.update { it.copy(output = text) }
    }

    private fun parseProcesses(lines: List<String>): List<TerminalProcess> {
        val headerIndex = lines.indexOfFirst { it.contains("PPID") && it.contains("PID") }
        val rows = if (headerIndex >= 0) {
            lines.drop(headerIndex + 1).mapNotNull { line ->
                val parts = line.trim().split(WHITESPACE, limit = 5)
                if (parts.size < 5) return@mapNotNull null
                val pid = parts[0].toIntOrNull() ?: return@mapNotNull null
                TerminalProcess(
                    pid = pid,
                    ppid = parts[1].toIntOrNull() ?: 0,
                    user = parts[2],
                    state = parts[3],
                    command = parts[4],
                    isSelf = pid == Process.myPid(),
                )
            }
        } else {
            // Older toybox builds only support plain `ps`, whose columns are
            // USER PID PPID VSZ RSS WCHAN ADDR S NAME.
            lines.dropWhile { it.contains("USER") && it.contains("PID") }
                .mapNotNull { line ->
                    val parts = line.trim().split(WHITESPACE, limit = 9)
                    if (parts.size < 9) return@mapNotNull null
                    val pid = parts[1].toIntOrNull() ?: return@mapNotNull null
                    TerminalProcess(
                        pid = pid,
                        ppid = parts[2].toIntOrNull() ?: 0,
                        user = parts[0],
                        state = parts[7],
                        command = parts[8],
                        isSelf = pid == Process.myPid(),
                    )
                }
        }
        return rows.distinctBy { it.pid }.sortedBy { it.pid }
    }

    companion object {
        private const val PID_MARKER = "__PMAX_TERMINAL_PID__"
        private const val MAX_SCRIPT_LENGTH = 1024 * 1024
        private const val MAX_OUTPUT_CHARS = 120_000
        private const val KEEP_OUTPUT_CHARS = 100_000
        private const val OUTPUT_PUSH_INTERVAL_MS = 90L
        private val WHITESPACE = Regex("\\s+")
    }
}
