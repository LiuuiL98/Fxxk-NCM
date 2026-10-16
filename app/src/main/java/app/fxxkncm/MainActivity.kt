package app.fxxkncm

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

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
    val error: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FxxkNcmApp() {
    val context = LocalContext.current
    val resolver = context.contentResolver
    val scope = rememberCoroutineScope()
    val queue = remember { mutableStateListOf<QueueItem>() }
    var outputTree by remember { mutableStateOf<Uri?>(null) }
    var isConverting by remember { mutableStateOf(false) }
    var currentName by remember { mutableStateOf("") }
    var completed by remember { mutableStateOf(0) }
    var total by remember { mutableStateOf(0) }
    var darkTheme by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("选择 NCM 文件，指定输出目录，然后开始转换。") }
    val progress by animateFloatAsState(
        targetValue = if (total == 0) 0f else completed.toFloat() / total,
        label = "conversion-progress"
    )

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val name = queryDisplayName(resolver, uri) ?: "unknown.ncm"
            if (queue.none { it.uri == uri }) queue.add(QueueItem(uri, name))
        }
        if (uris.isNotEmpty()) statusMessage = "已添加 ${uris.size} 个文件。"
    }
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
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
            primary = androidx.compose.ui.graphics.Color(0xFFB7C5FF),
            secondary = androidx.compose.ui.graphics.Color(0xFF80D7C7)
        ) else androidx.compose.material3.lightColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF465DCC),
            secondary = androidx.compose.ui.graphics.Color(0xFF006B5F),
            surface = androidx.compose.ui.graphics.Color(0xFFF8F8FC)
        ),
        shapes = androidx.compose.material3.Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(32.dp)
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text("Fxxk-NCM", fontWeight = FontWeight.Bold)
                                Text("音频转换工作台", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        actions = {
                            Icon(Icons.Rounded.Settings, contentDescription = null,
                                modifier = Modifier.padding(end = 16.dp))
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                    )
                }
            ) { insets ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(insets),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        HeroCard(
                            fileCount = queue.size,
                            outputReady = outputTree != null,
                            progress = progress,
                            isConverting = isConverting
                        )
                    }
                    item {
                        SectionTitle("转换设置", "选择输入文件和保存位置")
                    }
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.FolderOpen, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.size(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("输入文件", fontWeight = FontWeight.SemiBold)
                                        Text(if (queue.isEmpty()) "尚未选择 .ncm 文件" else "已选择 ${queue.size} 个文件",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    OutlinedButton(onClick = { filePicker.launch(arrayOf("*/*")) }, enabled = !isConverting) {
                                        Text("选择文件")
                                    }
                                }
                                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Output, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.size(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("输出目录", fontWeight = FontWeight.SemiBold)
                                        Text(if (outputTree == null) "未设置输出位置" else "已选择自定义目录",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    OutlinedButton(onClick = { folderPicker.launch(null) }, enabled = !isConverting) {
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
                            if (queue.isNotEmpty()) {
                                OutlinedButton(onClick = { if (!isConverting) queue.clear() }, enabled = !isConverting) {
                                    Text("清空")
                                }
                            }
                        }
                    }
                    if (queue.isEmpty()) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                                Column(Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 18.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Rounded.AudioFile, contentDescription = null, modifier = Modifier.size(42.dp),
                                        tint = MaterialTheme.colorScheme.secondary)
                                    Spacer(Modifier.height(10.dp))
                                    Text("这里还很安静", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text("添加 NCM 文件后，转换任务会显示在这里。",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else {
                        itemsIndexed(queue, key = { _, item -> item.uri.toString() }) { index, item ->
                            QueueRow(item = item, index = index + 1)
                        }
                    }
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("外观", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                    Text("深色主题", style = MaterialTheme.typography.bodyMedium)
                                    Spacer(Modifier.size(8.dp))
                                    Switch(checked = darkTheme, onCheckedChange = { darkTheme = it })
                                }
                                Text("界面采用 Material 3 组件与自适应布局；后续会继续打磨动效与主题选项。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    item {
                        AnimatedVisibility(visible = statusMessage.isNotBlank()) {
                            Text(statusMessage, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    item {
                        Button(
                            onClick = {
                                val target = outputTree
                                if (queue.isEmpty()) {
                                    statusMessage = "请先添加至少一个 NCM 文件。"
                                } else if (target == null) {
                                    statusMessage = "请先选择输出目录。"
                                } else if (!isConverting) {
                                    scope.launch {
                                        isConverting = true
                                        completed = 0
                                        total = queue.size
                                        var successes = 0
                                        for (index in queue.indices) {
                                            val task = queue[index]
                                            currentName = task.name
                                            queue[index] = task.copy(status = "转换中", error = null)
                                            try {
                                                val outputName = withContext(Dispatchers.IO) {
                                                    convertOne(resolver, task.uri, target, task.name)
                                                }
                                                queue[index] = task.copy(status = "已完成", error = outputName)
                                                successes++
                                            } catch (e: Exception) {
                                                queue[index] = task.copy(status = "失败", error = e.message ?: "未知错误")
                                            }
                                            completed = index + 1
                                        }
                                        isConverting = false
                                        statusMessage = "转换结束：成功 $successes / ${queue.size}。"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(58.dp),
                            enabled = !isConverting
                        ) {
                            if (isConverting) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.size(10.dp))
                                Text("正在转换：$currentName")
                            } else {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text("开始转换", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                    item {
                        Text("提示：首版解码器需要用真实 NCM 文件验证兼容性。若文件转换失败，请保留错误提示用于后续排查。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(fileCount: Int, outputReady: Boolean, progress: Float, isConverting: Boolean) {
    Card(
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().background(
                Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer)
                )
            ).padding(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.GraphicEq, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text("把音乐还给你", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("NCM → MP3 / FLAC", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text("本地处理 · 自选目录 · 批量转换", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill("$fileCount 个文件")
                    StatusPill(if (outputReady) "输出已就绪" else "等待设置目录")
                }
                AnimatedVisibility(visible = isConverting) {
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
        Text(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium)
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
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(item.error ?: item.status, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.status == "失败") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.size(8.dp))
            when (item.status) {
                "已完成" -> Icon(Icons.Rounded.CheckCircle, contentDescription = "已完成", tint = MaterialTheme.colorScheme.primary)
                "失败" -> Icon(Icons.Rounded.Warning, contentDescription = "失败", tint = MaterialTheme.colorScheme.error)
                "转换中" -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else -> Text(index.toString(), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
    return resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}

private fun convertOne(resolver: ContentResolver, inputUri: Uri, treeUri: Uri, originalName: String): String {
    val safeBase = originalName.substringBeforeLast('.', originalName).ifBlank { "audio" }
    val initialName = "$safeBase.mp3"
    // OpenDocumentTree returns a tree URI, not a document URI. Convert it to
    // the actual selected directory document URI before calling createDocument.
    val parentDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
    val parentDocumentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocumentId)
    val created = DocumentsContract.createDocument(resolver, parentDocumentUri, "audio/mpeg", initialName)
        ?: throw IOException("无法在所选目录创建输出文件")
    try {
        val input = resolver.openInputStream(inputUri) ?: throw IOException("无法读取输入文件")
        val output = resolver.openOutputStream(created, "wt") ?: throw IOException("无法写入输出文件")
        val result = input.use { src -> output.use { dst -> NcmDecoder.decode(src, dst) } }
        if (result.extension == "flac" && !initialName.endsWith(".flac", true)) {
            // SAF providers do not reliably support renaming every document; keep output safe and clear.
            val renamed = runCatching {
                DocumentsContract.renameDocument(resolver, created, "$safeBase.flac")
            }.getOrNull()
            if (renamed != null) return "$safeBase.flac"
            return "$initialName (实际内容为 FLAC，建议重命名为 .flac)"
        }
        return initialName
    } catch (e: Exception) {
        runCatching { DocumentsContract.deleteDocument(resolver, created) }
        throw e
    }
}
