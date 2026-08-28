package com.nitronbox.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nitronbox.app.data.model.ContextOverflowStrategy
import com.nitronbox.app.data.remote.ProviderProfile
import com.nitronbox.app.data.remote.ProviderProtocol
import com.nitronbox.app.data.settings.LanguageSetting
import com.nitronbox.app.data.settings.ThemeModeSetting
import com.nitronbox.app.ui.i18n.LocalStrings
import com.nitronbox.app.ui.theme.NitronTheme
import com.nitronbox.app.ui.theme.SurfaceLevel
import com.nitronbox.app.ui.theme.nitronSurface
import androidx.compose.material3.Switch
import com.nitronbox.app.ui.components.AnimatedSegmented
import com.nitronbox.app.ui.components.NitronBottomPanel
import com.nitronbox.app.ui.components.NitronCenterDialog
import com.nitronbox.app.ui.components.TextButtonFlat
import com.nitronbox.app.ui.theme.pressableRipple

private val PROVIDER_TEMPLATES = listOf(
    ProviderTemplate("OpenAI", ProviderProtocol.OPENAI_COMPATIBLE, "https://api.openai.com/"),
    ProviderTemplate("Anthropic", ProviderProtocol.ANTHROPIC, "https://api.anthropic.com/"),
    ProviderTemplate("Gemini", ProviderProtocol.GEMINI, "https://generativelanguage.googleapis.com/"),
    ProviderTemplate("DeepSeek", ProviderProtocol.OPENAI_COMPATIBLE, "https://api.deepseek.com/"),
    ProviderTemplate("Groq", ProviderProtocol.OPENAI_COMPATIBLE, "https://api.groq.com/openai/"),
    ProviderTemplate("Ollama (local)", ProviderProtocol.OLLAMA, "http://localhost:11434/"),
)

