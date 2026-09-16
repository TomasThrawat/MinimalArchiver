package com.minimalarchiver.app

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ArchiveUtils {

    fun listZipEntries(zipFile: File): List<String> {
        val names = mutableListOf<String>()
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                names.add(entry.name)
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return names
    }

    fun extractZip(zipFile: File, destDir: File): Result<Int> = runCatching {
        if (!destDir.exists()) destDir.mkdirs()
        var count = 0
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                // Guard against zip-slip (entry paths escaping destDir)
                if (!outFile.canonicalPath.startsWith(destDir.canonicalPath + File.separator)) {
                    throw SecurityException("مسار غير آمن جوه الأرشيف: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    BufferedOutputStream(FileOutputStream(outFile)).use { bos ->
                        zis.copyTo(bos)
                    }
                    count++
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        count
    }

    fun createZip(sourceFiles: List<File>, destZip: File): Result<Unit> = runCatching {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(destZip))).use { zos ->
            sourceFiles.forEach { file -> addToZip(file, file.name, zos) }
        }
    }

    private fun addToZip(file: File, entryName: String, zos: ZipOutputStream) {
        if (file.isDirectory) {
            val children = file.listFiles() ?: emptyArray()
            if (children.isEmpty()) {
                zos.putNextEntry(ZipEntry("$entryName/"))
                zos.closeEntry()
            } else {
                children.forEach { child ->
                    addToZip(child, "$entryName/${child.name}", zos)
                }
            }
        } else {
            zos.putNextEntry(ZipEntry(entryName))
            FileInputStream(file).use { fis -> fis.copyTo(zos) }
            zos.closeEntry()
        }
    }
}
