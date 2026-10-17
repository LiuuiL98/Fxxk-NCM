package app.fxxkncm

import android.app.TimePickerDialog
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBar
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Home
import android.os.Build
import android.view.View
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.rounded.ArrowBack
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text as MaterialText
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
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
import java.util.Locale

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
private enum class AppLanguage(val title: String) {
    SYSTEM("跟随系统 / System default"), ZH_CN("简体中文（中国）"),
    EN_US("English (United States)"), ZH_HK("繁體中文（香港）")
}
private val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.SYSTEM }
private fun resolveLanguage(mode: AppLanguage): String = when (mode) {
    AppLanguage.ZH_CN -> "zh"
    AppLanguage.EN_US -> "en"
    AppLanguage.ZH_HK -> "hk"
    AppLanguage.SYSTEM -> {
        val locale = Locale.getDefault()
        when {
            locale.language.equals("en", true) -> "en"
            locale.country.equals("HK", true) -> "hk"
            locale.language.equals("zh", true) && locale.script.equals("Hant", true) -> "hk"
            else -> "zh"
        }
    }
}
private val translations = mapOf(
    "设置" to listOf("设置", "Settings", "設定", "設定"),
    "音频转换工作台" to listOf("音频转换工作台", "Audio Converter", "音訊轉換工作台", "音訊轉換工作台"),
    "转换设置" to listOf("转换设置", "Conversion", "轉換設定", "轉換設定"),
    "输入目录" to listOf("输入目录", "Input folder", "輸入資料夾", "輸入資料夾"),
    "选择目录" to listOf("选择目录", "Choose folder", "選擇資料夾", "選擇資料夾"),
    "输出目录" to listOf("输出目录", "Output folder", "輸出資料夾", "輸出資料夾"),
    "任务队列" to listOf("任务队列", "Queue", "工作佇列", "工作佇列"),
    "清空" to listOf("清空", "Clear", "清除", "清除"),
    "这里还很安静" to listOf("这里还很安静", "Nothing here yet", "這裡還沒有內容", "這裡還沒有內容"),
    "开始转换" to listOf("开始转换", "Start conversion", "開始轉換", "開始轉換"),
    "把音乐还给你" to listOf("把音乐还给你", "Your music, restored", "把音樂還給你", "把音樂還給你"),
    "本地处理 · 目录扫描 · 批量转换" to listOf("本地处理 · 目录扫描 · 批量转换", "Local processing · Folder scan · Batch conversion", "本機處理 · 資料夾掃描 · 批次轉換", "本機處理 · 資料夾掃描 · 批次轉換"),
    "输出已就绪" to listOf("输出已就绪", "Output ready", "輸出已就緒", "輸出已就緒"),
    "等待设置目录" to listOf("等待设置目录", "Choose output folder", "等待設定資料夾", "等待設定資料夾"),
    "外观模式" to listOf("外观模式", "Appearance", "外觀模式", "外觀模式"),
    "跟随系统" to listOf("跟随系统", "Follow system", "跟隨系統", "跟隨系統"),
    "浅色" to listOf("浅色", "Light", "淺色", "淺色"),
    "深色" to listOf("深色", "Dark", "深色", "深色"),
    "定时切换" to listOf("定时切换", "Scheduled", "定時切換", "定時切換"),
    "深色模式时段" to listOf("深色模式时段", "Dark mode schedule", "深色模式時段", "深色模式時段"),
    "语言" to listOf("语言", "Language", "語言", "語言"),
    "自动跟随系统语言" to listOf("自动跟随系统语言", "Follow system language automatically", "自動跟隨系統語言", "自動跟隨系統語言"),
    "选择包含 NCM 文件的文件夹" to listOf("选择包含 NCM 文件的文件夹", "Choose a folder containing NCM files", "選擇包含 NCM 檔案的資料夾", "選擇包含 NCM 檔案的資料夾"),
    "未设置保存位置" to listOf("未设置保存位置", "No output location selected", "尚未設定儲存位置", "尚未設定儲存位置"),
    "已选择保存位置" to listOf("已选择保存位置", "Output location selected", "已選擇儲存位置", "已選擇儲存位置"),
    "首次请选择内部存储根目录；授权后每次启动自动扫描其中的 .ncm 文件。" to listOf("选择目录后，符合条件的 NCM 文件会自动加入队列。", "Choose a folder to add matching NCM files to the queue.", "選擇資料夾後，符合條件的 NCM 檔案會自動加入佇列。", "選擇資料夾後，符合條件的 NCM 檔案會自動加入佇列。"),
    "完成" to listOf("完成", "Done", "完成", "完成"),
    "返回" to listOf("返回", "Back", "返回", "返回"),
    "外观与语言" to listOf("外观与语言", "Appearance & language", "外觀與語言", "外觀與語言"),
    "根据你的使用习惯自定义应用外观和显示语言。" to listOf("根据你的使用习惯自定义应用外观和显示语言。", "Customize the app's appearance and display language.", "依照使用習慣自訂應用程式外觀與顯示語言。", "依照使用習慣自訂應用程式外觀與顯示語言。"),
    "颜色与主题" to listOf("颜色与主题", "Color & theme", "色彩與主題", "色彩與主題"),
    "浅色、深色或跟随系统自动切换" to listOf("浅色、深色或跟随系统自动切换", "Light, dark, or follow system automatically", "淺色、深色或自動跟隨系統", "淺色、深色或自動跟隨系統"),
    "自动跟随系统语言，也可在此手动指定" to listOf("自动跟随系统语言，也可在此手动指定", "Follow the system language or choose one manually", "自動跟隨系統語言，也可在此手動指定", "自動跟隨系統語言，也可在此手動指定"),
    "Fxxk-NCM · 仅在本机处理音频文件" to listOf("Fxxk-NCM · 仅在本机处理音频文件", "Fxxk-NCM · Audio files are processed locally", "Fxxk-NCM · 僅在本機處理音訊檔案", "Fxxk-NCM · 僅在本機處理音訊檔案"),
    "等待转换" to listOf("等待转换", "Waiting", "等待轉換", "等待轉換"),
    "转换中" to listOf("转换中", "Converting", "轉換中", "轉換中"),
    "已完成" to listOf("已完成", "Completed", "已完成", "已完成"),
    "失败" to listOf("失败", "Failed", "失敗", "失敗"),
    "选择输入目录，应用只会处理其中的 .ncm 文件。" to listOf("选择输入目录，应用只会处理其中的 .ncm 文件。", "Choose an input folder. Only .ncm files will be processed.", "選擇輸入資料夾，應用程式只會處理其中的 .ncm 檔案。", "選擇輸入資料夾，應用程式只會處理其中的 .ncm 檔案。"),
    "正在扫描目录中的 NCM 文件…" to listOf("正在扫描目录中的 NCM 文件…", "Scanning for NCM files…", "正在掃描資料夾中的 NCM 檔案…", "正在掃描資料夾中的 NCM 檔案…"),
    "该目录下没有 .ncm 文件；其他文件已自动跳过。" to listOf("该目录下没有 .ncm 文件；其他文件已自动跳过。", "No .ncm files found. Other files were skipped.", "此資料夾沒有 .ncm 檔案；其他檔案已自動略過。", "此資料夾沒有 .ncm 檔案；其他檔案已自動略過。"),
    "扫描完成：找到" to listOf("扫描完成：找到", "Scan complete: found", "掃描完成：找到", "掃描完成：找到"),
    "输出目录已设置。" to listOf("输出目录已设置。", "Output folder set.", "輸出資料夾已設定。", "輸出資料夾已設定。"),
    "请先选择包含 NCM 文件的输入目录。" to listOf("请先选择包含 NCM 文件的输入目录。", "Choose an input folder containing NCM files first.", "請先選擇包含 NCM 檔案的輸入資料夾。", "請先選擇包含 NCM 檔案的輸入資料夾。"),
    "请先选择输出目录。" to listOf("请先选择输出目录。", "Choose an output folder first.", "請先選擇輸出資料夾。", "請先選擇輸出資料夾。"),
    "所有文件均在本机处理；目录扫描只筛选 .ncm，不会修改或删除其他文件。" to listOf("所有文件均在本机处理；目录扫描只筛选 .ncm，不会修改或删除其他文件。", "Files are processed locally. Only .ncm files are selected; other files are never changed or deleted.", "所有檔案均在本機處理；只會篩選 .ncm，不會修改或刪除其他檔案。", "所有檔案均在本機處理；只會篩選 .ncm，不會修改或刪除其他檔案。")
)
@Composable
private fun Text(text: String, modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = androidx.compose.material3.LocalTextStyle.current,
    color: Color = Color.Unspecified, fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE, overflow: TextOverflow = TextOverflow.Clip) {
    val index = when (resolveLanguage(LocalAppLanguage.current)) { "en" -> 1; "hk" -> 2; else -> 0 }
    val translated = translations[text]?.get(index) ?: when {
        text.startsWith("正在转换：") && index == 1 -> "Converting: " + text.substringAfter("：")
        text.startsWith("正在转换：") && index in 2..3 -> "正在轉換：" + text.substringAfter("：")
        text.startsWith("扫描目录失败：") && index == 1 -> "Folder scan failed: " + text.substringAfter("：")
        text.startsWith("扫描目录失败：") && index in 2..3 -> "掃描資料夾失敗：" + text.substringAfter("：")
        text.startsWith("已扫描 · ") && index == 1 -> "Scanned · " + text.substringAfter("· ").replace(" 个 NCM 文件", " NCM files")
        text.startsWith("已扫描 · ") && index in 2..3 -> "已掃描 · " + text.substringAfter("· ").replace(" 个 NCM 文件", " 個 NCM 檔案")
        text.startsWith("扫描完成：找到 ") && index == 1 -> text.replace("扫描完成：找到 ", "Scan complete: found ").replace(" 个 NCM 文件，其他文件未加入队列。", " NCM files. Other files were skipped.")
        text.startsWith("扫描完成：找到 ") && index in 2..3 -> text.replace("扫描完成：找到 ", "掃描完成：找到 ").replace(" 个 NCM 文件，其他文件未加入队列。", " 個 NCM 檔案，其他檔案未加入佇列。")
        text.startsWith("转换结束：成功 ") && index == 1 -> text.replace("转换结束：成功 ", "Conversion finished: ").replace("。", " succeeded.")
        text.startsWith("转换结束：成功 ") && index in 2..3 -> text.replace("转换结束：成功 ", "轉換完成：成功 ")
        text.matches(Regex("\\d+ 个文件")) && index == 1 -> text.replace(" 个文件", " files")
        text.matches(Regex("\\d+ 个文件")) && index in 2..3 -> text.replace(" 个文件", " 個檔案")
        text.endsWith("% 完成") && index == 1 -> text.replace("% 完成", "% complete")
        text.startsWith("切换为深色：") && index == 1 -> "Switch to dark: " + text.substringAfter("：")
        text.startsWith("切换为深色：") && index in 2..3 -> "切換為深色：" + text.substringAfter("：")
        text.startsWith("切换为浅色：") && index == 1 -> "Switch to light: " + text.substringAfter("：")
        text.startsWith("切换为浅色：") && index in 2..3 -> "切換為淺色：" + text.substringAfter("：")
        else -> text
    }
    MaterialText(translated, modifier = modifier, style = style, color = color,
        fontWeight = fontWeight, maxLines = maxLines, overflow = overflow)
}