private data class ProviderTemplate(
    val name: String,
    val protocol: ProviderProtocol,
    val baseUrl: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val providers by viewModel.providers.collectAsState()
    val discovered by viewModel.discoveredModels.collectAsState()
    val health by viewModel.providerHealth.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val workspace by viewModel.activeWorkspace.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val language by viewModel.language.collectAsState()
    val wallpaper by viewModel.wallpaper.collectAsState()
    val wallpaperImageUri by viewModel.wallpaperImageUri.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    var editingProvider by remember { mutableStateOf<ProviderProfile?>(null) }
    var prefill by remember { mutableStateOf<ProviderTemplate?>(null) }
    var wallpaperOpen by remember { mutableStateOf(false) }
    var deletingProvider by remember { mutableStateOf<ProviderProfile?>(null) }
    var skillDraft by remember { mutableStateOf<com.nitronbox.app.data.settings.Skill?>(null) }
    val skills by viewModel.skills.collectAsState()
    val blurEnabled by viewModel.blurEnabled.collectAsState()
    val blurStrength by viewModel.blurStrength.collectAsState(18f)
    val blurredPanels by viewModel.blurredPanels.collectAsState()
    val galleryImages by viewModel.galleryImages.collectAsState()

    // Every overlay on this screen blurs the settings content beneath it.
    val overlayOpen = editingProvider != null || prefill != null ||
        wallpaperOpen || deletingProvider != null || skillDraft != null
    val blurProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (overlayOpen) 1f else 0f,
        label = "settingsBlur",
    )

    Box(modifier.fillMaxSize().background(NitronTheme.colors.background)) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .blur(20.dp * blurProgress),
        topBar = {
            TopAppBar(
                title = { Text(strings.settings) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = NitronTheme.colors.textPrimary)
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = NitronTheme.colors.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = NitronTheme.colors.background,
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    strings.providers,
                    style = MaterialTheme.typography.titleLarge,
                    color = NitronTheme.colors.textPrimary,
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "One tap to add · ",
                        style = MaterialTheme.typography.labelMedium,
                        color = NitronTheme.colors.textTertiary,
                    )
                    if (busy) CircularProgressIndicator(modifier = Modifier.height(16.dp), strokeWidth = 2.dp)
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PROVIDER_TEMPLATES) { template ->
                        Text(
                            "+ ${template.name}",
                            style = MaterialTheme.typography.labelLarge,
                            color = NitronTheme.colors.textPrimary,
                            modifier = Modifier
                                .nitronSurface(SurfaceLevel.Raised, NitronTheme.shapes.pill)
                                .pressableRipple(shape = NitronTheme.shapes.pill) { prefill = template }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                        )
                    }
                }
            }
            if (providers.isEmpty()) {
                item {
                    Text(
                        strings.noProvidersHint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = NitronTheme.colors.textSecondary,
                    )
                }
            }
            items(providers, key = { it.id }) { profile ->
                ProviderCard(
                    profile = profile,
                    health = health[profile.id],
                    models = discovered[profile.id].orEmpty(),
                    onEdit = { editingProvider = profile },
                )
            }
            item {
                Text(
                    strings.providerHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = NitronTheme.colors.textTertiary,
                )
            }
            item { HorizontalDivider(color = NitronTheme.colors.border) }
            item {
                Text(
                    strings.appearance,
                    style = MaterialTheme.typography.titleLarge,
                    color = NitronTheme.colors.textPrimary,
                )
            }
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .nitronSurface(SurfaceLevel.Raised, NitronTheme.shapes.medium)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(strings.theme, style = MaterialTheme.typography.labelLarge, color = NitronTheme.colors.textSecondary)
                    SegmentedSelector(
                        options = listOf(
                            strings.themeSystem to ThemeModeSetting.SYSTEM,
                            strings.themeLight to ThemeModeSetting.LIGHT,
                            strings.themeDark to ThemeModeSetting.DARK,
                        ),
                        selected = themeMode,
                        onSelect = viewModel::setThemeMode,
                    )
                    HorizontalDivider(color = NitronTheme.colors.border)
                    Text(strings.language, style = MaterialTheme.typography.labelLarge, color = NitronTheme.colors.textSecondary)
                    SegmentedSelector(
                        options = listOf(
                            strings.languageSystem to LanguageSetting.SYSTEM,
                            "English" to LanguageSetting.ENGLISH,
                            "Русский" to LanguageSetting.RUSSIAN,
                        ),
                        selected = language,
                        onSelect = viewModel::setLanguage,
                    )
                    HorizontalDivider(color = NitronTheme.colors.border)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            strings.blurBackground,
                            style = MaterialTheme.typography.labelLarge,
                            color = NitronTheme.colors.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = blurEnabled, onCheckedChange = viewModel::setBlurEnabled)
                    }
                    androidx.compose.animation.AnimatedVisibility(visible = blurEnabled) {
                        Column {
                            Text(
                                strings.strength + ": " + blurStrength.toInt(),
                                style = MaterialTheme.typography.labelMedium,
                                color = NitronTheme.colors.textSecondary,
                            )
                            com.nitronbox.app.ui.components.NitronSlider(
                                value = blurStrength,
                                onValueChange = viewModel::setBlurStrength,
                                valueRange = 4f..32f,
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            strings.blurredPanels,
                            style = MaterialTheme.typography.labelLarge,
                            color = NitronTheme.colors.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = blurredPanels, onCheckedChange = viewModel::setBlurredPanels)
                    }
                    HorizontalDivider(color = NitronTheme.colors.border)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .nitronSurface(SurfaceLevel.Muted, NitronTheme.shapes.small)
                            .pressableRipple(shape = NitronTheme.shapes.small) { wallpaperOpen = true }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            strings.wallpaper,
                            style = MaterialTheme.typography.labelLarge,
                            color = NitronTheme.colors.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        WallpaperThumb(
                            previewColors = wallpaperPreviewColors(wallpaper),
                            isSelected = false,
                            onClick = { wallpaperOpen = true },
                        )
                        Icon(
                            Icons.Rounded.ExpandMore,
                            null,
                            tint = NitronTheme.colors.textSecondary,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
            item { HorizontalDivider(color = NitronTheme.colors.border) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        strings.skills,
                        style = MaterialTheme.typography.titleLarge,
                        color = NitronTheme.colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "+ " + strings.addSkill,
                        style = MaterialTheme.typography.labelLarge,
                        color = NitronTheme.colors.accent,
                        modifier = Modifier
                            .pressableRipple(shape = NitronTheme.shapes.small) {
                                skillDraft = com.nitronbox.app.data.settings.Skill("", "")
                            }
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                    )
                }
            }
            items(skills.size) { index ->
                val skill = skills[index]
                Column(
                    Modifier
                        .fillMaxWidth()
                        .nitronSurface(SurfaceLevel.Raised, NitronTheme.shapes.medium)
                        .pressableRipple(shape = NitronTheme.shapes.medium) { skillDraft = skill }
                        .padding(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            skill.name.ifBlank { "\u2014" },
                            style = MaterialTheme.typography.titleSmall,
                            color = if (skill.enabled) NitronTheme.colors.textPrimary else NitronTheme.colors.textTertiary,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = skill.enabled, onCheckedChange = { viewModel.toggleSkill(skill) })
                    }
                    Text(
                        skill.prompt,
                        style = MaterialTheme.typography.labelSmall,
                        color = NitronTheme.colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            item { HorizontalDivider(color = NitronTheme.colors.border) }
            item {
                Text(
                    strings.workspace,
                    style = MaterialTheme.typography.titleLarge,
                    color = NitronTheme.colors.textPrimary,
                )
            }
            item {
                workspace?.let { current ->
                    WorkspaceEditor(
                        workspace = current,
                        onSave = viewModel::saveWorkspace,
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (prefill != null || editingProvider != null) {
        val template = prefill
        ProviderEditorSheet(
            modifier = Modifier.align(Alignment.BottomCenter),
            initial = editingProvider,
            prefillName = template?.name.orEmpty(),
            prefillProtocol = template?.protocol ?: ProviderProtocol.OPENAI_COMPATIBLE,
            prefillBaseUrl = template?.baseUrl ?: "https://",
            onDismiss = {
                prefill = null
                editingProvider = null
            },
            onSave = { profile, key ->
                viewModel.saveProvider(profile, key)
                prefill = null
                editingProvider = null
            },
            onTest = { profileId -> viewModel.testProvider(profileId) },
            onDiscover = { profileId -> viewModel.discoverModels(profileId) },
            onDeleteRequest = { profile ->
                deletingProvider = profile
            },
        )
    }

    if (wallpaperOpen) {
        WallpaperPanel(
            modifier = Modifier.align(Alignment.BottomCenter),
            selected = wallpaper,
            imageUri = wallpaperImageUri,
            onSelect = viewModel::setWallpaper,
            galleryImages = galleryImages,
            onPickFromGallery = { uri -> viewModel.setWallpaperImage(android.net.Uri.parse(uri)) },
            onDismiss = { wallpaperOpen = false },
        )
    }

    skillDraft?.let { draft ->
        NitronBottomPanel(
            visible = true,
            onDismiss = { skillDraft = null },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            SkillEditor(
                initial = draft,
                onDismiss = { skillDraft = null },
                onSave = { saved ->
                    viewModel.saveSkill(saved)
                    skillDraft = null
                },
            )
        }
    }

    deletingProvider?.let { profile ->
        NitronCenterDialog(visible = true, onDismiss = { deletingProvider = null }, modifier = Modifier.align(Alignment.Center)) {
            Column(Modifier.padding(18.dp).fillMaxWidth(0.86f)) {
                Text(strings.delete, style = MaterialTheme.typography.titleMedium, color = NitronTheme.colors.textPrimary)
                Spacer(Modifier.height(8.dp))
                Text(profile.displayName, style = MaterialTheme.typography.bodyMedium, color = NitronTheme.colors.textSecondary)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
                    TextButtonFlat(strings.cancel) { deletingProvider = null }
                    TextButtonFlat(strings.delete, destructive = true) {
                        viewModel.deleteProvider(profile.id)
                        deletingProvider = null
                    }
                }
            }
        }
    }
    }
}

/** Preset preview colors, shared by the settings row and the wallpaper panel. */
private fun wallpaperPreviewColors(preset: com.nitronbox.app.data.settings.WallpaperPreset): List<Color> = when (preset) {
    com.nitronbox.app.data.settings.WallpaperPreset.MIDNIGHT -> listOf(Color(0xFF0B1428), Color(0xFF2C4A7C))
    com.nitronbox.app.data.settings.WallpaperPreset.AURORA -> listOf(Color(0xFF07231D), Color(0xFF1E8F6E))
    com.nitronbox.app.data.settings.WallpaperPreset.SUNSET -> listOf(Color(0xFF2B0F1E), Color(0xFF93395B))
    com.nitronbox.app.data.settings.WallpaperPreset.GRAPHITE -> listOf(Color(0xFF0E0E10), Color(0xFF3A3A42))
    com.nitronbox.app.data.settings.WallpaperPreset.LOGO -> listOf(Color(0xFF0C1020), Color(0xFF3FC8F5))
    com.nitronbox.app.data.settings.WallpaperPreset.CUSTOM -> listOf(Color(0xFF3A3A40), Color(0xFF6E6E78))
    com.nitronbox.app.data.settings.WallpaperPreset.NONE -> listOf(Color(0xFF16181D), Color(0xFF2A2D34))
}

/** Skill editor: name plus the instruction block merged into the system prompt. */
@Composable
private fun SkillEditor(
    initial: com.nitronbox.app.data.settings.Skill,
    onDismiss: () -> Unit,
    onSave: (com.nitronbox.app.data.settings.Skill) -> Unit,
) {
    val strings = LocalStrings.current
    var name by remember { mutableStateOf(initial.name) }
    var prompt by remember { mutableStateOf(initial.prompt) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (initial.name.isBlank()) strings.addSkill else initial.name,
            style = MaterialTheme.typography.headlineSmall,
            color = NitronTheme.colors.textPrimary,
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(strings.skillName) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text(strings.skillPrompt) },
            minLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onSave(initial.copy(name = name.trim(), prompt = prompt.trim())) },
            enabled = name.isNotBlank() && prompt.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(strings.save) }
    }
}

