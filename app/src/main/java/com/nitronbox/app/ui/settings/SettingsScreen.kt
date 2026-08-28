package com.nitronbox.app.ui.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandLess
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nitronbox.app.data.model.ContextOverflowStrategy
import com.nitronbox.app.data.remote.ProviderProfile
import com.nitronbox.app.data.remote.ProviderProtocol
import com.nitronbox.app.ui.theme.NitronTheme
import com.nitronbox.app.ui.theme.SurfaceLevel
import com.nitronbox.app.ui.theme.nitronSurface
import com.nitronbox.app.ui.theme.pressableRipple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val providers by viewModel.providers.collectAsState()
    val discovered by viewModel.discoveredModels.collectAsState()
    val health by viewModel.providerHealth.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val workspace by viewModel.activeWorkspace.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    var editingProvider by remember { mutableStateOf<ProviderProfile?>(null) }
    var creatingProvider by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Providers",
                        style = MaterialTheme.typography.titleLarge,
                        color = NitronTheme.colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    if (busy) CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                    IconButton(onClick = { creatingProvider = true }) {
                        Icon(Icons.Rounded.Add, "Add provider", tint = NitronTheme.colors.textPrimary)
                    }
                }
            }
            items(providers.size) { index ->
                val profile = providers[index]
                ProviderCard(
                    profile = profile,
                    health = health[profile.id],
                    models = discovered[profile.id].orEmpty(),
                    onEdit = { editingProvider = profile },
                )
            }
            item {
                Text(
                    "Tap a provider to edit its endpoint and API key. API keys are stored in the Android Keystore and never leave the device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NitronTheme.colors.textTertiary,
                )
            }
            item { HorizontalDivider(color = NitronTheme.colors.border) }
            item {
                Text(
                    "Workspace",
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

    if (creatingProvider || editingProvider != null) {
        ProviderEditorDialog(
            initial = editingProvider,
            onDismiss = {
                creatingProvider = false
                editingProvider = null
            },
            onSave = { profile, key ->
                viewModel.saveProvider(profile, key)
                creatingProvider = false
                editingProvider = null
            },
            onTest = { profileId -> viewModel.testProvider(profileId) },
            onDiscover = { profileId -> viewModel.discoverModels(profileId) },
            onDelete = { profileId ->
                viewModel.deleteProvider(profileId)
                creatingProvider = false
                editingProvider = null
            },
        )
    }
}

@Composable
private fun ProviderCard(
    profile: ProviderProfile,
    health: com.nitronbox.app.data.remote.ProviderHealth?,
    models: List<com.nitronbox.app.data.remote.DiscoveredModel>,
    onEdit: () -> Unit,
) {
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
                    "${profile.protocol.name.lowercase().replace('_', ' ')}  ·  ${profile.baseUrl}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NitronTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when {
                health?.reachable == true -> Text("Online", style = MaterialTheme.typography.labelMedium, color = NitronTheme.colors.accent)
                health?.reachable == false -> Text("Offline", style = MaterialTheme.typography.labelMedium, color = NitronTheme.colors.destructive)
                else -> Text("Untested", style = MaterialTheme.typography.labelMedium, color = NitronTheme.colors.textTertiary)
            }
        }
        if (models.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "${models.size} models discovered",
                style = MaterialTheme.typography.labelSmall,
                color = NitronTheme.colors.textTertiary,
            )
        }
    }
}

