import { useEffect, useRef, useState } from 'react';
import {
  ArrowUp, Check, ChevronDown, Code2, Copy, History, KeyRound, Lightbulb,
  Menu, MessageCircle, MoreHorizontal, PanelLeftClose, PenLine, Plus, RefreshCw,
  Search, Settings2, Sparkles, Square, Trash2, X,
} from 'lucide-react';
import Logo from './Logo';
import Markdown from './Markdown';
import { PROVIDERS, STARTERS } from './providers';
import { isNativeApp, requestNative, requestNativeModels } from './nativeApi';

const CHATS_KEY = 'nitronbox_chats_v1';
const SETTINGS_KEY = 'nitronbox_settings_v1';
const uid = () => `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 7)}`;

function load(key, fallback) {
  try { return JSON.parse(localStorage.getItem(key)) || fallback; } catch { return fallback; }
}

export default function App() {
  const [chats, setChats] = useState(() => load(CHATS_KEY, []));
  const [activeId, setActiveId] = useState(() => load(CHATS_KEY, [])[0]?.id || null);
  const [settings, setSettings] = useState(() => ({ provider: 'openai', model: '', baseUrl: '', system: '', temperature: 0.7, ...load(SETTINGS_KEY, {}) }));
  const [keys, setKeys] = useState({});
  const [modelLists, setModelLists] = useState({});
  const [modelsLoading, setModelsLoading] = useState(false);
  const [input, setInput] = useState('');
  const [streaming, setStreaming] = useState(false);
  const [error, setError] = useState('');
  const [sidebar, setSidebar] = useState(false);
  const [providerOpen, setProviderOpen] = useState(false);
  const [modelOpen, setModelOpen] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [keyVisible, setKeyVisible] = useState(false);
  const abortRef = useRef(null);
  const scrollRef = useRef(null);
  const textareaRef = useRef(null);
  const activeChat = chats.find((chat) => chat.id === activeId);
  const messages = activeChat?.messages || [];
  const provider = PROVIDERS.find((item) => item.id === settings.provider) || PROVIDERS[0];
  const models = modelLists[settings.provider] || [];

  useEffect(() => localStorage.setItem(CHATS_KEY, JSON.stringify(chats.slice(0, 50))), [chats]);
  useEffect(() => localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings)), [settings]);
  useEffect(() => { scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: 'smooth' }); }, [messages]);

  function patchSettings(patch) { setSettings((value) => ({ ...value, ...patch })); }
  function chooseProvider(next) {
    patchSettings({ provider: next.id, model: '', ...(next.custom ? {} : { baseUrl: '' }) });
    setProviderOpen(false);
    setSettingsOpen(true);
    setError('');
  }
  function newChat() { setActiveId(null); setInput(''); setError(''); setSidebar(false); }
  function openChat(id) { setActiveId(id); setSidebar(false); setError(''); }
  function deleteChat(id) {
    setChats((value) => value.filter((chat) => chat.id !== id));
    if (id === activeId) setActiveId(null);
  }
  function updateChat(id, updater) { setChats((value) => value.map((chat) => chat.id === id ? updater(chat) : chat)); }
  function resizeInput(event) {
    setInput(event.target.value);
    event.target.style.height = 'auto';
    event.target.style.height = `${Math.min(event.target.scrollHeight, 144)}px`;
  }

  async function fetchModels(openAfter = false) {
    const apiKey = keys[settings.provider];
    if (!apiKey) { setError('Сначала добавьте API-ключ'); setSettingsOpen(true); return; }
    if (provider.custom && !settings.baseUrl.trim()) { setError('Укажите Base URL своего провайдера'); setSettingsOpen(true); return; }
    setModelsLoading(true);
    setError('');
    try {
      let loaded;
      if (isNativeApp()) {
        loaded = await requestNativeModels({ provider: settings.provider, apiKey, baseUrl: settings.baseUrl });
      } else {
        const response = await fetch('/api/models', {
          method: 'POST', headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ provider: settings.provider, apiKey, baseUrl: settings.baseUrl }),
        });
        const body = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(body.error || `Ошибка ${response.status}`);
        loaded = body.models || [];
      }
      setModelLists((value) => ({ ...value, [settings.provider]: loaded }));
      if (loaded.length && !loaded.some((item) => item.id === settings.model)) patchSettings({ model: loaded[0].id });
      if (!loaded.length) throw new Error('Провайдер вернул пустой список моделей');
      if (openAfter) { setSettingsOpen(false); setModelOpen(true); }
    } catch (requestError) {
      setError(requestError.message || 'Не удалось загрузить модели');
    } finally {
      setModelsLoading(false);
    }
  }

  function openModels() {
    if (!keys[settings.provider] || (provider.custom && !settings.baseUrl)) { setSettingsOpen(true); return; }
    if (!models.length) fetchModels(true); else setModelOpen(true);
  }

  async function send(text = input) {
    const content = text.trim();
    if (!content || streaming) return;
    if (!keys[settings.provider]) { setSettingsOpen(true); setError('Добавьте API-ключ провайдера'); return; }
    if (!settings.model.trim()) { openModels(); setError('Выберите модель из каталога'); return; }
    if (provider.custom && !settings.baseUrl.trim()) { setSettingsOpen(true); setError('Укажите Base URL'); return; }

    let id = activeId;
    const prior = messages;
    const userMessage = { id: uid(), role: 'user', content };
    const assistantMessage = { id: uid(), role: 'assistant', content: '' };
    if (!id) {
      id = uid();
      setChats((value) => [{ id, title: content.slice(0, 42), createdAt: Date.now(), messages: [userMessage, assistantMessage] }, ...value]);
      setActiveId(id);
    } else updateChat(id, (chat) => ({ ...chat, messages: [...chat.messages, userMessage, assistantMessage] }));
    setInput('');
    if (textareaRef.current) textareaRef.current.style.height = 'auto';
    setStreaming(true);
    setError('');
    const controller = new AbortController();
    abortRef.current = controller;

    try {
      const payloadMessages = [...(settings.system.trim() ? [{ role: 'system', content: settings.system.trim() }] : []), ...prior, userMessage]
        .filter((item) => item.content).map(({ role, content: value }) => ({ role, content: value }));
      if (isNativeApp()) {
        const answer = await requestNative({ provider: settings.provider, apiKey: keys[settings.provider], baseUrl: settings.baseUrl, model: settings.model, temperature: settings.temperature, messages: payloadMessages });
        updateChat(id, (chat) => ({ ...chat, messages: chat.messages.map((item) => item.id === assistantMessage.id ? { ...item, content: answer } : item) }));
        return;
      }
      const response = await fetch('/api/chat', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, signal: controller.signal,
        body: JSON.stringify({ provider: settings.provider, apiKey: keys[settings.provider], baseUrl: settings.baseUrl, model: settings.model, temperature: settings.temperature, messages: payloadMessages }),
      });
      if (!response.ok) { const data = await response.json().catch(() => ({})); throw new Error(data.error || `Ошибка ${response.status}`); }
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';
        for (const line of lines) {
          if (!line.trim()) continue;
          const event = JSON.parse(line);
          if (event.error) throw new Error(event.error);
          if (event.delta) updateChat(id, (chat) => ({ ...chat, messages: chat.messages.map((item) => item.id === assistantMessage.id ? { ...item, content: item.content + event.delta } : item) }));
        }
      }
    } catch (requestError) {
      if (requestError.name !== 'AbortError') setError(requestError.message || 'Не удалось получить ответ');
      updateChat(id, (chat) => ({ ...chat, messages: chat.messages.filter((item) => item.id !== assistantMessage.id || item.content) }));
    } finally { setStreaming(false); abortRef.current = null; }
  }

  return (
    <div className="app-shell">
      <div className="aurora aurora-one" /><div className="aurora aurora-two" /><div className="aurora aurora-three" /><div className="noise" />
      <Sidebar open={sidebar} chats={chats} activeId={activeId} onClose={() => setSidebar(false)} onNew={newChat} onOpen={openChat} onDelete={deleteChat} />
      <main className="chat-shell">
        <header className="topbar liquid-panel">
          <button className="icon-button mobile-menu" onClick={() => setSidebar(true)} aria-label="Открыть историю"><Menu size={20} /></button>
          <Brand />
          <div className="top-actions">
            <button className="model-pill liquid-control" onClick={openModels}><ProviderMark provider={provider} small /><span>{settings.model || 'Выбрать модель'}</span><ChevronDown size={15} /></button>
            <button className="icon-button" onClick={() => setSettingsOpen(true)} aria-label="Подключение"><Settings2 size={19} /></button>
            <button className="icon-button" onClick={newChat} aria-label="Новый чат"><Plus size={20} /></button>
          </div>
        </header>

        <section className="message-scroll" ref={scrollRef}>
          {messages.length === 0 ? <Welcome provider={provider} settings={settings} connected={Boolean(keys[settings.provider])} onPrompt={send} onModels={openModels} /> : (
            <div className="messages"><div className="conversation-date"><span>Сегодня</span></div>{messages.map((message, index) => <Message key={message.id} message={message} provider={provider} typing={streaming && index === messages.length - 1} />)}</div>
          )}
        </section>

        <div className="composer-wrap">
          {error && <div className="error-toast"><span>{error}</span><button onClick={() => setError('')}><X size={15} /></button></div>}
          <div className="composer liquid-panel">
            <textarea ref={textareaRef} value={input} onChange={resizeInput} onKeyDown={(event) => { if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); send(); } }} placeholder="Сообщение для NitronBox" rows={1} />
            <div className="composer-row"><button className="round-action liquid-control" onClick={() => setProviderOpen(true)} aria-label="Провайдер"><ProviderMark provider={provider} small /></button><span className="privacy-note">BYOK · ключ остаётся на устройстве</span>{streaming ? <button className="send-button stop" onClick={() => abortRef.current?.abort()}><Square size={14} fill="currentColor" /></button> : <button className="send-button" disabled={!input.trim()} onClick={() => send()}><ArrowUp size={20} /></button>}</div>
          </div>
          <p className="disclaimer">NitronBox может ошибаться. Проверяйте важную информацию.</p>
        </div>
      </main>

      {providerOpen && <ProviderSheet selected={settings.provider} onSelect={chooseProvider} onClose={() => setProviderOpen(false)} />}
      {modelOpen && <ModelSheet provider={provider} models={models} selected={settings.model} loading={modelsLoading} onRefresh={() => fetchModels(false)} onSelect={(model) => { patchSettings({ model }); setModelOpen(false); }} onClose={() => setModelOpen(false)} />}
      {settingsOpen && <SettingsSheet settings={settings} provider={provider} apiKey={keys[settings.provider] || ''} modelsCount={models.length} loading={modelsLoading} keyVisible={keyVisible} onKeyVisible={() => setKeyVisible((value) => !value)} onKey={(value) => { setKeys((current) => ({ ...current, [settings.provider]: value })); setModelLists((current) => ({ ...current, [settings.provider]: [] })); }} onChange={patchSettings} onLoadModels={() => fetchModels(true)} onProviders={() => { setSettingsOpen(false); setProviderOpen(true); }} onClose={() => { setSettingsOpen(false); setError(''); }} />}
    </div>
  );
}

