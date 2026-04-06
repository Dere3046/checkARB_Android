package com.dere3046.checkarb

import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dere3046.arbinspector.ArbInspector
import com.dere3046.arbinspector.ArbResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

data class XblFileInfo(
    val name: String,
    val path: String,
    val size: Long,
    val source: String,
    val localHeaderOffset: Long = -1,
    val payloadOffset: Long = -1,
    val compressedSize: Long = 0,
    val description: String = ""
)

data class XblScanResult(
    val fileName: String,
    val result: ArbResult?
)

data class ZipArchiveInfo(
    val fileName: String,
    val fileSize: Long
)

data class PartitionInfo(
    val name: String,
    val size: Long,
    val operations: List<PartitionOperation>
)

data class PartitionOperation(
    val type: Int,         // 0=REPLACE, 8=REPLACE_XZ, 1=REPLACE_BZ, 6=ZERO
    val dataOffset: Long,  // offset in payload data section
    val dataLength: Long,  // compressed/raw data length
    val dstStartBlock: Long,
    val dstNumBlocks: Long
)

object ZipUtil {
    private const val CENSIG = 0x02014b50L
    private const val LOCSIG = 0x04034b50L
    private const val ENDSIG = 0x06054b50L
    private const val ENDHDR = 22
    private const val ZIP64_ENDSIG = 0x06064b50L
    private const val ZIP64_LOCSIG = 0x07064b50L
    private const val ZIP64_LOCHDR = 20
    private const val ZIP64_MAGICVAL = 0xFFFFFFFFL

    data class CentralDirInfo(val offset: Long, val size: Long)
    data class ZipFileInfo(val localHeaderOffset: Long, val compressedSize: Long, val path: String)

    fun locateCentralDirectory(byteArray: ByteArray, fileLength: Long): CentralDirInfo {
        val byteBuffer = ByteBuffer.wrap(byteArray).order(ByteOrder.LITTLE_ENDIAN)
        val offset = byteBuffer.capacity() - ENDHDR
        var cenSize: Long = -1
        var cenOffset: Long = -1

        for (i in 0 until byteBuffer.capacity() - ENDHDR + 1) {
            byteBuffer.position(offset - i)
            if (byteBuffer.getInt().toLong() == ENDSIG) {
                val endSigOffset = byteBuffer.position()
                byteBuffer.position(byteBuffer.position() + 12)

                if (byteBuffer.getInt().toUInt().toLong() == ZIP64_MAGICVAL) {
                    byteBuffer.position(endSigOffset - ZIP64_LOCHDR - 4)
                    if (byteBuffer.getInt().toLong() == ZIP64_LOCSIG) {
                        byteBuffer.position(byteBuffer.position() + 4)
                        val zip64EndSigOffset = byteBuffer.getLong()
                        byteBuffer.position(4096 - (fileLength - zip64EndSigOffset).toInt())
                        if (byteBuffer.getInt().toLong() == ZIP64_ENDSIG) {
                            byteBuffer.position(byteBuffer.position() + 36)
                            cenSize = byteBuffer.getLong().toULong().toLong()
                            cenOffset = byteBuffer.getLong().toULong().toLong()
                        }
                    }
                } else {
                    byteBuffer.position(endSigOffset + 8)
                    cenSize = byteBuffer.getInt().toUInt().toLong()
                    cenOffset = byteBuffer.getInt().toUInt().toLong()
                    break
                }
            }
        }
        return CentralDirInfo(cenOffset, cenSize)
    }

    fun locateLocalFileHeader(byteArray: ByteArray, fileName: String): Long {
        val byteBuffer = ByteBuffer.wrap(byteArray).order(ByteOrder.LITTLE_ENDIAN)
        var localHeaderOffset: Long = -1

        while (true) {
            if (byteBuffer.getInt().toLong() == CENSIG) {
                byteBuffer.position(byteBuffer.position() + 24)
                val fileNameLength = byteBuffer.getShort().toUInt().toInt()
                val extraFieldLength = byteBuffer.getShort().toUInt().toInt()
                val fileCommentLength = byteBuffer.getShort().toUInt().toInt()
                byteBuffer.position(byteBuffer.position() + 8)
                val localHeaderOffsetTemp = byteBuffer.getInt().toUInt().toLong()
                val fileNameBytes = ByteArray(fileNameLength)
                byteBuffer.get(fileNameBytes)
                if (fileName == String(fileNameBytes, Charsets.UTF_8)) {
                    localHeaderOffset = localHeaderOffsetTemp
                    break
                }
                byteBuffer.position(byteBuffer.position() + extraFieldLength + fileCommentLength)
            } else {
                break
            }
        }
        return localHeaderOffset
    }

    fun locateLocalFileOffset(byteArray: ByteArray): Long {
        val byteBuffer = ByteBuffer.wrap(byteArray).order(ByteOrder.LITTLE_ENDIAN)
        var localFileOffset: Long = -1

        if (byteBuffer.getInt().toLong() == LOCSIG) {
            byteBuffer.position(byteBuffer.position() + 22)
            val fileNameLength = byteBuffer.getShort().toUInt().toInt()
            val extraFieldLength = byteBuffer.getShort().toUInt().toInt()
            byteBuffer.position(byteBuffer.position() + fileNameLength + extraFieldLength)
            localFileOffset = byteBuffer.position().toLong()
        }
        return localFileOffset
    }

