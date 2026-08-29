package com.nitronbox.app.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nitronbox.app.data.local.ConversationEntity
import com.nitronbox.app.ui.chat.ChatSessionViewModel
import com.nitronbox.app.ui.i18n.LocalStrings
import com.nitronbox.app.ui.theme.NitronTheme
import com.nitronbox.app.ui.theme.SurfaceLevel
import com.nitronbox.app.ui.theme.nitronSurface
import com.nitronbox.app.ui.theme.pressableRipple
import java.text.DateFormat
import java.util.Date

/**
 * Conversations drawer content: brand mark, Chats/Creator sections, workspace switcher,
 * rename/delete via custom dark dialogs. Rounded corners and blur come from the host overlay.
 */
@Composable
fun ConversationsPanel(
    viewModel: ChatSessionViewModel,
    modifier: Modifier = Modifier,
    onRename: (ConversationEntity) -> Unit = {},
    onDelete: (ConversationEntity) -> Unit = {},
) {
    val strings = LocalStrings.current
    val creatorMode by viewModel.creatorMode.collectAsState()
    val normalConversations by viewModel.normalConversations.collectAsState(initial = emptyList())
    val creatorConversations by viewModel.creatorConversations.collectAsState(initial = emptyList())
    val activeConversation by viewModel.activeConversation.collectAsState()
    val workspaces by viewModel.workspaces.collectAsState()
    val activeWorkspace by viewModel.activeWorkspace.collectAsState()
    val creatorFolder by viewModel.creatorFolderUri.collectAsState()
    val creatorFolders by viewModel.creatorFolders.collectAsState()
    val visibleCreatorChats = creatorConversations.filter { it.folderUri == creatorFolder }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::pickCreatorFolder)
    }

    Column(
        modifier
            .fillMaxSize()
            .background(NitronTheme.colors.background)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SpinningLogo(size = 30.dp)
            Spacer(Modifier.padding(4.dp))
            Text("NitronBox", style = MaterialTheme.typography.titleMedium, color = NitronTheme.colors.textPrimary)
        }
        Spacer(Modifier.height(12.dp))

        AnimatedSegmented(
            options = listOf(strings.chatsTab to false, strings.creator to true),
            selected = creatorMode,
            onSelect = viewModel::setCreatorMode,
        )
        Spacer(Modifier.height(10.dp))

        if (creatorMode) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .nitronSurface(SurfaceLevel.Raised, NitronTheme.shapes.medium)
                    .padding(10.dp),
            ) {
                // Every added folder is listed; the selected one is used for new chats.
                creatorFolders.forEach { folder ->
                    val selected = folder == creatorFolder
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .pressableRipple(shape = NitronTheme.shapes.small) { viewModel.selectCreatorFolder(folder) }
                            .padding(horizontal = 4.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Folder,
                            null,
                            tint = if (selected) NitronTheme.colors.accent else NitronTheme.colors.textTertiary,
                            modifier = Modifier.height(15.dp),
                        )
                        Spacer(Modifier.padding(3.dp))
                        Text(
                            viewModel.folderDisplayName(folder),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) NitronTheme.colors.textPrimary else NitronTheme.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Text(strings.active, style = MaterialTheme.typography.labelSmall, color = NitronTheme.colors.accent)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "+ " + strings.chooseFolder,
                    style = MaterialTheme.typography.labelLarge,
                    color = NitronTheme.colors.accent,
                    modifier = Modifier.pressableRipple(shape = NitronTheme.shapes.small) { folderPicker.launch(null) },
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        Text(
            strings.workspaces,
            style = MaterialTheme.typography.labelMedium,
            color = NitronTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(6.dp))
        workspaces.forEach { workspace ->
            val selected = workspace.id == activeWorkspace?.id
            Text(
                workspace.name,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) NitronTheme.colors.accent else NitronTheme.colors.textPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .pressableRipple(shape = NitronTheme.shapes.small) { viewModel.selectWorkspace(workspace.id) }
                    .padding(horizontal = 8.dp, vertical = 7.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (creatorMode) strings.creator else strings.conversations,
                style = MaterialTheme.typography.labelMedium,
                color = NitronTheme.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = viewModel::newConversation, modifier = Modifier.height(32.dp)) {
                Icon(Icons.Rounded.Add, strings.newConversation, tint = NitronTheme.colors.textPrimary, modifier = Modifier.height(18.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        val visibleConversations = if (creatorMode) visibleCreatorChats else normalConversations
        if (creatorMode && creatorFolder == null) {
            Text(
                strings.noFolderHint,
                style = MaterialTheme.typography.bodySmall,
                color = NitronTheme.colors.textTertiary,
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(visibleConversations, key = ConversationEntity::id) { conversation ->
                val selected = conversation.id == activeConversation?.id
                var actionsOpen by remember { mutableStateOf(false) }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .animateItem()
                        .nitronSurface(
                            if (selected) SurfaceLevel.Muted else SurfaceLevel.Base,
                            NitronTheme.shapes.small,
                        )
                        .pressableRipple(shape = NitronTheme.shapes.small) {
                            viewModel.selectConversation(conversation.id)
                        }
                        .padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            conversation.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = NitronTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                .format(Date(conversation.updatedAtEpochMillis)),
                            style = MaterialTheme.typography.labelSmall,
                            color = NitronTheme.colors.textTertiary,
                        )
                    }
                    Box {
                        IconButton(onClick = { actionsOpen = true }, modifier = Modifier.height(30.dp)) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                strings.conversationActions,
                                tint = NitronTheme.colors.textTertiary,
                                modifier = Modifier.height(16.dp),
                            )
                        }
                        DropdownMenu(expanded = actionsOpen, onDismissRequest = { actionsOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(strings.rename) },
                                leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                                onClick = {
                                    actionsOpen = false
                                    onRename(conversation)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(strings.delete) },
                                leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = NitronTheme.colors.destructive) },
                                onClick = {
                                    actionsOpen = false
                                    onDelete(conversation)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Flat text button used inside custom dialogs. */
@Composable
fun TextButtonFlat(
    label: String,
    enabled: Boolean = true,
    accent: Boolean = false,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val color = when {
        destructive -> NitronTheme.colors.destructive
        accent -> NitronTheme.colors.accent
        else -> NitronTheme.colors.textSecondary
    }
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (enabled) color else color.copy(alpha = 0.4f),
        modifier = Modifier
            .pressableRipple(shape = NitronTheme.shapes.small, enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}
