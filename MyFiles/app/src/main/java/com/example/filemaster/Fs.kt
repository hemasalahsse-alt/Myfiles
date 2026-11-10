package com.example.filemaster

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import rikka.shizuku.Shizuku
import java.io.File

object App {
    lateinit var ctx: Context
    var tick by mutableIntStateOf(0)
}

data class FItem(val path: String, val name: String, val isDir: Boolean, val size: Long, val modified: Long) {
    val ext: String get() = name.substringAfterLast('.', "").lowercase()
}

/** تنفيذ أوامر بصلاحية shell عن طريق Shizuku (للوصول لـ Android/data و Android/obb) */
object Shell {
    fun available(): Boolean = try { Shizuku.pingBinder() } catch (e: Throwable) { false }
    fun granted(): Boolean = try {
        available() && !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) { false }
    /** 0 = Shizuku مش شغال ، 1 = شغال بس مفيش صلاحية ، 2 = جاهز */
    fun status(): Int = if (!available()) 0 else if (granted()) 2 else 1
    fun request() { try { Shizuku.requestPermission(1001) } catch (e: Throwable) {} }

    fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"

    fun spawn(cmd: String): Process {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        )
        m.isAccessible = true
        return m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
    }

    fun run(cmd: String): Pair<Int, ByteArray> {
        val p = spawn(cmd)
        val out = p.inputStream.readBytes()
        return p.waitFor() to out
    }

    fun write(path: String, data: ByteArray) {
        val p = spawn("cat > ${q(path)}")
        p.outputStream.use { it.write(data) }
        p.waitFor()
    }

    fun pipeTo(path: String, dst: File) {
        val p = spawn("cat ${q(path)} 2>/dev/null")
        p.inputStream.use { i -> dst.outputStream().use { o -> i.copyTo(o) } }
        p.waitFor()
    }
}

object FS {
    val root: String = Environment.getExternalStorageDirectory().absolutePath

    fun shell(p: String) = Build.VERSION.SDK_INT >= 30 &&
        (p == "$root/Android/data" || p.startsWith("$root/Android/data/") ||
            p == "$root/Android/obb" || p.startsWith("$root/Android/obb/"))

    /** null = مفيش صلاحية */
    fun list(p: String): List<FItem>? {
        val r: List<FItem> = if (shell(p)) {
            if (!Shell.granted()) return null
            val (_, o) = Shell.run("find ${Shell.q(p)} -maxdepth 1 -mindepth 1 -exec stat -c '%F|%s|%Y|%n' {} + 2>/dev/null")
            String(o).lines().mapNotNull { l ->
                val a = l.split("|", limit = 4)
                if (a.size < 4) null else FItem(
                    a[3], a[3].substringAfterLast('/'), a[0].startsWith("directory"),
                    a[1].toLongOrNull() ?: 0L, (a[2].toLongOrNull() ?: 0L) * 1000
                )
            }
        } else {
            val arr = File(p).listFiles() ?: return null
            arr.map { FItem(it.absolutePath, it.name, it.isDirectory, it.length(), it.lastModified()) }
        }
        return r.sortedWith(compareBy({ !it.isDir }, { it.name.lowercase() }))
    }

    fun read(p: String): ByteArray =
        if (shell(p)) Shell.run("cat ${Shell.q(p)} 2>/dev/null").second else File(p).readBytes()

    fun write(p: String, data: ByteArray) {
        if (shell(p)) Shell.write(p, data) else File(p).writeBytes(data)
    }

    fun delete(p: String) {
        if (shell(p)) Shell.run("rm -rf ${Shell.q(p)}") else File(p).deleteRecursively()
    }

    fun mkdir(p: String) {
        if (shell(p)) Shell.run("mkdir -p ${Shell.q(p)}") else File(p).mkdirs()
    }

    fun touch(p: String) {
        if (shell(p)) Shell.run("touch ${Shell.q(p)}") else File(p).createNewFile()
    }

    fun rename(from: String, to: String) {
        if (shell(from) || shell(to)) Shell.run("mv ${Shell.q(from)} ${Shell.q(to)}")
        else if (!File(from).renameTo(File(to))) throw Exception("فشل تغيير الاسم")
    }

    fun copy(from: String, to: String) {
        if (shell(from) || shell(to)) Shell.run("cp -r ${Shell.q(from)} ${Shell.q(to)}")
        else File(from).copyRecursively(File(to), overwrite = false)
    }

    fun move(from: String, to: String) {
        if (shell(from) || shell(to)) { Shell.run("mv ${Shell.q(from)} ${Shell.q(to)}"); return }
        if (!File(from).renameTo(File(to))) {
            File(from).copyRecursively(File(to), overwrite = false)
            File(from).deleteRecursively()
        }
    }

    /** يرجّع مسار عادي يقدر أي مشغّل يقراه (ينسخ لملف مؤقت لو الملف في مسار محمي) */
    fun materialize(p: String): String {
        if (!shell(p)) return p
        val f = File(App.ctx.cacheDir, "m_" + p.hashCode() + "_" + p.substringAfterLast('/'))
        if (!f.exists() || f.length() == 0L) Shell.pipeTo(p, f)
        return f.absolutePath
    }

    /** يمسح الذاكرة الداخلية كلها ويجيب ملفات الامتدادات المطلوبة (من غير Android/) */
    fun scan(exts: Set<String>): List<FItem> =
        File(root).walkTopDown()
            .onEnter { !(it.parent == root && it.name == "Android") && !it.name.startsWith(".") }
            .filter { it.isFile && it.extension.lowercase() in exts }
            .map { FItem(it.absolutePath, it.name, false, it.length(), it.lastModified()) }
            .sortedByDescending { it.modified }
            .toList()
}
