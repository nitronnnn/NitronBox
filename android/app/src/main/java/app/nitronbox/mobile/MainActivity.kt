package app.nitronbox.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

private val Background = Color(0xFF080D19)
private val TextPrimary = Color(0xFFF0F5FF)
private val TextMuted = Color(0xFF8293B2)
private val Cyan = Color(0xFF6CE7FF)
private val Violet = Color(0xFF8974FF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { NitronTheme { NitronBoxApp() } }
    }
}

@Composable
private fun NitronTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = Cyan, secondary = Violet, background = Background, surface = Color(0xFF11192A), onBackground = TextPrimary, onSurface = TextPrimary),
        typography = Typography(), content = content,
    )
}

private enum class Sheet { PROVIDERS, MODELS, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NitronBoxApp(vm: MainViewModel = viewModel()) {
    val state = vm.state
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var draft by remember { mutableStateOf("") }
    val active = state.conversations.find { it.id == state.activeId }

    fun openModels() {
        if (vm.key().isBlank() || (state.provider.custom && state.baseUrl.isBlank())) sheet = Sheet.SETTINGS
        else if (state.models.isEmpty()) vm.loadModels { sheet = Sheet.MODELS }
        else sheet = Sheet.MODELS
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = { Drawer(state, vm, close = { scope.launch { drawerState.close() } }) },
    ) {
        Box(Modifier.fillMaxSize().background(Background)) {
            AuroraBackground()
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                TopBar(state, openDrawer = { scope.launch { drawerState.open() } }, openModels = ::openModels, openSettings = { sheet = Sheet.SETTINGS }, newChat = vm::newChat)
                if (active == null || active.messages.isEmpty()) Welcome(state, vm.key().isNotBlank(), openModels, send = { vm.send(it) })
                else ChatMessages(active.messages, state.generating)
                state.error.takeIf { it.isNotBlank() }?.let { ErrorPill(it, vm::clearError) }
                Composer(draft, state.generating, onDraft = { draft = it }, onSend = { vm.send(draft) { draft = "" } }, onStop = vm::stop, onProvider = { sheet = Sheet.PROVIDERS })
            }
        }
    }

    if (sheet != null) {
        ModalBottomSheet(
            onDismissRequest = { sheet = null }, containerColor = Color(0xF0151D2D), contentColor = TextPrimary,
            scrimColor = Color(0xB0000309), dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF52617D)) },
        ) {
            when (sheet) {
                Sheet.PROVIDERS -> ProviderSheet(state.provider, onChoose = { vm.chooseProvider(it); sheet = Sheet.SETTINGS })
                Sheet.MODELS -> ModelSheet(state, onSelect = { vm.chooseModel(it); sheet = null }, onRefresh = vm::loadModels)
                Sheet.SETTINGS -> SettingsSheet(state, vm, openProviders = { sheet = Sheet.PROVIDERS }, loadModels = { vm.loadModels { sheet = Sheet.MODELS } }, close = { sheet = null })
                null -> Unit
            }
        }
    }
}

@Composable
private fun AuroraBackground() {
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(Brush.radialGradient(listOf(Color(0x553A69FF), Color.Transparent)), radius = size.minDimension * .7f, center = Offset(0f, 0f))
        drawCircle(Brush.radialGradient(listOf(Color(0x443F27D8), Color.Transparent)), radius = size.minDimension * .65f, center = Offset(size.width, size.height))
        drawCircle(Brush.radialGradient(listOf(Color(0x1912CFC4), Color.Transparent)), radius = size.minDimension * .38f, center = center)
    }
}

@Composable
private fun Glass(modifier: Modifier = Modifier, shape: RoundedCornerShape = RoundedCornerShape(24.dp), content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier.background(Brush.linearGradient(listOf(Color(0x2ADDF1FF), Color(0x127B96C5), Color(0x197F67CB))), shape)
            .border(1.dp, Brush.linearGradient(listOf(Color(0x55E9F7FF), Color(0x1888A5D7), Color(0x358E7BFF))), shape)
            .clip(shape), content = content,
    )
}