/** Custom wallpaper panel: preset thumbs, plus an in-app gallery of device photos. */
@Composable
private fun WallpaperPanel(
    modifier: Modifier = Modifier,
    selected: com.nitronbox.app.data.settings.WallpaperPreset,
    imageUri: String?,
    galleryImages: List<String>,
    onSelect: (com.nitronbox.app.data.settings.WallpaperPreset) -> Unit,
    onPickFromGallery: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    var showGallery by remember { mutableStateOf(false) }
    val presets = listOf(
        com.nitronbox.app.data.settings.WallpaperPreset.NONE,
        com.nitronbox.app.data.settings.WallpaperPreset.LOGO,
        com.nitronbox.app.data.settings.WallpaperPreset.MIDNIGHT,
        com.nitronbox.app.data.settings.WallpaperPreset.AURORA,
        com.nitronbox.app.data.settings.WallpaperPreset.SUNSET,
        com.nitronbox.app.data.settings.WallpaperPreset.GRAPHITE,
    )
    com.nitronbox.app.ui.components.NitronBottomPanel(visible = true, onDismiss = onDismiss, modifier = modifier) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 20.dp)) {
            Text(strings.wallpaper, style = MaterialTheme.typography.headlineSmall, color = NitronTheme.colors.textPrimary)
            Spacer(Modifier.height(12.dp))
            if (showGallery) {
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp),
                ) {
                    items(galleryImages.size) { index ->
                        val galleryUri = galleryImages[index]
                        coil.compose.AsyncImage(
                            model = galleryUri,
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier
                                .size(110.dp)
                                .clip(NitronTheme.shapes.small)
                                .pressableRipple(shape = NitronTheme.shapes.small) {
                                    onPickFromGallery(galleryUri)
                                    onDismiss()
                                },
                        )
                    }
                }
            } else {
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(presets.size) { index ->
                    val preset = presets[index]
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        WallpaperThumb(
                            previewColors = wallpaperPreviewColors(preset),
                            isSelected = preset == selected,
                            onClick = { onSelect(preset) },
                        )
                    }
                }
                item {
                    val isSelected = selected == com.nitronbox.app.data.settings.WallpaperPreset.CUSTOM
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            Modifier
                                .size(width = 62.dp, height = 42.dp)
                                .nitronSurface(SurfaceLevel.Raised, NitronTheme.shapes.small)
                                .then(
                                    if (isSelected) {
                                        Modifier.border(2.dp, NitronTheme.colors.accent, NitronTheme.shapes.small)
                                    } else {
                                        Modifier.border(1.dp, NitronTheme.colors.border, NitronTheme.shapes.small)
                                    },
                                )
                                .pressableRipple(shape = NitronTheme.shapes.small, onClick = { showGallery = true }),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected && imageUri != null) {
                                coil.compose.AsyncImage(
                                    model = imageUri,
                                    contentDescription = null,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Text("+", style = MaterialTheme.typography.titleMedium, color = NitronTheme.colors.textPrimary)
                            }
                        }
                        Text(
                            strings.wallpaperPhoto,
                            style = MaterialTheme.typography.labelSmall,
                            color = NitronTheme.colors.textSecondary,
                        )
                    }
                }
            }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(strings.close) }
        }
    }
}

