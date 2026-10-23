package io.github.openquesttuner.persistence

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.github.openquesttuner.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class GameLearningStore(private val context: Context) : LearningStore {
    private val prefs=context.getSharedPreferences("measured_learning_v1",Context.MODE_PRIVATE)
    private val json=Json { ignoreUnknownKeys=true; encodeDefaults=true }
    private val _windows=MutableStateFlow(runCatching { json.decodeFromString<List<LearnedWindow>>(prefs.getString("windows","[]")!!) }.getOrDefault(emptyList()))
    val observed=_windows.asStateFlow()
    private val _overlayEnabled=MutableStateFlow(prefs.getBoolean("native_learning_overlay",true))
    val overlayEnabled=_overlayEnabled.asStateFlow()
    private val _events=MutableStateFlow(runCatching { json.decodeFromString<List<LearningEvent>>(prefs.getString("events","[]")!!) }.getOrDefault(emptyList()))
    val events=_events.asStateFlow()
    fun setOverlayEnabled(enabled: Boolean): Boolean {
        if(!prefs.edit().putBoolean("native_learning_overlay",enabled).commit()) return false
        _overlayEnabled.value=enabled;return true
    }
    @kotlinx.serialization.Serializable data class Completion(val context: LearningContext,val profile: GameProfile)
    private val _completed=MutableStateFlow(runCatching { json.decodeFromString<Map<String,Completion>>(prefs.getString("native_completed","{}")!!) }.getOrDefault(emptyMap()))
    val completed=_completed.asStateFlow()
    fun isCompleted(pkg: String): Boolean { val saved=_completed.value[pkg] ?: return false;return saved.context==contextFor(pkg) }
    @Synchronized fun markCompleted(ctx: LearningContext,profile: GameProfile): Boolean {
        val next=_completed.value+(ctx.packageName to Completion(ctx,profile))
        if(!prefs.edit().putString("native_completed",json.encodeToString(next)).commit())return false
        _completed.value=next;return true
    }
    @Synchronized fun resetCompletion(pkg: String): Boolean {
        val next=_completed.value-pkg
        if(!prefs.edit().putString("native_completed",json.encodeToString(next)).commit())return false
        _completed.value=next;return true
    }
    fun setup(pkg: String): LearningConfig = runCatching {
        json.decodeFromString<LearningConfig>(prefs.getString("setup:$pkg",null) ?: return LearningExperience.defaults(halfRate=halfRate(pkg)))
    }.getOrDefault(LearningExperience.defaults(halfRate=halfRate(pkg))).takeIf { it.valid() } ?: LearningExperience.defaults(halfRate=halfRate(pkg))
    fun saveSetup(pkg: String,config: LearningConfig)=prefs.edit().putString("setup:$pkg",json.encodeToString(config)).commit()
    @Synchronized override fun event(event: LearningEvent): Boolean {
        val next=(_events.value+event).takeLast(256)
        if(!prefs.edit().putString("events",json.encodeToString(next)).commit()) return false
        _events.value=next;return true
    }
    fun ovrCaptureOwned()=prefs.getBoolean("ovr_capture_owned",false)
    fun setOvrCaptureOwned(owned: Boolean)=prefs.edit().putBoolean("ovr_capture_owned",owned).commit()
    override fun windows()=_windows.value
    fun halfRate(pkg: String)=prefs.getBoolean("half:$pkg",false)
    fun setHalfRate(pkg: String,value: Boolean)=prefs.edit().putBoolean("half:$pkg",value).commit()
    @Synchronized override fun add(window: LearnedWindow): Boolean {
        val next=GameLearning.retain(_windows.value.filterNot { it.context==window.context && it.runId==window.runId && it.stage==window.stage }+window)
        if(!prefs.edit().putString("windows",json.encodeToString(next)).commit()) return false
        _windows.value=next; return true
    }
    override fun undo(): LearningUndo? = runCatching { json.decodeFromString<LearningUndo>(prefs.getString("undo",null) ?: return null) }.getOrNull()
    override fun saveUndo(undo: LearningUndo)=prefs.edit().putString("undo",json.encodeToString(undo)).commit()
    override fun clearUndo()=prefs.edit().remove("undo").commit()
    @Synchronized override fun invalidate(context: LearningContext, profile: GameProfile): Boolean {
        val next=_windows.value.map { if(it.context==context && it.profile==profile) it.copy(verifiedTrial=false) else it }
        if(!prefs.edit().putString("windows",json.encodeToString(next)).commit()) return false
        _windows.value=next; return true
    }
    @Synchronized override fun clear(packageName: String) {
        val next=_windows.value.filterNot { it.context.packageName==packageName }
        val events=_events.value.filterNot { it.packageName==packageName }
        if(prefs.edit().putString("windows",json.encodeToString(next)).putString("events",json.encodeToString(events)).commit()) { _windows.value=next; _events.value=events }
    }
    @Suppress("DEPRECATION") fun contextFor(pkg: String): LearningContext? = try {
        val info=context.packageManager.getPackageInfo(pkg,0)
        LearningContext(pkg,info.longVersionCode,Build.FINGERPRINT,QuestModel.fromBuild(Build.DEVICE,Build.MODEL).name)
    } catch(_: PackageManager.NameNotFoundException) { null }
}
