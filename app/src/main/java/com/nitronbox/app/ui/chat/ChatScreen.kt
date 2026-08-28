package com.nitronbox.app.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.nitronbox.app.data.model.ChatMessage
import com.nitronbox.app.data.model.MessageRole
import com.nitronbox.app.data.model.MessageStatus
import com.nitronbox.app.ui.components.ConversationsPanel
import com.nitronbox.app.ui.components.NitronCenterDialog
import com.nitronbox.app.ui.components.SpinningLogo
import com.nitronbox.app.ui.components.TextButtonFlat
import com.nitronbox.app.ui.components.ModelPickerSheet
import com.nitronbox.app.ui.chat.components.MarkdownRenderer
import com.nitronbox.app.ui.i18n.LocalStrings
import com.nitronbox.app.ui.theme.LocalHazeState
import com.nitronbox.app.ui.theme.LocalUiFx
import com.nitronbox.app.ui.theme.NitronTheme
import com.nitronbox.app.ui.theme.SurfaceLevel
import com.nitronbox.app.ui.theme.WallpaperBackdrop
import com.nitronbox.app.ui.theme.nitronSurface
import com.nitronbox.app.ui.components.frostPanel
import com.nitronbox.app.ui.theme.pressableRipple
import dev.chrisbanes.haze.hazeSource

