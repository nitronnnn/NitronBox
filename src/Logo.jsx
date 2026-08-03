export default function Logo({ size = 38, className = '' }) {
  return (
    <svg className={className} width={size} height={size} viewBox="0 0 48 48" fill="none" aria-hidden="true">
      <defs>
        <linearGradient id="nitron-a" x1="9" y1="8" x2="36" y2="39" gradientUnits="userSpaceOnUse">
          <stop stopColor="#D7F7FF" />
          <stop offset="0.45" stopColor="#6FE0FF" />
          <stop offset="1" stopColor="#786BFF" />
        </linearGradient>
        <linearGradient id="nitron-b" x1="38" y1="7" x2="12" y2="41" gradientUnits="userSpaceOnUse">
          <stop stopColor="#FFFFFF" />
          <stop offset="0.4" stopColor="#A98BFF" />
          <stop offset="1" stopColor="#4D7CFF" />
        </linearGradient>
        <filter id="nitron-glow" x="-50%" y="-50%" width="200%" height="200%">
          <feGaussianBlur stdDeviation="2.2" result="blur" />
          <feMerge><feMergeNode in="blur" /><feMergeNode in="SourceGraphic" /></feMerge>
        </filter>
      </defs>
      <path d="M7.5 35.8 20.8 9.4c1.2-2.4 4.6-2.5 5.9-.1l4.1 7.5-8.4 16.8-11.8 5.1c-2.1.9-4.1-.9-3.1-2.9Z" fill="url(#nitron-a)" filter="url(#nitron-glow)" />
      <path d="m40.5 12.2-13.3 26.4c-1.2 2.4-4.6 2.5-5.9.1l-4.1-7.5 8.4-16.8 11.8-5.1c2.1-.9 4.1.9 3.1 2.9Z" fill="url(#nitron-b)" filter="url(#nitron-glow)" />
      <path d="m20.8 9.4 9.9 7.4-4.9 9.8-8.6 4.6 3.6-21.8Z" fill="white" fillOpacity=".2" />
      <path d="m27.2 38.6-9.9-7.4 4.9-9.8 8.6-4.6-3.6 21.8Z" fill="#C8F5FF" fillOpacity=".18" />
    </svg>
  );
}
