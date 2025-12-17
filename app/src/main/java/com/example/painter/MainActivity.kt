package com.example.painter


import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path as CPath
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke as CStroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/* =========================
   0) 데이터 모델
   ========================= */


enum class Screen { EDITOR, TIMELAPSE }

enum class BrushType { PENCIL, PEN, BRUSH, HIGHLIGHTER, AIRBRUSH, ERASER }

data class Pt(val x: Float, val y: Float)

data class Stroke(
    val brush: BrushType,
    val color: Long,     // ARGB packed (예: 0xFF000000L)
    val size: Float,
    val alpha: Float,
    val points: List<Pt>,
    val layer: Int
)

data class Project(
    val width: Int = 2000,
    val height: Int = 2000,
    val layerVisible: BooleanArray = booleanArrayOf(true, true),
    val strokes: List<Stroke> = emptyList()
)

/* =========================
   1) 상태 + ViewModel (과제용)
   ========================= */

data class UiState(
    val screen: Screen = Screen.EDITOR,

    val project: Project = Project(),
    val activeLayer: Int = 0,

    val brush: BrushType = BrushType.PENCIL,
    val color: Long = 0xFF000000L,
    val size: Float = 12f,
    val alpha: Float = 1f,

    val undo: List<Project> = emptyList(),
    val redo: List<Project> = emptyList(),

    // 타임랩스(스트로크 단위 스냅샷)
    val timelapse: List<Project> = listOf(Project()),

    // 줌/이동
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,

    val message: String? = null
)

class PaintViewModel : ViewModel() {
    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private fun pushHistory(newProject: Project) {
        val s = _state.value
        _state.value = s.copy(
            undo = s.undo + s.project,
            redo = emptyList(),
            project = newProject,
            timelapse = s.timelapse + newProject
        )
    }

    fun setScreen(screen: Screen) { _state.value = _state.value.copy(screen = screen) }

    fun setBrush(b: BrushType) { _state.value = _state.value.copy(brush = b) }
    fun setColor(c: Long) { _state.value = _state.value.copy(color = c) }
    fun setSize(v: Float) { _state.value = _state.value.copy(size = v) }
    fun setAlpha(v: Float) { _state.value = _state.value.copy(alpha = v) }
    fun setLayer(i: Int) { _state.value = _state.value.copy(activeLayer = i.coerceIn(0, 1)) }

    fun toggleLayerVisible(i: Int) {
        val p = _state.value.project
        val vis = p.layerVisible.clone()
        vis[i] = !vis[i]
        pushHistory(p.copy(layerVisible = vis))
    }

    fun addStroke(points: List<Pt>) {
        val s = _state.value
        val stroke = Stroke(
            brush = s.brush,
            color = s.color,
            size = s.size,
            alpha = s.alpha,
            points = points,
            layer = s.activeLayer
        )
        pushHistory(s.project.copy(strokes = s.project.strokes + stroke))
    }

    fun clearAll() { pushHistory(Project()) }

    fun undo() {
        val s = _state.value
        if (s.undo.isEmpty()) return
        val prev = s.undo.last()
        _state.value = s.copy(
            project = prev,
            undo = s.undo.dropLast(1),
            redo = s.redo + s.project
        )
    }

    fun redo() {
        val s = _state.value
        if (s.redo.isEmpty()) return
        val next = s.redo.last()
        _state.value = s.copy(
            project = next,
            redo = s.redo.dropLast(1),
            undo = s.undo + s.project
        )
    }

    fun setTransform(scale: Float, dx: Float, dy: Float) {
        val s = _state.value
        _state.value = s.copy(
            scale = scale.coerceIn(0.5f, 5f),
            offsetX = dx,
            offsetY = dy
        )
    }

    fun resetTransform() {
        _state.value = _state.value.copy(scale = 1f, offsetX = 0f, offsetY = 0f)
    }

    fun loadProject(p: Project) {
        _state.value = _state.value.copy(
            project = p,
            undo = emptyList(),
            redo = emptyList(),
            timelapse = listOf(p),
            message = "Loaded project"
        )
    }

    fun setMessage(msg: String?) { _state.value = _state.value.copy(message = msg) }
}

/* =========================
   2) 저장/불러오기 + PNG 내보내기(안정형)
   ========================= */

object Storage {
    private val gson = Gson()

    fun saveProjectJson(context: Context, project: Project, fileName: String = "last_project.json"): File {
        val dir = File(context.filesDir, "projects").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(gson.toJson(project))
        return file
    }

