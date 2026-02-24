package com.dere3046.checkarb

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dere3046.arbinspector.ArbInspector
import com.dere3046.arbinspector.ArbResult
import com.dere3046.checkarb.log.LogManager
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class AutoScanPreparation {
    object Ready : AutoScanPreparation()
    data class NeedRecreate(val devicePath: String, val major: Int, val minor: Int) : AutoScanPreparation()
    data class Error(val message: String) : AutoScanPreparation()
}

class AutoScanViewModel : ViewModel() {
    private val _deviceCandidates = MutableStateFlow<List<String>>(emptyList())
    val deviceCandidates: StateFlow<List<String>> = _deviceCandidates.asStateFlow()

    private val _selectedDevicePath = MutableStateFlow<String?>(null)
    val selectedDevicePath: StateFlow<String?> = _selectedDevicePath.asStateFlow()

    private val _isLoadingCandidates = MutableStateFlow(false)
    val isLoadingCandidates: StateFlow<Boolean> = _isLoadingCandidates.asStateFlow()

    private val _scanResult = MutableStateFlow("")
    val scanResult: StateFlow<String> = _scanResult.asStateFlow()

    private val _lastArb = MutableStateFlow<Int?>(null)
    val lastArb: StateFlow<Int?> = _lastArb.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _showResultDialog = MutableStateFlow(false)
    val showResultDialog: StateFlow<Boolean> = _showResultDialog.asStateFlow()

    private val _debugMode = MutableStateFlow(true)
    val debugMode: StateFlow<Boolean> = _debugMode.asStateFlow()

    private val _blockMode = MutableStateFlow(false)
    val blockMode: StateFlow<Boolean> = _blockMode.asStateFlow()

    private val _extractionTool = MutableStateFlow("cat")
    val extractionTool: StateFlow<String> = _extractionTool.asStateFlow()

    fun setDebugMode(enabled: Boolean) { _debugMode.value = enabled }
    fun setBlockMode(enabled: Boolean) { _blockMode.value = enabled }
    fun setExtractionTool(tool: String) { _extractionTool.value = tool }

    fun dismissResultDialog() {
        _showResultDialog.value = false
    }

    fun reportError(message: String) {
        _scanResult.value = message
        _lastArb.value = null
        _showResultDialog.value = true
        LogManager.e("AutoScan", message)
    }

    fun clearScanResult() {
        _scanResult.value = ""
        _lastArb.value = null
        _showResultDialog.value = false
    }

