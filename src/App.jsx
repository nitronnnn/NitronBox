import { useEffect, useRef, useState } from 'react';
import {
  ArrowUp, Check, ChevronDown, Code2, Copy, History, KeyRound, Lightbulb,
  Menu, MessageCircle, MoreHorizontal, PanelLeftClose, PenLine, Plus,
  Settings2, Sparkles, Square, Trash2, X,
} from 'lucide-react';
import Markdown from './Markdown';
import { PROVIDERS, STARTERS } from './providers';
import { isNativeApp, requestNative } from './nativeApi';

const CHATS_KEY = 'prism_chats_v1';
const SETTINGS_KEY = 'prism_settings_v1';
const uid = () => `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 7)}`;

function load(key, fallback) {
  try { return JSON.parse(localStorage.getItem(key)) || fallback; } catch { return fallback; }
}

export default function App() {
  const [chats, setChats] = useState(() => load(CHATS_KEY, []));
  const [activeId, setActiveId] = useState(() => load(CHATS_KEY, [])[0]?.id || null);
  const [settings, setSettings] = useState(() => ({ provider: 'openai', model: 'gpt-4o-mini', baseUrl: '', system: '', temperature: 0.7, ...load(SETTINGS_KEY, {}) }));
  const [keys, setKeys] = useState({});
  const [input, setInput] = useState('');
  const [streaming, setStreaming] = useState(false);
  const [error, setError] = useState('');
  const [sidebar, setSidebar] = useState(false);
  const [providerOpen, setProviderOpen] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [keyVisible, setKeyVisible] = useState(false);
  const abortRef = useRef(null);
  const scrollRef = useRef(null);
  const textareaRef = useRef(null);
  const activeChat = chats.find((chat) => chat.id === activeId);
  const messages = activeChat?.messages || [];
  const provider = PROVIDERS.find((item) => item.id === settings.provider) || PROVIDERS[0];

  useEffect(() => localStorage.setItem(CHATS_KEY, JSON.stringify(chats.slice(0, 50))), [chats]);
  useEffect(() => localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings)), [settings]);
  useEffect(() => { scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: 'smooth' }); }, [messages]);

  function patchSettings(patch) { setSettings((value) => ({ ...value, ...patch })); }
  function chooseProvider(next) {
    patchSettings({ provider: next.id, model: next.model, ...(next.custom ? {} : { baseUrl: '' }) });
    setProviderOpen(false);
  }
  function newChat() { setActiveId(null); setInput(''); setError(''); setSidebar(false); }
  function openChat(id) { setActiveId(id); setSidebar(false); setError(''); }
  function deleteChat(id) {
    setChats((value) => value.filter((chat) => chat.id !== id));
    if (id === activeId) setActiveId(null);
  }
  function updateChat(id, updater) {
    setChats((value) => value.map((chat) => chat.id === id ? updater(chat) : chat));
  }
  function resizeInput(event) {
    setInput(event.target.value);
    event.target.style.height = 'auto';
    event.target.style.height = `${Math.min(event.target.scrollHeight, 144)}px`;
  }

  async function send(text = input) {
    const content = text.trim();
    if (!content || streaming) return;
    if (!keys[settings.provider]) { setSettingsOpen(true); setError('Добавьте API-ключ провайдера'); return; }
    if (!settings.model.trim()) { setSettingsOpen(true); setError('Укажите модель'); return; }
    if (settings.provider === 'custom' && !settings.baseUrl.trim()) { setSettingsOpen(true); setError('Укажите Base URL'); return; }

    let id = activeId;
    let prior = messages;
    const userMessage = { id: uid(), role: 'user', content };
    const assistantMessage = { id: uid(), role: 'assistant', content: '' };
    if (!id) {
      id = uid();
      setChats((value) => [{ id, title: content.slice(0, 42), createdAt: Date.now(), messages: [userMessage, assistantMessage] }, ...value]);
      setActiveId(id);
    } else {
      updateChat(id, (chat) => ({ ...chat, messages: [...chat.messages, userMessage, assistantMessage] }));
    }
    setInput('');
    if (textareaRef.current) textareaRef.current.style.height = 'auto';
    setStreaming(true);
    setError('');
    const controller = new AbortController();
    abortRef.current = controller;

    try {
      const payloadMessages = [...(settings.system.trim() ? [{ role: 'system', content: settings.system.trim() }] : []), ...prior, userMessage]
        .filter((item) => item.content)
        .map(({ role, content: value }) => ({ role, content: value }));
      if (isNativeApp()) {
        const answer = await requestNative({
          provider: settings.provider,
          apiKey: keys[settings.provider],
          baseUrl: settings.baseUrl,
          model: settings.model,
          temperature: settings.temperature,
          messages: payloadMessages,
        });
        updateChat(id, (chat) => ({
          ...chat,
          messages: chat.messages.map((item) => item.id === assistantMessage.id ? { ...item, content: answer } : item),
        }));
        return;
      }
      const response = await fetch('/api/chat', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, signal: controller.signal,
        body: JSON.stringify({ provider: settings.provider, apiKey: keys[settings.provider], baseUrl: settings.baseUrl, model: settings.model, temperature: settings.temperature, messages: payloadMessages }),
      });
      if (!response.ok) {
        const data = await response.json().catch(() => ({}));
        throw new Error(data.error || `Ошибка ${response.status}`);
      }
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
    } finally {
      setStreaming(false);
      abortRef.current = null;
    }
  }

  return (
    <div className="app-shell">
      <div className="ambient ambient-a" /><div className="ambient ambient-b" /><div className="noise" />
      <Sidebar open={sidebar} chats={chats} activeId={activeId} onClose={() => setSidebar(false)} onNew={newChat} onOpen={openChat} onDelete={deleteChat} />
      <main className="chat-shell">
        <header className="topbar glass">
          <button className="icon-button mobile-menu" onClick={() => setSidebar(true)} aria-label="Открыть историю"><Menu size={20} /></button>
          <div className="brand"><div className="brand-orb"><Sparkles size={17} /></div><div><b>Prism</b><span>AI companion</span></div></div>
          <div className="top-actions">
            <button className="model-pill" onClick={() => setProviderOpen(true)}><ProviderMark provider={provider} small /><span>{settings.model || 'Выбрать модель'}</span><ChevronDown size={15} /></button>
            <button className="icon-button" onClick={() => setSettingsOpen(true)} aria-label="Настройки"><Settings2 size={19} /></button>
            <button className="icon-button" onClick={newChat} aria-label="Новый чат"><Plus size={20} /></button>
          </div>
        </header>

        <section className="message-scroll" ref={scrollRef}>
          {messages.length === 0 ? <Welcome provider={provider} settings={settings} onPrompt={send} onProviders={() => setProviderOpen(true)} /> : (
            <div className="messages">
              <div className="conversation-date">Сегодня</div>
              {messages.map((message, index) => <Message key={message.id} message={message} provider={provider} typing={streaming && index === messages.length - 1} />)}
            </div>
          )}
        </section>

        <div className="composer-wrap">
          {error && <div className="error-toast"><span>{error}</span><button onClick={() => setError('')}><X size={15} /></button></div>}
          <div className="composer glass">
            <textarea ref={textareaRef} value={input} onChange={resizeInput} onKeyDown={(event) => { if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); send(); } }} placeholder="Спросите что угодно..." rows={1} />
            <div className="composer-row"><button className="round-action" aria-label="Добавить"><Plus size={20} /></button><span className="privacy-note">Ключ не сохраняется</span>{streaming ? <button className="send-button stop" onClick={() => abortRef.current?.abort()}><Square size={15} fill="currentColor" /></button> : <button className="send-button" disabled={!input.trim()} onClick={() => send()}><ArrowUp size={20} /></button>}</div>
          </div>
          <p className="disclaimer">ИИ может ошибаться. Проверяйте важную информацию.</p>
        </div>
      </main>

      {providerOpen && <ProviderSheet selected={settings.provider} onSelect={chooseProvider} onClose={() => setProviderOpen(false)} />}
      {settingsOpen && <SettingsSheet settings={settings} provider={provider} apiKey={keys[settings.provider] || ''} keyVisible={keyVisible} onKeyVisible={() => setKeyVisible((value) => !value)} onKey={(value) => setKeys((current) => ({ ...current, [settings.provider]: value }))} onChange={patchSettings} onProviders={() => { setSettingsOpen(false); setProviderOpen(true); }} onClose={() => { setSettingsOpen(false); setError(''); }} />}
    </div>
  );
}