@Composable
private fun TopBar(state: UiState, openDrawer: () -> Unit, openModels: () -> Unit, openSettings: () -> Unit, newChat: () -> Unit) {
    Glass(Modifier.fillMaxWidth().padding(8.dp).height(62.dp)) {
        Row(Modifier.fillMaxSize().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NativeIcon(Icons.Outlined.Menu, openDrawer)
            ProviderBadge(state.provider, 26.dp)
            Text(state.model.ifBlank { "Выбрать модель" }, Modifier.weight(1f).padding(horizontal = 9.dp).clickable(onClick = openModels), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            NativeIcon(Icons.Outlined.ExpandMore, openModels, 36.dp)
            NativeIcon(Icons.Outlined.Tune, openSettings)
            NativeIcon(Icons.Outlined.Add, newChat)
        }
    }
}

@Composable
private fun Welcome(state: UiState, connected: Boolean, openModels: () -> Unit, send: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) { drawRoundRect(Color(0x168EDBFF), cornerRadius = androidx.compose.ui.geometry.CornerRadius(30f), style = Stroke(2f), topLeft = Offset(10f, 10f), size = Size(size.width - 20f, size.height - 20f)) }
            NitronLogo(82.dp)
        }
        Text("NITRONBOX INTELLIGENCE", color = Color(0xFF7188B5), fontSize = 9.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text("Все модели.", fontSize = 38.sp, lineHeight = 40.sp, letterSpacing = (-2).sp, fontWeight = FontWeight.Bold)
        Text("Одно пространство.", color = Cyan, fontSize = 33.sp, lineHeight = 38.sp, letterSpacing = (-2).sp, fontWeight = FontWeight.Bold)
        Text("Подключайте провайдера и выбирайте любую модель из его актуального каталога.", Modifier.padding(14.dp, 12.dp, 14.dp, 20.dp), color = TextMuted, fontSize = 11.sp, lineHeight = 17.sp)
        val prompts = listOf(Icons.Outlined.AutoAwesome to ("Придумать идею" to "Предложи необычную идею для приложения"), Icons.Outlined.Code to ("Помочь с кодом" to "Объясни архитектуру современного API"), Icons.Outlined.EditNote to ("Написать текст" to "Напиши короткий пост об ИИ"), Icons.Outlined.Lightbulb to ("Разобраться" to "Объясни квантовые вычисления просто"))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { prompts.take(2).forEach { item -> PromptCard(item.first, item.second.first, Modifier.weight(1f)) { send(item.second.second) } } }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { prompts.drop(2).forEach { item -> PromptCard(item.first, item.second.first, Modifier.weight(1f)) { send(item.second.second) } } }
        Spacer(Modifier.height(15.dp))
        Glass(Modifier.clickable(onClick = openModels), RoundedCornerShape(50)) {
            Row(Modifier.padding(9.dp, 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(if (connected) Color(0xFF5BE2AF) else Color(0xFF71809A), CircleShape))
                Spacer(Modifier.width(7.dp)); ProviderBadge(state.provider, 24.dp); Spacer(Modifier.width(7.dp))
                Column { Text(state.provider.title, fontSize = 9.sp, fontWeight = FontWeight.Bold); Text(state.model.ifBlank { if (connected) "выбрать модель" else "подключить" }, color = TextMuted, fontSize = 8.sp) }
                Spacer(Modifier.width(8.dp)); Icon(Icons.Outlined.ExpandMore, null, tint = TextMuted, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun PromptCard(icon: ImageVector, title: String, modifier: Modifier, action: () -> Unit) {
    Glass(modifier.height(58.dp).clickable(onClick = action), RoundedCornerShape(17.dp)) {
        Row(Modifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Cyan, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(9.dp)); Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun ChatMessages(messages: List<ChatMessage>, generating: Boolean) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.lastOrNull()?.content) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }
    LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState, contentPadding = PaddingValues(14.dp, 22.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        items(messages, key = { it.id }) { message ->
            if (message.role == "user") Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(Modifier.widthIn(max = 330.dp).background(Brush.linearGradient(listOf(Color(0xCC5979DB), Color(0xB0684DB7))), RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)).border(1.dp, Color(0x35D4ECFF), RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)).padding(14.dp, 11.dp)) { Text(message.content, fontSize = 13.sp, lineHeight = 20.sp) }
            } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(34.dp).background(Color(0x147CAEFF), RoundedCornerShape(12.dp)).border(1.dp, Color(0x248DDCFF), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { NitronLogo(27.dp) }
                Spacer(Modifier.width(10.dp))
                if (message.content.isEmpty() && generating) TypingDots() else MarkdownText(message.content, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TypingDots() {
    val transition = rememberInfiniteTransition(label = "typing")
    val alpha by transition.animateFloat(.25f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "alpha")
    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) { repeat(3) { Box(Modifier.size(6.dp).background(Cyan.copy(alpha = (alpha - it * .15f).coerceAtLeast(.2f)), CircleShape)) } }
}

@Composable
private fun Composer(value: String, generating: Boolean, onDraft: (String) -> Unit, onSend: () -> Unit, onStop: () -> Unit, onProvider: () -> Unit) {
    val focus = LocalFocusManager.current
    Glass(Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(bottom = 5.dp), RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(11.dp)) {
            BasicTextField(value, onDraft, Modifier.fillMaxWidth().heightIn(min = 34.dp, max = 130.dp), textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp, lineHeight = 20.sp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { focus.clearFocus(); onSend() }), decorationBox = { inner -> Box { if (value.isEmpty()) Text("Сообщение для NitronBox", color = Color(0xFF657693), fontSize = 14.sp); inner() } })
            Row(verticalAlignment = Alignment.CenterVertically) {
                NativeIcon(Icons.Outlined.Hub, onProvider, 35.dp)
                Text("BYOK · ключ остаётся на устройстве", Modifier.weight(1f), color = Color(0xFF61718D), fontSize = 8.sp)
                FilledIcon(if (generating) Icons.Outlined.Stop else Icons.Outlined.ArrowUpward, if (generating) onStop else onSend, enabled = generating || value.isNotBlank())
            }
        }
    }
}

@Composable
private fun ErrorPill(message: String, close: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp).background(Color(0x663E1726), RoundedCornerShape(13.dp)).border(1.dp, Color(0x35FF91AA), RoundedCornerShape(13.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Text(message, Modifier.weight(1f), color = Color(0xFFFFBBC9), fontSize = 10.sp); Icon(Icons.Outlined.Close, null, Modifier.size(17.dp).clickable(onClick = close), tint = Color(0xFFFFBBC9)) }
}

@Composable
private fun Drawer(state: UiState, vm: MainViewModel, close: () -> Unit) {
    ModalDrawerSheet(Modifier.width(310.dp), drawerContainerColor = Color(0xF0121928), drawerContentColor = TextPrimary) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { NitronLogo(42.dp); Spacer(Modifier.width(9.dp)); Column(Modifier.weight(1f)) { Text("NitronBox", fontWeight = FontWeight.Bold); Text("NATIVE AI WORKSPACE", color = TextMuted, fontSize = 8.sp) }; NativeIcon(Icons.Outlined.Close, close) }
            Button(onClick = { vm.newChat(); close() }, Modifier.fillMaxWidth().padding(vertical = 20.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5E6DCE))) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(7.dp)); Text("Новый чат", fontSize = 11.sp) }
            Text("НЕДАВНИЕ", color = Color(0xFF687B9E), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) { items(state.conversations, key = { it.id }) { chat -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (chat.id == state.activeId) Color(0x176CA8FF) else Color.Transparent).clickable { vm.openChat(chat.id); close() }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.size(16.dp), tint = TextMuted); Text(chat.title, Modifier.weight(1f).padding(horizontal = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 10.sp); Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(17.dp).clickable { vm.deleteChat(chat.id) }, tint = Color(0xFF6E7E9A)) } } }
            Text("API-ключи хранятся только в памяти приложения", color = Color(0xFF5E708E), fontSize = 8.sp, lineHeight = 13.sp)
        }
    }
}

