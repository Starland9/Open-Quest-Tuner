package io.github.openquesttuner.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable data class WmmSnapshot(val bootId: String, val original: Map<String, String>)
interface WmmSnapshotStore { fun load(): WmmSnapshot?; fun save(snapshot: WmmSnapshot): Boolean; fun clear() }

enum class WmmStatus { ON, OFF, PAUSED, MIXED, UNKNOWN, UNSUPPORTED }
data class WmmState(val status: WmmStatus = WmmStatus.UNKNOWN, val canRestore: Boolean = false)

/** Official test override with readback, rollback and a boot-scoped, durable undo snapshot. */
class WmmController(private val shell: ShellBackend, private val model: QuestModel, private val store: WmmSnapshotStore) {
    private val keys = WmmProperty.entries
    private val enabledValues = listOf("BODY_TRACKING", "1", "3")
    /** Read the actual override, never infer success from a button press or saved preference. */
    suspend fun readState(): WmmState {
        if (model !in listOf(QuestModel.QUEST_3, QuestModel.QUEST_3S)) return WmmState(WmmStatus.UNSUPPORTED)
        if (shell.state.value !is ConnectionState.Connected) return WmmState()
        return try {
            val boot = shell.exec(ShellCommand.bootId())
            if (!boot.isSuccess || !Regex("[a-fA-F0-9-]{36}").matches(boot.output.trim())) return WmmState()
            var saved = store.load()
            if (saved != null && saved.bootId != boot.output.trim()) { store.clear(); saved = null }
            val current = keys.map { key ->
                val read = shell.exec(ShellCommand.readDeviceProperty(key))
                if (!read.isSuccess) return WmmState()
                read.output.trim()
            }
            val matches = current.zip(enabledValues).count { (actual, enabled) -> actual == enabled }
            val status = when {
                matches == 3 -> WmmStatus.ON
                current == listOf("BODY_TRACKING", "1", "0") -> WmmStatus.PAUSED
                matches == 0 -> WmmStatus.OFF
                else -> WmmStatus.MIXED
            }
            val canRestore = saved != null && keys.indices.all {
                restoreCompatible(keys[it], current[it], saved.original[keys[it].key].orEmpty())
            }
            if (shell.state.value !is ConnectionState.Connected) WmmState() else WmmState(status, canRestore)
        } catch (_: ShellUnavailableException) { WmmState() }
    }
    suspend fun setEnabled(enabled: Boolean): String {
        if (model !in listOf(QuestModel.QUEST_3, QuestModel.QUEST_3S)) return "WMM requires Quest 3 or Quest 3S."
        val boot = shell.exec(ShellCommand.bootId())
        if (!boot.isSuccess || !Regex("[a-fA-F0-9-]{36}").matches(boot.output.trim())) return "Cannot identify this boot; no WMM changes were made."
        var saved = store.load()
        if (saved != null && saved.bootId != boot.output.trim()) { store.clear(); saved = null }
        val current = keys.associateWith { key ->
            val read = shell.exec(ShellCommand.readDeviceProperty(key))
            if (!read.isSuccess) return "Could not read ${key.key}; no changes were made."
            read.output.trim()
        }
        if (enabled && current.getValue(keys[0]) !in listOf("", "BODY_TRACKING")) return "Another performance-feature override is active. WMM was not changed, to preserve it."
        if (!enabled && saved == null) return "No OQT WMM override remains to restore on this boot."
        if (!enabled && keys.indices.any { !restoreCompatible(keys[it], current.getValue(keys[it]), saved!!.original[keys[it].key].orEmpty()) })
            return "WMM properties changed outside OQT. Restore stopped to preserve those changes; inspect them in Terminal."
        if (enabled && saved == null && keys.indices.all { current.getValue(keys[it]) == enabledValues[it] })
            return "WMM override is already on. OQT has no previous settings to restore; reboot to clear the temporary override."
        if (enabled && saved == null) {
            saved = WmmSnapshot(boot.output.trim(), current.mapKeys { it.key.key })
            if (!store.save(saved)) return "Could not save the undo snapshot. No WMM changes were made."
        }
        val values = if (enabled) enabledValues else keys.map { saved!!.original[it.key].orEmpty() }
        try {
            for ((index, key) in keys.withIndex()) {
                val write = shell.exec(ShellCommand.deviceProperty(key, values[index]))
                val read = shell.exec(ShellCommand.readDeviceProperty(key))
                if (!write.isSuccess || !read.isSuccess || read.output.trim() != values[index]) {
                    val restored = rollback(current)
                    return "WMM failed at ${key.key}. " + if (restored) "Previous values restored and read back." else "Rollback unconfirmed. Reconnect and use Restore."
                }
            }
        } catch (e: CancellationException) { withContext(NonCancellable) { rollback(current) }; throw e }
        catch (_: ShellUnavailableException) { return "ADB disconnected during WMM changes. Undo snapshot retained; reconnect and use Restore." }
        if (!enabled) store.clear()
        return "WMM ${if (enabled) "debug override read back" else "previous values restored"}. Restart the game. Check hand-pose behaviour in a compatible hand-tracking title."
    }
    /** Meta's live fusion toggle. Once configured, only handoverridetype changes (0 / 3). */
    suspend fun setFusionEnabled(enabled: Boolean): String {
        if (model !in listOf(QuestModel.QUEST_3, QuestModel.QUEST_3S)) return "WMM requires Quest 3 or Quest 3S."
        val boot = shell.exec(ShellCommand.bootId())
        if (!boot.isSuccess || !Regex("[a-fA-F0-9-]{36}").matches(boot.output.trim())) return "Cannot identify this boot; no WMM changes were made."
        var saved = store.load()
        if (saved != null && saved.bootId != boot.output.trim()) { store.clear(); saved = null }
        val current = keys.associateWith { key ->
            val read = shell.exec(ShellCommand.readDeviceProperty(key))
            if (!read.isSuccess) return "Could not read ${key.key}; no WMM changes were made."
            read.output.trim()
        }
        if (current.getValue(WmmProperty.FEATURES) != "BODY_TRACKING" || current.getValue(WmmProperty.ALLOW) != "1") {
            return if (enabled) setEnabled(true) else "WMM is not configured. Enable it before starting a compatible game; no changes were made."
        }
        val previous = current.getValue(WmmProperty.TYPE)
        if (previous !in listOf("0", "3")) return "WMM properties changed outside OQT; fusion toggle stopped to preserve those changes."
        val target = if (enabled) "3" else "0"
        if (previous == target) return "WMM fusion is already ${if (enabled) "on" else "off"}; readback verified."
        if (saved == null && !store.save(WmmSnapshot(boot.output.trim(), current.mapKeys { it.key.key })))
            return "Could not save the undo snapshot. No WMM changes were made."
        try {
            val write = shell.exec(ShellCommand.deviceProperty(WmmProperty.TYPE, target))
            val read = shell.exec(ShellCommand.readDeviceProperty(WmmProperty.TYPE))
            if (!write.isSuccess || !read.isSuccess || read.output.trim() != target) {
                val restored = restoreFusion(previous)
                return "WMM fusion change failed. " + if (restored) "Previous fusion value restored and read back." else "Restore unconfirmed; reconnect and check status."
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) { restoreFusion(previous) }; throw e
        } catch (_: ShellUnavailableException) {
            return "ADB disconnected during the fusion change. Status unconfirmed; reconnect and check. Undo snapshot retained."
        }
        return "WMM fusion ${if (enabled) "on" else "off"}; property readback verified. Setup is retained for this boot. Check tracking in the game."
    }
    private fun restoreCompatible(key: WmmProperty, current: String, original: String): Boolean =
        current == original || current == enabledValues[keys.indexOf(key)] || (key == WmmProperty.TYPE && current == "0")

    private suspend fun restoreFusion(previous: String): Boolean = try {
        val write = shell.exec(ShellCommand.deviceProperty(WmmProperty.TYPE, previous))
        val read = shell.exec(ShellCommand.readDeviceProperty(WmmProperty.TYPE))
        write.isSuccess && read.isSuccess && read.output.trim() == previous
    } catch (_: ShellUnavailableException) { false }

    private suspend fun rollback(current: Map<WmmProperty, String>): Boolean {
        var restored = true
        for (key in keys) try {
            val write = shell.exec(ShellCommand.deviceProperty(key, current.getValue(key)))
            val read = shell.exec(ShellCommand.readDeviceProperty(key))
            if (!write.isSuccess || !read.isSuccess || read.output.trim() != current.getValue(key)) restored = false
        } catch (_: ShellUnavailableException) { return false }
        return restored
    }
}
