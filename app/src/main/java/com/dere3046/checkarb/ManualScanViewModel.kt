package com.dere3046.checkarb

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dere3046.arbinspector.ArbConfig
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
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader

sealed class ScanPreparation {
    object Ready : ScanPreparation()
    data class NeedRecreate(val devicePath: String, val major: Int, val minor: Int) : ScanPreparation()
    data class Error(val message: String) : ScanPreparation()
}

class ManualScanViewModel : ViewModel() {

    private val _workDir = MutableStateFlow("")
    val workDir: StateFlow<String> = _workDir.asStateFlow()
    private val _workDirEditable = MutableStateFlow(false)
    val workDirEditable: StateFlow<Boolean> = _workDirEditable.asStateFlow()
    private val _debugMode = MutableStateFlow(false)
    val debugMode: StateFlow<Boolean> = _debugMode.asStateFlow()
    private val _blockMode = MutableStateFlow(false)
    val blockMode: StateFlow<Boolean> = _blockMode.asStateFlow()
    private val _fileMode = MutableStateFlow(FileSelectionMode.SAF)
    val fileMode: StateFlow<FileSelectionMode> = _fileMode.asStateFlow()
    private val _selectedSafUri = MutableStateFlow<String?>(null)
    val selectedSafUri: StateFlow<String?> = _selectedSafUri.asStateFlow()
    private val _directPath = MutableStateFlow("")
    val directPath: StateFlow<String> = _directPath.asStateFlow()
    private val _directRead = MutableStateFlow(false)
    val directRead: StateFlow<Boolean> = _directRead.asStateFlow()
    private val _devBlockCandidates = MutableStateFlow<List<String>>(emptyList())
    val devBlockCandidates: StateFlow<List<String>> = _devBlockCandidates.asStateFlow()
    private val _selectedDevBlockPath = MutableStateFlow<String?>(null)
    val selectedDevBlockPath: StateFlow<String?> = _selectedDevBlockPath.asStateFlow()
    private val _isLoadingCandidates = MutableStateFlow(false)
    val isLoadingCandidates: StateFlow<Boolean> = _isLoadingCandidates.asStateFlow()
    private val _advancedMode = MutableStateFlow(false)
    val advancedMode: StateFlow<Boolean> = _advancedMode.asStateFlow()

    private val _hashScanMax = MutableStateFlow("4096")
    val hashScanMax: StateFlow<String> = _hashScanMax.asStateFlow()
    private val _maxSegmentSize = MutableStateFlow((20 * 1024 * 1024).toString())
    val maxSegmentSize: StateFlow<String> = _maxSegmentSize.asStateFlow()
    private val _minVersion = MutableStateFlow("1")
    val minVersion: StateFlow<String> = _minVersion.asStateFlow()
    private val _maxVersion = MutableStateFlow("1000")
    val maxVersion: StateFlow<String> = _maxVersion.asStateFlow()
    private val _maxCommonSz = MutableStateFlow("4096")
    val maxCommonSz: StateFlow<String> = _maxCommonSz.asStateFlow()
    private val _maxQtiSz = MutableStateFlow("4096")
    val maxQtiSz: StateFlow<String> = _maxQtiSz.asStateFlow()
    private val _maxOemSz = MutableStateFlow("16384")
    val maxOemSz: StateFlow<String> = _maxOemSz.asStateFlow()
    private val _maxHashTblSz = MutableStateFlow("65536")
    val maxHashTblSz: StateFlow<String> = _maxHashTblSz.asStateFlow()
    private val _maxArb = MutableStateFlow("127")
    val maxArb: StateFlow<String> = _maxArb.asStateFlow()

    private val _scanResult = MutableStateFlow<ArbResult?>(null)
    val scanResult: StateFlow<ArbResult?> = _scanResult.asStateFlow()

    fun initializeWorkDir(context: Context) {
        if (_workDir.value.isEmpty()) {
            _workDir.value = context.filesDir.absolutePath
        }
    }

    fun setWorkDir(path: String) { _workDir.value = path }
    fun setWorkDirEditable(editable: Boolean) { _workDirEditable.value = editable }
    fun setDebugMode(enabled: Boolean) { _debugMode.value = enabled }
    fun setBlockMode(enabled: Boolean) { _blockMode.value = enabled }
    fun setFileMode(mode: FileSelectionMode) { _fileMode.value = mode }
    fun setSelectedSafUri(uri: String) { _selectedSafUri.value = uri }
    fun setDirectPath(path: String) { _directPath.value = path }
    fun setDirectRead(enabled: Boolean) { _directRead.value = enabled }
    fun setSelectedDevBlockPath(path: String) { _selectedDevBlockPath.value = path }
    fun setAdvancedMode(enabled: Boolean) { _advancedMode.value = enabled }

