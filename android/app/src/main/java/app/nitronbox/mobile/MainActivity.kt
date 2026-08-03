package app.nitronbox.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

private val AppBackground = Color(0xFF090D14)
private val Panel = Color(0xFF151C27)
private val PrimaryText = Color(0xFFF3F6FA)
private val SecondaryText = Color(0xFF8C99AC)
private val Accent = Color(0xFF72C7F5)
private val Border = Color(0xFF2D3848)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Accent, background = AppBackground, surface = Panel,
                    onBackground = PrimaryText, onSurface = PrimaryText,
                ),
            ) { NitronBoxApp() }
        }
    }
}

private enum class Sheet { PROVIDERS, MODELS, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NitronBoxApp(vm: MainViewModel = viewModel()) {
    val state = vm.state
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var draft by remember { mutableStateOf("") }
    val chat = state.conversations.find { it.id == state.activeId }

    fun openModels() {
        when {
            vm.key().isBlank() || state.provider.custom && state.baseUrl.isBlank() -> sheet = Sheet.SETTINGS
            state.models.isEmpty() -> vm.loadModels { sheet = Sheet.MODELS }
            else -> sheet = Sheet.MODELS
        }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = drawer.isOpen,
        drawerContent = { HistoryDrawer(state, vm) { scope.launch { drawer.close() } } },
    ) {
        Box(Modifier.fillMaxSize().background(AppBackground)) {
            CalmBackground()
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Header(
                    state = state,
                    menu = { scope.launch { drawer.open() } },
                    models = ::openModels,
                    settings = { sheet = Sheet.SETTINGS },
                    newChat = vm::newChat,
                )
                if (chat == null || chat.messages.isEmpty()) {
                    EmptyState(state, vm.key().isNotBlank(), Modifier.weight(1f), ::openModels) { vm.send(it) }
                } else ChatList(chat.messages, state.generating, Modifier.weight(1f))
                if (state.error.isNotBlank()) ErrorBanner(state.error, vm::clearError)
                MessageComposer(
                    value = draft,
                    generating = state.generating,
                    change = { draft = it },
                    send = { vm.send(draft) { draft = "" } },
                    stop = vm::stop,
                )
            }
        }
    }

    if (sheet != null) ModalBottomSheet(
        onDismissRequest = { sheet = null },
        containerColor = Color.Transparent,
        contentColor = PrimaryText,
        scrimColor = Color(0xB0000000),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF536071)) },
    ) {
        LiquidGlassSurface(
            Modifier.fillMaxWidth(), RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp), 16.dp
        ) {
            Column {
                when (sheet) {
                    Sheet.PROVIDERS -> ProvidersContent(state.provider) { vm.chooseProvider(it); sheet = Sheet.SETTINGS }
                    Sheet.MODELS -> ModelsContent(state, { vm.chooseModel(it); sheet = null }, vm::loadModels)
                    Sheet.SETTINGS -> SettingsContent(state, vm, { sheet = Sheet.PROVIDERS }, { vm.loadModels { sheet = Sheet.MODELS } }, { sheet = null })
                    null -> Unit
                }
            }
        }
    }
}

@Composable
private fun CalmBackground() = Canvas(Modifier.fillMaxSize()) {
    drawCircle(Brush.radialGradient(listOf(Color(0x263C7DA3), Color.Transparent)), size.minDimension * .65f, Offset(size.width * .2f, 0f))
    drawCircle(Brush.radialGradient(listOf(Color(0x172B617E), Color.Transparent)), size.minDimension * .55f, Offset(size.width, size.height))
}

@Composable
private fun Header(state: UiState, menu: () -> Unit, models: () -> Unit, settings: () -> Unit, newChat: () -> Unit) {
    LiquidGlassSurface(Modifier.fillMaxWidth().padding(10.dp).height(64.dp), RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SquareButton(Icons.Outlined.Menu, menu)
            Spacer(Modifier.width(8.dp))
            Row(
                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = models).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProviderBadge(state.provider, 28.dp)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(state.provider.title, color = SecondaryText, fontSize = 9.sp)
                    Text(state.model.ifBlank { "Выберите модель" }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Icon(Icons.Outlined.ExpandMore, null, tint = SecondaryText, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(6.dp)); SquareButton(Icons.Outlined.Tune, settings)
            Spacer(Modifier.width(6.dp)); SquareButton(Icons.Outlined.Add, newChat)
        }
    }
}

