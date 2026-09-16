package com.minimalarchiver.app

import java.io.File
import java.io.IOException

object FileOps {
    fun delete(target: File): Result<Unit> = runCatching {
        if (!target.deleteRecursively()) throw IOException("فشل حذف ${target.name}")
    }

    fun copyToPath(source: File, destDirPath: String): Result<File> = runCatching {
        val destDir = File(destDirPath)
        if (!destDir.exists() && !destDir.mkdirs()) {
            throw IOException("تعذر إنشاء المسار: $destDirPath")
        }
        val target = File(destDir, source.name)
        if (source.isDirectory) {
            source.copyRecursively(target, overwrite = true)
        } else {
            source.copyTo(target, overwrite = true)
        }
        target
    }
}
