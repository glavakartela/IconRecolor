package com.glavakartela.iconrecolor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Background / glyph color pairs
internal val PRESETS = listOf(
    0xFF1E3A5F to 0xFFFFD166,
    0xFFF4EDE4 to 0xFF3D2C8D,
    0xFF0F2E2A to 0xFF9BE7C4,
    0xFF2A1A3A to 0xFFFF8FB1,
    0xFFFFE6E1 to 0xFF8B1E3F,
    0xFF111111 to 0xFFF5F5F5,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Screen(resumeTick: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var themeMode by remember { mutableIntStateOf(Prefs.themeMode(ctx)) }
    var colorUi by remember { mutableStateOf(Prefs.colorUi(ctx)) }
    var settingsOpen by remember { mutableStateOf(false) }
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        1 -> false
        2 -> true
        else -> systemDark
    }

    var apps by remember { mutableStateOf(emptyList<AppEntry>()) }
    var query by remember { mutableStateOf("") }
    var wallpaperMode by remember { mutableStateOf(false) }
    var useSystemSeed by remember { mutableStateOf(false) }
    var darkIcons by remember { mutableStateOf(false) }
    var bg by remember { mutableIntStateOf(PRESETS[0].first.toInt()) }
    var fg by remember { mutableIntStateOf(PRESETS[0].second.toInt()) }
    var shape by remember { mutableStateOf(Prefs.selectedShape(ctx)) }
    var customs by remember { mutableStateOf(Prefs.customShapes(ctx)) }
    var picking by remember { mutableIntStateOf(-1) } // 0 = background, 1 = glyph
    var drawing by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var editApp by remember { mutableStateOf<AppEntry?>(null) }
    var methodTick by remember { mutableIntStateOf(0) }
    var pinTick by remember { mutableIntStateOf(0) }
    var shizukuStatus by remember { mutableStateOf("Shizuku: not connected") }
    var bgBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.Default) { Recolor.loadApps(ctx) }
        bgBitmap = withContext(Dispatchers.IO) { Backgrounds.loadSaved(ctx) }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val b = Backgrounds.import(ctx, uri)
                if (b != null) bgBitmap = b else toast(ctx, "Couldn't open the image")
            }
        }
    }

    // Wallpaper color: from the user's picked image first, otherwise from the system.
    // HyperOS reports inaccurate colors (a black-and-white wallpaper can come out purple),
    // so the image is more reliable.
    val sysColors = remember(resumeTick) { Palette.wallpaperColors(ctx) }
    val imageSeed = remember(bgBitmap) { bgBitmap?.let { WallpaperSeed.fromBitmap(it) } }
    val seed: Int? = when {
        imageSeed != null && !useSystemSeed -> imageSeed
        sysColors.isNotEmpty() -> sysColors[0]
        else -> null
    }
    val scheme = remember(seed, dark, colorUi) {
        appColorScheme(if (colorUi) seed ?: Palette.NEUTRAL else Palette.NEUTRAL, dark)
    }

    // Status bar icons must stay readable even when the app theme differs from the system theme
    val activity = ctx as? ComponentActivity
    DisposableEffect(dark) {
        activity?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
        )
        onDispose { }
    }
    val style = remember(wallpaperMode, seed, darkIcons, bg, fg, shape) {
        if (wallpaperMode) {
            val (b, f) = if (seed != null) {
                Palette.fromSeed(seed, darkIcons)
            } else {
                Palette.system(ctx, 1, darkIcons)
            }
            IconStyle(b, f, shape)
        } else {
            IconStyle(bg, fg, shape)
        }
    }
    val seedInfo = when {
        seed == null -> "Wallpaper color not detected, using the system palette."
        imageSeed != null && !useSystemSeed ->
            "Color from your image: ${hexOf(seed)}${if (Tonal.toLch(seed).second < 8.0) " (gray)" else ""}."
        else ->
            "Color from system data: ${hexOf(seed)}${if (Tonal.toLch(seed).second < 8.0) " (gray)" else ""}. " +
                "On HyperOS it can be inaccurate: pick a wallpaper image in the “Wallpaper preview” card."
    }
    val pinned = remember(resumeTick, pinTick) { Shortcuts.pinnedKeys(ctx) }
    val shown = remember(apps, query) {
        apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }
    val wallpaperImage = remember(bgBitmap) { bgBitmap?.asImageBitmap() }

    fun selectShape(s: IconShape) {
        shape = s
        Prefs.setSelectedShape(ctx, s)
    }

    fun addShape(q: List<Int>, prefix: String) {
        val existing = (IconShapes.presets + customs).firstOrNull { it.q == q }
        if (existing != null) {
            selectShape(existing)
            return
        }
        val s = IconShape("$prefix ${customs.size + 1}", q)
        customs = customs + s
        Prefs.saveCustomShapes(ctx, customs)
        selectShape(s)
    }

    fun deleteShape(s: IconShape) {
        customs = customs - s
        Prefs.saveCustomShapes(ctx, customs)
        if (shape == s) selectShape(IconShapes.LAUNCHER)
    }

    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
            Scaffold(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                topBar = {
                    LargeTopAppBar(
                        title = { Text("Icon Recolor") },
                        actions = {
                            IconButton(onClick = { settingsOpen = true }) {
                                Icon(Icons.Filled.Settings, contentDescription = "Settings")
                            }
                        },
                        scrollBehavior = scrollBehavior,
                    )
                },
                floatingActionButton = {
                    ExtendedFloatingActionButton(
                        onClick = {
                            val n = Shortcuts.updatePinned(ctx, style)
                            toast(
                                ctx,
                                when {
                                    n > 0 -> "Shortcuts updated: $n"
                                    n == 0 -> "No pinned shortcuts yet"
                                    else -> "The system rejected the update, try again"
                                },
                            )
                        },
                        icon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                        text = { Text("Update pinned") },
                    )
                },
            ) { padding ->
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(80.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = padding.calculateBottomPadding() + 96.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    fullWidth {
                        WallpaperPreview(
                            apps = apps,
                            style = style,
                            wallpaper = wallpaperImage,
                            methodTick = methodTick,
                            onPick = {
                                picker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                            onClear = {
                                Backgrounds.clear(ctx)
                                bgBitmap = null
                                useSystemSeed = false
                            },
                        )
                    }

                    fullWidth { HeroCard(apps, style, methodTick) }

                    fullWidth {
                        ColorsCard(
                            wallpaperMode = wallpaperMode,
                            onMode = { wallpaperMode = it },
                            bg = bg,
                            fg = fg,
                            onPickBg = { picking = 0 },
                            onPickFg = { picking = 1 },
                            onPreset = { a, b ->
                                bg = a
                                fg = b
                            },
                            hasImage = imageSeed != null,
                            useSystem = useSystemSeed,
                            onSource = { useSystemSeed = it },
                            seedInfo = seedInfo,
                            darkIcons = darkIcons,
                            onDark = { darkIcons = it },
                        )
                    }

                    fullWidth {
                        ShapeCard(
                            style = style,
                            shape = shape,
                            customs = customs,
                            onSelect = { selectShape(it) },
                            onDraw = { drawing = true },
                            onImport = { importing = true },
                            onDelete = { deleteShape(it) },
                            onCopy = { copyText(ctx, it) },
                        )
                    }

                    fullWidth {
                        ShizukuCard(
                            status = shizukuStatus,
                            onConnect = {
                                scope.launch {
                                    shizukuStatus = ShizukuHelper.connect(ctx)
                                    for (i in 0 until 10) {
                                        if (ShizukuHelper.isConnected()) {
                                            shizukuStatus = "Shizuku connected"
                                            break
                                        }
                                        delay(300)
                                    }
                                }
                            },
                            onGrant = {
                                scope.launch {
                                    val r = withContext(Dispatchers.IO) {
                                        ShizukuHelper.grantShortcutPermission(ctx)
                                    }
                                    toast(ctx, r)
                                }
                            },
                            onSettings = { openShortcutPermission(ctx) },
                        )
                    }

                    fullWidth {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(28.dp),
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            placeholder = { Text("Search apps") },
                        )
                    }

                    fullWidth {
                        Column(Modifier.padding(vertical = 8.dp)) {
                            Text("Apps · ${shown.size}", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Tap to pin. Long-press to choose the recoloring method for that app.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    items(shown, key = { it.component.flattenToString() }) { app ->
                        AppCell(
                            app = app,
                            style = style,
                            pinned = app.component.flattenToString() in pinned,
                            methodTick = methodTick,
                            onClick = {
                                pin(ctx, app, style)
                                pinTick++
                            },
                            onLongClick = { editApp = app },
                        )
                    }
                }
            }

            if (settingsOpen) {
                SettingsDialog(
                    themeMode = themeMode,
                    onTheme = {
                        themeMode = it
                        Prefs.setThemeMode(ctx, it)
                    },
                    colorUi = colorUi,
                    onColorUi = {
                        colorUi = it
                        Prefs.setColorUi(ctx, it)
                    },
                    onResetMethods = {
                        Prefs.clearMethods(ctx)
                        methodTick++
                        toast(ctx, "Recoloring methods reset")
                    },
                    onDismiss = { settingsOpen = false },
                )
            }
            if (picking >= 0) {
                ColorDialog(
                    title = if (picking == 0) "Background color" else "Glyph color",
                    initial = if (picking == 0) bg else fg,
                    onPick = { c ->
                        if (picking == 0) bg = c else fg = c
                        picking = -1
                    },
                    onDismiss = { picking = -1 },
                )
            }
            if (drawing) {
                DrawShapeDialog(
                    style = style,
                    onSave = { q ->
                        addShape(q, "Custom")
                        drawing = false
                    },
                    onDismiss = { drawing = false },
                )
            }
            if (importing) {
                ImportShapeDialog(
                    onImport = { q ->
                        addShape(q, "Imported")
                        importing = false
                    },
                    onDismiss = { importing = false },
                )
            }
            editApp?.let { app ->
                val current = remember(app, methodTick) { Prefs.method(ctx, app.component) }
                AppMethodDialog(
                    app = app,
                    current = current,
                    onSelect = { m ->
                        Prefs.setMethod(ctx, app.component, m)
                        methodTick++
                    },
                    onDismiss = { editApp = null },
                )
            }
        }
    }
}

