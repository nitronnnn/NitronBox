import { useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Check, Copy } from 'lucide-react';

function Code({ className, children, ...props }) {
  const [copied, setCopied] = useState(false);
  const match = /language-(\w+)/.exec(className || '');
  const block = Boolean(match) || String(children).includes('\n');
  if (!block) return <code className="inline-code" {...props}>{children}</code>;
  const code = String(children).replace(/\n$/, '');
  async function copy() {
    await navigator.clipboard.writeText(code);
    setCopied(true);
    setTimeout(() => setCopied(false), 1400);
  }
  return (
    <div className="code-block">
      <div className="code-head"><span>{match?.[1] || 'code'}</span><button onClick={copy}>{copied ? <Check size={14} /> : <Copy size={14} />}{copied ? 'Готово' : 'Копировать'}</button></div>
      <pre><code>{code}</code></pre>
    </div>
  );
}

export default function Markdown({ children }) {
  return <div className="markdown"><ReactMarkdown remarkPlugins={[remarkGfm]} components={{ code: Code }}>{children}</ReactMarkdown></div>;
}
