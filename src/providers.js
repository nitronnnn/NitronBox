export const PROVIDERS = [
  { id: 'openai', name: 'OpenAI', mark: 'OA', color: '#101010', model: 'gpt-4o-mini', hint: 'sk-...' },
  { id: 'anthropic', name: 'Anthropic', mark: 'AN', color: '#d97757', model: 'claude-3-5-sonnet-latest', hint: 'sk-ant-...' },
  { id: 'gemini', name: 'Google Gemini', mark: 'G', color: '#4285f4', model: 'gemini-2.0-flash', hint: 'AIza...' },
  { id: 'openrouter', name: 'OpenRouter', mark: 'OR', color: '#695cff', model: 'openai/gpt-4o-mini', hint: 'sk-or-...' },
  { id: 'groq', name: 'Groq', mark: 'GQ', color: '#f55036', model: 'llama-3.3-70b-versatile', hint: 'gsk_...' },
  { id: 'mistral', name: 'Mistral AI', mark: 'MI', color: '#ff7000', model: 'mistral-small-latest', hint: 'API key' },
  { id: 'xai', name: 'xAI', mark: 'x', color: '#171717', model: 'grok-2-latest', hint: 'xai-...' },
  { id: 'custom', name: 'Свой сервер', mark: '+', color: '#16a085', model: '', hint: 'API key', custom: true },
];

export const STARTERS = [
  { icon: 'sparkles', title: 'Придумать идею', text: 'Предложи необычную идею для мобильного приложения' },
  { icon: 'code', title: 'Помочь с кодом', text: 'Объясни, как спроектировать современный REST API' },
  { icon: 'pen', title: 'Написать текст', text: 'Напиши короткий пост о пользе искусственного интеллекта' },
  { icon: 'lightbulb', title: 'Разобраться в теме', text: 'Объясни квантовые вычисления простыми словами' },
];
