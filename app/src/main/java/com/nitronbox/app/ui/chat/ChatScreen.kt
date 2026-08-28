package com.nitronbox.app.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.nitronbox.app.data.model.ChatMessage
import com.nitronbox.app.data.model.MessageRole
import com.nitronbox.app.data.model.MessageStatus
import com.nitronbox.app.ui.components.ConversationsPanel
import com.nitronbox.app.ui.components.ModelPickerSheet
import com.nitronbox.app.ui.chat.components.MarkdownRenderer
import com.nitronbox.app.ui.theme.NitronBackground
import com.nitronbox.app.ui.theme.NitronTheme
import com.nitronbox.app.ui.theme.SurfaceLevel
import com.nitronbox.app.ui.theme.nitronSurface
import com.nitronbox.app.ui.theme.pressableRipple
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    viewModel: ChatSessionViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val workspace by viewModel.activeWorkspace.collectAsState()
    val conversation by viewModel.activeConversation.collectAsState()
    val model by viewModel.activeModel.collectAsState()
    val draft by viewModel.draft.collectAsState()
    val streaming by viewModel.isStreaming.collectAsState()
    val pendingAttachments by viewModel.pendingAttachments.collectAsState()
    val messages = viewModel.messages.collectAsLazyPagingItems()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    Scaffold(
        modifier = modifier,
        // Inset handling is done explicitly by the header, list, and composer; a zero-inset
        // scaffold prevents double navigation-bar padding while the composer rides the IME.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier.navigationBarsPadding(),
            )
        },
        containerColor = Color.Transparent,
    ) { scaffoldPadding ->
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ConversationsPanel(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxWidth(0.86f),
                )
            },
        ) {
            NitronBackground(Modifier.padding(scaffoldPadding)) {
                var modelPickerOpen by remember { mutableStateOf(false) }
                val openConversations: () -> Unit = { scope.launch { drawerState.open() } }

            val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()

            val listState = rememberLazyListState()
            LaunchedEffect(messages.itemCount) {
                if (messages.itemCount > 0) listState.scrollToItem(0)
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                reverseLayout = true,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 14.dp,
                    end = 14.dp,
                    top = statusTop + 72.dp,
                    bottom = maxOf(navigationBottom, imeBottom) + 128.dp,
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
                    MessageBubble(
                        message = message,
                        streaming = streaming,
                        onRegenerate = { viewModel.regenerate(message.id) },
                        onCopy = { content ->
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("message", content))
                        },
                    )
                }
            }

            ChatHeader(
                title = conversation?.title ?: workspace?.name ?: "NitronBox",
                modelLabel = model?.displayName ?: "No model selected",
                onOpenConversations = openConversations,
                onOpenModelPicker = { modelPickerOpen = true },
                onNewConversation = { viewModel.newConversation() },
                onOpenSettings = onOpenSettings,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = statusTop + 6.dp, start = 14.dp, end = 14.dp),
            )

            Composer(
                draft = draft,
                pendingAttachments = pendingAttachments,
                streaming = streaming,
                onDraftChange = viewModel::onDraftChange,
                onSend = viewModel::send,
                onStop = viewModel::stopGeneration,
                onPickAttachments = viewModel::addAttachments,
                onRemoveAttachment = viewModel::removeAttachment,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
            )

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
            }
        }
    }
}

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
    Row(
        modifier
            .fillMaxWidth()
            .nitronSurface(SurfaceLevel.Overlay, NitronTheme.shapes.large)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onOpenConversations) {
            Icon(Icons.Rounded.Menu, "Conversations", tint = NitronTheme.colors.textPrimary)
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
            Icon(Icons.Rounded.Add, "New conversation", tint = NitronTheme.colors.textPrimary)
        }
        IconButton(onOpenSettings) {
            Icon(Icons.Rounded.Settings, "Settings", tint = NitronTheme.colors.textPrimary)
        }
    }
}

