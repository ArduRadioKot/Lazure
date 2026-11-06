package com.rassvet.essential.ui.graph

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.net.Uri
import androidx.activity.compose.PredictiveBackHandler
import androidx.core.content.FileProvider
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import com.rassvet.essential.R
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rassvet.essential.data.index.IndexRepository
import com.rassvet.essential.data.local.AppDatabase
import com.rassvet.essential.data.local.NoteIndexEntity
import com.rassvet.essential.data.local.VaultPreferencesRepository
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val GraphBg = Color(0xFF000000)
private val EdgeColor = Color(0x66B0B0B0)
private val EdgeColorDim = Color(0x22B0B0B0)
private val EdgeColorBright = Color(0xCCFFFFFF)
private val TextWhite = Color(0xFFFFFFFF)
private val HintGray = Color(0xFF8E8E93)
private val DimAlpha = 0.18f


private val ClusterPalette = listOf(
    Color(0xFFE57373), Color(0xFFBA68C8), Color(0xFF7986CB),
    Color(0xFF64B5F6), Color(0xFF4DD0E1), Color(0xFF4DB6AC),
    Color(0xFF81C784), Color(0xFFDCE775), Color(0xFFFFD54F),
    Color(0xFFFFB74D), Color(0xFFA1887F), Color(0xFF90A4AE),
)

private data class GraphNode(
    val uri: String,
    val title: String,
    var pos: Offset,
    var vel: Offset = Offset.Zero,
)

private data class GraphEdge(val from: Int, val to: Int)