    fun loadProjectJson(context: Context, fileName: String = "last_project.json"): Project? {
        val dir = File(context.filesDir, "projects")
        val file = File(dir, fileName)
        if (!file.exists()) return null
        return try { gson.fromJson(file.readText(), Project::class.java) } catch (e: Exception) {
            null
        }
        }

    fun exportPngToGallery(context: Context, bitmap: Bitmap): Uri? {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val displayName = "SimplePaint_$stamp.png"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SimplePaint")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null

        resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        } ?: return null

        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        return uri
    }
}

/* =========================
   3) Android Canvas 기반 export 렌더러(안정형)
   ========================= */

private fun renderProjectToBitmap(project: Project): Bitmap {
    val bmp = Bitmap.createBitmap(project.width, project.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    canvas.drawColor(android.graphics.Color.WHITE) // 과제용: 흰 배경 고정

    val strokesByLayer = project.strokes.groupBy { it.layer }

    for (layer in 0..1) {
        if (!project.layerVisible[layer]) continue
        val list = strokesByLayer[layer].orEmpty()
        for (st in list) drawStrokeAndroid(canvas, st)
    }
    return bmp
}

private fun drawStrokeAndroid(canvas: android.graphics.Canvas, st: Stroke) {
    if (st.points.size < 2) return

    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    val intColor = st.color.toInt()
    val baseA = ((intColor ushr 24) and 0xFF) / 255f
    fun colorWithAlpha(mult: Float): Int {
        val finalA = (baseA * (st.alpha.coerceIn(0f, 1f)) * mult).coerceIn(0f, 1f)
        val a = (finalA * 255).roundToInt().coerceIn(0, 255)
        return (a shl 24) or (intColor and 0x00FFFFFF)
    }

    val path = Path().apply {
        moveTo(st.points.first().x, st.points.first().y)
        for (i in 1 until st.points.size) lineTo(st.points[i].x, st.points[i].y)
    }

    when (st.brush) {
        BrushType.PENCIL -> {
            p.color = colorWithAlpha(1.0f)
            p.strokeWidth = st.size
            canvas.drawPath(path, p)
        }
        BrushType.PEN -> {
            p.color = colorWithAlpha(1.0f)
            p.strokeWidth = st.size * 0.9f
            canvas.drawPath(path, p)
        }
        BrushType.BRUSH -> {
            p.color = colorWithAlpha(1.0f)
            p.strokeWidth = st.size * 1.3f
            canvas.drawPath(path, p)
        }
        BrushType.HIGHLIGHTER -> {
            p.color = colorWithAlpha(0.35f)
            p.strokeWidth = st.size * 1.8f
            p.strokeCap = Paint.Cap.SQUARE
            canvas.drawPath(path, p)
        }
        BrushType.AIRBRUSH -> {
            p.color = colorWithAlpha(0.15f)
            p.style = Paint.Style.FILL
            val r = st.size * 0.9f
            for (pt in st.points) canvas.drawCircle(pt.x, pt.y, r, p)
        }
        BrushType.ERASER -> {
            p.color = android.graphics.Color.WHITE
            p.strokeWidth = st.size * 2.0f
            canvas.drawPath(path, p)
        }
    }
}

/* =========================
   4) Compose 렌더러(화면 표시용)
   ========================= */

private fun androidx.compose.ui.graphics.drawscope.DrawScope.renderProjectCompose(project: Project) {
    val byLayer = project.strokes.groupBy { it.layer }
    for (layer in 0..1) {
        if (!project.layerVisible[layer]) continue
        val strokes = byLayer[layer].orEmpty()
        for (st in strokes) renderStrokeCompose(st)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.renderStrokeCompose(st: Stroke) {
    if (st.points.size < 2) return

    val pts = st.points.map { Offset(it.x, it.y) }
    val path = CPath().apply {
        moveTo(pts.first().x, pts.first().y)
        for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
    }

    fun c(alphaMul: Float): Color {
        val base = Color(st.color.toInt())
        val a = (st.alpha.coerceIn(0f, 1f) * alphaMul).coerceIn(0f, 1f)
        return base.copy(alpha = a)
    }

    when (st.brush) {
        BrushType.PENCIL -> drawPath(path, c(1f), style = CStroke(st.size, cap = StrokeCap.Round, join = StrokeJoin.Round))
        BrushType.PEN -> drawPath(path, c(1f), style = CStroke(st.size * 0.9f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        BrushType.BRUSH -> drawPath(path, c(1f), style = CStroke(st.size * 1.3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        BrushType.HIGHLIGHTER -> drawPath(path, c(0.35f), style = CStroke(st.size * 1.8f, cap = StrokeCap.Square))
        BrushType.AIRBRUSH -> {
            val col = c(0.15f)
            for (p in pts) drawCircle(col, radius = st.size * 0.9f, center = p)
        }
        BrushType.ERASER -> drawPath(path, Color.White, style = CStroke(st.size * 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/* =========================
   5) MainActivity + UI
   ========================= */

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    PaintApp()
                }
            }
        }
    }
}

@Composable
fun PaintApp(vm: PaintViewModel = viewModel()) {
    val s by vm.state.collectAsState()
    when (s.screen) {
        Screen.EDITOR -> EditorScreen(vm)
        Screen.TIMELAPSE -> TimelapseScreen(vm)
    }
}

@Composable
private fun EditorScreen(vm: PaintViewModel) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        ControlsBar(
            s = s,
            onBrush = vm::setBrush,
            onColor = vm::setColor,
            onSize = vm::setSize,
            onAlpha = vm::setAlpha,
            onLayer = vm::setLayer,
            onToggleLayer = vm::toggleLayerVisible,
            onUndo = vm::undo,
            onRedo = vm::redo,
            onResetView = vm::resetTransform,
            onClear = vm::clearAll,
            onSave = {
                Storage.saveProjectJson(context, s.project)
                vm.setMessage("Saved: last_project.json")
            },
            onLoad = {
                val p = Storage.loadProjectJson(context)
                if (p != null) vm.loadProject(p) else vm.setMessage("No saved file")
            },
            onExport = {
                val bmp = renderProjectToBitmap(s.project)
                val uri = Storage.exportPngToGallery(context, bmp)
                vm.setMessage(if (uri != null) "Exported PNG to Gallery" else "Export failed")
            },
            onTimelapse = { vm.setScreen(Screen.TIMELAPSE) }
        )

        s.message?.let {
            Text(it, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall)
        }

        var currentPoints by remember { mutableStateOf<List<Pt>>(emptyList()) }

        // 캔버스 영역
        Box(Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    // 2손가락: 줌/이동
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (s.scale * zoom).coerceIn(0.5f, 5f)
                            vm.setTransform(
                                scale = newScale,
                                dx = s.offsetX + pan.x,
                                dy = s.offsetY + pan.y
                            )
                        }
                    }
                    // 1손가락: 드로잉
                    .pointerInput(s.scale, s.offsetX, s.offsetY) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val down = event.changes.firstOrNull { it.pressed } ?: continue

                                // 멀티 터치면 드로잉 무시 (줌/이동만)
                                if (event.changes.count { it.pressed } >= 2) continue

                                val points = mutableListOf<Pt>()

                                fun toCanvas(pos: Offset): Pt {
                                    val x = (pos.x - s.offsetX) / s.scale
                                    val y = (pos.y - s.offsetY) / s.scale
                                    return Pt(x, y)
                                }

                                points.add(toCanvas(down.position))
                                down.consume()

                                while (true) {
                                    val e = awaitPointerEvent()
                                    val c = e.changes.firstOrNull() ?: break
                                    if (!c.pressed) break

                                    points.add(toCanvas(c.position))
                                    c.consume()
                                }

                                if (points.size >= 2) {
                                    vm.addStroke(points)
                                }
                            }
                        }
                    }

            ) {
                // 화면 배경 흰색
                drawRect(Color.White)

                withTransform({
                    translate(s.offsetX, s.offsetY)
                    scale(s.scale, s.scale)
                }) {
                    renderProjectCompose(s.project)
                }
            }
        }
    }
}