@Composable
private fun WallpaperThumb(
    previewColors: List<Color>,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.pressableRipple(shape = NitronTheme.shapes.small, onClick = onClick),
    ) {
        Box(
            Modifier
                .size(width = 62.dp, height = 42.dp)
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(previewColors),
                    shape = NitronTheme.shapes.small,
                )
                .then(
                    if (isSelected) {
                        Modifier.border(2.dp, NitronTheme.colors.accent, NitronTheme.shapes.small)
                    } else {
                        Modifier.border(1.dp, NitronTheme.colors.border, NitronTheme.shapes.small)
                    },
                ),
        )
    }
}

/** OpenAI/Vercel-style segmented control with a sliding selection pill. */
@Composable
private fun <T> SegmentedSelector(
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    com.nitronbox.app.ui.components.AnimatedSegmented(
        options = options,
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
    )
}

@Composable
private fun ProviderCard(
    profile: ProviderProfile,
    health: com.nitronbox.app.data.remote.ProviderHealth?,
    models: List<com.nitronbox.app.data.remote.DiscoveredModel>,
    onEdit: () -> Unit,
) {
    val strings = LocalStrings.current
    Column(
        Modifier
            .fillMaxWidth()
            .nitronSurface(SurfaceLevel.Raised, NitronTheme.shapes.medium)
            .pressableRipple(shape = NitronTheme.shapes.medium, onClick = onEdit)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(profile.displayName, style = MaterialTheme.typography.titleSmall, color = NitronTheme.colors.textPrimary)
                Text(
                    "${profile.protocol.friendlyName()}  ·  ${profile.baseUrl}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NitronTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when {
                health?.reachable == true -> Text(strings.online, style = MaterialTheme.typography.labelMedium, color = NitronTheme.colors.accent)
                health?.reachable == false -> Text(strings.offline, style = MaterialTheme.typography.labelMedium, color = NitronTheme.colors.destructive)
                else -> Text(strings.untested, style = MaterialTheme.typography.labelMedium, color = NitronTheme.colors.textTertiary)
            }
        }
        AnimatedVisibility(models.isNotEmpty()) {
            Column {
                Spacer(Modifier.height(8.dp))
                Text(
                    strings.modelsDiscovered(models.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = NitronTheme.colors.textTertiary,
                )
            }
        }
    }
}

