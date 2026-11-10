package com.example.filemaster

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

sealed class Screen {
    object Home : Screen()
    data class Browse(val path: String) : Screen()
    data class Category(val cat: Cat) : Screen()
    data class Editor(val path: String) : Screen()
    data class Player(val items: List<FItem>, val index: Int) : Screen()
    data class Img(val path: String) : Screen()
}

class MainActivity : ComponentActivity() {
    private var hasAccess by mutableStateOf(false)
    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { _, _ -> App.tick++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        App.ctx = applicationContext
        Pins.load()
        try { Shizuku.addRequestPermissionResultListener(shizukuListener) } catch (_: Throwable) {}
        setContent {
            MaterialTheme(colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize()) {
                        if (hasAccess) Root() else PermissionScreen()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasAccess = checkAccess()
        App.tick++
    }

    override fun onDestroy() {
        try { Shizuku.removeRequestPermissionResultListener(shizukuListener) } catch (_: Throwable) {}
        super.onDestroy()
    }

    private fun checkAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    @Composable
    fun PermissionScreen() {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.FolderOpen, null, Modifier.size(80.dp))
            Spacer(Modifier.height(16.dp))
            Text("التطبيق محتاج صلاحية الوصول لكل الملفات", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                if (Build.VERSION.SDK_INT >= 30) {
                    try {
                        startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
                    } catch (e: Exception) {
                        startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    }
                } else {
                    ActivityCompat.requestPermissions(
                        this@MainActivity,
                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 1
                    )
                }
            }) { Text("إعطاء الصلاحية") }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        hasAccess = checkAccess()
    }

    @Composable
    fun Root() {
        val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
        val drawer = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val nav: (Screen) -> Unit = { stack.add(it) }
        fun goRoot(s: Screen) {
            stack.clear(); stack.add(Screen.Home); if (s != Screen.Home) stack.add(s)
            scope.launch { drawer.close() }
        }
        BackHandler(drawer.isOpen) { scope.launch { drawer.close() } }
        BackHandler(stack.size > 1 && !drawer.isOpen) { stack.removeAt(stack.lastIndex) }
        val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
        val openDrawer: () -> Unit = { scope.launch { drawer.open() } }

        ModalNavigationDrawer(
            drawerState = drawer,
            gesturesEnabled = stack.last() is Screen.Home || stack.last() is Screen.Browse || stack.last() is Screen.Category,
            drawerContent = {
                ModalDrawerSheet {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
                        Text("ملفاتي", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
                        NavigationDrawerItem(
                            label = { Text("الرئيسية") }, selected = false,
                            icon = { Icon(Icons.Default.Home, null) }, onClick = { goRoot(Screen.Home) },
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        NavigationDrawerItem(
                            label = { Text("الذاكرة الداخلية") }, selected = false,
                            icon = { Icon(Icons.Default.PhoneAndroid, null) }, onClick = { goRoot(Screen.Browse(FS.root)) },
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        if (Build.VERSION.SDK_INT >= 30) {
                            NavigationDrawerItem(
                                label = { Text("Android/data (Shizuku)") }, selected = false,
                                icon = { Icon(Icons.Default.Lock, null) },
                                onClick = { goRoot(Screen.Browse(FS.root + "/Android/data")) },
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text("مجلداتي المهمة", style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                        if (Pins.list.isEmpty()) {
                            Text("اضغط ضغطة طويلة على أي فولدر ← إضافة للمهمة", style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 20.dp))
                        }
                        Pins.list.toList().forEach { (name, path) ->
                            Row(
                                Modifier.fillMaxWidth().clickable { goRoot(Screen.Browse(path)) }
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(16.dp))
                                Text(name, Modifier.weight(1f), maxLines = 1)
                                IconButton(onClick = { Pins.remove(path) }) { Icon(Icons.Default.Close, "حذف") }
                            }
                        }
                    }
                }
            }
        ) {
            when (val s = stack.last()) {
                is Screen.Home -> HomeScreen(nav, openDrawer)
                is Screen.Browse -> BrowserScreen(s.path, nav, openDrawer)
                is Screen.Category -> CategoryScreen(s.cat, nav, openDrawer)
                is Screen.Editor -> EditorScreen(s.path, back)
                is Screen.Player -> PlayerScreen(s.items, s.index, back)
                is Screen.Img -> ImageScreen(s.path, back)
            }
        }
    }
}