@Composable
fun ChatScreen(
    viewModel: ChatSessionViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val strings = LocalStrings.current
    val workspace by viewModel.activeWorkspace.collectAsState()
    val conversation by viewModel.activeConversation.collectAsState()
    val model by viewModel.activeModel.collectAsState()
    val draft by viewModel.draft.collectAsState()
    val streaming by viewModel.isStreaming.collectAsState()
    val pendingAttachments by viewModel.pendingAttachments.collectAsState()
    val contextUsage by viewModel.contextUsage.collectAsState()
    val messages = viewModel.messages.collectAsLazyPagingItems()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(snackbarHostState, modifier = Modifier.navigationBarsPadding())
        },
        containerColor = Color.Transparent,
    ) { scaffoldPadding ->
        val hazeState = androidx.compose.runtime.remember { dev.chrisbanes.haze.HazeState() }
        androidx.compose.runtime.CompositionLocalProvider(LocalHazeState provides hazeState) {
        var conversationsOpen by remember { mutableStateOf(false) }
        var modelPickerOpen by remember { mutableStateOf(false) }
        var renaming by remember { mutableStateOf<com.nitronbox.app.data.local.ConversationEntity?>(null) }
        var deleting by remember { mutableStateOf<com.nitronbox.app.data.local.ConversationEntity?>(null) }
        val fx = LocalUiFx.current
        val wallpaper by viewModel.wallpaper.collectAsState()
        val wallpaperImageUri by viewModel.wallpaperImageUri.collectAsState()

        // Blur the chat behind any overlay panel (RenderEffect on API 31+, no-op below).
        val blurProgress by animateFloatAsState(
            targetValue = if (modelPickerOpen || conversationsOpen) 1f else 0f,
            label = "blurProgress",
        )
        val blurRadius = if (fx.blurEnabled) fx.blurRadius.dp else 0.dp

        Box(
            Modifier
                .padding(scaffoldPadding)
                .fillMaxSize()
                .background(NitronTheme.colors.background),
        ) {
            // Content is the haze source; frosted surfaces above blur it for real.
            val listState = rememberLazyListState()
            // Parallax: the wallpaper trails the scroll for depth.
            val parallax by remember {
                androidx.compose.runtime.derivedStateOf {
                    (listState.firstVisibleItemIndex * 800 + listState.firstVisibleItemScrollOffset).toFloat()
                }
            }
            Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
                WallpaperBackdrop(
                    wallpaper,
                    wallpaperImageUri,
                    Modifier
                        .matchParentSize()
                        .graphicsLayer { translationY = parallax * 0.12f },
                )
                Column(Modifier.fillMaxSize()) {
                    LaunchedEffect(messages.itemCount) {
                        if (messages.itemCount > 0) listState.scrollToItem(0)
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        reverseLayout = true,
                        contentPadding = PaddingValues(
                            start = 14.dp,
                            end = 14.dp,
                            top = 86.dp,
                            bottom = 132.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        if (messages.itemCount == 0) {
                            item(key = "welcome") {
                                WelcomeCard(
                                    workspaceName = workspace?.name ?: "NitronBox",
                                    modelConfigured = model != null,
                                    onConfigureModels = onOpenSettings,
                                )
                            }
                        }
                        items(count = messages.itemCount, key = messages.itemKey(ChatMessage::id)) { index ->
                            val message = messages[index] ?: return@items
                            Box(Modifier.animateItem()) {
                                MessageBubble(
                                    message = message,
                                    onRegenerate = { viewModel.regenerate(message.id) },
                                    onDelete = { viewModel.deleteMessage(message.id) },
                                    onOpenAttachment = { attachment -> openAttachment(context, viewModel, attachment) },
                                    onCopy = { content ->
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("message", content))
                                    },
                                )
                            }
                        }
                    }
                }

                }

                ChatHeader(
                    title = conversation?.title ?: workspace?.name ?: "NitronBox",
                    modelLabel = model?.displayName ?: strings.noModelSelected,
                    onOpenConversations = { conversationsOpen = true },
                    onOpenModelPicker = { modelPickerOpen = true },
                    onNewConversation = viewModel::newConversation,
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = statusTopForHeader(), start = 14.dp, end = 14.dp),
                )

                Composer(
                    draft = draft,
                    pendingAttachments = pendingAttachments,
                    streaming = streaming,
                    modelLabel = model?.displayName ?: strings.noModelSelected,
                    contextUsage = contextUsage,
                    onDraftChange = viewModel::onDraftChange,
                    onSend = viewModel::send,
                    onStop = viewModel::stopGeneration,
                    onPickAttachments = viewModel::addAttachments,
                    onRemoveAttachment = viewModel::removeAttachment,
                    onOpenModelPicker = { modelPickerOpen = true },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                )
            }

            // Conversations overlay: scrim + sliding panel, fully custom (no system drawer).
            AnimatedVisibility(
                visible = conversationsOpen,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(180)),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable { conversationsOpen = false },
                )
            }
            AnimatedVisibility(
                visible = conversationsOpen,
                enter = slideInHorizontally(tween(260)) { -it } + fadeIn(tween(220)),
                exit = slideOutHorizontally(tween(220)) { -it } + fadeOut(tween(180)),
            ) {
                val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(top = statusTop, bottom = navigationBottom),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.88f)
                            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp)
                            .frostPanel(RoundedCornerShape(26.dp)),
                    ) {
                        ConversationsPanel(
                            viewModel = viewModel,
                            onRename = { renaming = it },
                            onDelete = { deleting = it },
                        )
                    }
                }
            }

            if (modelPickerOpen) {
                ModelPickerSheet(
                    viewModel = viewModel,
                    onDismiss = { modelPickerOpen = false },
                    onOpenSettings = {
                        modelPickerOpen = false
                        onOpenSettings()
                    },
                )
            }

            // Rename/delete dialogs live at screen level so they center over everything
            // and the drawer beneath them blurs.
            renaming?.let { conversation ->
                var title by remember(conversation.id) { mutableStateOf(conversation.title) }
                NitronCenterDialog(visible = true, onDismiss = { renaming = null }) {
                    Column(Modifier.padding(18.dp).fillMaxWidth(0.86f)) {
                        Text(strings.renameConversation, style = MaterialTheme.typography.titleMedium, color = NitronTheme.colors.textPrimary)
                        Spacer(Modifier.height(12.dp))
                        TextField(
                            value = title,
                            onValueChange = { title = it },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = NitronTheme.colors.surfaceMuted,
                                unfocusedContainerColor = NitronTheme.colors.surfaceMuted,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
                            TextButtonFlat(strings.cancel) { renaming = null }
                            TextButtonFlat(strings.save, enabled = title.isNotBlank(), accent = true) {
                                viewModel.renameConversation(conversation.id, title)
                                renaming = null
                            }
                        }
                    }
                }
            }
            deleting?.let { conversation ->
                NitronCenterDialog(visible = true, onDismiss = { deleting = null }) {
                    Column(Modifier.padding(18.dp).fillMaxWidth(0.86f)) {
                        Text(strings.deleteConversationTitle, style = MaterialTheme.typography.titleMedium, color = NitronTheme.colors.textPrimary)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            strings.deleteConversationBody(conversation.title),
                            style = MaterialTheme.typography.bodyMedium,
                            color = NitronTheme.colors.textSecondary,
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
                            TextButtonFlat(strings.cancel) { deleting = null }
                            TextButtonFlat(strings.delete, destructive = true) {
                                viewModel.deleteConversation(conversation.id)
                                deleting = null
                            }
                        }
                    }
                }
            }

            // Full-screen attachment viewer: images fit-to-screen, text files scrollable.
            viewModel.viewer.collectAsState().value?.let { view ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.94f))
                        .clickable { viewModel.closeViewer() },
                ) {
                    if (view.attachment.kind == com.nitronbox.app.data.model.AttachmentKind.IMAGE) {
                        coil.compose.AsyncImage(
                            model = view.attachment.persistedUri,
                            contentDescription = view.attachment.displayName,
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(18.dp),
                        )
                    } else {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .clickable(enabled = false) {}
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                        ) {
                            Text(
                                view.attachment.displayName,
                                style = MaterialTheme.typography.labelLarge,
                                color = NitronTheme.colors.accent,
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                view.text ?: "…",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                color = Color(0xFFDDDDDD),
                            )
                        }
                    }
                    IconButton(
                        onClick = { viewModel.closeViewer() },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = statusTopForViewer(), end = 8.dp),
                    ) {
                        Icon(Icons.Rounded.Close, "Close", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun statusTopForViewer(): androidx.compose.ui.unit.Dp =
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

/** In-app viewer for images and text-like files; other types open with an external app. */
private fun openAttachment(
    context: android.content.Context,
    viewModel: ChatSessionViewModel,
    attachment: com.nitronbox.app.data.model.AttachmentReference,
) {
    when (attachment.kind) {
        com.nitronbox.app.data.model.AttachmentKind.IMAGE ->
            viewModel.openAttachment(attachment, loadText = false)
        com.nitronbox.app.data.model.AttachmentKind.TEXT,
        com.nitronbox.app.data.model.AttachmentKind.CSV,
        com.nitronbox.app.data.model.AttachmentKind.JSON,
        com.nitronbox.app.data.model.AttachmentKind.CODE,
        -> viewModel.openAttachment(attachment, loadText = true)
        else -> {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(attachment.persistedUri), attachment.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(intent) }
                .onFailure {
                    android.widget.Toast.makeText(context, "No app can open this file", android.widget.Toast.LENGTH_SHORT).show()
                }
        }
    }
}

@Composable
private fun statusTopForHeader(): androidx.compose.ui.unit.Dp =
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

@Composable
private fun ChatHeader(
    title: String,
    modelLabel: String,
    onOpenConversations: () -> Unit,
    onOpenModelPicker: () -> Unit,
    onNewConversation: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    Row(
        modifier
            .frostPanel(NitronTheme.shapes.large)
            .border(1.dp, NitronTheme.colors.border, NitronTheme.shapes.large)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onOpenConversations) {
            Icon(Icons.Rounded.Menu, strings.conversations, tint = NitronTheme.colors.textPrimary)
        }
        Column(
            Modifier
                .weight(1f)
                .pressableRipple(shape = NitronTheme.shapes.small, onClick = onOpenModelPicker)
                .padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = NitronTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                modelLabel,
                style = MaterialTheme.typography.labelMedium,
                color = NitronTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onNewConversation) {
            Icon(Icons.Rounded.Add, strings.newConversation, tint = NitronTheme.colors.textPrimary)
        }
        IconButton(onOpenSettings) {
            Icon(Icons.Rounded.Settings, strings.settings, tint = NitronTheme.colors.textPrimary)
        }
    }
}