function Brand() { return <div className="brand"><div className="brand-logo"><Logo size={39} /></div><div><b>NitronBox</b><span>all models · one space</span></div></div>; }
function ProviderMark({ provider, small = false }) { return <span className={`provider-mark ${small ? 'small' : ''}`} style={{ '--provider': provider.color }}>{provider.mark}</span>; }

function Welcome({ provider, settings, connected, onPrompt, onModels }) {
  const icons = { sparkles: Sparkles, code: Code2, pen: PenLine, lightbulb: Lightbulb };
  return <div className="welcome"><div className="hero-logo"><div className="hero-glass-ring" /><Logo size={92} /></div><p className="eyebrow">NITRONBOX INTELLIGENCE</p><h1>Все модели.<br /><em>Одно пространство.</em></h1><p className="welcome-copy">Подключайте любимого провайдера и выбирайте любую доступную модель из его актуального каталога.</p><div className="starter-grid">{STARTERS.map((item) => { const Icon = icons[item.icon]; return <button className="liquid-card" key={item.title} onClick={() => onPrompt(item.text)}><span><Icon size={18} /></span><div><b>{item.title}</b><small>{item.text}</small></div></button>; })}</div><button className="connected-pill liquid-control" onClick={onModels}><span className={`status-dot ${connected ? '' : 'offline'}`} /><ProviderMark provider={provider} small /><span className="connection-copy"><b>{provider.name}</b><small>{settings.model || (connected ? 'выбрать модель' : 'подключить')}</small></span><ChevronDown size={14} /></button></div>;
}