    fun findAllFilesInCentralDirectory(buffer: ByteBuffer): List<ZipFileInfo> {
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val files = mutableListOf<ZipFileInfo>()
        val limit = buffer.limit()

        while (buffer.position() < limit - 4) {
            if (buffer.getInt().toLong() == CENSIG) {
                val afterSig = buffer.position()

                buffer.position(afterSig + 16)
                val compressedSize = buffer.getInt().toUInt().toLong()

                buffer.position(afterSig + 24)
                val fileNameLength = buffer.getShort().toUInt().toInt()
                val extraFieldLength = buffer.getShort().toUInt().toInt()
                val fileCommentLength = buffer.getShort().toUInt().toInt()
                buffer.position(buffer.position() + 8)
                val localHeaderOffset = buffer.getInt().toUInt().toLong()

                val fileNameBytes = ByteArray(fileNameLength)
                buffer.get(fileNameBytes)
                val fileName = String(fileNameBytes, Charsets.UTF_8)

                files.add(ZipFileInfo(localHeaderOffset, compressedSize, fileName))

                buffer.position(buffer.position() + extraFieldLength + fileCommentLength)
            }
        }
        return files
    }

    fun findFileInCentralDirectory(buffer: ByteBuffer, targetFileName: String): ZipFileInfo? {
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val limit = buffer.limit()

        while (buffer.position() < limit - 4) {
            if (buffer.getInt().toLong() == CENSIG) {
                val afterSig = buffer.position()

                buffer.position(afterSig + 16)
                val compressedSize = buffer.getInt().toUInt().toLong()

                buffer.position(afterSig + 24)
                val fileNameLength = buffer.getShort().toUInt().toInt()
                val extraFieldLength = buffer.getShort().toUInt().toInt()
                val fileCommentLength = buffer.getShort().toUInt().toInt()
                buffer.position(buffer.position() + 8)
                val localHeaderOffset = buffer.getInt().toUInt().toLong()

                val fileNameBytes = ByteArray(fileNameLength)
                buffer.get(fileNameBytes)
                val fileName = String(fileNameBytes, Charsets.UTF_8)

                if (fileName == targetFileName) {
                    return ZipFileInfo(localHeaderOffset, compressedSize, fileName)
                }

                buffer.position(buffer.position() + extraFieldLength + fileCommentLength)
            }
        }
        return null
    }

    fun findFilesInCentralDirectory(buffer: ByteBuffer, filter: (String) -> Boolean): List<XblFileInfo> {
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val files = mutableListOf<XblFileInfo>()
        val limit = buffer.limit()

        while (buffer.position() < limit - 4) {
            if (buffer.getInt().toLong() == CENSIG) {
                val afterSig = buffer.position()

                buffer.position(afterSig + 16)
                val compressedSize = buffer.getInt().toUInt().toLong()

                buffer.position(afterSig + 24)
                val fileNameLength = buffer.getShort().toUInt().toInt()
                val extraFieldLength = buffer.getShort().toUInt().toInt()
                val fileCommentLength = buffer.getShort().toUInt().toInt()
                buffer.position(buffer.position() + 8)
                val localHeaderOffset = buffer.getInt().toUInt().toLong()

                val fileNameBytes = ByteArray(fileNameLength)
                buffer.get(fileNameBytes)
                val fileName = String(fileNameBytes, Charsets.UTF_8)

                if (filter(fileName)) {
                    files.add(XblFileInfo(
                        name = fileName.substringAfterLast("/"),
                        path = fileName,
                        size = compressedSize,
                        source = "remote",
                        localHeaderOffset = localHeaderOffset,
                        compressedSize = compressedSize
                    ))
                }

                buffer.position(buffer.position() + extraFieldLength + fileCommentLength)
            }
        }
        return files
    }
}

object PayloadUtil {
    private const val MAGIC = "CrAU"
    private const val FORMAT_VERSION = 2L
    private const val LOCHDR = 30

    data class PayloadInfo(val manifestOffset: Long, val dataOffset: Long, val partitions: List<PartitionInfo>)