private fun LazyGridScope.fullWidth(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun WallpaperPreview(
    apps: List<AppEntry>,
    style: IconStyle,
    wallpaper: ImageBitmap?,
    methodTick: Int,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(300.dp)) {
                if (wallpaper != null) {
                    Image(
                        wallpaper,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(Color(0xFF1B1B1F), Color(0xFF3A3A44)))),
                    )
                }
                Column(
                    Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        apps.take(8).chunked(4).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                row.forEach { app -> PreviewIcon(app, style, methodTick) }
                            }
                        }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(26.dp))
                            .background(Color.White.copy(alpha = 0.14f))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        apps.drop(8).take(4).forEach { app -> IconBitmap(app, style, 52.dp, methodTick) }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Wallpaper preview",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(onClick = onPick) {
                    Text(if (wallpaper == null) "Choose wallpaper" else "Change")
                }
                if (wallpaper != null) TextButton(onClick = onClear) { Text("Reset") }
            }
        }
    }
}

@Composable
private fun PreviewIcon(app: AppEntry, style: IconStyle, methodTick: Int) {
    Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconBitmap(app, style, 56.dp, methodTick)
        Text(
            app.label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun HeroCard(apps: List<AppEntry>, style: IconStyle, methodTick: Int) {
    val sample = apps.take(5)
    SectionCard("Before and after") {
        PreviewRow("Before", sample, null, methodTick)
        PreviewRow("After", sample, style, methodTick)
    }
}

@Composable
private fun PreviewRow(label: String, apps: List<AppEntry>, style: IconStyle?, methodTick: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            apps.forEach { IconBitmap(it, style, 54.dp, methodTick) }
        }
    }
}