function Message({ message, provider, typing }) {
  const [copied, setCopied] = useState(false);
  async function copy() { await navigator.clipboard.writeText(message.content); setCopied(true); setTimeout(() => setCopied(false), 1400); }
  if (message.role === 'user') return <div className="message user-message"><div className="user-bubble">{message.content}</div></div>;
  return <div className="message assistant-message"><div className="assistant-logo"><Logo size={28} /></div><div className="answer"><Markdown>{message.content}</Markdown>{typing && <span className="typing-caret" />}{message.content && !typing && <div className="message-tools"><button onClick={copy}>{copied ? <Check size={15} /> : <Copy size={15} />}</button><button><MoreHorizontal size={16} /></button></div>}{typing && !message.content && <div className="typing-dots"><i /><i /><i /></div>}</div></div>;
}

function Sidebar({ open, chats, activeId, onClose, onNew, onOpen, onDelete }) {
  return <><button className={`scrim ${open ? 'show' : ''}`} onClick={onClose} aria-label="Закрыть" /><aside className={`sidebar liquid-panel ${open ? 'open' : ''}`}><div className="sidebar-head"><Brand /><button className="icon-button" onClick={onClose}><PanelLeftClose size={19} /></button></div><button className="new-chat" onClick={onNew}><Plus size={18} />Новый чат</button><div className="history-label"><History size={14} />Недавние</div><div className="chat-list">{chats.length === 0 ? <p>Здесь появится история диалогов</p> : chats.map((chat) => <div className={`chat-row ${chat.id === activeId ? 'active' : ''}`} key={chat.id}><button onClick={() => onOpen(chat.id)}><MessageCircle size={16} /><span>{chat.title}</span></button><button className="delete" onClick={() => onDelete(chat.id)}><Trash2 size={14} /></button></div>)}</div><div className="sidebar-foot"><KeyRound size={15} />API-ключи не попадают<br />в историю или облако</div></aside></>;
}