@Composable
private fun TimelapseScreen(vm: PaintViewModel) {
    val s by vm.state.collectAsState()
    val frames = s.timelapse
    var idx by remember { mutableStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var speedMs by remember { mutableStateOf(120L) }

    LaunchedEffect(playing, frames.size, speedMs) {
        while (playing && frames.isNotEmpty()) {
            delay(speedMs)
            idx = (idx + 1).coerceAtMost(frames.lastIndex)
            if (idx >= frames.lastIndex) playing = false
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { vm.setScreen(Screen.EDITOR) }) { Text("Back") }
            Button(onClick = { idx = 0; playing = false }) { Text("Rewind") }
            Button(onClick = { playing = !playing }) { Text(if (playing) "Pause" else "Play") }
            Spacer(Modifier.weight(1f))
            Text("Frame: ${if (frames.isEmpty()) 0 else idx + 1}/${frames.size}")
        }

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Speed")
            Slider(
                value = speedMs.toFloat(),
                onValueChange = { speedMs = it.toLong().coerceIn(30L, 400L) },
                valueRange = 30f..400f,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            Text("${speedMs}ms")
        }

        Spacer(Modifier.height(8.dp))

        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color.White)
            if (frames.isNotEmpty()) {
                renderProjectCompose(frames[idx.coerceIn(0, frames.lastIndex)])
            }
        }
    }
}