/** Provider editor as a custom bottom panel: protocol chips, credentials, health check. */
@Composable
private fun ProviderEditorSheet(
    modifier: Modifier = Modifier,
    initial: ProviderProfile?,
    prefillName: String,
    prefillProtocol: ProviderProtocol,
    prefillBaseUrl: String,
    onDismiss: () -> Unit,
    onSave: (ProviderProfile, CharArray?) -> Unit,
    onTest: (String) -> Unit,
    onDiscover: (String) -> Unit,
    onDeleteRequest: (ProviderProfile) -> Unit,
) {
    val strings = LocalStrings.current
    var displayName by remember { mutableStateOf(initial?.displayName ?: prefillName) }
    var baseUrl by remember { mutableStateOf(initial?.baseUrl ?: prefillBaseUrl) }
    var protocol by remember { mutableStateOf(initial?.protocol ?: prefillProtocol) }
    var apiKey by remember { mutableStateOf("") }

    val valid = displayName.isNotBlank() && baseUrl.startsWith("http")

    com.nitronbox.app.ui.components.NitronBottomPanel(visible = true, onDismiss = onDismiss, modifier = modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (initial == null) strings.addProvider else initial.displayName,
                style = MaterialTheme.typography.headlineSmall,
                color = NitronTheme.colors.textPrimary,
            )
            Text(strings.protocol, style = MaterialTheme.typography.labelLarge, color = NitronTheme.colors.textSecondary)
            SegmentedSelector(
                options = ProviderProtocol.entries.map { it.friendlyName() to it },
                selected = protocol,
                onSelect = { protocol = it },
            )
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text(strings.providerName) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text(strings.baseUrl) },
                supportingText = { Text(strings.baseUrlHint) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text(if (initial == null) strings.apiKey else strings.apiKeyKeep) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { initial?.let { onTest(it.id) } },
                    enabled = initial != null,
                ) { Text(strings.test) }
                OutlinedButton(
                    onClick = { initial?.let { onDiscover(it.id) } },
                    enabled = initial != null,
                ) { Text(strings.loadModels) }
            }
            Button(
                onClick = {
                    val id = initial?.id ?: "provider-${System.currentTimeMillis()}"
                    val profile = ProviderProfile(
                        id = id,
                        displayName = displayName.trim(),
                        baseUrl = baseUrl.trim(),
                        credentialAlias = initial?.credentialAlias ?: "provider.$id",
                        protocol = protocol,
                    )
                    val key = if (apiKey.isBlank()) null else apiKey.toCharArray()
                    onSave(profile, key)
                    apiKey = ""
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(strings.save) }
            if (initial != null) {
                TextButton(
                    onClick = { onDeleteRequest(initial) },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(strings.delete, color = NitronTheme.colors.destructive)
                }
            }
        }
    }
}

