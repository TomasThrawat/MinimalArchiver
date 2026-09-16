package com.minimalarchiver.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    ArchiverScreen()
                }
            }
        }
    }
}

private enum class BrowseMode { LOCAL, SHIZUKU }

private sealed class BrowseTarget {
    data class Local(val file: File) : BrowseTarget()
    data class Shell(val fullPath: String, val isDirectory: Boolean) : BrowseTarget()
}

private val BrowseTarget.displayName: String
    get() = when (this) {
        is BrowseTarget.Local -> file.name
        is BrowseTarget.Shell -> fullPath.substringAfterLast('/')
    }

private data class ShellEntry(val fullPath: String, val isDirectory: Boolean) {
    val name: String get() = fullPath.substringAfterLast('/')
}

private fun hasStorageAccess(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true

private fun listEntries(dir: File): List<File> =
    (dir.listFiles()?.toList() ?: emptyList())
        .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

private fun shellQuote(path: String): String = "'" + path.replace("'", "'\\''") + "'"

private fun buildListCommand(path: String): String {
    val q = shellQuote(path)
    return "for f in $q/*; do [ -e \"\$f\" ] || continue; if [ -d \"\$f\" ]; then echo D \"\$f\"; else echo F \"\$f\"; fi; done"
}

private fun parseShellListing(output: String): List<ShellEntry> =
    output.lineSequence()
        .filter { it.length > 2 && (it.startsWith("D ") || it.startsWith("F ")) }
        .map { ShellEntry(fullPath = it.substring(2), isDirectory = it.startsWith("D ")) }
        .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        .toList()

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArchiverScreen() {
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(hasStorageAccess()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { hasAccess = hasStorageAccess() }

    if (!hasAccess) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("محتاج صلاحية الوصول لكل الملفات", color = Color.White)
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    permissionLauncher.launch(intent)
                }) { Text("سماح") }
            }
        }
        return
    }

    var mode by remember { mutableStateOf(BrowseMode.LOCAL) }

    var currentDir by remember { mutableStateOf(Environment.getExternalStorageDirectory()) }
    var localFiles by remember { mutableStateOf(listEntries(currentDir)) }
    fun refreshLocal() { localFiles = listEntries(currentDir) }

    var shizukuPath by remember { mutableStateOf("/data") }
    var shizukuEntries by remember { mutableStateOf<List<ShellEntry>>(emptyList()) }
    var shellServiceBinder by remember { mutableStateOf<IShellService?>(null) }
    val bridge = remember {
        ShizukuBridge(context.packageName) { binder -> shellServiceBinder = binder }
    }
    DisposableEffect(Unit) {
        onDispose { bridge.unbindService() }
    }
    LaunchedEffect(shellServiceBinder, mode, shizukuPath) {
        if (mode == BrowseMode.SHIZUKU && shellServiceBinder != null) {
            shizukuEntries = parseShellListing(bridge.exec(buildListCommand(shizukuPath)))
        }
    }

    var statusMsg by remember { mutableStateOf("") }
    var showZipEntries by remember { mutableStateOf<Pair<File, List<String>>?>(null) }
    var actionMenuFor by remember { mutableStateOf<BrowseTarget?>(null) }
    var copyDialogFor by remember { mutableStateOf<BrowseTarget?>(null) }
    var deleteConfirmFor by remember { mutableStateOf<BrowseTarget?>(null) }

    fun ensureShizuku() {
        if (!bridge.isAvailable()) {
            statusMsg = "تطبيق Shizuku مش شغال أو مش متثبت"
            return
        }
        if (bridge.hasPermission()) bridge.bindService() else bridge.requestPermission()
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val canGoUp = if (mode == BrowseMode.LOCAL)
                        currentDir != Environment.getExternalStorageDirectory()
                    else
                        shizukuPath != "/"
                    if (canGoUp) {
                        TextButton(onClick = {
                            if (mode == BrowseMode.LOCAL) {
                                currentDir = currentDir.parentFile ?: currentDir
                                refreshLocal()
                            } else {
                                shizukuPath = File(shizukuPath).parent ?: "/"
                            }
                        }) { Text("←", color = Color.White) }
                    }
                    Text(
                        if (mode == BrowseMode.LOCAL) currentDir.path else shizukuPath,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        mode = if (mode == BrowseMode.LOCAL) BrowseMode.SHIZUKU else BrowseMode.LOCAL
                        if (mode == BrowseMode.SHIZUKU) ensureShizuku()
                    }) {
                        Text(if (mode == BrowseMode.LOCAL) "Shizuku" else "التخزين", color = Color.Cyan)
                    }
                }
                if (mode == BrowseMode.SHIZUKU && shellServiceBinder == null) {
                    Text(
                        "محتاج صلاحية Shizuku — اضغط \"سماح\" في تطبيق Shizuku لما يطلب",
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (statusMsg.isNotEmpty()) {
                Text(statusMsg, color = Color.Green, modifier = Modifier.padding(8.dp))
            }
            LazyColumn {
                if (mode == BrowseMode.LOCAL) {
                    items(localFiles) { file ->
                        val prefix = when {
                            file.isDirectory -> "📁"
                            file.extension.equals("zip", ignoreCase = true) -> "🗜"
                            else -> "📄"
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        when {
                                            file.isDirectory -> {
                                                currentDir = file
                                                refreshLocal()
                                            }
                                            file.extension.equals("zip", ignoreCase = true) -> {
                                                showZipEntries = file to ArchiveUtils.listZipEntries(file)
                                            }
                                            else -> {}
                                        }
                                    },
                                    onLongClick = { actionMenuFor = BrowseTarget.Local(file) }
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(prefix)
                            Spacer(Modifier.width(12.dp))
                            Text(file.name, color = Color.White)
                        }
                    }
                } else {
                    items(shizukuEntries) { entry ->
                        val prefix = if (entry.isDirectory) "📁" else "📄"
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (entry.isDirectory) shizukuPath = entry.fullPath
                                    },
                                    onLongClick = {
                                        actionMenuFor = BrowseTarget.Shell(entry.fullPath, entry.isDirectory)
                                    }
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(prefix)
                            Spacer(Modifier.width(12.dp))
                            Text(entry.name, color = Color.White)
                        }
                    }
                }
            }
        }
    }

    showZipEntries?.let { (zipFile, entries) ->
        AlertDialog(
            onDismissRequest = { showZipEntries = null },
            title = { Text(zipFile.name) },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    entries.forEach { Text(it) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val destDir = File(currentDir, zipFile.nameWithoutExtension)
                    val result = ArchiveUtils.extractZip(zipFile, destDir)
                    statusMsg = if (result.isSuccess) "تم فك الضغط في ${destDir.name}" else "فشل فك الضغط"
                    showZipEntries = null
                    refreshLocal()
                }) { Text("فك الضغط") }
            },
            dismissButton = {
                TextButton(onClick = { showZipEntries = null }) { Text("إلغاء") }
            }
        )
    }

    actionMenuFor?.let { target ->
        val isShell = target is BrowseTarget.Shell
        val isZip = target is BrowseTarget.Local && target.file.extension.equals("zip", ignoreCase = true)
        AlertDialog(
            onDismissRequest = { actionMenuFor = null },
            title = { Text(target.displayName) },
            text = {
                Column {
                    if (!isShell) {
                        TextButton(onClick = {
                            val file = (target as BrowseTarget.Local).file
                            val destZip = File(currentDir, "${file.nameWithoutExtension}_archive.zip")
                            val result = ArchiveUtils.createZip(listOf(file), destZip)
                            statusMsg = if (result.isSuccess) "تم إنشاء ${destZip.name}" else "فشل الضغط"
                            refreshLocal()
                            actionMenuFor = null
                        }) { Text("ضغط") }
                    }
                    if (isZip) {
                        TextButton(onClick = {
                            val file = (target as BrowseTarget.Local).file
                            val destDir = File(currentDir, file.nameWithoutExtension)
                            val result = ArchiveUtils.extractZip(file, destDir)
                            statusMsg = if (result.isSuccess) "تم فك الضغط في ${destDir.name}" else "فشل فك الضغط"
                            refreshLocal()
                            actionMenuFor = null
                        }) { Text("فك الضغط") }
                    }
                    TextButton(onClick = {
                        copyDialogFor = target
                        actionMenuFor = null
                    }) { Text("نسخ إلى مسار آخر") }
                    TextButton(onClick = {
                        deleteConfirmFor = target
                        actionMenuFor = null
                    }) { Text("حذف") }
                    if (isShell) {
                        Text(
                            "الضغط/فك الضغط مش متاح هنا — toybox مفيهش zip/unzip",
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { actionMenuFor = null }) { Text("إلغاء") }
            }
        )
    }

    copyDialogFor?.let { target ->
        var destPath by remember(target) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { copyDialogFor = null },
            title = { Text("نسخ ${target.displayName} إلى") },
            text = {
                OutlinedTextField(
                    value = destPath,
                    onValueChange = { destPath = it },
                    placeholder = { Text("/storage/emulated/0/Download") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    when (target) {
                        is BrowseTarget.Local -> {
                            val result = FileOps.copyToPath(target.file, destPath)
                            statusMsg = if (result.isSuccess) "تم النسخ" else "فشل النسخ: ${result.exceptionOrNull()?.message}"
                            refreshLocal()
                        }
                        is BrowseTarget.Shell -> {
                            val out = bridge.exec("mkdir -p ${shellQuote(destPath)} && cp -r ${shellQuote(target.fullPath)} ${shellQuote(destPath)}/")
                            statusMsg = if (out.isBlank()) "تم النسخ" else "فشل النسخ: $out"
                        }
                    }
                    copyDialogFor = null
                }) { Text("نسخ") }
            },
            dismissButton = {
                TextButton(onClick = { copyDialogFor = null }) { Text("إلغاء") }
            }
        )
    }

    deleteConfirmFor?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteConfirmFor = null },
            title = { Text("حذف ${target.displayName}؟") },
            text = { Text("مفيش تراجع بعد الحذف.") },
            confirmButton = {
                TextButton(onClick = {
                    when (target) {
                        is BrowseTarget.Local -> {
                            val result = FileOps.delete(target.file)
                            statusMsg = if (result.isSuccess) "تم الحذف" else "فشل الحذف"
                            refreshLocal()
                        }
                        is BrowseTarget.Shell -> {
                            val out = bridge.exec("rm -rf ${shellQuote(target.fullPath)}")
                            statusMsg = if (out.isBlank()) "تم الحذف" else "فشل الحذف: $out"
                            shizukuEntries = parseShellListing(bridge.exec(buildListCommand(shizukuPath)))
                        }
                    }
                    deleteConfirmFor = null
                }) { Text("حذف") }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmFor = null }) { Text("إلغاء") }
            }
        )
    }
}
