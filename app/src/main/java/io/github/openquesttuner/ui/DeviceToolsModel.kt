package io.github.openquesttuner.ui

import android.content.Context
import io.github.openquesttuner.AppContainer
import io.github.openquesttuner.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json

class DeviceToolsModel(context: Context, private val container: AppContainer, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("device_overrides", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val snapshot = object : WmmSnapshotStore {
        override fun load(): WmmSnapshot? = runCatching { json.decodeFromString(WmmSnapshot.serializer(), prefs.getString("wmm_snapshot", "null") ?: "null") }.getOrNull()
        override fun save(snapshot: WmmSnapshot): Boolean = prefs.edit().putString("wmm_snapshot", json.encodeToString(WmmSnapshot.serializer(), snapshot)).commit()
        override fun clear() { prefs.edit().remove("wmm_snapshot").commit() }
    }
    private val wmmController = WmmController(container.adb, container.questModel, snapshot)
    private val _wmmState = MutableStateFlow(WmmState())
    val wmmState = _wmmState.asStateFlow()
    init {
        scope.launch {
            container.adb.state.collect { _ ->
                // A lost or newly established connection invalidates the previous readback.
                _wmmState.value = WmmState()
            }
        }
    }
    fun refreshWmm() {
        if (_busy.value) return
        run {
            _wmmState.value = wmmController.readState()
            _output.value
        }
    }
    private val _output = MutableStateFlow("Pair the headset to use device tools.")
    val output = _output.asStateFlow()
    private val _terminal = MutableStateFlow("On-device ADB shell. Enter a shell command below.")
    val terminal = _terminal.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _terminalRunning = MutableStateFlow(false)
    val terminalRunning = _terminalRunning.asStateFlow()
    private val _users = MutableStateFlow<List<HeadsetUser>>(emptyList())
    val users = _users.asStateFlow()
    private val _result = MutableStateFlow<String?>(null)
    val result = _result.asStateFlow()
    private var job: Job? = null
    fun dismissResult() { _result.value = null }
    fun report(text: String) { _output.value = text; _result.value = text }
    private fun run(showResult: Boolean = false, block: suspend () -> String) {
        if (_busy.value) return
        _busy.value = true
        job = scope.launch {
            try { _output.value = block() }
            catch (_: ShellUnavailableException) {
                _output.value = "ADB unavailable or command timed out. Check headset state before retrying."
                if (_terminalRunning.value) _terminal.update { (it + "\n" + _output.value).takeLast(48_000) }
            }
            catch (e: IllegalArgumentException) { _output.value = e.message ?: "Invalid command" }
            finally { _terminalRunning.value = false; _busy.value = false; if (showResult) _result.value = _output.value }
        }
    }
    fun runTerminal(script: String) = run {
        _terminalRunning.value = true
        val command = ShellCommand.terminal(script.removePrefix("adb shell "))
        val result = container.adb.exec(command)
        val response = "$ $script\nExit ${result.exitCode ?: "unknown"}\n${result.output.takeLast(40_000)}"
        _terminal.update { (it + "\n\n" + response).takeLast(48_000) }
        response
    }
    fun cancelTerminal() { if (_terminalRunning.value) { job?.cancel(); _terminal.update { (it + "\n[Command cancelled]").takeLast(48_000) } } }
    fun power(reboot: Boolean) = run(showResult = true) {
        val result = container.adb.exec(ShellCommand.power(reboot))
        if (result.isSuccess) "${if (reboot) "Reboot" else "Shutdown"} requested. The connection may close." else "Power request failed: ${result.output}"
    }
    fun loadUsers() = run {
        _users.value = emptyList()
        val result = container.adb.exec(ShellCommand.users())
        if (result.isSuccess) _users.value = HeadsetUsers.parse(result.output)
        if (_users.value.size < 2) "No additional switchable accounts found. Add an account in Quest system settings." else "Choose an account. Horizon OS may require its unlock code."
    }
    fun switchUser(id: Int) = run(showResult = true) {
        require(_users.value.any { it.id == id }) { "Refresh the headset account list first." }
        val sent = container.adb.exec(ShellCommand.switchUser(id))
        val read = container.adb.exec(ShellCommand.currentUser())
        if (sent.isSuccess && read.isSuccess && read.output.trim() == id.toString()) "Account switch verified." else "Switch not confirmed: ${sent.output}\n${read.output}"
    }
    fun wmmFusion(enabled: Boolean) = run {
        _wmmState.value = WmmState()
        try { wmmController.setFusionEnabled(enabled) }
        finally { _wmmState.value = wmmController.readState() }
    }
    fun wmm(enabled: Boolean) = run {
        _wmmState.value = WmmState()
        try { wmmController.setEnabled(enabled) }
        finally { _wmmState.value = wmmController.readState() }
    }
}