@Composable
private fun ProviderEditorDialog(
    initial: ProviderProfile?,
    onDismiss: () -> Unit,
    onSave: (ProviderProfile, CharArray?) -> Unit,
    onTest: (String) -> Unit,
    onDiscover: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var displayName by remember { mutableStateOf(initial?.displayName.orEmpty()) }
    var baseUrl by remember { mutableStateOf(initial?.baseUrl ?: "https://") }
    var protocol by remember { mutableStateOf(initial?.protocol ?: ProviderProtocol.OPENAI_COMPATIBLE) }
    var apiKey by remember { mutableStateOf("") }
    var protocolMenuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val valid = displayName.isNotBlank() && baseUrl.startsWith("http")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add provider" else "Edit provider") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box {
                    OutlinedTextField(
                        value = protocol.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Protocol") },
                        trailingIcon = {
                            IconButton(onClick = { protocolMenuOpen = true }) {
                                Icon(
                                    if (protocolMenuOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    "Protocol",
                                    tint = NitronTheme.colors.textSecondary,
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DropdownMenu(expanded = protocolMenuOpen, onDismissRequest = { protocolMenuOpen = false }) {
                        ProviderProtocol.entries.forEach { entry ->
                            DropdownMenuItem(
                                text = { Text(entry.name) },
                                onClick = {
                                    protocol = entry
                                    protocolMenuOpen = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    supportingText = { Text("HTTPS required; loopback allowed for local models") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(if (initial == null) "API key" else "API key (leave blank to keep)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { initial?.let { onTest(it.id) } },
                        enabled = initial != null,
                    ) { Text("Test") }
                    OutlinedButton(
                        onClick = { initial?.let { onDiscover(it.id) } },
                        enabled = initial != null,
                    ) { Text("Load models") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
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
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Delete", color = NitronTheme.colors.destructive)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )

    if (confirmDelete) {
        initial?.let { profile ->
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Delete provider?") },
                text = { Text("“${profile.displayName}” and its stored key will be removed.") },
                confirmButton = {
                    TextButton(onClick = {
                        onDelete(profile.id)
                        confirmDelete = false
                    }) { Text("Delete", color = NitronTheme.colors.destructive) }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun WorkspaceEditor(
    workspace: com.nitronbox.app.data.model.Workspace,
    onSave: (com.nitronbox.app.data.model.Workspace) -> Unit,
) {
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
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = systemPrompt,
            onValueChange = { systemPrompt = it },
            label = { Text("System prompt") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Temperature: ${"%.2f".format(temperature)}", style = MaterialTheme.typography.labelMedium, color = NitronTheme.colors.textSecondary)
        Slider(value = temperature, onValueChange = { temperature = it }, valueRange = 0f..2f)
        OutlinedTextField(
            value = maxOutputTokens,
            onValueChange = { maxOutputTokens = it.filter(Char::isDigit) },
            label = { Text("Max output tokens") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        HorizontalDivider(color = NitronTheme.colors.border)
        Text("Context window", style = MaterialTheme.typography.titleSmall, color = NitronTheme.colors.textPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = maxInputTokens,
                onValueChange = { maxInputTokens = it.filter(Char::isDigit) },
                label = { Text("Max input") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = reservedOutput,
                onValueChange = { reservedOutput = it.filter(Char::isDigit) },
                label = { Text("Reserve out") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Box {
            OutlinedTextField(
                value = strategy.name.lowercase().replace('_', ' '),
                onValueChange = {},
                readOnly = true,
                label = { Text("Overflow strategy") },
                trailingIcon = {
                    IconButton(onClick = { strategyMenuOpen = true }) {
                        Icon(
                            if (strategyMenuOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            "Strategy",
                            tint = NitronTheme.colors.textSecondary,
                        )
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
                            maxOutputTokens = maxOutputTokens.toIntOrNull() ?: workspace.generation.maxOutputTokens,
                        ),
                        contextPolicy = workspace.contextPolicy.copy(
                            maxInputTokens = maxInputTokens.toIntOrNull() ?: workspace.contextPolicy.maxInputTokens,
                            reservedOutputTokens = reservedOutput.toIntOrNull() ?: workspace.contextPolicy.reservedOutputTokens,
                            strategy = strategy,
                        ),
                        updatedAtEpochMillis = System.currentTimeMillis(),
                    ),
                )
            },
            modifier = Modifier.align(Alignment.End),
        ) { Text("Save workspace") }
    }
}
