@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.example.filemaster

import android.content.Context
import android.content.Intent
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ---------- فتح الملفات ----------
fun openItem(ctx: Context, f: FItem, siblings: List<FItem>, nav: (Screen) -> Unit, scope: CoroutineScope) {
    val e = f.ext
    when {
        e in Cat.IMAGES.exts -> nav(Screen.Img(f.path))
        e in Cat.VIDEOS.exts || e in Cat.AUDIO.exts -> {
            val grp = if (e in Cat.VIDEOS.exts) Cat.VIDEOS.exts else Cat.AUDIO.exts
            val l = siblings.filter { !it.isDir && it.ext in grp }
            nav(Screen.Player(l, l.indexOfFirst { it.path == f.path }.coerceAtLeast(0)))
        }
        e in EDITABLE -> nav(Screen.Editor(f.path))
        else -> openExternal(ctx, f, scope)
    }
}

fun openExternal(ctx: Context, f: FItem, scope: CoroutineScope) {
    scope.launch {
        try {
            val p = withContext(Dispatchers.IO) { FS.materialize(f.path) }
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", File(p))
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.ext) ?: "*/*"
            val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ctx.startActivity(Intent.createChooser(i, null))
        } catch (x: Exception) {
            ctx.toast("مفيش تطبيق يفتح الملف ده")
        }
    }
}

