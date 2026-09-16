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

private fun hasStorageAccess(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true

private fun listEntries(dir: File): List<File> =
    (dir.listFiles()?.toList() ?: emptyList())
        .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

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

    var currentDir by remember { mutableStateOf(Environment.getExternalStorageDirectory()) }
    var files by remember { mutableStateOf(listEntries(currentDir)) }
    val selected = remember { mutableStateListOf<File>() }
    var statusMsg by remember { mutableStateOf("") }
    var showEntries by remember { mutableStateOf<Pair<File, List<String>>?>(null) }

    fun refresh() {
        files = listEntries(currentDir)
        selected.clear()
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentDir != Environment.getExternalStorageDirectory()) {
                    TextButton(onClick = {
                        currentDir = currentDir.parentFile ?: currentDir
                        refresh()
                    }) { Text("←", color = Color.White) }
                }
                Text(currentDir.path, color = Color.White, maxLines = 1)
            }
        },
        floatingActionButton = {
            if (selected.isNotEmpty()) {
                FloatingActionButton(onClick = {
                    val destZip = File(currentDir, "archive_${System.currentTimeMillis()}.zip")
                    val result = ArchiveUtils.createZip(selected.toList(), destZip)
                    statusMsg = if (result.isSuccess) "تم إنشاء ${destZip.name}" else "فشل الإنشاء"
                    refresh()
                }) { Text("+") }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (statusMsg.isNotEmpty()) {
                Text(statusMsg, color = Color.Green, modifier = Modifier.padding(8.dp))
            }
            LazyColumn {
                items(files) { file ->
                    val isSelected = selected.contains(file)
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
                                            refresh()
                                        }
                                        file.extension.equals("zip", ignoreCase = true) -> {
                                            showEntries = file to ArchiveUtils.listZipEntries(file)
                                        }
                                        else -> {
                                            if (isSelected) selected.remove(file) else selected.add(file)
                                        }
                                    }
                                },
                                onLongClick = {
                                    if (isSelected) selected.remove(file) else selected.add(file)
                                }
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(prefix)
                        Spacer(Modifier.width(12.dp))
                        Text(file.name, color = if (isSelected) Color.Cyan else Color.White)
                    }
                }
            }
        }
    }

    showEntries?.let { (zipFile, entries) ->
        AlertDialog(
            onDismissRequest = { showEntries = null },
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
                    showEntries = null
                    refresh()
                }) { Text("فك الضغط") }
            },
            dismissButton = {
                TextButton(onClick = { showEntries = null }) { Text("إلغاء") }
            }
        )
    }
}
