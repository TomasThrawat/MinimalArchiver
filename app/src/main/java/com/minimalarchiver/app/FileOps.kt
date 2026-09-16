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

    fun rename(target: File, newName: String): Result<File> = runCatching {
        if (newName.isBlank() || newName == "." || newName == ".." || newName.contains('/')) {
            throw IOException("اسم غير صالح")
        }
        val dest = File(target.parentFile, newName)
        if (dest.exists()) throw IOException("في ملف بنفس الاسم بالفعل")
        if (!target.renameTo(dest)) throw IOException("فشل إعادة تسمية ${target.name}")
        dest
    }
}
