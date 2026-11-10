package com.example.filemaster

import android.content.Context
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Cat(val title: String, val icon: ImageVector, val exts: Set<String>) {
    IMAGES("الصور", Icons.Default.Image, setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic")),
    VIDEOS("الفيديوهات", Icons.Default.VideoLibrary, setOf("mp4", "mkv", "avi", "mov", "3gp", "webm", "flv", "m4v")),
    AUDIO("الأغاني", Icons.Default.LibraryMusic, setOf("mp3", "m4a", "wav", "ogg", "flac", "aac", "opus", "amr")),
    TEXT("ملفات نصية", Icons.Default.TextSnippet, setOf("txt", "md", "log", "json", "xml", "csv", "ini", "cfg", "yml", "yaml")),
    PYTHON("بايثون", Icons.Default.Code, setOf("py", "ipynb")),
    WEB("HTML / CSS / JS", Icons.Default.Language, setOf("html", "htm", "css", "js")),
    DOCS("وورد / PDF / إكسل", Icons.Default.Description, setOf("doc", "docx", "pdf", "xls", "xlsx", "ppt", "pptx", "odt")),
    ARCHIVES("مضغوطات", Icons.Default.FolderZip, setOf("zip", "rar", "7z", "tar", "gz")),
    APK("تطبيقات APK", Icons.Default.Android, setOf("apk"))
}

val EDITABLE: Set<String> = Cat.TEXT.exts + Cat.PYTHON.exts + Cat.WEB.exts +
    setOf("java", "kt", "sh", "c", "cpp", "h", "properties", "bat", "sql", "php", "gradle", "toml", "conf")

object Pins {
    val list = mutableStateListOf<Pair<String, String>>() // الاسم ← المسار
    private fun prefs() = App.ctx.getSharedPreferences("pins", Context.MODE_PRIVATE)
    fun load() {
        list.clear()
        prefs().getString("p", "")!!.lines().filter { it.contains('\t') }.forEach {
            val a = it.split('\t', limit = 2); list.add(a[0] to a[1])
        }
    }
    private fun save() = prefs().edit().putString("p", list.joinToString("\n") { it.first + "\t" + it.second }).apply()
    fun add(name: String, path: String) { list.removeAll { it.second == path }; list.add(name to path); save() }
    fun remove(path: String) { list.removeAll { it.second == path }; save() }
}

object Clip {
    var paths by mutableStateOf<List<String>>(emptyList())
    var cut by mutableStateOf(false)
}

fun Context.toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

fun fmtSize(b: Long): String {
    if (b < 1024) return "$b B"
    val u = arrayOf("KB", "MB", "GB", "TB")
    var v = b / 1024.0; var i = 0
    while (v >= 1024 && i < 3) { v /= 1024; i++ }
    return String.format(Locale.US, "%.1f %s", v, u[i])
}

fun fmtDate(t: Long): String = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(t))

fun iconFor(i: FItem): ImageVector =
    if (i.isDir) Icons.Default.Folder
    else Cat.values().firstOrNull { i.ext in it.exts }?.icon ?: Icons.Default.InsertDriveFile