@Composable
private fun ProviderSheet(current: Provider, onChoose: (Provider) -> Unit) {
    SheetTitle("Провайдеры", "Официальные API и собственный OpenAI-compatible API")
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 580.dp), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(Providers) { provider -> Glass(Modifier.fillMaxWidth().clickable { onChoose(provider) }, RoundedCornerShape(17.dp)) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { ProviderBadge(provider, 34.dp); Column(Modifier.weight(1f).padding(horizontal = 11.dp)) { Text(provider.title, fontSize = 12.sp, fontWeight = FontWeight.Bold); Text(if (provider.custom) "OpenAI-compatible API" else "Актуальный каталог из API", color = TextMuted, fontSize = 8.sp) }; if (provider == current) Icon(Icons.Outlined.Check, null, tint = Cyan) } } }
    }
}

@Composable
private fun ModelSheet(state: UiState, onSelect: (String) -> Unit, onRefresh: () -> Unit) {
    var search by remember { mutableStateOf("") }
    val shown = state.models.filter { "${it.title} ${it.id} ${it.description}".contains(search, true) }
    SheetTitle("Модели · ${state.provider.title}", "${state.models.size} моделей получено напрямую из API")
    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(search, { search = it }, Modifier.weight(1f), placeholder = { Text("Найти модель...", fontSize = 11.sp) }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.width(8.dp)); NativeIcon(Icons.Outlined.Refresh, onRefresh, 48.dp)
    }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp), contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 30.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        items(shown, key = { it.id }) { model -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (model.id == state.model) Color(0x1769CFFF) else Color(0x087A96C5)).clickable { onSelect(model.id) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(model.title, fontSize = 11.sp, fontWeight = FontWeight.Bold); Text(model.id, color = Color(0xFF7693BE), fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis); if (model.description.isNotBlank()) Text(model.description, color = Color(0xFF5F718F), fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }; if (model.id == state.model) Icon(Icons.Outlined.Check, null, tint = Cyan) }
        }
    }
}