@Composable
private fun ColorsCard(
    wallpaperMode: Boolean,
    onMode: (Boolean) -> Unit,
    bg: Int,
    fg: Int,
    onPickBg: () -> Unit,
    onPickFg: () -> Unit,
    onPreset: (Int, Int) -> Unit,
    hasImage: Boolean,
    useSystem: Boolean,
    onSource: (Boolean) -> Unit,
    seedInfo: String,
    darkIcons: Boolean,
    onDark: (Boolean) -> Unit,
) {
    SectionCard("Colors") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !wallpaperMode,
                onClick = { onMode(false) },
                label = { Text("Custom colors") },
            )
            FilterChip(
                selected = wallpaperMode,
                onClick = { onMode(true) },
                label = { Text("From wallpaper") },
            )
        }

        AnimatedVisibility(visible = !wallpaperMode) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SwatchButton("Background", bg, Modifier.weight(1f), onPickBg)
                    SwatchButton("Glyph", fg, Modifier.weight(1f), onPickFg)
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PRESETS.forEach { (a, b) ->
                        PresetPill(a.toInt(), b.toInt()) { onPreset(a.toInt(), b.toInt()) }
                    }
                }
            }
        }

        AnimatedVisibility(visible = wallpaperMode) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = hasImage && !useSystem,
                        onClick = { onSource(false) },
                        enabled = hasImage,
                        label = { Text("From my image") },
                    )
                    FilterChip(
                        selected = !hasImage || useSystem,
                        onClick = { onSource(true) },
                        label = { Text("From system") },
                    )
                }
                Text(
                    seedInfo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SwitchRow("Dark icons", "Light glyph on a dark background", darkIcons, onDark)
            }
        }
    }
}