@Composable
private fun WelcomeCard(
    workspaceName: String,
    modelConfigured: Boolean,
    onConfigureModels: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .nitronSurface(SurfaceLevel.Raised, NitronTheme.shapes.large)
            .padding(20.dp),
    ) {
        Text("What can we create?", style = MaterialTheme.typography.headlineMedium, color = NitronTheme.colors.textPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            "$workspaceName keeps every conversation isolated and routes requests through your preferred models.",
            style = MaterialTheme.typography.bodyMedium,
            color = NitronTheme.colors.textSecondary,
        )
        if (!modelConfigured) {
            Spacer(Modifier.height(14.dp))
            Text(
                "No model is selected yet. Add a provider and pick a model to start chatting.",
                style = MaterialTheme.typography.bodyMedium,
                color = NitronTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Open Settings",
                style = MaterialTheme.typography.labelLarge,
                color = NitronTheme.colors.accent,
                modifier = Modifier.pressableRipple(shape = NitronTheme.shapes.small, onClick = onConfigureModels),
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    streaming: Boolean,
    onRegenerate: () -> Unit,
    onCopy: (String) -> Unit,
) {
    val isUser = message.role == MessageRole.USER
    var actionsOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current

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
                        Modifier.nitronSurface(SurfaceLevel.Raised, NitronTheme.shapes.large)
                    },
                )
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        onCopy(message.content)
                        android.widget.Toast.makeText(context, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                    },
                )
                .padding(horizontal = 15.dp, vertical = 12.dp),
        ) {
            if (message.status == MessageStatus.FAILED) {
                Text(
                    "Generation failed",
                    style = MaterialTheme.typography.labelMedium,
                    color = NitronTheme.colors.destructive,
                )
                Spacer(Modifier.height(6.dp))
            }
            if (isUser) {
                Text(message.content, style = MaterialTheme.typography.bodyLarge, color = NitronTheme.colors.textPrimary)
            } else {
                if (message.content.isEmpty() && message.status == MessageStatus.STREAMING) {
                    StreamingDots()
                } else {
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
            message.attachments.forEach { attachment ->
                Spacer(Modifier.height(6.dp))
                AttachmentChip(attachment.displayName, attachment.byteSize, onRemove = null)
            }
            when {
                message.status == MessageStatus.FAILED && message.errorText != null -> {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        message.errorText,
                        style = MaterialTheme.typography.bodySmall,
                        color = NitronTheme.colors.textSecondary,
                    )
                }
                message.outputTokens != null && !isUser -> {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        listOfNotNull(message.modelId, "${message.outputTokens} tok")
                            .joinToString("  ·  "),
                        style = MaterialTheme.typography.labelSmall,
                        color = NitronTheme.colors.textTertiary,
                    )
                }
            }
            if (!isUser && message.status != MessageStatus.STREAMING) {
                Box(contentAlignment = Alignment.CenterEnd) {
                    DropdownMenu(expanded = actionsOpen, onDismissRequest = { actionsOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Copy text") },
                            onClick = {
                                actionsOpen = false
                                onCopy(message.content)
                                android.widget.Toast.makeText(context, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(if (message.status == MessageStatus.FAILED) "Retry" else "Regenerate") },
                            onClick = {
                                actionsOpen = false
                                onRegenerate()
                            },
                        )
                    }
                    IconButton(
                        onClick = { actionsOpen = true },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            "Message actions",
                            tint = NitronTheme.colors.textTertiary,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingDots() {
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
        Text(
            "Thinking…",
            style = MaterialTheme.typography.labelMedium,
            color = NitronTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun Composer(
    draft: String,
    pendingAttachments: List<com.nitronbox.app.data.model.AttachmentReference>,
    streaming: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onPickAttachments: (List<Uri>) -> Unit,
    onRemoveAttachment: (com.nitronbox.app.data.model.AttachmentReference) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pickDocuments = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onPickAttachments(uris)
    }
    val canSend = draft.isNotBlank() || pendingAttachments.isNotEmpty()

    Column(
        modifier
            .fillMaxWidth()
            .nitronSurface(SurfaceLevel.Overlay, NitronTheme.shapes.extraLarge)
            .padding(7.dp),
    ) {
        if (pendingAttachments.isNotEmpty()) {
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
            placeholder = { Text("Message your workspace…", color = NitronTheme.colors.textSecondary) },
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
                    "Attach file",
                    tint = NitronTheme.colors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(42.dp)
                    .background(
                        if (streaming) NitronTheme.colors.destructive else NitronTheme.colors.primary,
                        CircleShape,
                    )
                    .pressableRipple(
                        enabled = streaming || canSend,
                        shape = CircleShape,
                    ) {
                        if (streaming) onStop() else onSend()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (streaming) Icons.Rounded.Stop else Icons.Rounded.ArrowUpward,
                    if (streaming) "Stop generating" else "Send",
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
                Icon(Icons.Rounded.Close, "Remove attachment", tint = NitronTheme.colors.textSecondary, modifier = Modifier.size(15.dp))
            }
        }
    }
}

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024 -> "%.0f KB".format(bytes / 1_024.0)
    else -> "$bytes B"
}