@Composable
fun GraphScreen(
    db: AppDatabase,
    prefs: VaultPreferencesRepository,
    onOpenNote: (Uri) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val index = remember { IndexRepository(db) }
    val coroutineScope = rememberCoroutineScope()
    var nodes by remember { mutableStateOf<List<GraphNode>>(emptyList()) }
    var edges by remember { mutableStateOf<List<GraphEdge>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var zoom by remember { mutableStateOf(1f) }
    var simulationTick by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }

    var selectedNode by remember { mutableStateOf<Int?>(null) }
    var focusMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }


    val neighborsOf by remember(edges, nodes.size) {
        derivedStateOf {
            val arr = Array(nodes.size) { HashSet<Int>() }
            for (e in edges) {
                arr[e.from].add(e.to)
                arr[e.to].add(e.from)
            }
            arr
        }
    }
    val clusterColors by remember(edges, nodes.size) {
        derivedStateOf { computeClusterColors(nodes.size, edges) }
    }

    val searchMatches by remember(searchQuery, nodes) {
        derivedStateOf {
            val q = searchQuery.trim().lowercase()
            if (q.isEmpty()) null
            else nodes.indices.filterTo(HashSet()) { nodes[it].title.lowercase().contains(q) }
        }
    }


    val activeIndices by remember(selectedNode, focusMode, neighborsOf, searchMatches) {
        derivedStateOf {
            when {
                selectedNode != null -> {
                    val s = selectedNode!!
                    val out = HashSet<Int>()
                    out.add(s)
                    out.addAll(neighborsOf.getOrNull(s).orEmpty())
                    out
                }
                searchMatches != null -> searchMatches!!
                else -> null
            }
        }
    }

    suspend fun loadGraph() {
        loading = true
        val vault = prefs.vaultTreeUri.first()
        if (vault.isNullOrBlank()) {
            nodes = emptyList(); edges = emptyList(); loading = false; return
        }
        val (allNotes, edgePairs) = withContext(Dispatchers.IO) {
            index.rebuild(context, vault)
            val ns: List<NoteIndexEntity> = index.allNotes()
            val titleToIdx = HashMap<String, Int>()
            for ((i, n) in ns.withIndex()) titleToIdx[n.title.lowercase()] = i
            val pairs = ArrayList<Pair<Int, Int>>()
            for ((i, n) in ns.withIndex()) {
                for (t in index.linksFor(n.uri)) {
                    val j = titleToIdx[t.lowercase()] ?: continue
                    if (j == i) continue
                    pairs.add(i to j)
                }
            }
            ns to pairs
        }
        val r = 400f
        val newNodes = allNotes.mapIndexed { i, n ->
            val angle = (2.0 * Math.PI * i / max(1, allNotes.size)).toFloat()
            GraphNode(
                uri = n.uri,
                title = n.title,
                pos = Offset(r * kotlin.math.cos(angle), r * kotlin.math.sin(angle)),
            )
        }
        nodes = newNodes
        edges = edgePairs.map { (a, b) -> GraphEdge(a, b) }
        selectedNode = null
        focusMode = false
        loading = false
    }

    LaunchedEffect(Unit) { loadGraph() }

    LaunchedEffect(nodes.size, edges.size) {
        if (nodes.isEmpty()) return@LaunchedEffect
        repeat(180) {
            simulateStep(nodes, edges)
            simulationTick++
            delay(16L)
        }
    }

    PredictiveBackHandler { progress ->
        try {
            progress.collect {  }
            onBack()
        } catch (_: CancellationException) { }
    }

    val tapMargin = with(density) { 28.dp.toPx() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GraphBg)
            .statusBarsPadding(),
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.graph_title),
                    color = TextWhite,
                    style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
                )
                Text(
                    text = if (nodes.isEmpty() && !loading) {
                        stringResource(R.string.graph_no_wiki_notes)
                    } else {
                        stringResource(
                            R.string.graph_stats_subtitle,
                            nodes.size,
                            edges.size,
                            clusterColors.distinct().size,
                        )
                    },
                    color = HintGray,
                    style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
                )
            }
            IconButton(onClick = { searchOpen = !searchOpen }) {
                Icon(
                    imageVector = if (searchOpen) Icons.Outlined.Close else Icons.Outlined.Search,
                    contentDescription = stringResource(R.string.cd_search),
                    tint = TextWhite,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = {
                coroutineScope.launch {
                    shareGraphAsPng(context, nodes, edges, clusterColors)
                }
            }) {
                Icon(
                    imageVector = Icons.Outlined.IosShare,
                    contentDescription = stringResource(R.string.cd_share),
                    tint = TextWhite,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = { coroutineScope.launch { loadGraph() } }) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = stringResource(R.string.cd_rebuild),
                    tint = TextWhite,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = searchOpen,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(120)),
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = HintGray,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(color = TextWhite, fontSize = 15.sp, lineHeight = 20.sp),
                        cursorBrush = SolidColor(TextWhite),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        decorationBox = { inner ->
                            Box {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.graph_search_hint),
                                        color = HintGray,
                                        style = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
                                    )
                                }
                                inner()
                            }
                        },
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.cd_clear),
                                tint = HintGray,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }


        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(nodes.size) {
                        detectTransformGestures { _, panDelta, zoomDelta, _ ->
                            pan += panDelta
                            zoom = (zoom * zoomDelta).coerceIn(0.3f, 4f)
                        }
                    }
                    .pointerInput(nodes.size, zoom, pan, focusMode, selectedNode) {
                        detectTapGestures(
                            onTap = { tapPos ->
                                val origin = Offset(size.width / 2f, size.height / 2f) + pan
                                val candidates = if (focusMode && selectedNode != null) {
                                    val s = selectedNode!!
                                    (neighborsOf.getOrNull(s).orEmpty() + s)
                                } else nodes.indices.toList()
                                val hit = candidates
                                    .map { it to nodes[it] }
                                    .firstOrNull { (_, n) ->
                                        val sp = origin + n.pos * zoom
                                        (sp - tapPos).getDistance() <= tapMargin
                                    }?.first
                                selectedNode = if (hit == selectedNode) null else hit
                                if (hit == null) focusMode = false
                            },
                            onDoubleTap = { tapPos ->
                                val origin = Offset(size.width / 2f, size.height / 2f) + pan
                                val hit = nodes.indices
                                    .firstOrNull {
                                        val sp = origin + nodes[it].pos * zoom
                                        (sp - tapPos).getDistance() <= tapMargin
                                    }
                                if (hit != null) {
                                    runCatching { onOpenNote(Uri.parse(nodes[hit].uri)) }
                                }
                            },
                        )
                    },
            ) {
                canvasSize = size
                val origin = Offset(size.width / 2f, size.height / 2f) + pan

                fun nodeAlpha(i: Int): Float {
                    val active = activeIndices ?: return 1f
                    return if (i in active) 1f else DimAlpha
                }
                fun edgeAlpha(e: GraphEdge): Float {
                    val active = activeIndices ?: return 1f
                    return if (e.from in active && e.to in active) 1f else DimAlpha * 0.7f
                }


                for (e in edges) {
                    val a = nodes.getOrNull(e.from) ?: continue
                    val b = nodes.getOrNull(e.to) ?: continue
                    val alpha = edgeAlpha(e)
                    val sel = selectedNode
                    val isHighlightEdge = sel != null && (e.from == sel || e.to == sel)
                    val color = when {
                        isHighlightEdge -> EdgeColorBright
                        else -> EdgeColor.copy(alpha = EdgeColor.alpha * alpha)
                    }
                    drawLine(
                        color = if (alpha < 1f && !isHighlightEdge)
                            EdgeColorDim
                        else color,
                        start = origin + a.pos * zoom,
                        end = origin + b.pos * zoom,
                        strokeWidth = if (isHighlightEdge) 2.5f else 1.5f,
                    )
                }


                val baseR = (8f * zoom).coerceIn(4f, 18f)
                for (i in nodes.indices) {
                    val n = nodes[i]
                    val sp = origin + n.pos * zoom
                    val alpha = nodeAlpha(i)
                    val baseColor = clusterColors.getOrNull(i) ?: Color.White
                    val isSelected = (selectedNode == i)
                    val radius = if (isSelected) baseR * 1.6f else baseR
                    drawCircle(
                        color = baseColor.copy(alpha = baseColor.alpha * alpha),
                        radius = radius,
                        center = sp,
                    )
                    drawCircle(
                        color = Color(0xFF1A1A1A),
                        radius = radius,
                        center = sp,
                        style = Stroke(width = if (isSelected) 2.5f else 1.5f),
                    )
                    if (isSelected) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.55f),
                            radius = radius + 6f,
                            center = sp,
                            style = Stroke(width = 2f),
                        )
                    }
                }


                if (zoom > 0.7f) {
                    val textPaint = android.graphics.Paint().apply {
                        textSize = 12f * zoom.coerceAtMost(1.5f)
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    for (i in nodes.indices) {
                        val n = nodes[i]
                        val sp = origin + n.pos * zoom
                        val alpha = (255 * nodeAlpha(i)).toInt().coerceIn(0, 255)
                        textPaint.color = android.graphics.Color.argb(alpha, 255, 255, 255)
                        this.drawContext.canvas.nativeCanvas.drawText(
                            n.title.take(24),
                            sp.x,
                            sp.y - baseR - 4f,
                            textPaint,
                        )
                    }
                }


                if (nodes.size >= 3) {
                    val miniW = 130f
                    val miniH = 96f
                    val miniPadding = 12f
                    val miniLeft = size.width - miniW - miniPadding
                    val miniTop = size.height - miniH - miniPadding
                    drawRect(
                        color = Color(0xCC0A0A0A),
                        topLeft = Offset(miniLeft, miniTop),
                        size = Size(miniW, miniH),
                    )
                    drawRect(
                        color = Color(0x66FFFFFF),
                        topLeft = Offset(miniLeft, miniTop),
                        size = Size(miniW, miniH),
                        style = Stroke(width = 1f),
                    )

                    var minX = Float.POSITIVE_INFINITY
                    var minY = Float.POSITIVE_INFINITY
                    var maxX = Float.NEGATIVE_INFINITY
                    var maxY = Float.NEGATIVE_INFINITY
                    for (n in nodes) {
                        if (n.pos.x < minX) minX = n.pos.x
                        if (n.pos.x > maxX) maxX = n.pos.x
                        if (n.pos.y < minY) minY = n.pos.y
                        if (n.pos.y > maxY) maxY = n.pos.y
                    }
                    val graphW = (maxX - minX).coerceAtLeast(1f)
                    val graphH = (maxY - minY).coerceAtLeast(1f)
                    val scale = min((miniW - 8f) / graphW, (miniH - 8f) / graphH)
                    val cx = miniLeft + miniW / 2f
                    val cy = miniTop + miniH / 2f
                    fun toMini(p: Offset): Offset = Offset(
                        cx + (p.x - (minX + maxX) / 2f) * scale,
                        cy + (p.y - (minY + maxY) / 2f) * scale,
                    )

                    for (e in edges) {
                        val a = nodes.getOrNull(e.from) ?: continue
                        val b = nodes.getOrNull(e.to) ?: continue
                        drawLine(
                            color = Color(0x55B0B0B0),
                            start = toMini(a.pos),
                            end = toMini(b.pos),
                            strokeWidth = 0.6f,
                        )
                    }

                    for (i in nodes.indices) {
                        drawCircle(
                            color = clusterColors.getOrNull(i) ?: Color.White,
                            radius = 1.6f,
                            center = toMini(nodes[i].pos),
                        )
                    }


                    val vMinX = (-size.width / 2f - pan.x) / zoom
                    val vMaxX = (size.width / 2f - pan.x) / zoom
                    val vMinY = (-size.height / 2f - pan.y) / zoom
                    val vMaxY = (size.height / 2f - pan.y) / zoom
                    val tlMini = toMini(Offset(vMinX, vMinY))
                    val brMini = toMini(Offset(vMaxX, vMaxY))
                    val rectLeft = max(miniLeft, min(tlMini.x, brMini.x))
                    val rectTop = max(miniTop, min(tlMini.y, brMini.y))
                    val rectRight = min(miniLeft + miniW, max(tlMini.x, brMini.x))
                    val rectBottom = min(miniTop + miniH, max(tlMini.y, brMini.y))
                    if (rectRight > rectLeft && rectBottom > rectTop) {
                        drawRect(
                            color = Color(0xFFFFD54F).copy(alpha = 0.85f),
                            topLeft = Offset(rectLeft, rectTop),
                            size = Size(rectRight - rectLeft, rectBottom - rectTop),
                            style = Stroke(width = 1.2f),
                        )
                    }
                }
            }

            if (loading) {
                Text(
                    text = stringResource(R.string.graph_building),
                    color = HintGray,
                    style = TextStyle(fontSize = 14.sp),
                    modifier = Modifier.align(Alignment.Center),
                )
            }


            val sel = selectedNode
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = sel != null,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(120)),
            ) {
                val node = sel?.let { nodes.getOrNull(it) }
                if (node != null) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = node.title,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
                                maxLines = 2,
                            )
                            val neighborCount = neighborsOf.getOrNull(sel).orEmpty().size
                            Text(
                                text = "$neighborCount соседей",
                                color = HintGray,
                                style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
                            )
                            Spacer(Modifier.size(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    onClick = {
                                        runCatching { onOpenNote(Uri.parse(node.uri)) }
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.action_open))
                                }
                                FilledTonalButton(onClick = { focusMode = !focusMode }) {
                                    Icon(
                                        imageVector = Icons.Outlined.CenterFocusStrong,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        stringResource(
                                            if (focusMode) R.string.graph_reset_focus else R.string.graph_focus,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }
}


private fun computeClusterColors(n: Int, edges: List<GraphEdge>): List<Color> {
    if (n == 0) return emptyList()
    val communities = louvainCommunities(n, edges.map { it.from to it.to })
    val sizes = HashMap<Int, Int>()
    for (c in communities) sizes.merge(c, 1, Int::plus)
    val orderedCommunities = sizes.entries.sortedByDescending { it.value }.map { it.key }
    val communityToColor = HashMap<Int, Color>()
    for ((i, c) in orderedCommunities.withIndex()) {
        communityToColor[c] = ClusterPalette[i % ClusterPalette.size]
    }
    return List(n) { communityToColor[communities[it]] ?: Color.White }
}


private suspend fun shareGraphAsPng(
    context: android.content.Context,
    nodes: List<GraphNode>,
    edges: List<GraphEdge>,
    clusterColors: List<Color>,
) {
    if (nodes.isEmpty()) {
        android.widget.Toast.makeText(context, R.string.graph_is_empty, android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    val ok = withContext(Dispatchers.IO) {
        try {
            val sizePx = 1600
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = AndroidCanvas(bmp)
            canvas.drawColor(android.graphics.Color.BLACK)

            var minX = Float.POSITIVE_INFINITY
            var minY = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY
            var maxY = Float.NEGATIVE_INFINITY
            for (n in nodes) {
                if (n.pos.x < minX) minX = n.pos.x
                if (n.pos.x > maxX) maxX = n.pos.x
                if (n.pos.y < minY) minY = n.pos.y
                if (n.pos.y > maxY) maxY = n.pos.y
            }
            val padding = 120f
            val graphW = (maxX - minX).coerceAtLeast(1f)
            val graphH = (maxY - minY).coerceAtLeast(1f)
            val scale = min((sizePx - padding * 2) / graphW, (sizePx - padding * 2) / graphH)
            val cx = sizePx / 2f
            val cy = sizePx / 2f
            fun toCanvas(p: Offset): Pair<Float, Float> = Pair(
                cx + (p.x - (minX + maxX) / 2f) * scale,
                cy + (p.y - (minY + maxY) / 2f) * scale,
            )
            val edgePaint = android.graphics.Paint().apply {
                color = android.graphics.Color.argb(120, 200, 200, 200)
                strokeWidth = 1.5f
                isAntiAlias = true
            }
            for (e in edges) {
                val a = nodes.getOrNull(e.from) ?: continue
                val b = nodes.getOrNull(e.to) ?: continue
                val (ax, ay) = toCanvas(a.pos)
                val (bx, by) = toCanvas(b.pos)
                canvas.drawLine(ax, ay, bx, by, edgePaint)
            }
            val nodePaint = android.graphics.Paint().apply { isAntiAlias = true }
            val strokePaint = android.graphics.Paint().apply {
                isAntiAlias = true
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 1.5f
                color = android.graphics.Color.argb(255, 26, 26, 26)
            }
            val textPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 16f
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            for (i in nodes.indices) {
                val n = nodes[i]
                val (x, y) = toCanvas(n.pos)
                val c = clusterColors.getOrNull(i) ?: Color.White
                nodePaint.color = android.graphics.Color.argb(
                    (255 * c.alpha).toInt().coerceIn(0, 255),
                    (255 * c.red).toInt().coerceIn(0, 255),
                    (255 * c.green).toInt().coerceIn(0, 255),
                    (255 * c.blue).toInt().coerceIn(0, 255),
                )
                canvas.drawCircle(x, y, 10f, nodePaint)
                canvas.drawCircle(x, y, 10f, strokePaint)
                canvas.drawText(n.title.take(24), x, y - 16f, textPaint)
            }

            val brand = android.graphics.Paint().apply {
                color = android.graphics.Color.argb(170, 255, 255, 255)
                textSize = 22f
                isAntiAlias = true
            }
            canvas.drawText("Lazure · граф связей", 40f, sizePx - 40f, brand)

            val dir = java.io.File(context.filesDir, "share").apply { mkdirs() }
            val file = java.io.File(dir, "graph_${System.currentTimeMillis()}.png")
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, context.getString(R.string.graph_share_title))
                .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            withContext(Dispatchers.Main) {
                context.startActivity(chooser)
            }
            true
        } catch (t: Throwable) {
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(
                    context,
                    context.getString(
                        R.string.sheet_error_with_message,
                        t.message ?: context.getString(R.string.sheet_error_generic),
                    ),
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            }
            false
        }
    }
    if (!ok) return
}

private fun simulateStep(nodes: List<GraphNode>, edges: List<GraphEdge>) {
    val n = nodes.size
    if (n == 0) return
    val kRepel = 12000f
    val kSpring = 0.04f
    val targetEdgeLen = 110f
    val damping = 0.85f
    val maxStep = 30f

    for (i in 0 until n) {
        var fx = 0f; var fy = 0f
        val pi = nodes[i].pos
        for (j in 0 until n) {
            if (i == j) continue
            val pj = nodes[j].pos
            val dx = pi.x - pj.x
            val dy = pi.y - pj.y
            val d2 = (dx * dx + dy * dy).coerceAtLeast(1f)
            val f = kRepel / d2
            val d = sqrt(d2)
            fx += (dx / d) * f
            fy += (dy / d) * f
        }
        fx += -pi.x * 0.002f
        fy += -pi.y * 0.002f
        nodes[i].vel = Offset(
            (nodes[i].vel.x + fx).coerceIn(-maxStep, maxStep),
            (nodes[i].vel.y + fy).coerceIn(-maxStep, maxStep),
        )
    }
    for (e in edges) {
        val a = nodes.getOrNull(e.from) ?: continue
        val b = nodes.getOrNull(e.to) ?: continue
        val dx = b.pos.x - a.pos.x
        val dy = b.pos.y - a.pos.y
        val d = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
        val delta = d - targetEdgeLen
        val fx = (dx / d) * delta * kSpring
        val fy = (dy / d) * delta * kSpring
        a.vel = Offset(a.vel.x + fx, a.vel.y + fy)
        b.vel = Offset(b.vel.x - fx, b.vel.y - fy)
    }
    for (i in 0 until n) {
        val node = nodes[i]
        val step = Offset(
            min(maxStep, max(-maxStep, node.vel.x)),
            min(maxStep, max(-maxStep, node.vel.y)),
        )
        node.pos = Offset(node.pos.x + step.x, node.pos.y + step.y)
        node.vel = Offset(node.vel.x * damping, node.vel.y * damping)
    }
}