    fun setHashScanMax(value: String) { _hashScanMax.value = value }
    fun setMaxSegmentSize(value: String) { _maxSegmentSize.value = value }
    fun setMinVersion(value: String) { _minVersion.value = value }
    fun setMaxVersion(value: String) { _maxVersion.value = value }
    fun setMaxCommonSz(value: String) { _maxCommonSz.value = value }
    fun setMaxQtiSz(value: String) { _maxQtiSz.value = value }
    fun setMaxOemSz(value: String) { _maxOemSz.value = value }
    fun setMaxHashTblSz(value: String) { _maxHashTblSz.value = value }
    fun setMaxArb(value: String) { _maxArb.value = value }

    fun loadDevBlockCandidates() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingCandidates.update { true }
            try {
                val result = Shell.cmd("find /dev/block -name '*xbl_config*' 2>/dev/null").exec()
                val paths = result.out.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                _devBlockCandidates.update { paths }
                LogManager.i("ManualScan", "Loaded ${paths.size} dev block candidates")
            } catch (e: Exception) {
                LogManager.e("ManualScan", "Failed to load dev block candidates", e)
            } finally {
                _isLoadingCandidates.update { false }
            }
        }
    }

    suspend fun resolveSymlink(path: String): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val result = Shell.cmd("readlink -f $path").exec()
            result.out.firstOrNull()?.trim()
        } catch (e: Exception) {
            LogManager.e("ManualScan", "Failed to resolve symlink: $path", e)
            null
        }
    }

    suspend fun existsWithRoot(path: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val result = Shell.cmd("[ -e \"$path\" ] && echo 'exists'").exec()
            result.out.any { it.contains("exists") }
        } catch (e: Exception) {
            LogManager.e("ManualScan", "Failed to check existence with root: $path", e)
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
            LogManager.w("ManualScan", "Device $baseName not found in /proc/partitions")
            null
        } catch (e: Exception) {
            LogManager.e("ManualScan", "Failed to read /proc/partitions", e)
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
                LogManager.i("ManualScan", "Device node recreated: $path ($major:$minor)")
                true
            } else {
                LogManager.e("ManualScan", "Failed to recreate device node: $path, stderr: ${result.err}")
                false
            }
        } catch (e: Exception) {
            LogManager.e("ManualScan", "Exception during mknod for $path", e)
            false
        }
    }

    suspend fun importParameters(context: Context, uri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val jsonString = BufferedReader(InputStreamReader(input)).readText()
                    val json = JSONObject(jsonString)
                    _hashScanMax.update { json.optString("hashScanMax", "4096") }
                    _maxSegmentSize.update { json.optString("maxSegmentSize", (20*1024*1024).toString()) }
                    _minVersion.update { json.optString("minVersion", "1") }
                    _maxVersion.update { json.optString("maxVersion", "1000") }
                    _maxCommonSz.update { json.optString("maxCommonSz", "4096") }
                    _maxQtiSz.update { json.optString("maxQtiSz", "4096") }
                    _maxOemSz.update { json.optString("maxOemSz", "16384") }
                    _maxHashTblSz.update { json.optString("maxHashTblSz", "65536") }
                    _maxArb.update { json.optString("maxArb", "127") }
                }
                LogManager.i("ManualScan", "Parameters imported from $uri")
            } catch (e: Exception) {
                LogManager.e("ManualScan", "Failed to import parameters from $uri", e)
            }
        }
    }

    suspend fun exportParameters(context: Context, uri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("hashScanMax", _hashScanMax.value)
                    put("maxSegmentSize", _maxSegmentSize.value)
                    put("minVersion", _minVersion.value)
                    put("maxVersion", _maxVersion.value)
                    put("maxCommonSz", _maxCommonSz.value)
                    put("maxQtiSz", _maxQtiSz.value)
                    put("maxOemSz", _maxOemSz.value)
                    put("maxHashTblSz", _maxHashTblSz.value)
                    put("maxArb", _maxArb.value)
                }
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(json.toString(2).toByteArray())
                }
                LogManager.i("ManualScan", "Parameters exported to $uri")
            } catch (e: Exception) {
                LogManager.e("ManualScan", "Failed to export parameters to $uri", e)
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun prepareScan(context: Context, extractionTool: String): ScanPreparation = withContext(Dispatchers.IO) {
        when (fileMode.value) {
            FileSelectionMode.SAF -> {
                if (selectedSafUri.value == null) ScanPreparation.Error("No SAF file selected")
                else ScanPreparation.Ready
            }
            FileSelectionMode.DIRECT_PATH -> {
                if (directPath.value.isBlank()) ScanPreparation.Error("No direct path entered")
                else ScanPreparation.Ready
            }
            FileSelectionMode.DEV_BLOCK_LIST -> {
                val path = selectedDevBlockPath.value ?: return@withContext ScanPreparation.Error("No device selected")
                val target = resolveSymlink(path) ?: return@withContext ScanPreparation.Error("Failed to resolve symlink: $path")
                if (!existsWithRoot(target)) {
                    val (major, minor) = getMajorMinorForDevice(target) ?: return@withContext ScanPreparation.Error("Could not determine major/minor for $target")
                    ScanPreparation.NeedRecreate(target, major, minor)
                } else {
                    ScanPreparation.Ready
                }
            }
        }
    }

    suspend fun executeScan(context: Context, extractionTool: String, recreatedPath: String? = null): ArbResult? = withContext(Dispatchers.IO) {
        val sourcePath = when (fileMode.value) {
            FileSelectionMode.SAF -> {
                val uri = Uri.parse(selectedSafUri.value ?: return@withContext null)
                val destFile = File(workDir.value, "source_${System.currentTimeMillis()}")
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(destFile).use { output -> input.copyTo(output) }
                    } ?: return@withContext null
                } catch (e: Exception) {
                    LogManager.e("ManualScan", "Failed to copy SAF file", e)
                    throw e
                }
                destFile.absolutePath
            }
            FileSelectionMode.DIRECT_PATH -> {
                if (directRead.value) directPath.value else {
                    val destFile = File(workDir.value, "source_${System.currentTimeMillis()}")
                    if (!copyWithTool(directPath.value, destFile.absolutePath, extractionTool)) return@withContext null
                    destFile.absolutePath
                }
            }
            FileSelectionMode.DEV_BLOCK_LIST -> {
                val path = selectedDevBlockPath.value ?: return@withContext null
                if (directRead.value) {
                    recreatedPath ?: (resolveSymlink(path) ?: return@withContext null)
                } else {
                    val source = recreatedPath ?: (resolveSymlink(path) ?: return@withContext null)
                    val destFile = File(workDir.value, "source_${System.currentTimeMillis()}")
                    if (!copyWithTool(source, destFile.absolutePath, extractionTool)) return@withContext null
                    destFile.absolutePath
                }
            }
        }

        val config = if (advancedMode.value) {
            val hsm = hashScanMax.value.toIntOrNull() ?: 4096
            val mss = maxSegmentSize.value.toLongOrNull() ?: (20 * 1024 * 1024)
            val minV = minVersion.value.toIntOrNull() ?: 1
            val maxV = maxVersion.value.toIntOrNull() ?: 1000
            val mcs = maxCommonSz.value.toIntOrNull() ?: 4096
            val mqs = maxQtiSz.value.toIntOrNull() ?: 4096
            val mos = maxOemSz.value.toIntOrNull() ?: 16384
            val mhts = maxHashTblSz.value.toIntOrNull() ?: 65536
            val ma = maxArb.value.toIntOrNull() ?: 127

            ArbConfig().apply {
                hashScanMax = hsm
                maxSegmentSize = mss
                minVersion = minV
                maxVersion = maxV
                maxCommonSz = mcs
                maxQtiSz = mqs
                maxOemSz = mos
                maxHashTblSz = mhts
                maxArb = ma
            }
        } else null

        val inspector = ArbInspector()
        val result = try {
            if (config != null) {
                inspector.extractWithConfig(sourcePath, debugMode.value, blockMode.value, config)
            } else {
                inspector.extract(sourcePath, debugMode.value, blockMode.value)
            }
        } catch (e: Exception) {
            LogManager.e("ManualScan", "JNI extract failed", e)
            throw e
        }

        if (fileMode.value != FileSelectionMode.DEV_BLOCK_LIST || !directRead.value) {
            if (sourcePath.startsWith(workDir.value)) {
                try {
                    File(sourcePath).delete()
                } catch (e: Exception) {
                    LogManager.w("ManualScan", "Failed to delete temporary file: $sourcePath", e)
                }
            }
        }

        _scanResult.value = result
        result
    }

    private suspend fun copyWithTool(src: String, dst: String, tool: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val cmd = when (tool) {
                "dd" -> "dd if=$src of=$dst bs=4M"
                else -> "cat $src > $dst"
            }
            val result = Shell.cmd(cmd).exec()
            val success = result.isSuccess
            if (success) {
                LogManager.i("ManualScan", "Copied $src to $dst using $tool")
            } else {
                LogManager.w("ManualScan", "Copy failed: $cmd, stderr: ${result.err}")
            }
            success
        } catch (e: Exception) {
            LogManager.e("ManualScan", "Exception during copy with $tool from $src to $dst", e)
            false
        }
    }
}