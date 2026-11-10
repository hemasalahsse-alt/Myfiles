@file:OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)

package com.example.filemaster

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.RepeatModeUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ---------- محرر النصوص ----------
@Composable
fun EditorScreen(path: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf<String?>(null) }
    var orig by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var confirmExit by remember { mutableStateOf(false) }
    LaunchedEffect(path) {
        withContext(Dispatchers.IO) {
            try {
                val b = FS.read(path)
                if (b.size > 3_000_000) err = "الملف كبير (${fmtSize(b.size.toLong())}) — المحرر بيدعم لحد 3MB"
                else { val s = String(b); orig = s; text = s }
            } catch (e: Exception) { err = e.message }
        }
    }
    val dirty = text != null && text != orig
    BackHandler(dirty) { confirmExit = true }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(path.substringAfterLast('/') + if (dirty) " •" else "", maxLines = 1) },
            navigationIcon = { IconButton(onClick = { if (dirty) confirmExit = true else onBack() }) { Icon(Icons.Default.ArrowBack, "رجوع") } },
            actions = {
                IconButton(enabled = dirty, onClick = {
                    val t = text ?: return@IconButton
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { runCatching { FS.write(path, t.toByteArray()) } }
                        if (r.isSuccess) { orig = t; ctx.toast("اتحفظ ✓") } else ctx.toast("فشل الحفظ: ${r.exceptionOrNull()?.message}")
                    }
                }) { Icon(Icons.Default.Save, "حفظ") }
            }
        )
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val t = text
            when {
                err != null -> Text(err!!, Modifier.align(Alignment.Center).padding(24.dp))
                t == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(8.dp)) {
                        BasicTextField(
                            value = t, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                        )
                    }
                    Text("${t.count { it == '\n' } + 1} سطر  •  ${t.length} حرف", Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    if (confirmExit) AlertDialog(
        onDismissRequest = { confirmExit = false }, title = { Text("فيه تعديلات متحفظتش") },
        confirmButton = {
            TextButton(onClick = {
                val t = text ?: ""
                scope.launch {
                    withContext(Dispatchers.IO) { runCatching { FS.write(path, t.toByteArray()) } }
                    confirmExit = false; onBack()
                }
            }) { Text("حفظ وخروج") }
        },
        dismissButton = { TextButton(onClick = { confirmExit = false; onBack() }) { Text("خروج من غير حفظ") } }
    )
}

// ---------- مشغّل الفيديو والأغاني ----------
@Composable
fun PlayerScreen(itemsIn: List<FItem>, startIn: Int, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    val cur = itemsIn.getOrNull(startIn)
    val protectedFile = cur != null && FS.shell(cur.path)
    val items = if (protectedFile) listOf(cur!!) else itemsIn
    val start = if (protectedFile) 0 else startIn
    val isVideo = cur?.ext in Cat.VIDEOS.exts
    val exo = remember { ExoPlayer.Builder(ctx).build() }
    var title by remember { mutableStateOf(cur?.name ?: "") }

    LaunchedEffect(Unit) {
        val mi = withContext(Dispatchers.IO) {
            items.map {
                MediaItem.Builder()
                    .setUri(Uri.fromFile(File(FS.materialize(it.path))))
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(it.name).build())
                    .build()
            }
        }
        exo.setMediaItems(mi, start, 0L)
        exo.prepare()
        exo.playWhenReady = true
    }
    DisposableEffect(Unit) {
        val l = object : Player.Listener {
            override fun onMediaMetadataChanged(m: MediaMetadata) { m.title?.let { title = it.toString() } }
        }
        exo.addListener(l)
        onDispose {
            exo.removeListener(l); exo.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "رجوع", tint = Color.White) }
            Text(title, Modifier.weight(1f), color = Color.White, maxLines = 1)
            if (isVideo) IconButton(onClick = {
                val land = activity?.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                activity?.requestedOrientation =
                    if (land) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }) { Icon(Icons.Default.ScreenRotation, "تدوير", tint = Color.White) }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (!isVideo) Icon(
                Icons.Default.MusicNote, null, tint = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.align(Alignment.Center).size(140.dp)
            )
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c ->
                    PlayerView(c).apply {
                        player = exo
                        useController = true
                        setShowShuffleButton(true)
                        setRepeatToggleModes(RepeatModeUtil.REPEAT_TOGGLE_MODE_ONE or RepeatModeUtil.REPEAT_TOGGLE_MODE_ALL)
                        if (!isVideo) { controllerShowTimeoutMs = 0; controllerHideOnTouch = false; showController() }
                    }
                }
            )
        }
    }
}

// ---------- عارض الصور ----------
@Composable
fun ImageScreen(path: String, onBack: () -> Unit) {
    var file by remember { mutableStateOf<File?>(null) }
    LaunchedEffect(path) { file = withContext(Dispatchers.IO) { File(FS.materialize(path)) } }
    var scale by remember { mutableFloatStateOf(1f) }
    var off by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        file?.let {
            AsyncImage(
                model = it, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            off = if (scale == 1f) Offset.Zero else off + pan
                        }
                    }
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = off.x, translationY = off.y)
            )
        }
        IconButton(onClick = onBack, Modifier.align(Alignment.TopStart).padding(8.dp)) {
            Icon(Icons.Default.ArrowBack, "رجوع", tint = Color.White)
        }
        Text(path.substringAfterLast('/'), Modifier.align(Alignment.BottomCenter).padding(12.dp), color = Color.White, maxLines = 1)
    }
}
