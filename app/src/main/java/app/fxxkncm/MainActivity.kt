package app.fxxkncm

import android.app.TimePickerDialog
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Output
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FxxkNcmApp() }
    }
}

private data class QueueItem(
    val uri: Uri,
    val name: String,
    val status: String = "等待转换",
    val detail: String? = null
)

private enum class ThemeMode(val title: String) {
    SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色"), SCHEDULED("定时切换")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FxxkNcmApp() {
    val context = LocalContext.current
    val resolver = context.contentResolver
    val prefs = remember { context.getSharedPreferences("appearance", 0) }
    val scope = rememberCoroutineScope()
    val queue = remember { mutableStateListOf<QueueItem>() }
    var inputTree by remember { mutableStateOf<Uri?>(null) }
    var outputTree by remember { mutableStateOf<Uri?>(null) }
    var isConverting by remember { mutableStateOf(false) }
    var currentName by remember { mutableStateOf("") }
    var completed by remember { mutableStateOf(0) }
    var total by remember { mutableStateOf(0) }
    var statusMessage by remember { mutableStateOf("选择输入目录，应用只会处理其中的 .ncm 文件。") }
    var showSettings by remember { mutableStateOf(false) }
    var themeMode by remember { mutableStateOf(ThemeMode.valueOf(prefs.getString("mode", "SYSTEM") ?: "SYSTEM")) }
    var darkStart by remember { mutableStateOf(prefs.getInt("darkStart", 22 * 60)) }
    var lightStart by remember { mutableStateOf(prefs.getInt("lightStart", 7 * 60)) }
    var scheduledDark by remember { mutableStateOf(false) }
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()

    fun persistTheme() {
        prefs.edit().putString("mode", themeMode.name)
            .putInt("darkStart", darkStart).putInt("lightStart", lightStart).apply()
    }

    fun updateScheduledTheme() {
        val now = Calendar.getInstance()
        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        scheduledDark = if (darkStart == lightStart) false
        else if (darkStart > lightStart) minute >= darkStart || minute < lightStart
        else minute >= darkStart && minute < lightStart
    }

