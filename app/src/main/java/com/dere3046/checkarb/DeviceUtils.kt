package com.dere3046.checkarb

import android.content.Context
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object DeviceUtils {

    /**
     * Check if target xbl_config partition exists
     */
    suspend fun hasTargetPartition(): Boolean = withContext(Dispatchers.IO) {
        val checkA = Shell.cmd("[ -e /dev/block/by-name/xbl_config_a ] && echo exists").exec()
        val checkB = Shell.cmd("[ -e /dev/block/by-name/xbl_config_b ] && echo exists").exec()
        val checkPlain = Shell.cmd("[ -e /dev/block/by-name/xbl_config ] && echo exists").exec()
        checkA.out.any { it.contains("exists") } ||
            checkB.out.any { it.contains("exists") } ||
            checkPlain.out.any { it.contains("exists") }
    }

    /**
     * Check if device supports A/B slots
     */
    suspend fun isDualSlotDevice(): Boolean = withContext(Dispatchers.IO) {
        val result = Shell.cmd("[ -e /dev/block/by-name/xbl_config_a ] && [ -e /dev/block/by-name/xbl_config_b ] && echo dual").exec()
        result.out.any { it.contains("dual") }
    }

    /**
     * Build standard by-name symlink path for given suffix
     */
    fun buildStandardPath(suffix: String): String = "/dev/block/by-name/xbl_config$suffix"

    /**
     * Find xbl_config device for given suffix
     * 1. Try standard by-name symlink
     * 2. Fallback to searching /dev/block
     */
    suspend fun findXblConfigDevice(suffix: String): String? = withContext(Dispatchers.IO) {
        val standard = buildStandardPath(suffix)
        if (existsWithRoot(standard)) return@withContext standard

        val pattern = if (suffix.isEmpty()) "xbl_config*" else "*xbl_config*$suffix*"
        val cmd = "find /dev/block -name '$pattern' 2>/dev/null"
        val result = Shell.cmd(cmd).exec()
        android.util.Log.d("DeviceUtils", "Find command: $cmd")
        android.util.Log.d("DeviceUtils", "Found devices: ${result.out}")
        result.out.firstOrNull()?.trim()
    }

    /**
     * Check if a path exists with root permissions
     */
    suspend fun existsWithRoot(path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = Shell.cmd("[ -e \"$path\" ] && echo exists").exec()
            result.out.any { it.contains("exists") }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Resolve symlink to real path
     */
    suspend fun resolveSymlink(path: String): String? = withContext(Dispatchers.IO) {
        val result = Shell.cmd("readlink -f $path").exec()
        result.out.firstOrNull()?.trim()
    }

    /**
     * Get major/minor numbers for a block device from /proc/partitions
     */
    suspend fun getMajorMinorForDevice(devicePath: String): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        val baseName = File(devicePath).name
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
        null
    }

    /**
     * Recreate device node using mknod
     */
    suspend fun recreateDeviceNode(path: String, major: Int, minor: Int): Boolean = withContext(Dispatchers.IO) {
        val parent = File(path).parent
        if (parent != null) Shell.cmd("mkdir -p $parent").exec()
        val result = Shell.cmd("mknod $path b $major $minor").exec()
        result.isSuccess
    }

    /**
     * Copy file using root shell (for block devices)
     */
    suspend fun copyWithRoot(src: String, dst: String, tool: String = "cat"): Boolean = withContext(Dispatchers.IO) {
        val cmd = when (tool) {
            "dd" -> "dd if=$src of=$dst bs=4M"
            else -> "cat $src > $dst"
        }
        val result = Shell.cmd(cmd).exec()
        result.isSuccess
    }

    /**
     * Copy file using standard File API (for regular files)
     */
    suspend fun copyWithFileApi(src: String, dst: String): Boolean = withContext(Dispatchers.IO) {
        try {
            File(src).inputStream().use { input ->
                File(dst).outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}