private enum class MainTab(val title: String) { HOME("首页"), FILES("文件"), TASKS("任务"), SETTINGS("设置") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FxxkNcmApp() {
    val context = LocalContext.current
    val resolver = context.contentResolver
    val prefs = remember { context.getSharedPreferences("appearance", 0) }
    val pathPrefs = remember { context.getSharedPreferences("selected_paths", 0) }
    val scope = rememberCoroutineScope()
    val queue = remember { mutableStateListOf<QueueItem>() }
    var inputTree by remember { mutableStateOf(pathPrefs.getString("input_tree", null)?.let(Uri::parse)) }
    var outputTree by remember { mutableStateOf(pathPrefs.getString("output_tree", null)?.let(Uri::parse)) }
    var isConverting by remember { mutableStateOf(false) }
    var currentName by remember { mutableStateOf("") }
    var completed by remember { mutableStateOf(0) }
    var total by remember { mutableStateOf(0) }
    var successCount by remember { mutableStateOf(0) }
    var failureCount by remember { mutableStateOf(0) }
    var durationSeconds by remember { mutableStateOf(0L) }
    var statusMessage by remember { mutableStateOf("首次请选择输入目录；授权后可在每次启动时自动扫描其中的 .ncm 文件。") }
    var selectedTab by remember { mutableStateOf(MainTab.HOME) }
    var selectedUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    var searchQuery by remember { mutableStateOf("") }
    var onlySelected by remember { mutableStateOf(false) }
    var scanRequest by remember { mutableStateOf(0) }
    var consumedScanRequest by remember { mutableStateOf(0) }
    var autoScan by remember { mutableStateOf(prefs.getBoolean("auto_scan", true)) }
    var dynamicColor by remember { mutableStateOf(prefs.getBoolean("dynamic_color", true)) }
    var languageMode by remember { mutableStateOf(runCatching { AppLanguage.valueOf(prefs.getString("language", "SYSTEM") ?: "SYSTEM") }.getOrDefault(AppLanguage.SYSTEM)) }
    var themeMode by remember { mutableStateOf(runCatching { ThemeMode.valueOf(prefs.getString("mode", "SYSTEM") ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM)) }
    var darkStart by remember { mutableStateOf(prefs.getInt("darkStart", 22 * 60)) }
    var lightStart by remember { mutableStateOf(prefs.getInt("lightStart", 7 * 60)) }
    var scheduledDark by remember { mutableStateOf(false) }
    var resultShown by remember { mutableStateOf(false) }
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()

    fun persistTheme() {
        prefs.edit().putString("mode", themeMode.name).putInt("darkStart", darkStart).putInt("lightStart", lightStart).apply()
    }
    fun updateScheduledTheme() {
        val now = Calendar.getInstance()
        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        scheduledDark = if (darkStart == lightStart) false
            else if (darkStart > lightStart) minute >= darkStart || minute < lightStart
            else minute >= darkStart && minute < lightStart
    }
    LaunchedEffect(themeMode, darkStart, lightStart) {
        if (themeMode == ThemeMode.SCHEDULED) while (true) { updateScheduledTheme(); delay(60_000) }
        else scheduledDark = false
    }
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SCHEDULED -> scheduledDark
    }
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) androidx.compose.material3.dynamicDarkColorScheme(context) else androidx.compose.material3.dynamicLightColorScheme(context)
        darkTheme -> androidx.compose.material3.darkColorScheme(primary = Color(0xFFB7C5FF), secondary = Color(0xFF80D7C7))
        else -> androidx.compose.material3.lightColorScheme(
            primary = Color(0xFF245CEB), secondary = Color(0xFF006B5F),
            surface = Color(0xFFFDFBFF), background = Color(0xFFF5F6FF),
            primaryContainer = Color(0xFFDCE5FF), secondaryContainer = Color(0xFFD0F2E9)
        )
    }
    SideEffect {
        val window = (context as? ComponentActivity)?.window
        if (window != null) {
            window.statusBarColor = scheme.background.toArgb()
            val flags = window.decorView.systemUiVisibility
            window.decorView.systemUiVisibility = if (darkTheme) flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                else flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
    }

    val inputPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val granted = resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
            if (!granted) throw SecurityException("系统未保留输入目录读取授权")
            pathPrefs.edit().putString("input_tree", uri.toString()).apply()
            inputTree = uri
            scanRequest++
        } catch (e: Exception) { statusMessage = "无法记住输入目录授权：" + (e.message ?: "授权失败") }
    }
    val outputPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            val granted = resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission && it.isWritePermission }
            if (!granted) throw SecurityException("系统未保留输出目录读写授权")
            pathPrefs.edit().putString("output_tree", uri.toString()).apply()
            outputTree = uri
            statusMessage = "输出目录已设置。"
        } catch (e: Exception) { statusMessage = "无法记住输出目录授权：" + (e.message ?: "授权失败") }
    }

    LaunchedEffect(inputTree, autoScan, scanRequest) {
        val source = inputTree ?: return@LaunchedEffect
        if (!autoScan && scanRequest == consumedScanRequest) return@LaunchedEffect
        consumedScanRequest = scanRequest
        val granted = resolver.persistedUriPermissions.any { it.uri == source && it.isReadPermission }
        if (!granted) {
            inputTree = null; pathPrefs.edit().remove("input_tree").apply()
            queue.clear(); selectedUris = emptySet()
            statusMessage = "输入目录授权已失效，请重新选择输入目录。"
            return@LaunchedEffect
        }
        statusMessage = "正在扫描目录中的 NCM 文件…"
        try {
            val found = withContext(Dispatchers.IO) { listNcmFiles(resolver, source) }
            queue.clear(); queue.addAll(found)
            selectedUris = found.map { it.uri.toString() }.toSet()
            statusMessage = if (found.isEmpty()) "该目录下没有 .ncm 文件；其他文件已自动跳过。"
                else "扫描完成：找到 " + found.size + " 个 NCM 文件，已加入队列。"
        } catch (e: Exception) { statusMessage = "扫描目录失败：" + (e.message ?: "请重新选择目录") }
    }
    LaunchedEffect(outputTree) {
        val target = outputTree ?: return@LaunchedEffect
        val granted = resolver.persistedUriPermissions.any { it.uri == target && it.isReadPermission && it.isWritePermission }
        if (!granted) {
            outputTree = null; pathPrefs.edit().remove("output_tree").apply()
            statusMessage = "输出目录授权已失效，请重新选择输出目录。"
        }
    }

    fun startConversion() {
        val target = outputTree
        val todo = queue.filter { it.uri.toString() in selectedUris }
        if (queue.isEmpty()) { statusMessage = "请先选择输入目录并扫描 NCM 文件。"; return }
        if (todo.isEmpty()) { statusMessage = "请至少选择一个要转换的文件。"; selectedTab = MainTab.FILES; return }
        if (target == null) { statusMessage = "请先选择输出目录。"; selectedTab = MainTab.HOME; return }
        if (isConverting) return
        scope.launch {
            isConverting = true; resultShown = false; completed = 0; total = todo.size
            successCount = 0; failureCount = 0; durationSeconds = 0
            val startMillis = System.currentTimeMillis()
            selectedTab = MainTab.TASKS
            for (task in todo) {
                currentName = task.name
                val index = queue.indexOfFirst { it.uri == task.uri }
                if (index < 0) continue
                queue[index] = task.copy(status = "转换中", detail = null)
                try {
                    val outputName = withContext(Dispatchers.IO) { convertOne(resolver, task.uri, target, task.name) }
                    queue[index] = task.copy(status = "已完成", detail = outputName); successCount++
                } catch (e: Exception) {
                    queue[index] = task.copy(status = "失败", detail = e.message ?: "未知错误"); failureCount++
                }
                completed++
                durationSeconds = ((System.currentTimeMillis() - startMillis) / 1000L).coerceAtLeast(0)
            }
            durationSeconds = ((System.currentTimeMillis() - startMillis) / 1000L).coerceAtLeast(0)
            isConverting = false; resultShown = true
            statusMessage = "转换结束：成功 " + successCount + " 个，失败 " + failureCount + " 个。"
        }
    }

    MaterialTheme(
        colorScheme = scheme,
        shapes = androidx.compose.material3.Shapes(
            extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(22.dp), large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(32.dp)
        )
    ) {
        CompositionLocalProvider(LocalAppLanguage provides languageMode) {
            androidx.activity.compose.BackHandler(enabled = selectedTab != MainTab.HOME) { selectedTab = MainTab.HOME }
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(when (selectedTab) {
                                    MainTab.HOME -> "Fxxk-NCM"
                                    MainTab.FILES -> "NCM 文件列表"
                                    MainTab.TASKS -> if (resultShown) "转换结果" else "转换任务"
                                    MainTab.SETTINGS -> "设置"
                                }, fontWeight = FontWeight.Bold)
                                if (selectedTab == MainTab.HOME) Text("NCM 音频转换工具",
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (selectedTab == MainTab.FILES) Text(queue.size.toString() + " 个文件 · 已选 " + selectedUris.size + " 个",
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        actions = {
                            if (selectedTab == MainTab.HOME) androidx.compose.material3.IconButton(
                                onClick = { scanRequest++ }, enabled = inputTree != null && !isConverting
                            ) { Icon(Icons.Rounded.Refresh, contentDescription = "重新扫描") }
                            if (selectedTab == MainTab.FILES && searchQuery.isNotEmpty()) androidx.compose.material3.IconButton(
                                onClick = { searchQuery = "" }
                            ) { Icon(Icons.Rounded.Close, contentDescription = "清除搜索") }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                    )
                },
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        MainTab.values().forEach { page ->
                            val icon = when (page) {
                                MainTab.HOME -> Icons.Rounded.Home
                                MainTab.FILES -> Icons.Rounded.LibraryMusic
                                MainTab.TASKS -> Icons.Rounded.PlaylistPlay
                                MainTab.SETTINGS -> Icons.Rounded.Settings
                            }
                            NavigationBarItem(
                                selected = selectedTab == page, onClick = { selectedTab = page },
                                icon = { Icon(icon, contentDescription = page.title) },
                                label = { Text(page.title) }, alwaysShowLabel = true
                            )
                        }
                    }
                }
            ) { insets ->
                val body = Modifier.fillMaxSize().padding(insets)
                when (selectedTab) {
                    MainTab.HOME -> HomeScreen(
                        modifier = body, fileCount = queue.size, selectedCount = selectedUris.size,
                        hasInput = inputTree != null, hasOutput = outputTree != null,
                        statusMessage = statusMessage, isConverting = isConverting,
                        onPickInput = { inputPicker.launch(inputTree) }, onPickOutput = { outputPicker.launch(outputTree) },
                        onScan = { scanRequest++ }, onStart = { startConversion() },
                        onShowFiles = { selectedTab = MainTab.FILES }
                    )
                    MainTab.FILES -> FilesScreen(
                        modifier = body, items = queue, selectedUris = selectedUris, searchQuery = searchQuery,
                        onlySelected = onlySelected, isConverting = isConverting,
                        onSearchChange = { searchQuery = it }, onOnlySelectedChange = { onlySelected = it },
                        onToggle = { item ->
                            val id = item.uri.toString()
                            selectedUris = if (id in selectedUris) selectedUris - id else selectedUris + id
                        },
                        onSelectAll = { selectedUris = queue.map { it.uri.toString() }.toSet() },
                        onClearSelection = { selectedUris = emptySet() },
                        onStart = { startConversion() }
                    )
                    MainTab.TASKS -> TasksScreen(
                        modifier = body, items = queue, isConverting = isConverting, resultShown = resultShown,
                        currentName = currentName, completed = completed, total = total,
                        successes = successCount, failures = failureCount, elapsedSeconds = durationSeconds,
                        outputReady = outputTree != null, onBrowse = { selectedTab = MainTab.FILES },
                        onRetry = {
                            selectedUris = queue.filter { it.status == "失败" }.map { it.uri.toString() }.toSet()
                            startConversion()
                        }
                    )
                    MainTab.SETTINGS -> SettingsScreen(
                        modifier = body, themeMode = themeMode, languageMode = languageMode,
                        darkStart = darkStart, lightStart = lightStart, autoScan = autoScan, dynamicColor = dynamicColor,
                        onAutoScanChange = { value -> autoScan = value; prefs.edit().putBoolean("auto_scan", value).apply(); if (value) scanRequest++ },
                        onDynamicColorChange = { value -> dynamicColor = value; prefs.edit().putBoolean("dynamic_color", value).apply() },
                        onThemeChange = { themeMode = it; persistTheme() },
                        onLanguageChange = { languageMode = it; prefs.edit().putString("language", it.name).apply() },
                        onDarkStartChange = { darkStart = it; persistTheme() },
                        onLightStartChange = { lightStart = it; persistTheme() }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier, fileCount: Int, selectedCount: Int, hasInput: Boolean, hasOutput: Boolean,
    statusMessage: String, isConverting: Boolean, onPickInput: () -> Unit, onPickOutput: () -> Unit,
    onScan: () -> Unit, onStart: () -> Unit, onShowFiles: () -> Unit
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 18.dp, top = 10.dp, end = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(30.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Box(Modifier.fillMaxWidth().background(Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer)
                )).padding(22.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(52.dp).clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                            }
                            Spacer(Modifier.size(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("把音乐还给你", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text("NCM → MP3 / FLAC", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        Text("本地处理 · 自动扫描 · 批量转换", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusPill(fileCount.toString() + " 个文件")
                            StatusPill(selectedCount.toString() + " 个已选")
                            StatusPill(if (hasOutput) "输出已就绪" else "等待设置输出")
                        }
                    }
                }
            }
        }
        item { SectionTitle("目录设置", "输入和输出位置独立保存，授权后下次继续使用") }
        item {
            FolderCard("输入目录", if (hasInput) "已保存目录授权，启动后可自动扫描" else "请选择包含 NCM 文件的目录",
                MaterialTheme.colorScheme.primaryContainer, "选择目录", onPickInput, true)
        }
        item {
            FolderCard("输出目录", if (hasOutput) "转换后的音频将保存到此目录" else "请先选择音频保存位置",
                MaterialTheme.colorScheme.secondaryContainer, "选择目录", onPickOutput, false)
        }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center) { Icon(Icons.Rounded.LibraryMusic, null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("扫描结果", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(statusMessage, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    androidx.compose.material3.IconButton(onClick = onScan, enabled = hasInput && !isConverting) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "重新扫描")
                    }
                }
                if (fileCount > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    TextButton(onClick = onShowFiles, modifier = Modifier.fillMaxWidth()) {
                        Text("查看文件列表（" + fileCount + "）")
                    }
                }
            }
        }
        item {
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(58.dp),
                enabled = !isConverting && selectedCount > 0 && hasOutput) {
                Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.size(8.dp))
                Text("开始转换（" + selectedCount + "）", style = MaterialTheme.typography.titleMedium)
            }
        }
        item {
            Text("所有文件均在本机处理；扫描只筛选 .ncm，不会修改或删除其他文件。",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FolderCard(title: String, subtitle: String, accent: Color, buttonText: String, onClick: () -> Unit, input: Boolean) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(accent), contentAlignment = Alignment.Center) {
                Icon(if (input) Icons.Rounded.FolderOpen else Icons.Rounded.Output, null,
                    tint = if (input) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
            }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.size(8.dp)); OutlinedButton(onClick = onClick) { Text(buttonText) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilesScreen(
    modifier: Modifier, items: List<QueueItem>, selectedUris: Set<String>, searchQuery: String,
    onlySelected: Boolean, isConverting: Boolean, onSearchChange: (String) -> Unit,
    onOnlySelectedChange: (Boolean) -> Unit, onToggle: (QueueItem) -> Unit,
    onSelectAll: () -> Unit, onClearSelection: () -> Unit, onStart: () -> Unit
) {
    val visibleItems = items.filter { it.name.contains(searchQuery, ignoreCase = true) && (!onlySelected || it.uri.toString() in selectedUris) }
    Column(modifier) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = searchQuery, onValueChange = onSearchChange, modifier = Modifier.fillMaxWidth(),
                singleLine = true, leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = { if (searchQuery.isNotEmpty()) androidx.compose.material3.IconButton(onClick = { onSearchChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = "清除搜索")
                } },
                placeholder = { Text("搜索文件名") }, shape = RoundedCornerShape(20.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !onlySelected, onClick = { onOnlySelectedChange(false) }, label = { Text("全部（" + items.size + "）") })
                FilterChip(selected = onlySelected, onClick = { onOnlySelectedChange(true) }, label = { Text("已选择（" + selectedUris.size + "）") })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("已选择 " + selectedUris.size + " 个", modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onSelectAll, enabled = !isConverting && items.isNotEmpty()) { Text("全选") }
                TextButton(onClick = onClearSelection, enabled = !isConverting && selectedUris.isNotEmpty()) { Text("清除") }
            }
        }
        if (visibleItems.isEmpty()) {
            EmptyState(Modifier.weight(1f).fillMaxWidth(),
                if (items.isEmpty()) "还没有发现 NCM 文件" else "没有匹配的文件",
                if (items.isEmpty()) "请先到首页选择输入目录并扫描。" else "试试其他关键词，或切换到“全部”。")
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(visibleItems, key = { _, item -> item.uri.toString() }) { _, item ->
                    val checked = item.uri.toString() in selectedUris
                    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(
                        containerColor = if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = checked, onCheckedChange = { onToggle(item) }, enabled = !isConverting)
                            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center) { Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.primary) }
                            Spacer(Modifier.size(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                Text(item.detail ?: item.status, style = MaterialTheme.typography.bodySmall,
                                    color = if (item.status == "失败") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp).height(52.dp),
                enabled = !isConverting && selectedUris.isNotEmpty()) {
                Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.size(8.dp))
                Text("开始转换（" + selectedUris.size + "）")
            }
        }
    }
}