    suspend fun parsePayloadManifest(url: String, payloadFile: ZipUtil.ZipFileInfo, client: OkHttpClient): PayloadInfo? {
        try {
            val headerData = downloadRange(url, payloadFile.localHeaderOffset, payloadFile.localHeaderOffset + 1024, client)
            val header = ByteBuffer.wrap(headerData).order(ByteOrder.LITTLE_ENDIAN)
            if (header.getInt().toLong() != 0x04034b50L) return null

            header.position(26)
            val fileNameLen = header.getShort().toUInt().toInt()
            val extraFieldLen = header.getShort().toUInt().toInt()
            val dataStart = payloadFile.localHeaderOffset + LOCHDR + fileNameLen + extraFieldLen

            android.util.Log.d("XblExtractor", "payload.bin data starts at $dataStart")

            val magicBytes = ByteArray(4)
            val fullHeader = downloadRange(url, dataStart, dataStart + 23, client)
            System.arraycopy(fullHeader, 0, magicBytes, 0, 4)
            if (String(magicBytes, StandardCharsets.UTF_8) != MAGIC) {
                android.util.Log.d("XblExtractor", "Invalid magic: ${String(magicBytes)}")
                return null
            }

            val formatBuffer = ByteBuffer.wrap(fullHeader).order(ByteOrder.BIG_ENDIAN)
            formatBuffer.position(4)
            val fileFormatVersion = formatBuffer.getLong()
            if (fileFormatVersion != FORMAT_VERSION) {
                android.util.Log.d("XblExtractor", "Unsupported version: $fileFormatVersion")
                return null
            }

            val manifestSize = formatBuffer.getLong()
            val metadataSignatureSize = formatBuffer.getInt()

            android.util.Log.d("XblExtractor", "manifestSize=$manifestSize, metadataSignatureSize=$metadataSignatureSize")

            if (manifestSize <= 0 || manifestSize > 100 * 1024 * 1024) {
                android.util.Log.d("XblExtractor", "Invalid manifestSize")
                return null
            }
            if (metadataSignatureSize < 0 || metadataSignatureSize > 10 * 1024 * 1024) {
                android.util.Log.d("XblExtractor", "Invalid metadataSignatureSize")
                return null
            }

            // Manifest starts at offset 24 from payload data start
            val manifestStart = dataStart + 24
            val manifestData = downloadRange(url, manifestStart, manifestStart + manifestSize - 1, client)
            android.util.Log.d("XblExtractor", "Downloaded ${manifestData.size} bytes of manifest from offset $manifestStart")

            // Use protobuf parsing
            val parsedManifest = chromeos_update_engine.UpdateMetadata.DeltaArchiveManifest.parseFrom(manifestData)
            android.util.Log.d("XblExtractor", "Parsed manifest: blockSize=${parsedManifest.blockSize}, partitions=${parsedManifest.partitionsList.size}")

            val manifest = parsedManifest.partitionsList.map { partition ->
                val ops = partition.operationsList.map { op ->
                    val dstExtent = if (op.dstExtentsCount > 0) op.dstExtentsList[0] else null
                    PartitionOperation(
                        type = op.type.number,
                        dataOffset = op.dataOffset,
                        dataLength = op.dataLength,
                        dstStartBlock = dstExtent?.startBlock ?: 0L,
                        dstNumBlocks = dstExtent?.numBlocks ?: 0L
                    )
                }
                val size = if (ops.isNotEmpty()) {
                    (ops.last().dataOffset + ops.last().dataLength) - ops.first().dataOffset
                } else 0L
                PartitionInfo(partition.partitionName, size, ops)
            }

            android.util.Log.d("XblExtractor", "Parsed ${manifest.size} partitions from manifest")

            val dataOffset = dataStart + 24 + manifestSize + metadataSignatureSize
            android.util.Log.d("XblExtractor", "dataOffset (start of blob data) = $dataOffset")

            return PayloadInfo(
                dataStart + 24,
                dataOffset,
                manifest
            )
        } catch (e: Exception) {
            android.util.Log.d("XblExtractor", "parsePayloadManifest failed: ${e.message}")
            return null
        }
    }

    private fun parseDeltaArchiveManifest(data: ByteArray): List<PartitionInfo> {
        val partitions = mutableListOf<PartitionInfo>()
        var pos = 0
        val dataEnd = data.size

        try {
            while (pos < dataEnd) {
                val tagResult = VarInt.decodeInt(data, pos)
                val tag = tagResult.first
                pos += tagResult.second
                val wireType = tag and 0x07
                val fieldNumber = tag shr 3

                if (fieldNumber == 13 && wireType == 2) {
                    // partitions (repeated message, field 13)
                    val lenResult = VarInt.decodeInt(data, pos)
                    val msgLen = lenResult.first
                    pos += lenResult.second
                    val partition = parsePartitionUpdate(data, pos, pos + msgLen)
                    if (partition != null) partitions.add(partition)
                    pos += msgLen
                } else if (wireType == 2) {
                    val lenResult = VarInt.decodeInt(data, pos)
                    pos += lenResult.second + lenResult.first
                } else if (wireType == 0) {
                    val result = VarInt.decodeLong(data, pos)
                    pos += result.second
                } else if (wireType == 1) {
                    pos += 8
                } else if (wireType == 5) {
                    pos += 4
                } else {
                    pos++
                }
            }
        } catch (e: Exception) {
            android.util.Log.d("XblExtractor", "parseDeltaArchiveManifest error at pos $pos: ${e.message}")
        }
        return partitions
    }

    private fun parsePartitionUpdate(data: ByteArray, start: Int, end: Int): PartitionInfo? {
        var pos = start
        var partitionName: String? = null
        val operations = mutableListOf<PartitionOperation>()

        try {
            while (pos < end) {
                val tagResult = VarInt.decodeInt(data, pos)
                val tag = tagResult.first
                pos += tagResult.second
                val wireType = tag and 0x07
                val fieldNumber = tag shr 3

                if (fieldNumber == 1 && wireType == 2) {
                    // partition_name (string, field 1)
                    val lenResult = VarInt.decodeInt(data, pos)
                    val strLen = lenResult.first
                    pos += lenResult.second
                    if (pos + strLen <= end) {
                        partitionName = String(data, pos, strLen, Charsets.UTF_8)
                    }
                    pos += strLen
                } else if (fieldNumber == 8 && wireType == 2) {
                    // operations (repeated message, field 8)
                    val lenResult = VarInt.decodeInt(data, pos)
                    val msgLen = lenResult.first
                    pos += lenResult.second
                    val op = parseInstallOperation(data, pos, pos + msgLen)
                    if (op != null) operations.add(op)
                    pos += msgLen
                } else if (wireType == 2) {
                    val lenResult = VarInt.decodeInt(data, pos)
                    pos += lenResult.second + lenResult.first
                } else if (wireType == 0) {
                    val result = VarInt.decodeLong(data, pos)
                    pos += result.second
                } else if (wireType == 1) {
                    pos += 8
                } else if (wireType == 5) {
                    pos += 4
                } else {
                    pos++
                }
            }
        } catch (e: Exception) {
            android.util.Log.d("XblExtractor", "parsePartitionUpdate error at pos $pos: ${e.message}")
        }

        if (partitionName != null) {
            return PartitionInfo(partitionName, 0L, operations)
        }
        return null
    }

