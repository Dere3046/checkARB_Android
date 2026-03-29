// MainViewModel.kt
package com.dere3046.checkarb

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dere3046.arbinspector.ArbInspector
import com.dere3046.arbinspector.ArbResult
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainViewModel(private val context: Context) : ViewModel() {

    data class SlotInfo(
        val suffix: String,
        val devicePath: String?,
        val realPath: String?,
        val arbValue: Int?,
        val error: String?,
        val blockDeviceFound: Boolean,
        val debugMessages: List<String> = emptyList()
    )

    data class DeviceInfo(
        val model: String = Build.MODEL,
        val buildNumber: String = Build.ID,
        val kernelVersion: String = "",
        val arbInspectorVersion: String = "",
        val isDualSlot: Boolean = false,
        val activeSlot: String = "",
        val slotSuffix: String = ""
    )

    private val _deviceInfo = MutableStateFlow(DeviceInfo())
    val deviceInfo: StateFlow<DeviceInfo> = _deviceInfo.asStateFlow()

    private val _slotA = MutableStateFlow(SlotInfo("_a", null, null, null, null, false))
    val slotA: StateFlow<SlotInfo> = _slotA.asStateFlow()

    private val _slotB = MutableStateFlow(SlotInfo("_b", null, null, null, null, false))
    val slotB: StateFlow<SlotInfo> = _slotB.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _availableDevices = MutableStateFlow<List<String>>(emptyList())
    val availableDevices: StateFlow<List<String>> = _availableDevices.asStateFlow()

    private val _slotADevicePath = MutableStateFlow<String?>(null)
    val slotADevicePath: StateFlow<String?> = _slotADevicePath.asStateFlow()
    private val _slotBDevicePath = MutableStateFlow<String?>(null)
    val slotBDevicePath: StateFlow<String?> = _slotBDevicePath.asStateFlow()

    private val _hasRootAccess = MutableStateFlow(false)
    val hasRootAccess: StateFlow<Boolean> = _hasRootAccess.asStateFlow()

    private val _autoScanResult = MutableStateFlow<ArbResult?>(null)
    val autoScanResult: StateFlow<ArbResult?> = _autoScanResult.asStateFlow()

    init {
        android.util.Log.d("MainViewModel", "init started")
        viewModelScope.launch(Dispatchers.IO) {
            android.util.Log.d("MainViewModel", "ROOT detection coroutine started")
            var rootAvailable = false
            try {
                val shell = Shell.getShell()
                rootAvailable = shell.isRoot
                android.util.Log.d("MainViewModel", "Shell initialized: $rootAvailable")
            } catch (e: Exception) {
                android.util.Log.d("MainViewModel", "Shell init failed: ${e.message}")
                try {
                    val result = Shell.cmd("id").exec()
                    rootAvailable = result.isSuccess
                    android.util.Log.d("MainViewModel", "Command test: ${result.out}")
                } catch (e2: Exception) {
                    android.util.Log.d("MainViewModel", "Command test failed: ${e2.message}")
                }
            }
            _hasRootAccess.update { rootAvailable }
            android.util.Log.d("MainViewModel", "ROOT status: $rootAvailable")

            loadDeviceInfo()
        }

        viewModelScope.launch(Dispatchers.IO) {
            android.util.Log.d("MainViewModel", "Slot detection coroutine started")
            loadPersistedSlotDevices()
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            android.util.Log.d("MainViewModel", "Device discovery coroutine started")
            discoverDevices()
            android.util.Log.d("MainViewModel", "Starting scanAllSlots")
            scanAllSlots()
        }
    }

    private fun loadDeviceInfo() {
        val kernelVersion = System.getProperty("os.version") ?: "unknown"
        
        val version = try {
            ArbInspector.getVersion()
        } catch (e: UnsatisfiedLinkError) {
            "JNI error"
        } catch (e: Exception) {
            "error"
        }

        val slotSuffix = getSlotSuffix()
        val isDual = slotSuffix.isNotEmpty()
        val active = if (isDual) slotSuffix else ""

        _deviceInfo.update {
            DeviceInfo(
                kernelVersion = kernelVersion,
                arbInspectorVersion = version,
                isDualSlot = isDual,
                activeSlot = active,
                slotSuffix = slotSuffix
            )
        }
    }

    private fun getSlotSuffix(): String {
        return try {
            val result = Shell.cmd("getprop ro.boot.slot_suffix").exec()
            if (result.isSuccess && result.out.isNotEmpty()) {
                result.out.firstOrNull()?.trim() ?: ""
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    private suspend fun loadPersistedSlotDevices() {
        android.util.Log.d("MainViewModel", "loadPersistedSlotDevices started")
        val repo = SettingsRepository(context)
        repo.slotADevicePathFlow.collect { path ->
            android.util.Log.d("MainViewModel", "slotADevicePathFlow: $path")
            _slotADevicePath.update { path }
            if (path != null) scanSlotDevice("_a", path)
        }
        repo.slotBDevicePathFlow.collect { path ->
            android.util.Log.d("MainViewModel", "slotBDevicePathFlow: $path")
            _slotBDevicePath.update { path }
            if (path != null) scanSlotDevice("_b", path)
        }
    }

    fun setSlotDevicePath(slot: String, path: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val repo = SettingsRepository(context)
            when (slot) {
                "_a" -> {
                    repo.setSlotADevicePath(path)
                    _slotADevicePath.update { path }
                    if (path != null) scanSlotDevice("_a", path)
                }
                "_b" -> {
                    repo.setSlotBDevicePath(path)
                    _slotBDevicePath.update { path }
                    if (path != null) scanSlotDevice("_b", path)
                }
            }
        }
    }

    fun resetSlotDevicePath(slot: String) {
        setSlotDevicePath(slot, null)
        viewModelScope.launch(Dispatchers.IO) {
            when (slot) {
                "_a" -> scanSlot("_a")
                "_b" -> scanSlot("_b")
            }
        }
    }

    suspend fun discoverDevices() {
        val devices = listOf("_a", "_b", "").mapNotNull { suffix ->
            DeviceUtils.findXblConfigDevice(suffix)
        }.toSet().toList()
        _availableDevices.update { devices }
    }

    fun scanAllSlots() {
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.update { true }
            try {
                listOf("_a", "_b").forEach { suffix ->
                    scanSlot(suffix)
                }
            } finally {
                _isScanning.update { false }
            }
        }
    }

    private suspend fun scanSlot(suffix: String) {
        android.util.Log.d("MainViewModel", "scanSlot: $suffix")
        val userPath = when (suffix) {
            "_a" -> _slotADevicePath.value
            "_b" -> _slotBDevicePath.value
            else -> null
        }
        android.util.Log.d("MainViewModel", "scanSlot: userPath=$userPath")
        
        val devicePath = userPath ?: DeviceUtils.findXblConfigDevice(suffix)
        android.util.Log.d("MainViewModel", "scanSlot: devicePath=$devicePath")
        
        if (devicePath == null && userPath == null) {
            android.util.Log.d("MainViewModel", "scanSlot: Device not found for $suffix")
            val slotData = SlotInfo(
                suffix = suffix,
                devicePath = null,
                realPath = null,
                arbValue = null,
                error = "Device not found",
                blockDeviceFound = false,
                debugMessages = mutableListOf()
            )
            when (suffix) {
                "_a" -> _slotA.update { slotData }
                "_b" -> _slotB.update { slotData }
            }
            return
        }
        
        scanSlotDevice(suffix, devicePath)
    }

    private suspend fun scanSlotDevice(suffix: String, devicePath: String?) {
        if (!_hasRootAccess.value) {
            val slotData = SlotInfo(
                suffix = suffix,
                devicePath = devicePath,
                realPath = null,
                arbValue = null,
                error = null,
                blockDeviceFound = devicePath != null,
                debugMessages = mutableListOf()
            )
            when (suffix) {
                "_a" -> _slotA.update { slotData }
                "_b" -> _slotB.update { slotData }
            }
            return
        }
        
        val blockFound = devicePath != null
        val (realPath, arbResult) = if (blockFound) {
            readArbFromDevice(devicePath!!, false, true)
        } else {
            null to null
        }

        val slotData = SlotInfo(
            suffix = suffix,
            devicePath = devicePath,
            realPath = realPath,
            arbValue = arbResult?.arb,
            error = arbResult?.error,
            blockDeviceFound = blockFound,
            debugMessages = arbResult?.debugMessages ?: mutableListOf()
        )
        when (suffix) {
            "_a" -> _slotA.update { slotData }
            "_b" -> _slotB.update { slotData }
        }
    }

    private suspend fun readArbFromDevice(devicePath: String, fullMode: Boolean = false, debug: Boolean = true): Pair<String?, ArbResult?> {
        val realPath = DeviceUtils.resolveSymlink(devicePath) ?: devicePath
        val exists = DeviceUtils.existsWithRoot(realPath)

        var tempCreated = false
        val targetPath = if (!exists) {
            if (!_hasRootAccess.value) {
                return realPath to ArbResult().apply { error = "Device node missing and no root permission to recreate" }
            }
            val (major, minor) = DeviceUtils.getMajorMinorForDevice(realPath) ?: return realPath to ArbResult().apply { error = "Failed to get major/minor for $realPath" }
            if (DeviceUtils.recreateDeviceNode(realPath, major, minor)) {
                tempCreated = true
                realPath
            } else {
                return realPath to ArbResult().apply { error = "Failed to recreate device node $realPath" }
            }
        } else {
            realPath
        }

        val privateDir = context.filesDir.absolutePath
        val tempFile = File(privateDir, "arb_scan_${System.currentTimeMillis()}")
        val copySuccess = if (_hasRootAccess.value) {
            DeviceUtils.copyWithRoot(targetPath, tempFile.absolutePath, SettingsRepository(context).extractionToolFlow.first())
        } else {
            DeviceUtils.copyWithFileApi(targetPath, tempFile.absolutePath)
        }

        if (!copySuccess) {
            if (tempCreated) Shell.cmd("rm -f $targetPath").exec()
            tempFile.delete()
            return targetPath to ArbResult().apply { error = "Failed to copy device content" }
        }

        val result = try {
            ArbInspector.extractWithMode(tempFile.absolutePath, fullMode, debug)
        } catch (e: UnsatisfiedLinkError) {
            ArbResult().apply { error = "JNI error: ${e.message}" }
        } catch (e: Exception) {
            ArbResult().apply { error = "JNI exception: ${e.message}" }
        } finally {
            tempFile.delete()
            if (tempCreated) Shell.cmd("rm -f $targetPath").exec()
        }

        return targetPath to result
    }

    suspend fun scanFile(path: String, fullMode: Boolean = false, debug: Boolean = true, workDir: String? = null): ArbResult? {
        return withContext(Dispatchers.IO) {
            var tempFile: File? = null
            try {
                val targetPath = if (path.startsWith("content://")) {
                    val destFile = File(workDir ?: context.filesDir.absolutePath, "temp_${System.currentTimeMillis()}")
                    context.contentResolver.openInputStream(android.net.Uri.parse(path))?.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    tempFile = destFile
                    destFile.absolutePath
                } else if (workDir != null && !path.startsWith(workDir)) {
                    val destFile = File(workDir, "temp_${System.currentTimeMillis()}")
                    if (_hasRootAccess.value) {
                        DeviceUtils.copyWithRoot(path, destFile.absolutePath, SettingsRepository(context).extractionToolFlow.first())
                    } else {
                        DeviceUtils.copyWithFileApi(path, destFile.absolutePath)
                    }
                    tempFile = destFile
                    destFile.absolutePath
                } else {
                    path
                }
                ArbInspector.extractWithMode(targetPath, fullMode, debug)
            } catch (e: Exception) {
                ArbResult().apply { error = e.message }
            } finally {
                tempFile?.delete()
            }
        }
    }

    suspend fun autoScan(slot: String, fullMode: Boolean = false, debug: Boolean = true) {
        val devicePath = when (slot) {
            "_a" -> _slotADevicePath.value ?: DeviceUtils.findXblConfigDevice("_a")
            "_b" -> _slotBDevicePath.value ?: DeviceUtils.findXblConfigDevice("_b")
            else -> null
        }
        if (devicePath.isNullOrEmpty()) {
            _autoScanResult.update { null }
            return
        }
        val (_, result) = readArbFromDevice(devicePath, fullMode, debug)
        _autoScanResult.update { result }
    }
}