@Composable
private fun TasksScreen(
    modifier: Modifier, items: List<QueueItem>, isConverting: Boolean, resultShown: Boolean, currentName: String,
    completed: Int, total: Int, successes: Int, failures: Int, elapsedSeconds: Long, outputReady: Boolean,
    onBrowse: () -> Unit, onRetry: () -> Unit
) {
    val progress = if (total == 0) 0f else completed.toFloat() / total
    LazyColumn(modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 18.dp, top = 12.dp, end = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (isConverting) {
            item {
                Card(shape = RoundedCornerShape(30.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(148.dp), strokeWidth = 10.dp)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text((progress * 100).toInt().toString() + "%", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                                Text(completed.toString() + " / " + total, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Text("正在转换", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(currentName, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        } else if (resultShown) {
            item {
                Card(shape = RoundedCornerShape(30.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(68.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(40.dp))
                        }
                        Text(if (failures == 0) "转换完成" else "转换已结束", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("成功转换 " + successes + " / " + total + " 个文件")
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Metric("成功", successes.toString(), MaterialTheme.colorScheme.primary)
                        Metric("失败", failures.toString(), MaterialTheme.colorScheme.error)
                        Metric("用时", formatDuration(elapsedSeconds), MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            item {
                Button(onClick = onBrowse, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Rounded.LibraryMusic, null); Spacer(Modifier.size(8.dp)); Text("返回文件列表")
                }
            }
            if (failures > 0) item {
                OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.size(8.dp)); Text("重试失败文件")
                }
            }
            item { Text(if (outputReady) "输出目录已保存，可在首页更改。" else "请先在首页设置输出目录。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            item { EmptyState(Modifier.fillMaxWidth().height(260.dp), "准备好转换了吗？", "从首页扫描文件，或到“文件”页选择要转换的歌曲。") }
            item {
                OutlinedButton(onClick = onBrowse, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Rounded.LibraryMusic, null); Spacer(Modifier.size(8.dp)); Text("浏览文件列表")
                }
            }
        }
        if (!isConverting && items.isNotEmpty()) {
            item { SectionTitle("任务记录", items.size.toString() + " 个文件 · " + items.count { it.status == "已完成" } + " 个完成 · " + items.count { it.status == "失败" } + " 个失败") }
            itemsIndexed(items.take(30), key = { _, item -> item.uri.toString() }) { index, item -> QueueRow(item, index + 1) }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = tint)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyState(modifier: Modifier, title: String, subtitle: String) {
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.AudioFile, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier, themeMode: ThemeMode, languageMode: AppLanguage, darkStart: Int, lightStart: Int,
    autoScan: Boolean, dynamicColor: Boolean, onAutoScanChange: (Boolean) -> Unit, onDynamicColorChange: (Boolean) -> Unit,
    onThemeChange: (ThemeMode) -> Unit, onLanguageChange: (AppLanguage) -> Unit,
    onDarkStartChange: (Int) -> Unit, onLightStartChange: (Int) -> Unit
) {
    val context = LocalContext.current
    LazyColumn(modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 18.dp, top = 12.dp, end = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("让转换流程和外观更符合你的习惯。", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SettingsGroup("目录与扫描", "目录授权会分别保存") {
                SettingSwitchRow(Icons.Rounded.Refresh, "启动时自动扫描", "每次打开应用时扫描上次授权的输入目录", autoScan, onAutoScanChange)
            }
        }
        item {
            SettingsGroup("外观", "基于 Material 3，优先适配系统色彩") {
                SettingSwitchRow(Icons.Rounded.Palette, "系统动态配色",
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "使用设备壁纸生成的系统颜色" else "需要 Android 12 或更高版本",
                    dynamicColor, onDynamicColorChange, Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("主题模式", fontWeight = FontWeight.SemiBold)
                        Text("跟随系统、浅色、深色或定时切换", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                ThemeMode.values().forEach { mode ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(if (themeMode == mode) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = themeMode == mode, onClick = { onThemeChange(mode) })
                        Text(mode.title, modifier = Modifier.padding(start = 6.dp))
                    }
                }
                if (themeMode == ThemeMode.SCHEDULED) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("深色模式时段", style = MaterialTheme.typography.titleSmall)
                    OutlinedButton(onClick = {
                        TimePickerDialog(context, { _, h, m -> onDarkStartChange(h * 60 + m) }, darkStart / 60, darkStart % 60, true).show()
                    }, modifier = Modifier.fillMaxWidth()) { Text("切换为深色：" + formatTime(darkStart)) }
                    OutlinedButton(onClick = {
                        TimePickerDialog(context, { _, h, m -> onLightStartChange(h * 60 + m) }, lightStart / 60, lightStart % 60, true).show()
                    }, modifier = Modifier.fillMaxWidth()) { Text("切换为浅色：" + formatTime(lightStart)) }
                }
            }
        }
        item {
            SettingsGroup("语言", "选择应用界面的显示语言") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Language, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.size(10.dp))
                    Text("显示语言", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                AppLanguage.values().forEach { lang ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(if (languageMode == lang) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = languageMode == lang, onClick = { onLanguageChange(lang) })
                        Text(lang.title, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
        item {
            SettingsGroup("关于", "Fxxk-NCM · 版本 0.2.3") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.size(10.dp))
                    Column {
                        Text("本地音频转换", fontWeight = FontWeight.SemiBold)
                        Text("文件不会上传到网络；仅处理你选择的 NCM 文件。",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, subtitle: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

@Composable
private fun SettingSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String,
    checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun StatusPill(text: String) {
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f), shape = RoundedCornerShape(50)) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium)
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
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center) { Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
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
                else -> Text(index.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun formatDuration(seconds: Long): String = if (seconds < 60) seconds.toString() + " 秒" else (seconds / 60).toString() + " 分 " + (seconds % 60) + " 秒"
private fun formatTime(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

private fun listNcmFiles(resolver: ContentResolver, treeUri: Uri): List<QueueItem> {
    val result = ArrayList<QueueItem>()
    val visited = HashSet<String>()
    val pending = java.util.ArrayDeque<String>()
    pending.addLast(DocumentsContract.getTreeDocumentId(treeUri))

    while (pending.isNotEmpty()) {
        val directoryId = pending.removeLast()
        if (!visited.add(directoryId)) continue

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, directoryId)
        resolver.query(
            childrenUri,
            arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE),
            null, null, null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndex(Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndex(Document.COLUMN_DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndex(Document.COLUMN_MIME_TYPE)
            if (idColumn < 0 || nameColumn < 0) throw IOException("目录提供程序未返回必要字段")

            while (cursor.moveToNext()) {
                val id = cursor.getString(idColumn) ?: continue
                val name = cursor.getString(nameColumn) ?: continue
                val mime = if (mimeColumn >= 0) cursor.getString(mimeColumn) else null
                when {
                    mime == Document.MIME_TYPE_DIR -> pending.addLast(id)
                    name.endsWith(".ncm", ignoreCase = true) ->
                        result.add(QueueItem(DocumentsContract.buildDocumentUriUsingTree(treeUri, id), name))
                }
            }
        } ?: throw IOException("无法读取目录，请确认目录访问权限")
    }

    return result.sortedBy { it.name.lowercase() }
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