function ProviderMark({ provider, small = false }) { return <span className={`provider-mark ${small ? 'small' : ''}`} style={{ '--provider': provider.color }}>{provider.mark}</span>; }

function Welcome({ provider, settings, onPrompt, onProviders }) {
  const icons = { sparkles: Sparkles, code: Code2, pen: PenLine, lightbulb: Lightbulb };
  return <div className="welcome"><div className="hero-orb"><Sparkles size={30} /></div><p className="eyebrow">ВАШ ПЕРСОНАЛЬНЫЙ ИИ</p><h1>Чем помочь?</h1><p className="welcome-copy">Общайтесь, создавайте и разбирайтесь в сложном вместе с лучшими моделями.</p><div className="starter-grid">{STARTERS.map((item) => { const Icon = icons[item.icon]; return <button key={item.title} onClick={() => onPrompt(item.text)}><span><Icon size={19} /></span><div><b>{item.title}</b><small>{item.text}</small></div></button>; })}</div><button className="connected-pill" onClick={onProviders}><span className="status-dot" /><ProviderMark provider={provider} small />{provider.name}<span className="divider-dot">·</span>{settings.model || 'модель не выбрана'}<ChevronDown size={14} /></button></div>;
}

function Message({ message, provider, typing }) {
  const [copied, setCopied] = useState(false);
  async function copy() { await navigator.clipboard.writeText(message.content); setCopied(true); setTimeout(() => setCopied(false), 1400); }
  if (message.role === 'user') return <div className="message user-message"><div className="user-bubble">{message.content}</div></div>;
  return <div className="message assistant-message"><ProviderMark provider={provider} /><div className="answer"><Markdown>{message.content}</Markdown>{typing && <span className="typing-caret" />}{message.content && !typing && <div className="message-tools"><button onClick={copy}>{copied ? <Check size={15} /> : <Copy size={15} />}</button><button><MoreHorizontal size={16} /></button></div>}{typing && !message.content && <div className="typing-dots"><i /><i /><i /></div>}</div></div>;
}