@Composable
private fun EmptyState(state: UiState, connected: Boolean, modifier: Modifier, models: () -> Unit, send: (String) -> Unit) {
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(94.dp), contentAlignment = Alignment.Center) { NitronLogo(76.dp) }
        Spacer(Modifier.height(18.dp))
        Text("NitronBox", fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
        Text("Ваши модели в одном месте", color = SecondaryText, fontSize = 13.sp)
        Spacer(Modifier.height(28.dp))
        LiquidGlassSurface(Modifier.fillMaxWidth().clickable(onClick = models), RoundedCornerShape(18.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                ProviderBadge(state.provider, 38.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(if (connected) "Готово к работе" else "Требуется подключение", color = SecondaryText, fontSize = 10.sp)
                    Text(state.model.ifBlank { "Подключить ${state.provider.title}" }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = SecondaryText)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickAction("Объяснить тему", Icons.Outlined.Lightbulb, Modifier.weight(1f)) { send("Объясни сложную тему простыми словами") }
            QuickAction("Помочь с кодом", Icons.Outlined.Code, Modifier.weight(1f)) { send("Помоги разобраться с кодом") }
        }
    }
}

@Composable
private fun QuickAction(text: String, icon: ImageVector, modifier: Modifier, click: () -> Unit) {
    LiquidGlassSurface(modifier.height(52.dp).clickable(onClick = click), RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Accent, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(text, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ChatList(messages: List<ChatMessage>, generating: Boolean, modifier: Modifier) {
    val list = rememberLazyListState()
    LaunchedEffect(messages.lastOrNull()?.content) { if (messages.isNotEmpty()) list.animateScrollToItem(messages.lastIndex) }
    LazyColumn(modifier.fillMaxWidth(), state = list, contentPadding = PaddingValues(14.dp, 18.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        items(messages, key = { it.id }) { message ->
            if (message.role == "user") Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(Modifier.widthIn(max = 330.dp).background(Color(0xFF263B4B), RoundedCornerShape(18.dp, 18.dp, 5.dp, 18.dp)).border(1.dp, Color(0xFF3D5A6B), RoundedCornerShape(18.dp, 18.dp, 5.dp, 18.dp)).padding(14.dp, 11.dp)) { Text(message.content, fontSize = 13.sp, lineHeight = 20.sp) }
            } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                NitronLogo(30.dp); Spacer(Modifier.width(10.dp))
                if (message.content.isEmpty() && generating) Typing() else MarkdownText(message.content, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Typing() {
    val t = rememberInfiniteTransition(label = "typing")
    val alpha by t.animateFloat(.25f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "dots")
    Row(Modifier.padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) { repeat(3) { Box(Modifier.size(6.dp).background(Accent.copy(alpha = (alpha - it * .15f).coerceAtLeast(.2f)), CircleShape)) } }
}

@Composable
private fun MessageComposer(value: String, generating: Boolean, change: (String) -> Unit, send: () -> Unit, stop: () -> Unit) {
    val focus = LocalFocusManager.current
    LiquidGlassSurface(Modifier.fillMaxWidth().padding(10.dp), RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Bottom) {
            BasicTextField(
                value, change, Modifier.weight(1f).heightIn(min = 44.dp, max = 132.dp).padding(11.dp),
                textStyle = TextStyle(PrimaryText, 14.sp, lineHeight = 20.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { focus.clearFocus(); send() }),
                decorationBox = { inner -> Box { if (value.isBlank()) Text("Сообщение", color = SecondaryText, fontSize = 14.sp); inner() } },
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = if (generating) stop else send,
                enabled = generating || value.isNotBlank(),
                modifier = Modifier.size(44.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Accent, contentColor = Color(0xFF071018), disabledContainerColor = Color(0xFF26303D)),
                shape = RoundedCornerShape(14.dp),
            ) { Icon(if (generating) Icons.Outlined.Stop else Icons.Outlined.ArrowUpward, null, Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, dismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).background(Color(0xFF25171C), RoundedCornerShape(12.dp)).border(1.dp, Color(0xFF51303A), RoundedCornerShape(12.dp)).padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(message, Modifier.weight(1f), color = Color(0xFFE9B6C1), fontSize = 11.sp); IconButton(dismiss, Modifier.size(28.dp)) { Icon(Icons.Outlined.Close, null, Modifier.size(16.dp)) }
    }
}

@Composable
private fun HistoryDrawer(state: UiState, vm: MainViewModel, close: () -> Unit) {
    ModalDrawerSheet(Modifier.width(310.dp), drawerContainerColor = Color.Transparent) {
        LiquidGlassSurface(Modifier.fillMaxSize(), RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp), 18.dp) {
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { NitronLogo(38.dp); Text("NitronBox", Modifier.weight(1f).padding(start = 10.dp), fontSize = 17.sp, fontWeight = FontWeight.Bold); SquareButton(Icons.Outlined.Close, close) }
                Button(vm::newChat, Modifier.fillMaxWidth().padding(vertical = 18.dp).height(48.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF263B4B))) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("Новый чат") }
                Text("ИСТОРИЯ", color = SecondaryText, fontSize = 9.sp, letterSpacing = 1.sp)
                LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) { items(state.conversations, key = { it.id }) { chat ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (chat.id == state.activeId) Color(0xFF202D3A) else Color.Transparent).clickable { vm.openChat(chat.id); close() }.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.size(17.dp), SecondaryText); Text(chat.title, Modifier.weight(1f).padding(horizontal = 9.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp); Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(17.dp).clickable { vm.deleteChat(chat.id) }, SecondaryText)
                    }
                } }
                Text("Ключи хранятся только в памяти", color = SecondaryText, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun ProvidersContent(current: Provider, choose: (Provider) -> Unit) {
    SheetHeader("Провайдер", "Выберите сервис для подключения")
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 590.dp), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(Providers) { provider ->
            Row(Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(15.dp)).background(if (provider == current) Color(0xFF243544) else Color(0xFF171F2A)).border(1.dp, if (provider == current) Color(0xFF40647A) else Border, RoundedCornerShape(15.dp)).clickable { choose(provider) }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                ProviderBadge(provider, 34.dp); Text(provider.title, Modifier.weight(1f).padding(horizontal = 12.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium); if (provider == current) Icon(Icons.Outlined.Check, null, tint = Accent)
            }
        }
    }
}

@Composable
private fun ModelsContent(state: UiState, choose: (String) -> Unit, refresh: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val models = state.models.filter { "${it.title} ${it.id}".contains(query, true) }
    SheetHeader("Модели", "${state.provider.title} · ${state.models.size} доступно")
    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        AppField(query, { query = it }, "Поиск модели", Modifier.weight(1f), Icons.Outlined.Search)
        Spacer(Modifier.width(8.dp)); SquareButton(Icons.Outlined.Refresh, refresh, 52.dp)
    }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 540.dp), contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        items(models, key = { it.id }) { model ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(if (model.id == state.model) Color(0xFF223542) else Color.Transparent).clickable { choose(model.id) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(model.title, fontSize = 12.sp, fontWeight = FontWeight.Medium); Text(model.id, color = SecondaryText, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }; if (model.id == state.model) Icon(Icons.Outlined.Check, null, tint = Accent)
            }
        }
    }
}

@Composable
private fun SettingsContent(state: UiState, vm: MainViewModel, providers: () -> Unit, load: () -> Unit, close: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    SheetHeader("Подключение", "Настройте доступ к API")
    Column(Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Label("Провайдер")
        Row(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF171F2A)).border(1.dp, Border, RoundedCornerShape(14.dp)).clickable(onClick = providers).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) { ProviderBadge(state.provider, 28.dp); Text(state.provider.title, Modifier.weight(1f).padding(start = 10.dp), fontSize = 13.sp); Icon(Icons.Outlined.ExpandMore, null, tint = SecondaryText) }
        Label("API-ключ")
        AppField(vm.key(), vm::setKey, state.provider.keyHint, Modifier.fillMaxWidth(), trailing = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, trailingClick = { visible = !visible }, hidden = !visible)
        Text("Не сохраняется после закрытия приложения", color = SecondaryText, fontSize = 9.sp)
        if (state.provider.custom) { Label("Base URL"); AppField(state.baseUrl, vm::setBaseUrl, "https://api.example.com/v1", Modifier.fillMaxWidth()) }
        Button(load, Modifier.fillMaxWidth().height(50.dp), enabled = vm.key().isNotBlank() && !state.loadingModels, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF263B4B))) { if (state.loadingModels) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.Sync, null); Spacer(Modifier.width(8.dp)); Text(if (state.models.isEmpty()) "Загрузить модели" else "Обновить модели (${state.models.size})") }
        Label("Системная инструкция"); AppField(state.system, vm::setSystem, "Поведение ассистента", Modifier.fillMaxWidth().height(90.dp), singleLine = false)
        Row { Label("Температура"); Spacer(Modifier.weight(1f)); Text("%.1f".format(state.temperature), color = Accent, fontSize = 11.sp) }; Slider(state.temperature, vm::setTemperature, valueRange = 0f..2f, colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent))
        Button(close, Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF071018))) { Text("Готово", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun AppField(value: String, change: (String) -> Unit, hint: String, modifier: Modifier, leading: ImageVector? = null, trailing: ImageVector? = null, trailingClick: () -> Unit = {}, hidden: Boolean = false, singleLine: Boolean = true) {
    OutlinedTextField(
        value, change, modifier.heightIn(min = 52.dp), placeholder = { Text(hint, color = SecondaryText, fontSize = 12.sp) },
        leadingIcon = leading?.let { { Icon(it, null, tint = SecondaryText) } },
        trailingIcon = trailing?.let { { IconButton(trailingClick) { Icon(it, null, tint = SecondaryText) } } },
        visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
        singleLine = singleLine, shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = Border, focusedContainerColor = Color(0xFF151D28), unfocusedContainerColor = Color(0xFF151D28), cursorColor = Accent),
        textStyle = TextStyle(PrimaryText, 13.sp),
    )
}

@Composable private fun SheetHeader(title: String, subtitle: String) { Column(Modifier.padding(18.dp, 8.dp, 18.dp, 16.dp)) { Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = SecondaryText, fontSize = 11.sp) } }
@Composable private fun Label(text: String) { Text(text.uppercase(), color = SecondaryText, fontSize = 9.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold) }