    private fun parseInstallOperation(data: ByteArray, start: Int, end: Int): PartitionOperation? {
        var pos = start
        var type = -1
        var dataOffset = 0L
        var dataLength = 0L
        var dstStartBlock = 0L
        var dstNumBlocks = 0L

        try {
            android.util.Log.d("XblExtractor", "parseOperation raw: ${data.copyOfRange(start, minOf(start + 32, end)).joinToString(" ") { "%02x".format(it) }}")

            while (pos < end) {
                val tagResult = VarInt.decodeInt(data, pos)
                val tag = tagResult.first
                pos += tagResult.second
                val wireType = tag and 0x07
                val fieldNumber = tag shr 3

                when {
                    fieldNumber == 1 && wireType == 0 -> {
                        val result = VarInt.decodeInt(data, pos)
                        type = result.first
                        pos += result.second
                    }
                    fieldNumber == 2 && wireType == 0 -> {
                        val result = VarInt.decodeLong(data, pos)
                        dataOffset = result.first
                        pos += result.second
                    }
                    fieldNumber == 3 && wireType == 0 -> {
                        val result = VarInt.decodeLong(data, pos)
                        dataLength = result.first
                        pos += result.second
                    }
                    fieldNumber == 6 && wireType == 2 -> {
                        val lenResult = VarInt.decodeInt(data, pos)
                        val msgLen = lenResult.first
                        pos += lenResult.second
                        if (msgLen > 0) {
                            val extent = parseExtent(data, pos, pos + msgLen)
                            if (extent != null) {
                                dstStartBlock = extent.first
                                dstNumBlocks = extent.second
                            }
                        }
                        pos += msgLen
                    }
                    wireType == 0 -> {
                        val result = VarInt.decodeLong(data, pos)
                        pos += result.second
                    }
                    wireType == 1 -> { pos += 8 }
                    wireType == 2 -> {
                        val lenResult = VarInt.decodeInt(data, pos)
                        pos += lenResult.second + lenResult.first
                    }
                    wireType == 5 -> { pos += 4 }
                    else -> { pos++ }
                }
            }
            android.util.Log.d("XblExtractor", "parseOperation: type=$type, dataOffset=$dataOffset, dataLength=$dataLength, dstStart=$dstStartBlock, dstBlocks=$dstNumBlocks")
        } catch (e: Exception) {
            android.util.Log.d("XblExtractor", "parseInstallOperation error: ${e.message}")
        }

        return PartitionOperation(type, dataOffset, dataLength, dstStartBlock, dstNumBlocks)
    }

    private fun parseExtent(data: ByteArray, start: Int, end: Int): Pair<Long, Long>? {
        var pos = start
        var startBlock = 0L
        var numBlocks = 0L

        try {
            while (pos < end) {
                val tagResult = VarInt.decodeInt(data, pos)
                val tag = tagResult.first
                pos += tagResult.second
                val wireType = tag and 0x07
                val fieldNumber = tag shr 3

                if (fieldNumber == 1 && wireType == 0) {
                    val result = VarInt.decodeLong(data, pos)
                    startBlock = result.first
                    pos += result.second
                } else if (fieldNumber == 2 && wireType == 0) {
                    val result = VarInt.decodeLong(data, pos)
                    numBlocks = result.first
                    pos += result.second
                } else if (wireType == 0) {
                    val result = VarInt.decodeLong(data, pos)
                    pos += result.second
                } else {
                    pos++
                }
            }
        } catch (e: Exception) {
            android.util.Log.d("XblExtractor", "parseExtent error: ${e.message}")
        }

        return Pair(startBlock, numBlocks)
    }

    private fun downloadRange(url: String, start: Long, end: Long, client: OkHttpClient): ByteArray {
        val request = Request.Builder().url(url).header("Range", "bytes=$start-$end").build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            response.body?.bytes() ?: throw Exception("Empty response")
        }
    }
}

object VarInt {
    fun decodeInt(data: ByteArray, offset: Int): Pair<Int, Int> {
        var pos = offset
        var result = 0
        var shift = 0
        var bytesRead = 0
        while (pos < data.size && bytesRead < 5) {
            val b = data[pos].toInt() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            pos++
            bytesRead++
            if (b and 0x80 == 0) break
            shift += 7
        }
        return Pair(result, bytesRead)
    }

    fun decodeLong(data: ByteArray, offset: Int): Pair<Long, Int> {
        var pos = offset
        var result = 0L
        var shift = 0
        var bytesRead = 0
        while (pos < data.size && bytesRead < 10) {
            val b = data[pos].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            pos++
            bytesRead++
            if (b and 0x80 == 0) break
            shift += 7
        }
        return Pair(result, bytesRead)
    }

    @Deprecated("Use decodeInt or decodeLong")
    fun decode(data: ByteArray, offset: Int): Pair<Int, Int> {
        return decodeInt(data, offset)
    }
}

class XblExtractorViewModel(application: android.app.Application) : AndroidViewModel(application) {