// ---------- عناصر مشتركة ----------
@Composable
fun FileRow(f: FItem, showPath: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLong).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!f.isDir && f.ext in Cat.IMAGES.exts && !FS.shell(f.path)) {
            AsyncImage(
                model = File(f.path), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp))
            )
        } else {
            Icon(
                iconFor(f), null, Modifier.size(44.dp).padding(6.dp),
                tint = if (f.isDir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = if (showPath) f.path.substringBeforeLast('/') + "  •  " + fmtSize(f.size)
            else if (f.isDir) fmtDate(f.modified) else fmtSize(f.size) + "  •  " + fmtDate(f.modified)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun TextDialog(title: String, initial: String, onOk: (String) -> Unit, onDismiss: () -> Unit) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(v, { v = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (v.isNotBlank()) onOk(v.trim()) }) { Text("تم") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

// ---------- الرئيسية ----------
@Composable
fun HomeScreen(nav: (Screen) -> Unit, openDrawer: () -> Unit) {
    val tick = App.tick
    val status = remember(tick) { Shell.status() }
    Scaffold(topBar = {
        TopAppBar(title = { Text("ملفاتي") }, navigationIcon = {
            IconButton(openDrawer) { Icon(Icons.Default.Menu, "القائمة") }
        })
    }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card(Modifier.fillMaxWidth().clickable { nav(Screen.Browse(FS.root)) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PhoneAndroid, null, Modifier.size(36.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("الذاكرة الداخلية", style = MaterialTheme.typography.titleMedium)
                            Text(FS.root, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= 30) item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Shizuku (للوصول لـ Android/data)", style = MaterialTheme.typography.titleSmall)
                        Text(
                            when (status) { 0 -> "مش شغال — افتح تطبيق Shizuku وشغّله"; 1 -> "شغال — محتاج تدي الصلاحية"; else -> "جاهز ✓" },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (status == 1) Button(onClick = { Shell.request() }) { Text("طلب الصلاحية") }
                            if (status == 2) Button(onClick = { nav(Screen.Browse(FS.root + "/Android/data")) }) { Text("فتح Android/data") }
                            OutlinedButton(onClick = { App.tick++ }) { Text("تحديث") }
                        }
                    }
                }
            }
            item { Text("الأقسام", style = MaterialTheme.typography.titleMedium) }
            items(Cat.values().toList().chunked(2)) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { c ->
                        Card(Modifier.weight(1f).clickable { nav(Screen.Category(c)) }) {
                            Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(c.icon, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(6.dp))
                                Text(c.title, maxLines = 1)
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            item { Text("مجلداتي المهمة", style = MaterialTheme.typography.titleMedium) }
            if (Pins.list.isEmpty()) item {
                Text("لسه مفيش. افتح أي فولدر واضغط عليه ضغطة طويلة ← «إضافة للمجلدات المهمة»، أو اضغط النجمة فوق.",
                    style = MaterialTheme.typography.bodySmall)
            }
            items(Pins.list.toList()) { (name, path) ->
                Card(Modifier.fillMaxWidth().clickable { nav(Screen.Browse(path)) }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, maxLines = 1)
                            Text(path, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

// ---------- قسم (صور / فيديو / ...) ----------
@Composable
fun CategoryScreen(cat: Cat, nav: (Screen) -> Unit, openDrawer: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var list by remember(cat) { mutableStateOf<List<FItem>?>(null) }
    LaunchedEffect(cat) { list = withContext(Dispatchers.IO) { FS.scan(cat.exts) } }
    Scaffold(topBar = {
        TopAppBar(title = { Text(cat.title + (list?.let { " (${it.size})" } ?: "")) },
            navigationIcon = { IconButton(openDrawer) { Icon(Icons.Default.Menu, "القائمة") } })
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val l = list
            if (l == null) CircularProgressIndicator(Modifier.align(Alignment.Center))
            else if (l.isEmpty()) Text("مفيش ملفات", Modifier.align(Alignment.Center))
            else LazyColumn(Modifier.fillMaxSize()) {
                items(l, key = { it.path }) { f ->
                    FileRow(f, true, { openItem(ctx, f, l, nav, scope) }, { openExternal(ctx, f, scope) })
                }
            }
        }
    }
}

// ---------- المتصفح ----------
private sealed class Dlg {
    object NewFolder : Dlg()
    object NewFile : Dlg()
    object PinCurrent : Dlg()
    data class Rename(val f: FItem) : Dlg()
    data class Delete(val f: FItem) : Dlg()
    data class Pin(val f: FItem) : Dlg()
}

@Composable
fun BrowserScreen(path: String, nav: (Screen) -> Unit, openDrawer: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var loaded by remember(path, tick) { mutableStateOf(false) }
    var items by remember(path, tick) { mutableStateOf<List<FItem>?>(null) }
    LaunchedEffect(path, tick, App.tick) {
        val r = withContext(Dispatchers.IO) { FS.list(path) }
        items = r; loaded = true
    }
    var menu by remember { mutableStateOf<FItem?>(null) }
    var dlg by remember { mutableStateOf<Dlg?>(null) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var showHidden by remember { mutableStateOf(false) }
    var plus by remember { mutableStateOf(false) }

    fun op(block: () -> Unit) {
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching(block) }
            r.exceptionOrNull()?.let { ctx.toast("خطأ: ${it.message}") }
            tick++
        }
    }

    val title = if (path == FS.root) "الذاكرة الداخلية" else path.substringAfterLast('/')
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) TextField(query, { query = it }, singleLine = true, placeholder = { Text("بحث...") })
                    else Column {
                        Text(title, maxLines = 1)
                        Text(path, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = { IconButton(openDrawer) { Icon(Icons.Default.Menu, "القائمة") } },
                actions = {
                    IconButton(onClick = { searching = !searching; query = "" }) { Icon(if (searching) Icons.Default.Close else Icons.Default.Search, "بحث") }
                    IconButton(onClick = { dlg = Dlg.PinCurrent }) { Icon(Icons.Default.StarBorder, "إضافة للمهمة") }
                    Box {
                        IconButton(onClick = { plus = true }) { Icon(Icons.Default.MoreVert, "المزيد") }
                        DropdownMenu(plus, { plus = false }) {
                            DropdownMenuItem(text = { Text("فولدر جديد") }, onClick = { plus = false; dlg = Dlg.NewFolder })
                            DropdownMenuItem(text = { Text("ملف جديد") }, onClick = { plus = false; dlg = Dlg.NewFile })
                            DropdownMenuItem(text = { Text(if (showHidden) "إخفاء الملفات المخفية" else "إظهار الملفات المخفية") },
                                onClick = { plus = false; showHidden = !showHidden })
                            DropdownMenuItem(text = { Text("تحديث") }, onClick = { plus = false; tick++ })
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (Clip.paths.isNotEmpty()) {
                BottomAppBar {
                    TextButton(onClick = {
                        val src = Clip.paths; val cut = Clip.cut
                        Clip.paths = emptyList()
                        op {
                            src.forEach { s ->
                                var name = s.substringAfterLast('/')
                                while (FS.list(path)?.any { it.name == name } == true) name = "نسخة_$name"
                                val d = "$path/$name"
                                if (cut) FS.move(s, d) else FS.copy(s, d)
                            }
                        }
                    }) { Icon(Icons.Default.ContentPaste, null); Spacer(Modifier.width(6.dp)); Text("لصق هنا (${Clip.paths.size})") }
                    TextButton(onClick = { Clip.paths = emptyList() }) { Text("إلغاء") }
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val all = items
            when {
                !loaded -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                all == null -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("مش قادر أفتح المجلد ده.")
                    if (FS.shell(path)) {
                        Text("محتاج Shizuku شغال ومديله الصلاحية.")
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { Shell.request() }) { Text("طلب صلاحية Shizuku") }
                    }
                }
                else -> {
                    val shown = all.filter { (showHidden || !it.name.startsWith(".")) && (query.isBlank() || it.name.contains(query, true)) }
                    LazyColumn(Modifier.fillMaxSize()) {
                        if (path != "/" && !searching) item {
                            Row(Modifier.fillMaxWidth().clickable { nav(Screen.Browse(path.substringBeforeLast('/').ifEmpty { "/" })) }.padding(16.dp)) {
                                Icon(Icons.Default.ArrowUpward, null); Spacer(Modifier.width(12.dp)); Text("المجلد الأعلى")
                            }
                        }
                        if (shown.isEmpty()) item { Text("فاضي", Modifier.padding(24.dp)) }
                        items(shown, key = { it.path }) { f ->
                            FileRow(f, false, {
                                if (f.isDir) nav(Screen.Browse(f.path)) else openItem(ctx, f, all, nav, scope)
                            }, { menu = f })
                        }
                    }
                }
            }
        }
    }

    // قائمة الضغط الطويل
    menu?.let { f ->
        AlertDialog(
            onDismissRequest = { menu = null }, title = { Text(f.name, maxLines = 1) },
            text = {
                Column {
                    @Composable fun entry(t: String, a: () -> Unit) =
                        Text(t, Modifier.fillMaxWidth().clickable { menu = null; a() }.padding(vertical = 12.dp))
                    if (f.isDir) entry("إضافة للمجلدات المهمة") { dlg = Dlg.Pin(f) }
                    if (!f.isDir) {
                        entry("فتح في المحرر (كنص)") { nav(Screen.Editor(f.path)) }
                        entry("فتح بتطبيق آخر") { openExternal(ctx, f, scope) }
                    }
                    entry("نسخ") { Clip.paths = listOf(f.path); Clip.cut = false; ctx.toast("اتنسخ — روح للمكان واضغط لصق") }
                    entry("قص") { Clip.paths = listOf(f.path); Clip.cut = true; ctx.toast("اتقص — روح للمكان واضغط لصق") }
                    entry("إعادة تسمية") { dlg = Dlg.Rename(f) }
                    entry("حذف") { dlg = Dlg.Delete(f) }
                }
            },
            confirmButton = { TextButton(onClick = { menu = null }) { Text("إغلاق") } }
        )
    }

    when (val d = dlg) {
        null -> {}
        is Dlg.NewFolder -> TextDialog("اسم الفولدر", "", { n -> dlg = null; op { FS.mkdir("$path/$n") } }, { dlg = null })
        is Dlg.NewFile -> TextDialog("اسم الملف", "new.txt", { n -> dlg = null; op { FS.touch("$path/$n") } }, { dlg = null })
        is Dlg.Rename -> TextDialog("اسم جديد", d.f.name, { n ->
            dlg = null; op { FS.rename(d.f.path, d.f.path.substringBeforeLast('/') + "/" + n) }
        }, { dlg = null })
        is Dlg.Pin -> TextDialog("اسم الاختصار", d.f.name, { n -> dlg = null; Pins.add(n, d.f.path); ctx.toast("اتضاف") }, { dlg = null })
        is Dlg.PinCurrent -> TextDialog("اسم الاختصار", title, { n -> dlg = null; Pins.add(n, path); ctx.toast("اتضاف") }, { dlg = null })
        is Dlg.Delete -> AlertDialog(
            onDismissRequest = { dlg = null }, title = { Text("حذف ${d.f.name}؟") },
            text = { Text("مش هينفع ترجعه تاني.") },
            confirmButton = { TextButton(onClick = { dlg = null; op { FS.delete(d.f.path) } }) { Text("حذف") } },
            dismissButton = { TextButton(onClick = { dlg = null }) { Text("إلغاء") } }
        )
    }
}