@Composable
private fun WorkspaceEditor(
    workspace: com.nitronbox.app.data.model.Workspace,
    onSave: (com.nitronbox.app.data.model.Workspace) -> Unit,
) {
    val strings = LocalStrings.current
    var name by remember(workspace.id) { mutableStateOf(workspace.name) }
    var systemPrompt by remember(workspace.id) { mutableStateOf(workspace.systemPrompt) }
    var temperature by remember(workspace.id) { mutableStateOf(workspace.generation.temperature) }
    var maxOutputTokens by remember(workspace.id) { mutableStateOf(workspace.generation.maxOutputTokens.toString()) }
    var maxInputTokens by remember(workspace.id) { mutableStateOf(workspace.contextPolicy.maxInputTokens.toString()) }
    var reservedOutput by remember(workspace.id) { mutableStateOf(workspace.contextPolicy.reservedOutputTokens.toString()) }
    var strategy by remember(workspace.id) { mutableStateOf(workspace.contextPolicy.strategy) }
    var strategyMenuOpen by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .nitronSurface(SurfaceLevel.Raised, NitronTheme.shapes.medium)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(strings.workspaceName) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = systemPrompt,
            onValueChange = { systemPrompt = it },
            label = { Text(strings.systemPrompt) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(strings.temperature(temperature), style = MaterialTheme.typography.labelMedium, color = NitronTheme.colors.textSecondary)
        com.nitronbox.app.ui.components.NitronSlider(
            value = temperature,
            onValueChange = { temperature = it },
            valueRange = 0f..2f,
        )
        OutlinedTextField(
            value = maxOutputTokens,
            onValueChange = { maxOutputTokens = it.filter(Char::isDigit) },
            label = { Text(strings.maxOutputTokens) },
            supportingText = { Text(strings.unlimitedHint) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        HorizontalDivider(color = NitronTheme.colors.border)
        Text(strings.contextWindow, style = MaterialTheme.typography.titleSmall, color = NitronTheme.colors.textPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = maxInputTokens,
                onValueChange = { maxInputTokens = it.filter(Char::isDigit) },
                label = { Text(strings.maxInput) },
                supportingText = { Text(strings.unlimitedHint) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = reservedOutput,
                onValueChange = { reservedOutput = it.filter(Char::isDigit) },
                label = { Text(strings.reserveOutput) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Box {
            OutlinedTextField(
                value = strategy.name.lowercase().replace('_', ' '),
                onValueChange = {},
                readOnly = true,
                label = { Text(strings.overflowStrategy) },
                trailingIcon = {
                    IconButton(onClick = { strategyMenuOpen = true }) {
                        Icon(Icons.Rounded.ExpandMore, null, tint = NitronTheme.colors.textSecondary)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownMenu(expanded = strategyMenuOpen, onDismissRequest = { strategyMenuOpen = false }) {
                ContextOverflowStrategy.entries.forEach { entry ->
                    DropdownMenuItem(
                        text = { Text(entry.name.lowercase().replace('_', ' ')) },
                        onClick = {
                            strategy = entry
                            strategyMenuOpen = false
                        },
                    )
                }
            }
        }
        Button(
            onClick = {
                onSave(
                    workspace.copy(
                        name = name.trim(),
                        systemPrompt = systemPrompt,
                        generation = workspace.generation.copy(
                            temperature = temperature,
                            maxOutputTokens = maxOutputTokens.trim().toIntOrNull(),
                        ),
                        contextPolicy = workspace.contextPolicy.copy(
                            maxInputTokens = maxInputTokens.trim().toIntOrNull()
                                ?: com.nitronbox.app.data.model.ContextPolicy.UNLIMITED_CONTEXT_TOKENS,
                            reservedOutputTokens = reservedOutput.toIntOrNull() ?: 0,
                            strategy = strategy,
                        ),
                        updatedAtEpochMillis = System.currentTimeMillis(),
                    ),
                )
            },
            modifier = Modifier.align(Alignment.End),
        ) { Text(strings.saveWorkspace) }
    }
}