@Composable
private fun SquareButton(icon: ImageVector, click: () -> Unit, size: Dp = 44.dp) {
    IconButton(click, Modifier.size(size).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1D2632)).border(1.dp, Border, RoundedCornerShape(14.dp))) { Icon(icon, null, Modifier.size(20.dp), SecondaryText) }
}

@Composable
private fun ProviderBadge(provider: Provider, size: Dp) { Box(Modifier.size(size).background(Color(provider.color), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) { Text(provider.badge, color = Color.White, fontSize = (size.value * .27f).sp, fontWeight = FontWeight.Bold) } }

@Composable
private fun NitronLogo(size: Dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height
    val a = Path().apply { moveTo(.14f*w,.76f*h); lineTo(.43f*w,.14f*h); quadraticTo(.48f*w,.05f*h,.55f*w,.18f*h); lineTo(.66f*w,.37f*h); lineTo(.47f*w,.76f*h); lineTo(.23f*w,.88f*h); quadraticTo(.08f*w,.94f*h,.14f*w,.76f*h); close() }
    val b = Path().apply { moveTo(.86f*w,.24f*h); lineTo(.57f*w,.86f*h); quadraticTo(.52f*w,.95f*h,.45f*w,.82f*h); lineTo(.34f*w,.63f*h); lineTo(.53f*w,.24f*h); lineTo(.77f*w,.12f*h); quadraticTo(.92f*w,.06f*h,.86f*w,.24f*h); close() }
    drawPath(a, Brush.linearGradient(listOf(Color(0xFFD8F6FF), Color(0xFF63CEF3), Color(0xFF4C8EA9))))
    drawPath(b, Brush.linearGradient(listOf(Color.White, Color(0xFF9EDCF4), Color(0xFF477A95))))
}