    fun findTargetDevice() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingCandidates.update { true }
            try {
                val slotSuffix = getActiveSlotSuffix()
                LogManager.i("AutoScan", "Active slot suffix: $slotSuffix")

                val byNameResult = Shell.cmd("find /dev/block/by-name -name '*xbl_config*' 2>/dev/null").exec()
                var candidates = byNameResult.out.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

                if (candidates.isNotEmpty()) {
                    candidates = filterCandidatesBySlot(candidates, slotSuffix)
                } else {
                    LogManager.i("AutoScan", "No xbl_config in by-name, scanning /dev/block/")
                    val blockResult = Shell.cmd("find /dev/block -name '*xbl_config*' 2>/dev/null").exec()
                    candidates = blockResult.out.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                }

                _deviceCandidates.update { candidates }
                _selectedDevicePath.value = candidates.firstOrNull()
                if (candidates.isEmpty()) {
                    LogManager.w("AutoScan", "No xbl_config device found")
                }
            } catch (e: Exception) {
                LogManager.e("AutoScan", "Failed to find target device", e)
            } finally {
                _isLoadingCandidates.update { false }
            }
        }
    }

    private suspend fun getActiveSlotSuffix(): String = withContext(Dispatchers.IO) {
        var result = Shell.cmd("getprop ro.boot.slot_suffix").exec()
        var suffix = result.out.firstOrNull()?.trim() ?: ""
        if (suffix.isBlank()) {
            result = Shell.cmd("getprop ro.boot.slot").exec()
            suffix = result.out.firstOrNull()?.trim()?.let { "_$it" } ?: ""
        }
        suffix
    }

    private fun filterCandidatesBySlot(candidates: List<String>, slotSuffix: String): List<String> {
        if (slotSuffix.isBlank()) return candidates
        val slotChar = slotSuffix.removePrefix("_").lowercase()
        return candidates.filter { path ->
            val name = File(path).name.lowercase()
            val remaining = name.replace("xbl_config", "")
            remaining.contains(slotChar)
        }.ifEmpty { candidates }
    }

    fun selectDevice(path: String) {
        _selectedDevicePath.value = path
    }

    suspend fun resolveSymlink(path: String): String? = withContext(Dispatchers.IO) {
        try {
            val result = Shell.cmd("readlink -f $path").exec()
            result.out.firstOrNull()?.trim()
        } catch (e: Exception) {
            LogManager.e("AutoScan", "Failed to resolve symlink: $path", e)
            null
        }
    }

    suspend fun existsWithRoot(path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = Shell.cmd("[ -e \"$path\" ] && echo 'exists'").exec()
            result.out.any { it.contains("exists") }
        } catch (e: Exception) {
            LogManager.e("AutoScan", "Failed to check existence with root: $path", e)
            false
        }
    }

    suspend fun getMajorMinorForDevice(devicePath: String): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        val baseName = File(devicePath).name
        try {
            val result = Shell.cmd("cat /proc/partitions").exec()
            for (line in result.out) {
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size == 4) {
                    val major = parts[0].toIntOrNull()
                    val minor = parts[1].toIntOrNull()
                    val name = parts[3]
                    if (major != null && minor != null && name == baseName) {
                        return@withContext Pair(major, minor)
                    }
                }
            }
            LogManager.w("AutoScan", "Device $baseName not found in /proc/partitions")
            null
        } catch (e: Exception) {
            LogManager.e("AutoScan", "Failed to read /proc/partitions", e)
            null
        }
    }

    suspend fun recreateDeviceNode(path: String, major: Int, minor: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val parent = File(path).parent
            if (parent != null) {
                Shell.cmd("mkdir -p $parent").exec()
            }
            val result = Shell.cmd("mknod $path b $major $minor").exec()
            if (result.isSuccess) {
                LogManager.i("AutoScan", "Device node recreated: $path ($major:$minor)")
                true
            } else {
                LogManager.e("AutoScan", "Failed to recreate device node: $path, stderr: ${result.err}")
                false
            }
        } catch (e: Exception) {
            LogManager.e("AutoScan", "Exception during mknod for $path", e)
            false
        }
    }

    suspend fun prepareScan(): AutoScanPreparation = withContext(Dispatchers.IO) {
        val path = _selectedDevicePath.value
        if (path.isNullOrBlank()) return@withContext AutoScanPreparation.Error("No device selected")

        val target = resolveSymlink(path) ?: return@withContext AutoScanPreparation.Error("Failed to resolve symlink: $path")

        if (!existsWithRoot(target)) {
            val (major, minor) = getMajorMinorForDevice(target) ?: return@withContext AutoScanPreparation.Error("Could not determine major/minor for $target")
            AutoScanPreparation.NeedRecreate(target, major, minor)
        } else {
            AutoScanPreparation.Ready
        }
    }

    private suspend fun copyWithRoot(src: String, dst: String, tool: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val cmd = when (tool) {
                "dd" -> "dd if=$src of=$dst bs=4M"
                else -> "cat $src > $dst"
            }
            val result = Shell.cmd(cmd).exec()
            if (result.isSuccess) {
                LogManager.i("AutoScan", "Copied $src to $dst using $tool")
                true
            } else {
                LogManager.e("AutoScan", "Copy failed: $cmd, stderr: ${result.err}")
                false
            }
        } catch (e: Exception) {
            LogManager.e("AutoScan", "Exception during copy with $tool", e)
            false
        }
    }

    suspend fun executeScan(
        context: Context,
        useDefaultParams: Boolean,
        recreatedPath: String? = null
    ) {
        _isScanning.update { true }
        _scanResult.update { context.getString(R.string.scanning) }
        _lastArb.value = null
        _showResultDialog.value = false

        val path = _selectedDevicePath.value ?: run {
            _scanResult.update { "No device selected" }
            _isScanning.update { false }
            _showResultDialog.value = true
            return
        }

        val sourceDevice = recreatedPath ?: (resolveSymlink(path) ?: run {
            _scanResult.update { "Failed to resolve device path" }
            _isScanning.update { false }
            _showResultDialog.value = true
            return
        })

        val debug = if (useDefaultParams) true else _debugMode.value
        val block = if (useDefaultParams) false else _blockMode.value
        val tool = if (useDefaultParams) "cat" else _extractionTool.value

        val workDir = context.filesDir.absolutePath
        val tempFile = File(workDir, "auto_scan_${System.currentTimeMillis()}.img")
        val copySuccess = copyWithRoot(sourceDevice, tempFile.absolutePath, tool)

        if (!copySuccess) {
            _scanResult.update { "Failed to copy device content" }
            _isScanning.update { false }
            _showResultDialog.value = true
            return
        }

        val inspector = ArbInspector()
        val result: ArbResult? = try {
            inspector.extract(tempFile.absolutePath, debug, block)
        } catch (e: Exception) {
            LogManager.e("AutoScan", "JNI extract failed", e)
            null
        } finally {
            tempFile.delete()
        }

        withContext(Dispatchers.Main) {
            if (result == null) {
                _scanResult.value = context.getString(R.string.scan_failed_detail)
            } else if (result.error != null) {
                _scanResult.value = buildString {
                    appendLine("${context.getString(R.string.scan_failed)}: ${result.error}")
                    if (result.debugMessages.isNotEmpty()) {
                        appendLine("\n${context.getString(R.string.debug_messages)}:")
                        result.debugMessages.forEach { appendLine(it) }
                    }
                }
            } else {
                _lastArb.value = result.arb
                _scanResult.value = buildString {
                    appendLine("${context.getString(R.string.major)}: ${result.major}")
                    appendLine("${context.getString(R.string.minor)}: ${result.minor}")
                    appendLine("ARB: ${result.arb}")
                    if (result.debugMessages.isNotEmpty()) {
                        appendLine("\n${context.getString(R.string.debug_messages)}:")
                        result.debugMessages.forEach { appendLine(it) }
                    }
                }
                LogManager.i("AutoScan", "Scan completed, ARB=${result.arb}")
            }
            _isScanning.update { false }
            _showResultDialog.value = true
        }

        if (recreatedPath != null) {
            withContext(Dispatchers.IO) {
                Shell.cmd("rm -f $recreatedPath").exec()
                LogManager.i("AutoScan", "Removed recreated device node: $recreatedPath")
            }
        }
    }
}