    LaunchedEffect(themeMode, darkStart, lightStart, systemDark) {
        while (true) {
            updateScheduledTheme()
            delay(60_000)
        }
    }
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SCHEDULED -> scheduledDark
    }
    val progress by animateFloatAsState(
        targetValue = if (total == 0) 0f else completed.toFloat() / total,
        label = "conversion-progress"
    )

    val inputPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            inputTree = uri
            queue.clear()
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            scope.launch {
                statusMessage = "正在扫描目录中的 NCM 文件…"
                try {
                    val found = withContext(Dispatchers.IO) { listNcmFiles(resolver, uri) }
                    queue.addAll(found)
                    statusMessage = if (found.isEmpty()) "该目录下没有 .ncm 文件；其他文件已自动跳过。"
                        else "扫描完成：找到 ${found.size} 个 NCM 文件，其他文件未加入队列。"
                } catch (e: Exception) {
                    statusMessage = "扫描目录失败：${e.message ?: "请重新选择目录"}"
                }
            }
        }
    }
    val outputPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            outputTree = uri
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            statusMessage = "输出目录已设置。"
        }
    }

    MaterialTheme(
        colorScheme = if (darkTheme) androidx.compose.material3.darkColorScheme(
            primary = Color(0xFFB7C5FF), secondary = Color(0xFF80D7C7)
        ) else androidx.compose.material3.lightColorScheme(
            primary = Color(0xFF465DCC), secondary = Color(0xFF006B5F),
            surface = Color(0xFFF8F8FC)
        ),
        shapes = androidx.compose.material3.Shapes(
            extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(32.dp)
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Fxxk-NCM", fontWeight = FontWeight.Bold)
                            Text("音频转换工作台", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    actions = {
                        androidx.compose.material3.IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Rounded.Settings, contentDescription = "设置")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            }) { insets ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(insets),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 18.dp, end = 18.dp, top = 8.dp, bottom = 28.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        HeroCard(queue.size, outputTree != null, progress, isConverting)
                    }
                    item { SectionTitle("转换设置", "输入目录仅扫描当前层级的 .ncm 文件") }
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            shape = RoundedCornerShape(24.dp)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.FolderOpen, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.size(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("输入目录", fontWeight = FontWeight.SemiBold)
                                        Text(if (inputTree == null) "选择包含 NCM 文件的文件夹"
                                            else "已扫描 · ${queue.size} 个 NCM 文件",
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    OutlinedButton(onClick = { inputPicker.launch(null) }, enabled = !isConverting) {
                                        Text("选择目录")
                                    }
                                }
                                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Output, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.size(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("输出目录", fontWeight = FontWeight.SemiBold)
                                        Text(if (outputTree == null) "未设置保存位置" else "已选择保存位置",
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    OutlinedButton(onClick = { outputPicker.launch(null) }, enabled = !isConverting) {
                                        Text("选择目录")
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("任务队列", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("${queue.count { it.status == "已完成" }} 已完成 · ${queue.count { it.status == "失败" }} 失败",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (queue.isNotEmpty()) OutlinedButton(
                                onClick = { if (!isConverting) queue.clear() }, enabled = !isConverting
                            ) { Text("清空") }
                        }
                    }
                    if (queue.isEmpty()) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                                Column(Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 18.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Rounded.AudioFile, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.secondary)
                                    Spacer(Modifier.height(10.dp))
                                    Text("这里还很安静", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text("选择目录后，符合条件的 NCM 文件会自动加入队列。",
                                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else {
                        itemsIndexed(queue, key = { _, item -> item.uri.toString() }) { index, item ->
                            androidx.compose.animation.AnimatedVisibility(
                                visible = true, enter = fadeIn() + slideInVertically { it / 8 },
                                exit = fadeOut() + slideOutVertically { it / 8 }
                            ) { QueueRow(item, index + 1) }
                        }
                    }
                    item {
                        AnimatedVisibility(visible = statusMessage.isNotBlank(), enter = fadeIn(), exit = fadeOut()) {
                            Text(statusMessage, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.animateContentSize())
                        }
                    }
                    item {
                        Button(
                            onClick = {
                                val target = outputTree
                                if (queue.isEmpty()) statusMessage = "请先选择包含 NCM 文件的输入目录。"
                                else if (target == null) statusMessage = "请先选择输出目录。"
                                else if (!isConverting) scope.launch {
                                    isConverting = true
                                    completed = 0
                                    total = queue.size
                                    var successes = 0
                                    for (index in queue.indices) {
                                        val task = queue[index]
                                        currentName = task.name
                                        queue[index] = task.copy(status = "转换中", detail = null)
                                        try {
                                            val outputName = withContext(Dispatchers.IO) {
                                                convertOne(resolver, task.uri, target, task.name)
                                            }
                                            queue[index] = task.copy(status = "已完成", detail = outputName)
                                            successes++
                                        } catch (e: Exception) {
                                            queue[index] = task.copy(status = "失败", detail = e.message ?: "未知错误")
                                        }
                                        completed = index + 1
                                    }
                                    isConverting = false
                                    statusMessage = "转换结束：成功 $successes / ${queue.size}。"
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(58.dp),
                            enabled = !isConverting
                        ) {
                            if (isConverting) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.size(10.dp))
                                AnimatedContent(currentName, label = "current-file") { Text("正在转换：$it", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            } else {
                                Icon(Icons.Rounded.PlayArrow, null)
                                Spacer(Modifier.size(8.dp))
                                Text("开始转换", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                    item {
                        Text("所有文件均在本机处理；目录扫描只筛选 .ncm，不会修改或删除其他文件。",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("设置") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("外观模式", style = MaterialTheme.typography.titleSmall)
                    ThemeMode.values().forEach { mode ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = themeMode == mode, onClick = {
                                themeMode = mode
                                persistTheme()
                            })
                            Text(mode.title, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                    if (themeMode == ThemeMode.SCHEDULED) {
                        Divider(Modifier.padding(vertical = 8.dp))
                        Text("深色模式时段", style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = {
                            TimePickerDialog(context, { _, hour, minute ->
                                darkStart = hour * 60 + minute
                                persistTheme()
                            }, darkStart / 60, darkStart % 60, true).show()
                        }) { Text("切换为深色：${formatTime(darkStart)}") }
                        TextButton(onClick = {
                            TimePickerDialog(context, { _, hour, minute ->
                                lightStart = hour * 60 + minute
                                persistTheme()
                            }, lightStart / 60, lightStart % 60, true).show()
                        }) { Text("切换为浅色：${formatTime(lightStart)}") }
                        Text("跨午夜时段也可用，例如 22:00 深色、07:00 浅色。",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("完成") } }
        )
    }
}

private fun formatTime(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

private fun listNcmFiles(resolver: ContentResolver, treeUri: Uri): List<QueueItem> {
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
        treeUri, DocumentsContract.getTreeDocumentId(treeUri)
    )
    val result = mutableListOf<QueueItem>()
    resolver.query(childrenUri, arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            val idColumn = cursor.getColumnIndex(Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndex(Document.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                if (idColumn < 0 || nameColumn < 0) continue
                val id = cursor.getString(idColumn) ?: continue
                val name = cursor.getString(nameColumn) ?: continue
                if (name.endsWith(".ncm", ignoreCase = true)) {
                    result += QueueItem(DocumentsContract.buildDocumentUriUsingTree(treeUri, id), name)
                }
            }
        } ?: throw IOException("无法读取所选目录，请尝试选择其他文件夹")
    return result.sortedBy { it.name.lowercase() }
}

@Composable
private fun HeroCard(fileCount: Int, outputReady: Boolean, progress: Float, isConverting: Boolean) {
    Card(shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Box(Modifier.fillMaxWidth().background(Brush.linearGradient(
            listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer)
        )).padding(22.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text("把音乐还给你", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("NCM → MP3 / FLAC", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text("本地处理 · 目录扫描 · 批量转换", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill("$fileCount 个文件")
                    StatusPill(if (outputReady) "输出已就绪" else "等待设置目录")
                }
                AnimatedVisibility(visible = isConverting, enter = fadeIn() + slideInVertically(), exit = fadeOut()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        Text("${(progress * 100).toInt()}% 完成", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(text: String) {
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), shape = CircleShape) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun QueueRow(item: QueueItem, index: Int) {
    Card(shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(item.detail ?: item.status, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.status == "失败") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.size(8.dp))
            when (item.status) {
                "已完成" -> Icon(Icons.Rounded.CheckCircle, "已完成", tint = MaterialTheme.colorScheme.primary)
                "失败" -> Icon(Icons.Rounded.Warning, "失败", tint = MaterialTheme.colorScheme.error)
                "转换中" -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else -> Text(index.toString(), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun convertOne(resolver: ContentResolver, inputUri: Uri, treeUri: Uri, originalName: String): String {
    val safeBase = originalName.substringBeforeLast('.', originalName).ifBlank { "audio" }
    val temporaryName = "$safeBase.ncm-converting"
    val parentId = DocumentsContract.getTreeDocumentId(treeUri)
    val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentId)
    val created = DocumentsContract.createDocument(resolver, parentUri, "application/octet-stream", temporaryName)
        ?: throw IOException("无法在所选目录创建输出文件")
    try {
        val input = resolver.openInputStream(inputUri) ?: throw IOException("无法读取输入文件")
        val output = resolver.openOutputStream(created, "wt") ?: throw IOException("无法写入输出文件")
        val result = input.use { src -> output.use { dst -> NcmDecoder.decode(src, dst) } }
        val finalName = "$safeBase.${result.extension}"
        val renamed = runCatching { DocumentsContract.renameDocument(resolver, created, finalName) }.getOrNull()
        if (renamed != null) return finalName
        return "$temporaryName（内容格式：${result.extension}；文件管理器中可重命名）"
    } catch (e: Exception) {
        runCatching { DocumentsContract.deleteDocument(resolver, created) }
        throw e
    }
}