@Composable
private fun ShapeCard(
    style: IconStyle,
    shape: IconShape,
    customs: List<IconShape>,
    onSelect: (IconShape) -> Unit,
    onDraw: () -> Unit,
    onImport: () -> Unit,
    onDelete: (IconShape) -> Unit,
    onCopy: (String) -> Unit,
) {
    SectionCard("Icon shape") {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            (listOf(IconShapes.LAUNCHER) + IconShapes.presets + customs).forEach { s ->
                ShapeThumb(s, s == shape, Color(style.bg)) { onSelect(s) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDraw, modifier = Modifier.weight(1f)) { Text("Draw") }
            OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("Paste code") }
        }
        val code = shape.code
        if (code != null) {
            Text(
                "Shape code, you can share it:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(code, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onCopy(code) }) { Text("Copy code") }
                if (shape in customs) TextButton(onClick = { onDelete(shape) }) { Text("Delete shape") }
            }
        } else {
            Text(
                "The launcher defines the shape. Other shapes are cut into the icon itself: if the launcher adds a " +
                    "backdrop, check the result on the home screen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ShapeThumb(shape: IconShape, selected: Boolean, fill: Color, onClick: () -> Unit) {
    val q = shape.q ?: IconShapes.squircle.q!!
    val outline = remember(q) { IconShapes.outline(q) }
    val ring = MaterialTheme.colorScheme.primary
    val edge = MaterialTheme.colorScheme.outline
    Column(
        Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(56.dp)) {
            val half = size.minDimension / 2f
            val path = Path()
            for (k in 0 until outline.size / 2) {
                val x = half + outline[2 * k] * half * 0.9f
                val y = half + outline[2 * k + 1] * half * 0.9f
                if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, color = fill)
            drawPath(
                path,
                color = if (selected) ring else edge,
                style = Stroke(width = if (selected) 3.dp.toPx() else 1.dp.toPx()),
            )
        }
        Text(
            shape.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ShizukuCard(
    status: String,
    onConnect: () -> Unit,
    onGrant: () -> Unit,
    onSettings: () -> Unit,
) {
    SectionCard("Shortcut permission") {
        Text(
            "The “Home screen shortcuts” permission is required. Shizuku (works like adb, no PC needed) can enable it for you.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            status,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onConnect, modifier = Modifier.weight(1f)) { Text("Connect") }
            FilledTonalButton(onClick = onGrant, modifier = Modifier.weight(1f)) { Text("Allow") }
        }
        TextButton(onClick = onSettings) { Text("Open settings manually") }
    }
}

@Composable
internal fun IconBitmap(app: AppEntry, style: IconStyle?, size: Dp, methodTick: Int = 0) {
    val ctx = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, app, style, methodTick) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                val bmp = if (style == null) {
                    Recolor.original(ctx, app.component)
                } else {
                    Recolor.preview(ctx, app.component, style)
                }
                bmp.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(Modifier.size(size)) {
        val b = bitmap
        if (b != null) {
            Image(b, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(size * 0.28f))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppCell(
    app: AppEntry,
    style: IconStyle,
    pinned: Boolean,
    methodTick: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            IconBitmap(app, style, 60.dp, methodTick)
            if (pinned) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Pinned",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(22.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                )
            }
        }
        Text(
            app.label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun SwatchButton(label: String, color: Int, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Text(
                    hexOf(color),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PresetPill(a: Int, b: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.size(22.dp, 44.dp).background(Color(a)))
        Box(Modifier.size(22.dp, 44.dp).background(Color(b)))
    }
}

@Composable
internal fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

internal fun hexOf(c: Int): String = String.format("#%06X", 0xFFFFFF and c)

internal fun parseHex(t: String): Int? = runCatching {
    android.graphics.Color.parseColor(if (t.startsWith("#")) t else "#$t")
}.getOrNull()

internal fun toast(ctx: Context, text: String) {
    Toast.makeText(ctx, text, Toast.LENGTH_LONG).show()
}

private fun copyText(ctx: Context, text: String) {
    val cm = ctx.getSystemService(ClipboardManager::class.java)
    cm.setPrimaryClip(ClipData.newPlainText("icrec", text))
    toast(ctx, "Code copied")
}

private fun pin(ctx: Context, app: AppEntry, style: IconStyle) {
    val sent = runCatching { Shortcuts.pin(ctx, app, style) }.getOrDefault(false)
    toast(
        ctx,
        if (sent) {
            "Confirm in the launcher dialog. No dialog? Enable the “Home screen shortcuts” permission"
        } else {
            "Your launcher doesn't support pinning shortcuts"
        },
    )
}

/** Opens the app's permission screen: the Xiaomi-specific one first, then regular settings. */
private fun openShortcutPermission(ctx: Context) {
    val pkg = ctx.packageName
    val tries = listOf(
        Intent("miui.intent.action.APP_PERM_EDITOR")
            .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
            .putExtra("extra_pkgname", pkg),
        Intent("miui.intent.action.APP_PERM_EDITOR")
            .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.AppPermissionsEditorActivity")
            .putExtra("extra_pkgname", pkg),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")),
    )
    for (intent in tries) {
        try {
            ctx.startActivity(intent)
            return
        } catch (e: Exception) {
            // try the next option
        }
    }
}
