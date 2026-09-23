package my.noveldokusha.core

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

fun atomicWrite(target: File, bytes: ByteArray) {
    val tmp = File(target.path + ".tmp")
    try {
        tmp.parentFile?.mkdirs()
        FileOutputStream(tmp).use { fos ->
            fos.write(bytes)
            fos.flush()
            runCatching { fos.fd.sync() }
        }
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    } catch (e: Exception) {
        tmp.delete()
        throw e
    }
}