    private val context: Context = getApplication()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _foundFiles = MutableStateFlow<List<XblFileInfo>>(emptyList())
    val foundFiles: StateFlow<List<XblFileInfo>> = _foundFiles.asStateFlow()

    private val _selectedFiles = MutableStateFlow<Set<Int>>(emptySet())
    val selectedFiles: StateFlow<Set<Int>> = _selectedFiles.asStateFlow()

    private val _scanResults = MutableStateFlow<List<XblScanResult>>(emptyList())
    val scanResults: StateFlow<List<XblScanResult>> = _scanResults.asStateFlow()

    private val _archiveInfo = MutableStateFlow<ZipArchiveInfo?>(null)
    val archiveInfo: StateFlow<ZipArchiveInfo?> = _archiveInfo.asStateFlow()

    private var localZipUri: Uri? = null
    private var remoteZipUrl: String? = null
    private var remoteCenData: ByteArray? = null
    private var saveDirUri: Uri? = null
    private var payloadInfo: PayloadUtil.PayloadInfo? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun clearError() { _error.value = null }
    fun clearStatus() { _status.value = null }
    fun clearResults() { _scanResults.value = emptyList() }
    fun setSaveDirUri(uri: Uri?) { saveDirUri = uri }

    fun toggleFileSelection(index: Int) {
        val current = _selectedFiles.value.toMutableSet()
        if (current.contains(index)) current.remove(index) else current.add(index)
        _selectedFiles.value = current
    }

    fun selectAll() { _selectedFiles.value = _foundFiles.value.indices.toSet() }
    fun clearSelection() { _selectedFiles.value = emptySet() }

    fun scanLocalZip(uri: Uri) {
        viewModelScope.launch {
            _isProcessing.value = true
            _error.value = null
            _scanResults.value = emptyList()
            _status.value = "Scanning local file..."
            localZipUri = uri
            remoteZipUrl = null
            remoteCenData = null
            payloadInfo = null
            try {
                val files = scanLocalZipForXbl(context, uri) { status ->
                    _status.value = status
                }
                _foundFiles.value = files
                _status.value = "Found ${files.size} xbl_config file(s)"
            } catch (e: Exception) {
                _error.value = "Failed to scan: ${e.message}"
                _status.value = null
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun scanRemoteZip(url: String) {
        viewModelScope.launch {
            _isProcessing.value = true
            _error.value = null
            _scanResults.value = emptyList()
            remoteZipUrl = url
            localZipUri = null
            remoteCenData = null
            payloadInfo = null
            try {
                val result = withContext(Dispatchers.IO) { scanRemoteZipForXbl(url) }
                _archiveInfo.value = result.first
                _foundFiles.value = result.second
                remoteCenData = result.third
                payloadInfo = result.fourth
                android.util.Log.d("XblExtractor", "scanRemoteZip: stored payloadInfo=${payloadInfo != null}")
                _status.value = "Found ${result.second.size} xbl_config file(s)"
            } catch (e: Exception) {
                _error.value = "Failed to scan: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun extractAndScanSelected(fullMode: Boolean = true, debug: Boolean = true) {
        val files = _foundFiles.value
        val selected = _selectedFiles.value
        if (selected.isEmpty()) { _error.value = "No files selected"; return }

        android.util.Log.d("XblExtractor", "extractAndScanSelected: payloadInfo=${payloadInfo != null}, remoteUrl=$remoteZipUrl")

        viewModelScope.launch {
            _isProcessing.value = true
            _error.value = null
            _scanResults.value = emptyList()
            val results = mutableListOf<XblScanResult>()
            val outputDir = File(context.cacheDir, "xbl_temp")
            if (!outputDir.exists()) outputDir.mkdirs()

            _status.value = "Starting extraction..."

            selected.forEachIndexed { idx, fileIdx ->
                if (fileIdx < files.size) {
                    val file = files[fileIdx]
                    _status.value = "Extracting file ${idx + 1}/${selected.size}: ${file.name}"
                    try {
                        val tempFile = withContext(Dispatchers.IO) {
                            extractSingleFile(file, outputDir)
                        }
                        if (tempFile != null) {
                            _status.value = "Scanning ${file.name}..."
                            val arbResult = ArbInspector.extractWithMode(tempFile.absolutePath, fullMode, debug)
                            results.add(XblScanResult(file.name, arbResult))
                            tempFile.delete()
                            _status.value = "Completed ${idx + 1}/${selected.size}"
                        } else {
                            _status.value = "Failed to extract ${file.name}"
                            results.add(XblScanResult(file.name, ArbResult().apply { error = "Extraction failed" }))
                        }
                    } catch (e: Exception) {
                        _status.value = "Error: ${e.message}"
                        results.add(XblScanResult(file.name, ArbResult().apply { error = e.message }))
                    }
                }
            }

            _scanResults.value = results
            _status.value = "Extraction complete - ${results.size} file(s) processed"
            _isProcessing.value = false
        }
    }

    fun saveSelectedFiles() {
        val files = _foundFiles.value
        val selected = _selectedFiles.value
        if (selected.isEmpty()) { _error.value = "No files selected"; return }

        viewModelScope.launch {
            _isProcessing.value = true
            _error.value = null
            var successCount = 0

            selected.forEachIndexed { idx, fileIdx ->
                if (fileIdx < files.size) {
                    val file = files[fileIdx]
                    _status.value = "Saving ${file.name} (${idx + 1}/${selected.size})..."
                    try {
                        val saved = withContext(Dispatchers.IO) {
                            if (saveDirUri != null) {
                                extractToUri(file, saveDirUri!!)
                            } else {
                                val outputDir = File(context.filesDir, "xbl_exports")
                                if (!outputDir.exists()) outputDir.mkdirs()
                                extractSingleFile(file, outputDir)
                            }
                        }
                        if (saved != null) successCount++
                    } catch (e: Exception) { /* skip */ }
                }
            }

            val location = if (saveDirUri != null) "selected folder" else "${context.filesDir}/xbl_exports"
            _status.value = "Saved $successCount file(s) to $location"
            _isProcessing.value = false
        }
    }

    private suspend fun scanLocalZipForXbl(context: Context, uri: Uri, onStatus: (String) -> Unit): List<XblFileInfo> {
        val files = mutableListOf<XblFileInfo>()
        var entryCount = 0
        var lastUpdate = 0L

        // First pass: search for xbl_config files in ZIP (up to 3 levels deep)
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zis ->
                var entry: java.util.zip.ZipEntry?
                while (zis.nextEntry.also { entry = it } != null) {
                    entryCount++
                    val now = System.currentTimeMillis()
                    if (now - lastUpdate > 500) { // Update every 500ms
                        onStatus("Scanning ZIP... ($entryCount entries checked)")
                        lastUpdate = now
                    }

                    val name = entry!!.name
                    // Check if file contains xbl_config and is not a directory
                    if (name.contains("xbl_config", ignoreCase = true) && !name.endsWith("/")) {
                        // Count directory depth (number of '/' characters)
                        val depth = name.count { it == '/' }
                        if (depth <= 3) {
                            files.add(XblFileInfo(
                                name = name.substringAfterLast("/"),
                                path = name,
                                size = entry!!.size,
                                source = "local",
                                description = "ZIP entry (${depth} level${if (depth != 1) "s" else ""} deep)"
                            ))
                        }
                    }
                }
            }
        }

        // Second pass: search for .bin files and try to find xbl_config inside them
        if (files.isEmpty()) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zis ->
                    var entry: java.util.zip.ZipEntry?
                    while (zis.nextEntry.also { entry = it } != null) {
                        entryCount++
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 500) { // Update every 500ms
                            onStatus("Scanning for .bin files... ($entryCount entries checked)")
                            lastUpdate = now
                        }

                        val name = entry!!.name
                        if (name.endsWith(".bin") && !name.endsWith("/")) {
                            // Try to parse as payload.bin
                            val depth = name.count { it == '/' }
                            if (depth <= 3) {
                                files.add(XblFileInfo(
                                    name = name.substringAfterLast("/"),
                                    path = name,
                                    size = entry!!.size,
                                    source = "bin",
                                    description = "Binary file (may contain xbl_config partition)"
                                ))
                            }
                        }
                    }
                }
            }
        }