function Sidebar({ open, chats, activeId, onClose, onNew, onOpen, onDelete }) {
  return <><button className={`scrim ${open ? 'show' : ''}`} onClick={onClose} aria-label="Закрыть" /><aside className={`sidebar glass ${open ? 'open' : ''}`}><div className="sidebar-head"><div className="brand"><div className="brand-orb"><Sparkles size={17} /></div><b>Prism</b></div><button className="icon-button" onClick={onClose}><PanelLeftClose size={19} /></button></div><button className="new-chat" onClick={onNew}><Plus size={18} />Новый чат</button><div className="history-label"><History size={14} />История</div><div className="chat-list">{chats.length === 0 ? <p>Диалогов пока нет</p> : chats.map((chat) => <div className={`chat-row ${chat.id === activeId ? 'active' : ''}`} key={chat.id}><button onClick={() => onOpen(chat.id)}><MessageCircle size={16} /><span>{chat.title}</span></button><button className="delete" onClick={() => onDelete(chat.id)}><Trash2 size={14} /></button></div>)}</div><div className="sidebar-foot"><KeyRound size={15} />API-ключи хранятся только<br />до закрытия вкладки</div></aside></>;
}

function Sheet({ children, title, subtitle, onClose, wide = false }) {
  return <div className="modal-layer"><button className="modal-scrim" onClick={onClose} /><section className={`sheet glass ${wide ? 'wide' : ''}`}><div className="sheet-grabber" /><div className="sheet-head"><div><h2>{title}</h2>{subtitle && <p>{subtitle}</p>}</div><button className="icon-button" onClick={onClose}><X size={19} /></button></div>{children}</section></div>;
}

function ProviderSheet({ selected, onSelect, onClose }) {
  return <Sheet title="Выберите провайдера" subtitle="Официальные API и OpenAI-совместимые серверы" onClose={onClose} wide><div className="provider-list">{PROVIDERS.map((provider) => <button key={provider.id} className={selected === provider.id ? 'selected' : ''} onClick={() => onSelect(provider)}><ProviderMark provider={provider} /><div><b>{provider.name}</b><span>{provider.custom ? 'Любой совместимый endpoint' : provider.model}</span></div>{selected === provider.id && <Check size={19} />}</button>)}</div></Sheet>;
}

function SettingsSheet({ settings, provider, apiKey, keyVisible, onKeyVisible, onKey, onChange, onProviders, onClose }) {
  return <Sheet title="Подключение" subtitle="Настройте модель и параметры запроса" onClose={onClose} wide><div className="settings-form"><label>Провайдер<button className="select-field" onClick={onProviders}><span><ProviderMark provider={provider} small />{provider.name}</span><ChevronDown size={16} /></button></label><label>API-ключ<div className="secret-field"><input type={keyVisible ? 'text' : 'password'} value={apiKey} onChange={(event) => onKey(event.target.value)} placeholder={provider.hint} autoComplete="off" /><button onClick={onKeyVisible}>{keyVisible ? 'Скрыть' : 'Показать'}</button></div><small><KeyRound size={12} />Ключ хранится только в памяти вкладки</small></label>{provider.custom && <label>Base URL<input value={settings.baseUrl} onChange={(event) => onChange({ baseUrl: event.target.value })} placeholder="https://api.example.com/v1" /></label>}<label>Модель<input value={settings.model} onChange={(event) => onChange({ model: event.target.value })} placeholder="Название модели" /></label><label>Системная инструкция<textarea value={settings.system} onChange={(event) => onChange({ system: event.target.value })} placeholder="Как должен вести себя ассистент?" rows={3} /></label><label className="range-label"><span>Температура <output>{settings.temperature.toFixed(1)}</output></span><input type="range" min="0" max="2" step="0.1" value={settings.temperature} onChange={(event) => onChange({ temperature: Number(event.target.value) })} /></label><button className="save-button" onClick={onClose}>Готово</button></div></Sheet>;
}
