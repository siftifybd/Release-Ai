package com.jarvis.assistant.audio

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.vosk.Model
import org.vosk.SpeakerModel
import java.io.File
import java.io.FileOutputStream

object VoskModelManager {
    private const val TAG = "VoskModelManager"

    private val _isModelReady = MutableStateFlow(false)
    val isModelReady: StateFlow<Boolean> = _isModelReady.asStateFlow()

    private val _isSpeakerModelReady = MutableStateFlow(false)
    val isSpeakerModelReady: StateFlow<Boolean> = _isSpeakerModelReady.asStateFlow()

    @Volatile
    var model: Model? = null
        private set

    @Volatile
    var speakerModel: SpeakerModel? = null
        private set

    private val isInitializing = java.util.concurrent.atomic.AtomicBoolean(false)

    @Volatile
    var hasAttemptedInit: Boolean = false
        private set

    fun init(context: Context, scope: CoroutineScope) {
        if (model != null) return
        if (!isInitializing.compareAndSet(false, true)) return

        scope.launch(Dispatchers.IO) {
            try {
                // 1. Unpack speech recognition model assets if present
                val targetDir = File(context.filesDir, "vosk-model")
                val acousticMarker = File(targetDir, "am/final.mdl")
                val flatMarker = File(targetDir, "final.mdl")

                if (!targetDir.exists() || (!acousticMarker.exists() && !flatMarker.exists())) {
                    Log.i(TAG, "Unpacking Vosk assets if available to: ${targetDir.absolutePath}")
                    targetDir.mkdirs()
                    copyAssetFolder(context.assets, "model-en-us", targetDir)
                }

                // Check whether valid acoustic model files actually exist before invoking native constructor
                val hasAcousticModel = File(targetDir, "am/final.mdl").exists() ||
                                       File(targetDir, "final.mdl").exists()

                if (hasAcousticModel) {
                    try {
                        val loadedModel = Model(targetDir.absolutePath)
                        model = loadedModel
                        _isModelReady.value = true
                        Log.i(TAG, "Vosk Model ready in memory!")
                    } catch (e: Throwable) {
                        Log.w(TAG, "Could not load Vosk Model from storage: ${e.message}")
                        model = null
                        _isModelReady.value = false
                    }
                } else {
                    Log.w(TAG, "Offline Vosk acoustic model (final.mdl) is not bundled in assets. Running in Cloud AI & Direct Audio mode.")
                    model = null
                    _isModelReady.value = false
                }

                // 2. Unpack & load speaker recognition model (x-vector extractor) if present
                try {
                    val spkTargetDir = File(context.filesDir, "vosk-spk-model")
                    val spkMarkerFile = File(spkTargetDir, "final.ext.raw")

                    if (!spkMarkerFile.exists()) {
                        spkTargetDir.mkdirs()
                        copyAssetFolder(context.assets, "model-spk", spkTargetDir)
                    }

                    if (File(spkTargetDir, "final.ext.raw").exists()) {
                        val loadedSpk = SpeakerModel(spkTargetDir.absolutePath)
                        speakerModel = loadedSpk
                        _isSpeakerModelReady.value = true
                        Log.i(TAG, "Vosk SpeakerModel ready in memory!")
                    } else {
                        Log.w(TAG, "Offline Vosk SpeakerModel (final.ext.raw) not bundled.")
                        speakerModel = null
                        _isSpeakerModelReady.value = false
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Vosk SpeakerModel initialization: ${e.message}")
                    speakerModel = null
                    _isSpeakerModelReady.value = false
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Vosk initialization fallback: ${e.message}")
            } finally {
                hasAttemptedInit = true
                isInitializing.set(false)
            }
        }
    }

    private fun copyAssetFolder(assetManager: AssetManager, fromAssetPath: String, toDir: File) {
        val list = assetManager.list(fromAssetPath)
        if (list.isNullOrEmpty()) {
            try {
                assetManager.open(fromAssetPath).use { input ->
                    toDir.parentFile?.mkdirs()
                    FileOutputStream(toDir).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not copy asset $fromAssetPath: ${e.message}")
            }
        } else {
            toDir.mkdirs()
            for (item in list) {
                val childAsset = if (fromAssetPath.isEmpty()) item else "$fromAssetPath/$item"
                val childTarget = File(toDir, item)
                copyAssetFolder(assetManager, childAsset, childTarget)
            }
        }
    }
}