        return files
    }

    private suspend fun scanRemoteZipForXbl(url: String): Tuple4<ZipArchiveInfo, List<XblFileInfo>, ByteArray, PayloadUtil.PayloadInfo?> {
        val headRequest = Request.Builder().url(url).head().build()
        val fileLength = client.newCall(headRequest).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            response.header("Content-Length")?.toLongOrNull() ?: 0L
        }
        if (fileLength == 0L) throw Exception("Cannot determine file size")

        val fileName = url.substringAfterLast("?").substringAfterLast("/")
        _archiveInfo.value = ZipArchiveInfo(fileName, fileLength)

        val readSize = 4096
        val rangeRequest = Request.Builder().url(url).header("Range", "bytes=${fileLength - readSize}-${fileLength - 1}").build()
        val tailData = client.newCall(rangeRequest).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            response.body?.bytes() ?: throw Exception("Empty response")
        }

        val fileInfo = ZipUtil.locateCentralDirectory(tailData, fileLength)
        if (fileInfo.offset < 0) throw Exception("Cannot find ZIP Central Directory")

        val cenData = downloadRange(url, fileInfo.offset, fileInfo.offset + fileInfo.size - 1)
        android.util.Log.d("XblExtractor", "Downloaded ${cenData.size} bytes of Central Directory")
        val cenBuffer = ByteBuffer.wrap(cenData)

        // Debug: list all files
        val allFiles = ZipUtil.findAllFilesInCentralDirectory(cenBuffer)
        android.util.Log.d("XblExtractor", "Total files in ZIP: ${allFiles.size}")
        allFiles.forEach { f -> android.util.Log.d("XblExtractor", "  ZIP: ${f.path}") }

        cenBuffer.position(0)
        val payloadFile = ZipUtil.findFileInCentralDirectory(cenBuffer, "payload.bin")
        val xblFiles = mutableListOf<XblFileInfo>()

        if (payloadFile != null) {
            android.util.Log.d("XblExtractor", "Found payload.bin at offset ${payloadFile.localHeaderOffset}, size ${payloadFile.compressedSize}")
            val pInfo = PayloadUtil.parsePayloadManifest(url, payloadFile, client)
            if (pInfo != null) {
                android.util.Log.d("XblExtractor", "Parsed payload manifest, found ${pInfo.partitions.size} partitions")
                val xblPartition = pInfo.partitions.find { it.name.equals("xbl_config", ignoreCase = true) }
                if (xblPartition != null) {
                    android.util.Log.d("XblExtractor", "Found xbl_config partition with ${xblPartition.operations.size} operations")
                    xblFiles.add(XblFileInfo(
                        name = "xbl_config",
                        path = "xbl_config",
                        size = xblPartition.size,
                        source = "payload",
                    ))
                } else {
                    android.util.Log.d("XblExtractor", "xbl_config partition not found in manifest")
                }
                return Tuple4(ZipArchiveInfo(fileName, fileLength), xblFiles, cenData, pInfo)
            } else {
                android.util.Log.d("XblExtractor", "Failed to parse payload.bin manifest")
            }
        } else {
            android.util.Log.d("XblExtractor", "payload.bin not found in ZIP")
        }

        val files = ZipUtil.findFilesInCentralDirectory(cenBuffer) { name ->
            name.contains("xbl_config", ignoreCase = true) && !name.endsWith("/")
        }

        return Tuple4(ZipArchiveInfo(fileName, fileLength), files, cenData, null)
    }

    private fun downloadRange(url: String, start: Long, end: Long): ByteArray {
        val length = end - start + 1
        if (length <= 0 || length > 100 * 1024 * 1024) {
            throw Exception("Invalid range: start=$start, end=$end, length=$length")
        }
        val request = Request.Builder().url(url).header("Range", "bytes=$start-$end").build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            val buffer = ByteArray(length.toInt())
            var offset = 0
            response.body?.byteStream()?.use { input ->
                while (offset < length) {
                    val read = input.read(buffer, offset, length.toInt() - offset)
                    if (read < 0) break
                    offset += read
                }
            }
            buffer
        }
    }

    private suspend fun extractSingleFile(file: XblFileInfo, outputDir: File): File? {
        return when (file.source) {
            "local" -> if (localZipUri != null) extractFromLocalZip(file, outputDir) else null
            "remote" -> if (remoteZipUrl != null) extractFromRemoteZip(file, outputDir) else null
            "payload" -> if (remoteZipUrl != null && payloadInfo != null) extractFromPayload(file, outputDir) else null
            else -> null
        }
    }

    private fun extractFromLocalZip(file: XblFileInfo, outputDir: File): File? {
        val outFile = File(outputDir, file.name)
        localZipUri?.let { uri ->
            getApplication<android.app.Application>().contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zis ->
                    var entry: java.util.zip.ZipEntry?
                    while (zis.nextEntry.also { entry = it } != null) {
                        if (entry!!.name == file.path) {
                            FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                            return outFile
                        }
                    }
                }
            }
        }
        return null
    }

    private fun extractFromRemoteZip(file: XblFileInfo, outputDir: File): File? {
        val outFile = File(outputDir, file.name)
        remoteZipUrl?.let { url ->
            try {
                val headerData = downloadRange(url, file.payloadOffset, file.payloadOffset + 1024)
                val buffer = ByteBuffer.wrap(headerData).order(ByteOrder.LITTLE_ENDIAN)
                if (buffer.getInt().toLong() != 0x04034b50L) return null
                buffer.position(26)
                val fileNameLen = buffer.getShort().toUInt().toInt()
                val extraFieldLen = buffer.getShort().toUInt().toInt()
                val dataStart = file.payloadOffset + 30 + fileNameLen + extraFieldLen
                val dataEnd = dataStart + file.compressedSize - 1
                val fileData = downloadRange(url, dataStart, dataEnd)
                FileOutputStream(outFile).use { it.write(fileData) }
                return outFile
            } catch (e: Exception) {
                return null
            }
        }
        return null
    }

    private suspend fun extractFromPayload(file: XblFileInfo, outputDir: File): File? {
        val outFile = File(outputDir, file.name)
        remoteZipUrl?.let { url ->
            try {
                val pInfo = payloadInfo ?: run {
                    android.util.Log.d("XblExtractor", "extractFromPayload: payloadInfo is null")
                    return@let null
                }

                android.util.Log.d("XblExtractor", "extractFromPayload: using cached payloadInfo, dataOffset=${pInfo.dataOffset}")
                android.util.Log.d("XblExtractor", "extractFromPayload: partitions=${pInfo.partitions.map { it.name }}")

                val xblPartition = pInfo.partitions.find { it.name.equals("xbl_config", ignoreCase = true) }
                    ?: run {
                        android.util.Log.d("XblExtractor", "extractFromPayload: xbl_config not found")
                        return@let null
                    }

                android.util.Log.d("XblExtractor", "extractFromPayload: found xbl_config with ${xblPartition.operations.size} operations")

                xblPartition.operations.forEachIndexed { idx, op ->
                    android.util.Log.d("XblExtractor", "  op[$idx] type=${op.type} dataOffset=${op.dataOffset} dataLength=${op.dataLength}")
                }

                java.io.FileOutputStream(outFile).use { fos ->
                    var totalWritten = 0L
                    xblPartition.operations.forEachIndexed { idx, op ->
                        android.util.Log.d("XblExtractor", "Operation $idx: type=$op.type, offset=${op.dataOffset}, length=${op.dataLength}")

                        if (op.type == 6) {
                            // ZERO: write zeros
                            val zeros = ByteArray(op.dataLength.toInt())
                            fos.write(zeros)
                            totalWritten += op.dataLength
                            android.util.Log.d("XblExtractor", "  Wrote ${op.dataLength} zeros")
                        } else if (op.dataLength > 0) {
                            val dataStart = pInfo.dataOffset + op.dataOffset
                            val dataEnd = dataStart + op.dataLength - 1
                            android.util.Log.d("XblExtractor", "  Downloading bytes $dataStart-$dataEnd (${op.dataLength} bytes)")

                            val opData = downloadRange(url, dataStart, dataEnd)
                            android.util.Log.d("XblExtractor", "  Downloaded ${opData.size} bytes, first 16: ${opData.take(16).joinToString(" ") { "%02x".format(it) }}")

                            when (op.type) {
                                0 -> { // REPLACE
                                    fos.write(opData)
                                    totalWritten += opData.size
                                    android.util.Log.d("XblExtractor", "  REPLACE: wrote ${opData.size} bytes")
                                }
                                8 -> { // REPLACE_XZ
                                    android.util.Log.d("XblExtractor", "  REPLACE_XZ: trying to decompress ${opData.size} bytes, header: ${opData.take(6).joinToString(" ") { "%02x".format(it) }}")
                                    val decompressed = decompressXz(opData)
                                    if (decompressed != null) {
                                        fos.write(decompressed)
                                        totalWritten += decompressed.size
                                        android.util.Log.d("XblExtractor", "  REPLACE_XZ: wrote ${decompressed.size} bytes")
                                    } else {
                                        android.util.Log.d("XblExtractor", "  REPLACE_XZ: decompression failed")
                                    }
                                }
                                1 -> { // REPLACE_BZ
                                    val decompressed = decompressBz2(opData)
                                    if (decompressed != null) {
                                        fos.write(decompressed)
                                        totalWritten += decompressed.size
                                        android.util.Log.d("XblExtractor", "  REPLACE_BZ: wrote ${decompressed.size} bytes")
                                    } else {
                                        android.util.Log.d("XblExtractor", "  REPLACE_BZ: decompression failed")
                                    }
                                }
                                6 -> {
                                    android.util.Log.d("XblExtractor", "  ZERO: wrote ${op.dataLength} zeros")
                                }
                                else -> android.util.Log.d("XblExtractor", "  Unknown op type: ${op.type}")
                            }
                        }
                    }
                    android.util.Log.d("XblExtractor", "Total written: $totalWritten bytes")
                }
                android.util.Log.d("XblExtractor", "Extracted ${outFile.length()} bytes to ${outFile.absolutePath}")
                return outFile
            } catch (e: Exception) {
                android.util.Log.d("XblExtractor", "extractFromPayload failed: ${e.message}", e)
                return null
            }
        }
        return null
    }

    private fun findPayloadFileInZip(url: String): ZipUtil.ZipFileInfo? {
        try {
            val headRequest = Request.Builder().url(url).head().build()
            val fileLength = client.newCall(headRequest).execute().use { response ->
                response.header("Content-Length")?.toLongOrNull() ?: 0L
            }
            if (fileLength == 0L) return null

            val readSize = 4096
            val rangeRequest = Request.Builder()
                .url(url)
                .header("Range", "bytes=${fileLength - readSize}-${fileLength - 1}")
                .build()
            val tailData = client.newCall(rangeRequest).execute().use { response ->
                response.body?.bytes()
            } ?: return null

            val fileInfo = ZipUtil.locateCentralDirectory(tailData, fileLength)
            if (fileInfo.offset < 0) return null

            val cenData = downloadRange(url, fileInfo.offset, fileInfo.offset + fileInfo.size - 1)
            return ZipUtil.findFileInCentralDirectory(java.nio.ByteBuffer.wrap(cenData), "payload.bin")
        } catch (e: Exception) {
            return null
        }
    }

    private fun decompressXz(data: ByteArray): ByteArray? {
        try {
            java.io.ByteArrayInputStream(data).use { bis ->
                org.tukaani.xz.XZInputStream(bis).use { xz ->
                    return xz.readBytes()
                }
            }
        } catch (e: Exception) {
            android.util.Log.d("XblExtractor", "XZ decompress failed: ${e.message}")
            return null
        }
    }

    private fun decompressBz2(data: ByteArray): ByteArray? {
        try {
            java.io.ByteArrayInputStream(data).use { bis ->
                java.util.zip.InflaterInputStream(bis, java.util.zip.Inflater(true)).use { bz2 ->
                    return bz2.readBytes()
                }
            }
        } catch (e: Exception) {
            android.util.Log.d("XblExtractor", "BZ2 decompress failed: ${e.message}")
            return null
        }
    }

    private fun extractToUri(file: XblFileInfo, dirUri: Uri): Uri? {
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME, file.name)
            put(android.provider.MediaStore.Files.FileColumns.MIME_TYPE, "application/octet-stream")
            put(android.provider.MediaStore.Files.FileColumns.RELATIVE_PATH, "Download/XBL_Export")
        }
        val contentUri = context.contentResolver.insert(
            android.provider.MediaStore.Files.getContentUri("external"), values
        )
        contentUri?.let { uri ->
            context.contentResolver.openOutputStream(uri)?.use { outStream ->
                if (file.source == "local" && localZipUri != null) {
                    extractToStream(file, outStream)
                }
                return uri
            }
        }
        return null
    }

    private fun extractToStream(file: XblFileInfo, outStream: java.io.OutputStream) {
        localZipUri?.let { uri ->
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zis ->
                    var entry: java.util.zip.ZipEntry?
                    while (zis.nextEntry.also { entry = it } != null) {
                        if (entry!!.name == file.path) { zis.copyTo(outStream); return }
                    }
                }
            }
        }
    }
}

data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

class XblExtractorViewModelFactory(private val context: Context) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return XblExtractorViewModel(context as android.app.Application) as T
    }
}
