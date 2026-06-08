import { useSimulationStore } from '../../store/simulationStore';

export default function SemaphoreWidget() {
  const { queueSize, queueCapacity, printers } = useSimulationStore();

  const permits    = queueSize;
  const maxDisplay = Math.min(queueCapacity, 40);
  const dots       = Array.from({ length: maxDisplay }, (_, i) => i < permits);
  const busyPrinters = (printers || []).filter(p => p.status === 'BUSY').length;
  const fillPct    = queueCapacity > 0 ? Math.round((permits / queueCapacity) * 100) : 0;

  const semColor = permits === 0
    ? 'var(--rose-2)'
    : permits < queueCapacity * 0.25
      ? 'var(--amber-2)'
      : 'var(--teal-2)';

  return (
    <div className="card">
      <div className="card-header">
        <span className="card-title">🔐 Counting Semaphore</span>
        <span className={`tag ${permits === 0 ? 'tag-rose' : 'tag-teal'} mono`}>
          {permits} permits
        </span>
      </div>

      <div className="semaphore-widget" style={{ flexDirection: 'column', gap: 10 }}>
        {}
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 10, width: '100%' }}>
          <span className="sem-count" style={{ color: semColor, fontSize: '2.8rem' }}>
            {permits}
          </span>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            <span style={{ fontSize: '0.65rem', color: 'var(--text-muted)' }}>available permits</span>
            <span style={{ fontSize: '0.65rem', color: 'var(--text-muted)' }}>of {queueCapacity} total</span>
          </div>

          {}
          <div style={{ marginLeft: 'auto', display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 4 }}>
            <div style={{ width: 60, height: 60, position: 'relative' }}>
              <svg width="60" height="60" viewBox="0 0 60 60" style={{ transform: 'rotate(-90deg)' }}>
                <circle cx="30" cy="30" r="24" fill="none" stroke="rgba(255,255,255,0.05)" strokeWidth="5" />
                <circle
                  cx="30" cy="30" r="24" fill="none"
                  stroke={semColor}
                  strokeWidth="5"
                  strokeLinecap="round"
                  strokeDasharray={`${2 * Math.PI * 24}`}
                  strokeDashoffset={`${2 * Math.PI * 24 * (1 - fillPct / 100)}`}
                  style={{ transition: 'stroke-dashoffset 0.5s ease, stroke 0.3s', filter: `drop-shadow(0 0 4px ${semColor})` }}
                />
              </svg>
              <div style={{
                position: 'absolute', inset: 0, display: 'flex', alignItems: 'center',
                justifyContent: 'center', fontSize: '0.68rem', fontFamily: 'var(--font-head)',
                fontWeight: 700, color: semColor,
              }}>
                {fillPct}%
              </div>
            </div>
          </div>
        </div>

        {}
        <div className="sem-permits" style={{ gap: 5 }}>
          {dots.map((active, i) => (
            <div
              key={i}
              className={`sem-permit ${active ? 'active' : ''}`}
              style={active ? { background: semColor, borderColor: semColor, boxShadow: `0 0 7px ${semColor}55` } : {}}
            />
          ))}
        </div>

        {}
        <div style={{
          width: '100%', padding: '8px 10px',
          background: 'var(--bg-card)', borderRadius: 8,
          border: '1px solid var(--border)',
          fontSize: '0.68rem', color: 'var(--text-muted)',
          lineHeight: 1.55,
        }}>
          {permits === 0 ? (
            <span style={{ color: 'var(--rose-2)' }}>
              ⛔ <strong>Blocked</strong> — printer threads are waiting (semaphore = 0). Queue empty.
            </span>
          ) : (
            <>
              ✅ <strong style={{ color: 'var(--text-secondary)' }}>{permits}</strong> job(s) ready ·{' '}
              <strong style={{ color: 'var(--text-secondary)' }}>{busyPrinters}</strong> printer(s) printing ·{' '}
              Printers <strong style={{ color: 'var(--text-secondary)' }}>block</strong> when count = 0
            </>
          )}
        </div>
      </div>
    </div>
  );
}