function Sheet({ children, title, subtitle, onClose, wide = false }) {
  return <div className="modal-layer"><button className="modal-scrim" onClick={onClose} /><section className={`sheet liquid-panel ${wide ? 'wide' : ''}`}><div className="sheet-grabber" /><div className="sheet-head"><div><h2>{title}</h2>{subtitle && <p>{subtitle}</p>}</div><button className="icon-button" onClick={onClose}><X size={19} /></button></div>{children}</section></div>;
}

function ProviderSheet({ selected, onSelect, onClose }) {
  return <Sheet title="Провайдеры" subtitle="Выберите официальный API или добавьте собственный" onClose={onClose} wide><div className="provider-list">{PROVIDERS.map((provider) => <button key={provider.id} className={`liquid-card ${selected === provider.id ? 'selected' : ''}`} onClick={() => onSelect(provider)}><ProviderMark provider={provider} /><div><b>{provider.name}</b><span>{provider.custom ? 'OpenAI-compatible API' : 'Загрузить актуальные модели'}</span></div>{selected === provider.id && <Check size={19} />}</button>)}</div></Sheet>;
}

function ModelSheet({ provider, models, selected, loading, onRefresh, onSelect, onClose }) {
  const [query, setQuery] = useState('');
  const filtered = models.filter((model) => `${model.name} ${model.id} ${model.description}`.toLowerCase().includes(query.toLowerCase()));
  return <Sheet title={`Модели · ${provider.name}`} subtitle={`${models.length} моделей получено напрямую из API`} onClose={onClose} wide><div className="model-toolbar"><div className="search-field"><Search size={17} /><input autoFocus value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Найти модель..." /></div><button className="refresh-button" onClick={onRefresh} disabled={loading}><RefreshCw size={17} className={loading ? 'spin' : ''} /></button></div><div className="model-list">{filtered.map((model) => <button key={model.id} className={selected === model.id ? 'selected' : ''} onClick={() => onSelect(model.id)}><div><b>{model.name}</b><code>{model.id}</code>{model.description && <small>{model.description}</small>}</div>{selected === model.id && <Check size={18} />}</button>)}{!filtered.length && <div className="empty-models">Ничего не найдено</div>}</div></Sheet>;
}

function SettingsSheet({ settings, provider, apiKey, modelsCount, loading, keyVisible, onKeyVisible, onKey, onChange, onLoadModels, onProviders, onClose }) {
  return <Sheet title="Подключение" subtitle="Ключ используется только для прямых запросов к выбранному API" onClose={onClose} wide><div className="settings-form"><label>Провайдер<button className="select-field liquid-control" onClick={onProviders}><span><ProviderMark provider={provider} small />{provider.name}</span><ChevronDown size={16} /></button></label><label>API-ключ<div className="secret-field liquid-control"><input type={keyVisible ? 'text' : 'password'} value={apiKey} onChange={(event) => onKey(event.target.value)} placeholder={provider.hint} autoComplete="off" /><button onClick={onKeyVisible}>{keyVisible ? 'Скрыть' : 'Показать'}</button></div><small><KeyRound size={12} />Хранится только в памяти до закрытия приложения</small></label>{provider.custom && <label>Base URL<input value={settings.baseUrl} onChange={(event) => onChange({ baseUrl: event.target.value })} placeholder="https://api.example.com/v1" /></label>}<button className="catalog-button" onClick={onLoadModels} disabled={loading || !apiKey}><RefreshCw size={17} className={loading ? 'spin' : ''} />{loading ? 'Получаем модели...' : modelsCount ? `Обновить каталог · ${modelsCount}` : 'Получить список моделей'}</button><label>Системная инструкция<textarea value={settings.system} onChange={(event) => onChange({ system: event.target.value })} placeholder="Как должен вести себя ассистент?" rows={3} /></label><label className="range-label"><span>Температура <output>{settings.temperature.toFixed(1)}</output></span><input type="range" min="0" max="2" step="0.1" value={settings.temperature} onChange={(event) => onChange({ temperature: Number(event.target.value) })} /></label><button className="save-button" onClick={onClose}>Готово</button></div></Sheet>;
}