@Composable
private fun WelcomeCard(
    workspaceName: String,
    modelConfigured: Boolean,
    onConfigureModels: () -> Unit,
) {
    val strings = LocalStrings.current
    Column(
        Modifier
            .fillMaxWidth()
            .nitronSurface(SurfaceLevel.Raised, NitronTheme.shapes.large)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SpinningLogo(size = 44.dp)
            Spacer(Modifier.padding(6.dp))
            Text(strings.welcomeTitle, style = MaterialTheme.typography.headlineMedium, color = NitronTheme.colors.textPrimary)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            strings.welcomeBody(workspaceName),
            style = MaterialTheme.typography.bodyMedium,
            color = NitronTheme.colors.textSecondary,
        )
        AnimatedVisibility(!modelConfigured, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column {
                Spacer(Modifier.height(14.dp))
                Text(
                    strings.welcomeNoModel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NitronTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    strings.openSettings,
                    style = MaterialTheme.typography.labelLarge,
                    color = NitronTheme.colors.accent,
                    modifier = Modifier.pressableRipple(shape = NitronTheme.shapes.small, onClick = onConfigureModels),
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    onRegenerate: () -> Unit,
    onDelete: () -> Unit,
    onOpenAttachment: (com.nitronbox.app.data.model.AttachmentReference) -> Unit,
    onCopy: (String) -> Unit,
) {
    val strings = LocalStrings.current
    val isUser = message.role == MessageRole.USER
    var actionsOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    fun copyText() {
        onCopy(message.content)
        android.widget.Toast.makeText(context, strings.copied, android.widget.Toast.LENGTH_SHORT).show()
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth(if (isUser) 0.85f else 0.96f)
                .then(
                    if (isUser) {
                        Modifier.background(NitronTheme.colors.userBubble, NitronTheme.shapes.large)
                    } else {
                        Modifier.frostPanel(NitronTheme.shapes.large)
                    },
                )
                .combinedClickable(
                    onClick = {},
                    onLongClick = { actionsOpen = true },
                )
                .padding(horizontal = 15.dp, vertical = 12.dp),
        ) {
            if (message.status == MessageStatus.FAILED) {
                Text(
                    strings.generationFailed,
                    style = MaterialTheme.typography.labelMedium,
                    color = NitronTheme.colors.destructive,
                )
                Spacer(Modifier.height(6.dp))
            }
            if (isUser) {
                Text(message.content, style = MaterialTheme.typography.bodyLarge, color = NitronTheme.colors.textPrimary)
            } else {
                if (!message.reasoning.isNullOrBlank()) {
                    var reasoningOpen by remember(message.id) { mutableStateOf(false) }
                    Row(
                        Modifier
                            .pressableRipple(shape = NitronTheme.shapes.small) { reasoningOpen = !reasoningOpen }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            (if (reasoningOpen) "▾ " else "▸ ") + strings.thinking,
                            style = MaterialTheme.typography.labelMedium,
                            color = NitronTheme.colors.textTertiary,
                        )
                    }
                    AnimatedVisibility(visible = reasoningOpen) {
                        Text(
                            message.reasoning!!,
                            style = MaterialTheme.typography.bodySmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                            color = NitronTheme.colors.textSecondary,
                            modifier = Modifier.padding(top = 6.dp, start = 2.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (message.content.isEmpty() && message.status == MessageStatus.STREAMING) {
                    StreamingDots(strings.thinking)
                } else {
                    val toolLines = remember(message.content) {
                        message.content.lines().filter { it.trimStart().startsWith("\u27e6tool\u27e7") }
                    }
                    val markdownText = remember(message.content) {
                        message.content.lines()
                            .filterNot { it.trimStart().startsWith("\u27e6tool\u27e7") }
                            .joinToString("\n")
                    }
                    if (toolLines.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            toolLines.forEach { line ->
                                val label = line.trimStart().removePrefix("\u27e6tool\u27e7 ").trim()
                                Row(
                                    Modifier
                                        .nitronSurface(SurfaceLevel.Muted, NitronTheme.shapes.pill)
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Rounded.Build,
                                        null,
                                        tint = NitronTheme.colors.accent,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(Modifier.padding(3.dp))
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = NitronTheme.colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    MarkdownRenderer(
                        markdown = message.content + if (message.status == MessageStatus.STREAMING) " ▍" else "",
                        onLinkClick = { url ->
                            runCatching {
                                val uri = Uri.parse(url)
                                if (uri.scheme in setOf("http", "https")) {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                }
                            }
                        },
                    )
                }
            }
            AnimatedVisibility(message.attachments.isNotEmpty()) {
                Column {
                    message.attachments.forEach { attachment ->
                        Spacer(Modifier.height(8.dp))
                        if (attachment.kind == com.nitronbox.app.data.model.AttachmentKind.IMAGE) {
                            // Inline preview: tap to open the full-screen viewer.
                            coil.compose.AsyncImage(
                                model = attachment.persistedUri,
                                contentDescription = attachment.displayName,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp)
                                    .clip(NitronTheme.shapes.medium)
                                    .combinedClickable(
                                        onClick = { onOpenAttachment(attachment) },
                                        onLongClick = { copyText() },
                                    ),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${attachment.displayName}  ·  ${formatBytes(attachment.byteSize)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = NitronTheme.colors.textTertiary,
                            )
                        } else {
                            AttachmentChip(attachment.displayName, attachment.byteSize) {
                                onOpenAttachment(attachment)
                            }
                        }
                    }
                }
            }
            if (message.status == MessageStatus.FAILED && message.errorText != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    message.errorText,
                    style = MaterialTheme.typography.bodySmall,
                    color = NitronTheme.colors.textSecondary,
                )
            }
            if (message.status != MessageStatus.STREAMING) {
                // Stats on the left, the ⋯ actions button right after them.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    val stats = statsLine(message)
                    if (stats != null) {
                        Text(
                            stats,
                            style = MaterialTheme.typography.labelSmall,
                            color = NitronTheme.colors.textTertiary,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    Box(contentAlignment = Alignment.CenterEnd) {
                        DropdownMenu(expanded = actionsOpen, onDismissRequest = { actionsOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(strings.copyText) },
                                onClick = {
                                    actionsOpen = false
                                    copyText()
                                },
                            )
                            if (!isUser) {
                                DropdownMenuItem(
                                    text = { Text(if (message.status == MessageStatus.FAILED) strings.retry else strings.regenerate) },
                                    onClick = {
                                        actionsOpen = false
                                        onRegenerate()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(strings.deleteMessage, color = NitronTheme.colors.destructive) },
                                onClick = {
                                    actionsOpen = false
                                    onDelete()
                                },
                            )
                        }
                        IconButton(
                            onClick = { actionsOpen = true },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                strings.conversationActions,
                                tint = NitronTheme.colors.textTertiary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** tok/s, response time and token counts, when the provider reported them. */
private fun statsLine(message: ChatMessage): String? {
    if (message.status != MessageStatus.COMPLETE) return null
    val durationSeconds = message.generationDurationMillis?.let { it / 1_000.0 } ?: return null
    val parts = buildList {
        val tokens = message.outputTokens
        if (tokens != null && tokens > 0 && durationSeconds > 0.05) {
            add("%.1f tok/s".format(tokens / durationSeconds))
        }
        add("%.1f s".format(durationSeconds))
        message.outputTokens?.let { add("$it tok") }
        message.inputTokens?.let { add("in $it") }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString("  ·  ")
}

@Composable
private fun StreamingDots(label: String) {
    val transition = rememberInfiniteTransition(label = "streaming")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "dotAlpha",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.padding(end = 5.dp).size(7.dp).background(NitronTheme.colors.accent.copy(alpha = alpha), CircleShape))
        Box(Modifier.padding(end = 5.dp).size(7.dp).background(NitronTheme.colors.accent.copy(alpha = alpha * 0.7f), CircleShape))
        Box(Modifier.padding(end = 8.dp).size(7.dp).background(NitronTheme.colors.accent.copy(alpha = alpha * 0.4f), CircleShape))
        Text(label, style = MaterialTheme.typography.labelMedium, color = NitronTheme.colors.textSecondary)
    }
}

/** Small circular gauge showing how much of the context window is used. */
@Composable
private fun ContextRing(progress: Float, modifier: Modifier = Modifier) {
    val ringColor = when {
        progress > 0.85f -> NitronTheme.colors.destructive
        progress > 0.6f -> NitronTheme.colors.accent
        else -> NitronTheme.colors.textTertiary
    }
    Canvas(modifier.size(24.dp)) {
        val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        drawArc(
            color = ringColor.copy(alpha = 0.22f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = stroke,
        )
        if (progress > 0.01f) {
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = stroke,
            )
        }
    }
}

@Composable
private fun Composer(
    draft: String,
    pendingAttachments: List<com.nitronbox.app.data.model.AttachmentReference>,
    streaming: Boolean,
    modelLabel: String,
    contextUsage: Float,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onPickAttachments: (List<Uri>) -> Unit,
    onRemoveAttachment: (com.nitronbox.app.data.model.AttachmentReference) -> Unit,
    onOpenModelPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val pickDocuments = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onPickAttachments(uris)
    }
    val canSend = draft.isNotBlank() || pendingAttachments.isNotEmpty()

    Column(
        modifier
            .fillMaxWidth()
            .frostPanel(NitronTheme.shapes.extraLarge)
            .padding(7.dp),
    ) {
        AnimatedVisibility(
            visible = pendingAttachments.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 2.dp, bottom = 6.dp),
            ) {
                items(pendingAttachments, key = com.nitronbox.app.data.model.AttachmentReference::id) { attachment ->
                    AttachmentChip(attachment.displayName, attachment.byteSize) { onRemoveAttachment(attachment) }
                }
            }
        }
        TextField(
            value = draft,
            onValueChange = onDraftChange,
            placeholder = { Text(strings.messageYourWorkspace, color = NitronTheme.colors.textSecondary) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = NitronTheme.colors.textPrimary),
            minLines = 1,
            maxLines = 6,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { pickDocuments.launch(arrayOf("*/*")) }, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Rounded.AttachFile,
                    strings.attachFile,
                    tint = NitronTheme.colors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
            // Model selector lives next to the attach button, always one tap away.
            Row(
                Modifier
                    .weight(1f, fill = false)
                    .nitronSurface(SurfaceLevel.Muted, NitronTheme.shapes.pill)
                    .pressableRipple(shape = NitronTheme.shapes.pill, onClick = onOpenModelPicker)
                    .padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    null,
                    tint = NitronTheme.colors.accent,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.padding(3.dp))
                Text(
                    modelLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = NitronTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 150.dp),
                )
                Icon(
                    Icons.Rounded.ExpandMore,
                    null,
                    tint = NitronTheme.colors.textTertiary,
                    modifier = Modifier.size(15.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            ContextRing(contextUsage)
            Spacer(Modifier.padding(3.dp))
            Box(
                Modifier
                    .size(42.dp)
                    .background(
                        if (streaming) NitronTheme.colors.destructive else NitronTheme.colors.primary,
                        CircleShape,
                    )
                    .pressableRipple(enabled = streaming || canSend, shape = CircleShape) {
                        if (streaming) onStop() else onSend()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (streaming) Icons.Rounded.Stop else Icons.Rounded.ArrowUpward,
                    if (streaming) strings.stopGenerating else strings.send,
                    tint = if (streaming || canSend) NitronTheme.colors.onPrimary else NitronTheme.colors.textTertiary,
                    modifier = Modifier.size(21.dp),
                )
            }
        }
    }
}

@Composable
fun AttachmentChip(name: String, byteSize: Long, onRemove: (() -> Unit)?) {
    Row(
        Modifier
            .nitronSurface(SurfaceLevel.Muted, NitronTheme.shapes.small)
            .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f, fill = false)) {
            Text(
                name,
                style = MaterialTheme.typography.labelMedium,
                color = NitronTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatBytes(byteSize),
                style = MaterialTheme.typography.labelSmall,
                color = NitronTheme.colors.textTertiary,
            )
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Rounded.Close, "Remove", tint = NitronTheme.colors.textSecondary, modifier = Modifier.size(15.dp))
            }
        }
    }
}

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024 -> "%.0f KB".format(bytes / 1_024.0)
    else -> "$bytes B"
}
