package com.nitronbox.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nitronbox.app.ui.chat.ChatSessionViewModel
import com.nitronbox.app.ui.i18n.LocalStrings
import com.nitronbox.app.ui.theme.NitronTheme
import com.nitronbox.app.ui.theme.SurfaceLevel
import com.nitronbox.app.ui.theme.nitronSurface
import com.nitronbox.app.ui.theme.pressableRipple

/**
 * Model selection: providers grouped with their live catalogs from each provider's discovery
 * endpoint. Catalogs load automatically on open; selection becomes the generation target.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    viewModel: ChatSessionViewModel,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit = {},
) {
    val strings = LocalStrings.current
    val providers by viewModel.providers.collectAsState()
    val discovered by viewModel.discoveredModels.collectAsState()
    val activeModel by viewModel.activeModel.collectAsState()

    LaunchedEffect(providers.size) {
        if (providers.isNotEmpty()) viewModel.refreshAllModels()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = NitronTheme.colors.background) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(strings.selectModel, style = MaterialTheme.typography.headlineSmall, color = NitronTheme.colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                strings.noProvidersHint,
                style = MaterialTheme.typography.bodySmall,
                color = NitronTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                if (providers.isEmpty()) {
                    item {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .nitronSurface(SurfaceLevel.Raised, NitronTheme.shapes.medium)
                                .padding(18.dp),
                        ) {
                            Text(
                                strings.noProviders,
                                style = MaterialTheme.typography.titleSmall,
                                color = NitronTheme.colors.textPrimary,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                strings.noProvidersHint,
                                style = MaterialTheme.typography.bodyMedium,
                                color = NitronTheme.colors.textSecondary,
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = onOpenSettings) {
                                Icon(Icons.Rounded.Settings, null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.padding(3.dp))
                                Text(strings.addProvider)
                            }
                        }
                    }
                }
                items(providers, key = { it.id }) { provider ->
                    var expanded by remember(provider.id) { mutableStateOf(true) }
                    val models = discovered[provider.id].orEmpty()
                    LaunchedEffect(provider.id) {
                        if (models.isEmpty()) viewModel.refreshModels(provider.id)
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .nitronSurface(SurfaceLevel.Raised, NitronTheme.shapes.medium),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .pressableRipple(shape = NitronTheme.shapes.medium) { expanded = !expanded }
                                .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    provider.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = NitronTheme.colors.textPrimary,
                                )
                                Text(
                                    when {
                                        models.isNotEmpty() -> strings.modelsCount(models.size)
                                        expanded -> strings.loadingModels
                                        else -> strings.tapToLoadModels
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NitronTheme.colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            AnimatedVisibility(expanded && models.isEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.padding(4.dp))
                                }
                            }
                            IconButton(onClick = { viewModel.refreshModels(provider.id) }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Rounded.Refresh, strings.refreshModels, tint = NitronTheme.colors.textSecondary, modifier = Modifier.size(18.dp))
                            }
                            Icon(
                                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                null,
                                tint = NitronTheme.colors.textSecondary,
                                modifier = Modifier.padding(end = 10.dp).size(20.dp),
                            )
                        }
                        AnimatedVisibility(expanded && models.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                            Column {
                                HorizontalDivider(color = NitronTheme.colors.border)
                                models.forEach { model ->
                                    val selected = activeModel?.providerId == provider.id && activeModel?.modelId == model.id
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .pressableRipple(shape = NitronTheme.shapes.small) {
                                                viewModel.setActiveModel(
                                                    com.nitronbox.app.data.settings.ActiveModel(
                                                        providerId = provider.id,
                                                        modelId = model.id,
                                                        displayName = model.displayName,
                                                    ),
                                                )
                                                onDismiss()
                                            }
                                            .padding(start = 22.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                                    ) {
                                        Text(
                                            model.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (selected) NitronTheme.colors.accent else NitronTheme.colors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (selected) {
                                            Text(strings.active, style = MaterialTheme.typography.labelSmall, color = NitronTheme.colors.accent)
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
}
