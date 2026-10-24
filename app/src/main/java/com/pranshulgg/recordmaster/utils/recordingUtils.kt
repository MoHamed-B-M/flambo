package com.pranshulgg.recordmaster.utils

import java.io.File

fun computeDirKey(dir: File): Long {
    val children = dir.listFiles() ?: return 0L
    var total = 0L
    for (child in children) {
        total += child.name.hashCode().toLong()
        total += child.lastModified()
        if (child.isDirectory) {
            total += computeDirKey(child)
        }
    }
    return total
}


 fun moveFileToDir(file: File, targetDir: File) {
    try {
        val target = File(targetDir, file.name)
        if (!file.renameTo(target)) {
            file.copyTo(target, overwrite = true)
            file.delete()
        }
    } catch (_: Exception) { }
}