@Composable
private fun SettingsSheet(state: UiState, vm: MainViewModel, openProviders: () -> Unit, loadModels: () -> Unit, close: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    SheetTitle("Подключение", "Прямые нативные запросы без промежуточного сервера")
    Column(Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 26.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        FieldLabel("ПРОВАЙДЕР"); Glass(Modifier.fillMaxWidth().clickable(onClick = openProviders), RoundedCornerShape(14.dp)) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { ProviderBadge(state.provider, 25.dp); Text(state.provider.title, Modifier.weight(1f).padding(horizontal = 9.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold); Icon(Icons.Outlined.ExpandMore, null) } }
        FieldLabel("API-КЛЮЧ"); OutlinedTextField(vm.key(), vm::setKey, Modifier.fillMaxWidth(), placeholder = { Text(state.provider.keyHint) }, singleLine = true, visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null) } }, shape = RoundedCornerShape(14.dp))
        if (state.provider.custom) { FieldLabel("BASE URL"); OutlinedTextField(state.baseUrl, vm::setBaseUrl, Modifier.fillMaxWidth(), placeholder = { Text("https://api.example.com/v1") }, singleLine = true, shape = RoundedCornerShape(14.dp)) }
        Button(onClick = loadModels, Modifier.fillMaxWidth(), enabled = !state.loadingModels && vm.key().isNotBlank(), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF304D79))) { if (state.loadingModels) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.Sync, null, Modifier.size(18.dp)); Spacer(Modifier.width(7.dp)); Text(if (state.models.isEmpty()) "Получить список моделей" else "Обновить каталог · ${state.models.size}", fontSize = 10.sp) }
        FieldLabel("СИСТЕМНАЯ ИНСТРУКЦИЯ"); OutlinedTextField(state.system, vm::setSystem, Modifier.fillMaxWidth().height(92.dp), placeholder = { Text("Как должен вести себя ассистент?") }, shape = RoundedCornerShape(14.dp))
        Row { FieldLabel("ТЕМПЕРАТУРА"); Spacer(Modifier.weight(1f)); Text("%.1f".format(state.temperature), color = Cyan, fontSize = 10.sp) }; Slider(state.temperature, vm::setTemperature, valueRange = 0f..2f)
        Button(onClick = close, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("Готово", fontSize = 11.sp) }
    }
}

@Composable private fun SheetTitle(title: String, subtitle: String) { Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) { Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = TextMuted, fontSize = 9.sp) } }
@Composable private fun FieldLabel(value: String) { Text(value, color = Color(0xFF7588AA), fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }

@Composable
private fun ProviderBadge(provider: Provider, size: androidx.compose.ui.unit.Dp) { Box(Modifier.size(size).background(Color(provider.color), RoundedCornerShape(size * .34f)).border(1.dp, Color.White.copy(.18f), RoundedCornerShape(size * .34f)), contentAlignment = Alignment.Center) { Text(provider.badge, fontSize = (size.value * .27f).sp, fontWeight = FontWeight.Bold, color = Color.White) } }

@Composable
private fun NativeIcon(icon: ImageVector, action: () -> Unit, size: androidx.compose.ui.unit.Dp = 42.dp) { IconButton(onClick = action, modifier = Modifier.size(size).clip(RoundedCornerShape(14.dp)).background(Color(0x0FFFFFFF))) { Icon(icon, null, tint = Color(0xFFB9C7DF), modifier = Modifier.size(20.dp)) } }

@Composable
private fun FilledIcon(icon: ImageVector, action: () -> Unit, enabled: Boolean) { IconButton(onClick = action, enabled = enabled, modifier = Modifier.size(39.dp).clip(RoundedCornerShape(13.dp)).background(if (enabled) Brush.linearGradient(listOf(Cyan, Violet)) else SolidColor(Color(0x334F6080)))) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(19.dp)) } }

@Composable
private fun NitronLogo(size: androidx.compose.ui.unit.Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val a = Path().apply { moveTo(.14f*w,.76f*h); lineTo(.43f*w,.14f*h); quadraticTo(.48f*w,.05f*h,.55f*w,.18f*h); lineTo(.66f*w,.37f*h); lineTo(.47f*w,.76f*h); lineTo(.23f*w,.88f*h); quadraticTo(.08f*w,.94f*h,.14f*w,.76f*h); close() }
        val b = Path().apply { moveTo(.86f*w,.24f*h); lineTo(.57f*w,.86f*h); quadraticTo(.52f*w,.95f*h,.45f*w,.82f*h); lineTo(.34f*w,.63f*h); lineTo(.53f*w,.24f*h); lineTo(.77f*w,.12f*h); quadraticTo(.92f*w,.06f*h,.86f*w,.24f*h); close() }
        drawPath(a, Brush.linearGradient(listOf(Color(0xFFD7F7FF), Color(0xFF69DEFF), Color(0xFF716BFF))))
        drawPath(b, Brush.linearGradient(listOf(Color.White, Color(0xFFA98BFF), Color(0xFF4D7CFF))))
    }
}
