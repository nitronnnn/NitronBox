package com.nitronbox.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nitronbox.app.data.local.ConversationEntity
import com.nitronbox.app.ui.chat.ChatSessionViewModel
import com.nitronbox.app.ui.theme.NitronTheme
import com.nitronbox.app.ui.theme.SurfaceLevel
import com.nitronbox.app.ui.theme.nitronSurface
import com.nitronbox.app.ui.theme.pressableRipple
import java.text.DateFormat
import java.util.Date

/** Side panel: workspace switcher plus conversation list with rename and delete actions. */
@Composable
fun ConversationsPanel(
    viewModel: ChatSessionViewModel,
    modifier: Modifier = Modifier,
) {
    val conversations by viewModel.conversations.collectAsState()
    val activeConversation by viewModel.activeConversation.collectAsState()
    val workspaces by viewModel.workspaces.collectAsState()
    val activeWorkspace by viewModel.activeWorkspace.collectAsState()

    var renaming by remember { mutableStateOf<ConversationEntity?>(null) }
    var deleting by remember { mutableStateOf<ConversationEntity?>(null) }

    ModalDrawerSheet(modifier = modifier, drawerContainerColor = NitronTheme.colors.background) {
        Column(Modifier.padding(14.dp)) {
            Text("Workspaces", style = MaterialTheme.typography.labelMedium, color = NitronTheme.colors.textSecondary)
            Spacer(Modifier.height(8.dp))
            workspaces.forEach { workspace ->
                val selected = workspace.id == activeWorkspace?.id
                Text(
                    workspace.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) NitronTheme.colors.accent else NitronTheme.colors.textPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressableRipple(shape = NitronTheme.shapes.small) { viewModel.selectWorkspace(workspace.id) }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Conversations",
                    style = MaterialTheme.typography.labelMedium,
                    color = NitronTheme.colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = viewModel::newConversation, modifier = Modifier.height(32.dp)) {
                    Icon(Icons.Rounded.Add, "New conversation", tint = NitronTheme.colors.textPrimary, modifier = Modifier.height(18.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(conversations, key = ConversationEntity::id) { conversation ->
                    val selected = conversation.id == activeConversation?.id
                    var actionsOpen by remember { mutableStateOf(false) }
                    Row(
                        Modifier
                            .fillMaxWidth()
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
                                    "Conversation actions",
                                    tint = NitronTheme.colors.textTertiary,
                                    modifier = Modifier.height(16.dp),
                                )
                            }
                            DropdownMenu(expanded = actionsOpen, onDismissRequest = { actionsOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                                    onClick = {
                                        actionsOpen = false
                                        renaming = conversation
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = NitronTheme.colors.destructive) },
                                    onClick = {
                                        actionsOpen = false
                                        deleting = conversation
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    renaming?.let { conversation ->
        var title by remember(conversation.id) { mutableStateOf(conversation.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename conversation") },
            text = {
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
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameConversation(conversation.id, title)
                        renaming = null
                    },
                    enabled = title.isNotBlank(),
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }

    deleting?.let { conversation ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete conversation?") },
            text = { Text("“${conversation.title}” and all of its messages will be removed permanently.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteConversation(conversation.id)
                    deleting = null
                }) { Text("Delete", color = NitronTheme.colors.destructive) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}