/* =========================
   6) 상단 컨트롤 바(과제용 최소)
   ========================= */

@Composable
private fun ControlsBar(
    s: UiState,
    onBrush: (BrushType) -> Unit,
    onColor: (Long) -> Unit,
    onSize: (Float) -> Unit,
    onAlpha: (Float) -> Unit,
    onLayer: (Int) -> Unit,
    onToggleLayer: (Int) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onResetView: () -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onExport: () -> Unit,
    onTimelapse: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(8.dp)) {

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onUndo) { Text("Undo") }
            Button(onClick = onRedo) { Text("Redo") }
            Button(onClick = onResetView) { Text("ViewReset") }
            Spacer(Modifier.weight(1f))
            Button(onClick = onTimelapse) { Text("Timelapse") }
        }

        Spacer(Modifier.height(8.dp))

        // 브러시 6종
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BrushChip("연필", s.brush == BrushType.PENCIL) { onBrush(BrushType.PENCIL) }
            BrushChip("볼펜", s.brush == BrushType.PEN) { onBrush(BrushType.PEN) }
            BrushChip("붓", s.brush == BrushType.BRUSH) { onBrush(BrushType.BRUSH) }
            BrushChip("형광", s.brush == BrushType.HIGHLIGHTER) { onBrush(BrushType.HIGHLIGHTER) }
            BrushChip("에어", s.brush == BrushType.AIRBRUSH) { onBrush(BrushType.AIRBRUSH) }
            BrushChip("지우개", s.brush == BrushType.ERASER) { onBrush(BrushType.ERASER) }
        }

        Spacer(Modifier.height(8.dp))

        // 크기 / 투명도
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Size", modifier = Modifier.width(48.dp))
            Slider(value = s.size, onValueChange = onSize, valueRange = 2f..60f, modifier = Modifier.weight(1f))
            Text("${s.size.toInt()}", modifier = Modifier.width(44.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Alpha", modifier = Modifier.width(48.dp))
            Slider(value = s.alpha, onValueChange = onAlpha, valueRange = 0.05f..1f, modifier = Modifier.weight(1f))
            Text("${(s.alpha * 100).toInt()}%", modifier = Modifier.width(54.dp))
        }

        Spacer(Modifier.height(6.dp))

        // 팔레트(간이)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            ColorChip(0xFF000000L, s.color == 0xFF000000L) { onColor(0xFF000000L) }
            ColorChip(0xFFFF0000L, s.color == 0xFFFF0000L) { onColor(0xFFFF0000L) }
            ColorChip(0xFF00AA00L, s.color == 0xFF00AA00L) { onColor(0xFF00AA00L) }
            ColorChip(0xFF0000FFL, s.color == 0xFF0000FFL) { onColor(0xFF0000FFL) }
            ColorChip(0xFFFFFF00L, s.color == 0xFFFFFF00L) { onColor(0xFFFFFF00L) }
            ColorChip(0xFFFF00FFL, s.color == 0xFFFF00FFL) { onColor(0xFFFF00FFL) }
        }

        Spacer(Modifier.height(8.dp))

        // 레이어(2개) 선택/숨김
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AssistChip(onClick = { onLayer(0) }, label = { Text(if (s.activeLayer == 0) "Layer1*" else "Layer1") })
            AssistChip(onClick = { onLayer(1) }, label = { Text(if (s.activeLayer == 1) "Layer2*" else "Layer2") })
            AssistChip(onClick = { onToggleLayer(0) }, label = { Text(if (s.project.layerVisible[0]) "L1:ON" else "L1:OFF") })
            AssistChip(onClick = { onToggleLayer(1) }, label = { Text(if (s.project.layerVisible[1]) "L2:ON" else "L2:OFF") })
        }

        Spacer(Modifier.height(8.dp))

        // 저장/불러오기/내보내기/초기화
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSave) { Text("Save") }
            Button(onClick = onLoad) { Text("Load") }
            Button(onClick = onExport) { Text("Export PNG") }
            Button(onClick = onClear) { Text("Clear") }
        }
    }
}

@Composable
private fun BrushChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(text) })
}

@Composable
private fun ColorChip(color: Long, selected: Boolean, onClick: () -> Unit) {
    val c = Color(color.toInt())
    AssistChip(
        onClick = onClick,
        label = { Text(if (selected) "●" else " ") },
        colors = AssistChipDefaults.assistChipColors(containerColor = c)
    )
}
