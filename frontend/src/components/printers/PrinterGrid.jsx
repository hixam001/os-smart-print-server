import { useSimulationStore } from '../../store/simulationStore';

const RADIUS = 32;
const CIRC   = 2 * Math.PI * RADIUS;

function ProgressRing({ percent, status }) {
  const offset = CIRC - (percent / 100) * CIRC;
  const colors = {
    BUSY:  { stroke: 'url(#busyGrad)', shadow: 'var(--accent)' },
    IDLE:  { stroke: 'var(--emerald)', shadow: 'var(--emerald)' },
    ERROR: { stroke: 'var(--rose)',    shadow: 'var(--rose)' },
  };
  const { stroke } = colors[status] ?? { stroke: 'var(--text-muted)' };

  return (
    <div className="ring-container" style={{ width: 76, height: 76 }}>
      <svg width="76" height="76" viewBox="0 0 76 76">
        <defs>
          <linearGradient id="busyGrad" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%"   stopColor="var(--accent)" />
            <stop offset="100%" stopColor="var(--accent-2)" />
          </linearGradient>
          <filter id="ringGlow">
            <feGaussianBlur stdDeviation="2" result="blur" />
            <feMerge>
              <feMergeNode in="blur" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
        </defs>
        <circle className="ring-track" cx="38" cy="38" r={RADIUS} />
        <circle
          className="ring-fill"
          cx="38" cy="38" r={RADIUS}
          stroke={stroke}
          strokeDasharray={CIRC}
          strokeDashoffset={status === 'IDLE' ? 0 : offset}
          style={{ filter: status === 'BUSY' ? 'drop-shadow(0 0 5px var(--accent))' : 'none' }}
        />
      </svg>
      <div className="ring-label">
        {status === 'BUSY'  && <span style={{ color: 'var(--accent)',     fontSize: '0.82rem' }}>{percent}%</span>}
        {status === 'IDLE'  && <span style={{ color: 'var(--emerald-2)',  fontSize: '0.9rem' }}>✓</span>}
        {status === 'ERROR' && <span style={{ color: 'var(--rose-2)',     fontSize: '0.9rem' }}>✕</span>}
      </div>
    </div>
  );
}

function PrinterCard({ printer }) {
  const {
    printerId, name, status, currentJobId, currentJobPages,
    jobProgressPercent, jobsCompleted, totalPages, averagePrintTimeMs,
  } = printer;

  const statusIcons = { BUSY: '🖨', IDLE: '✅', ERROR: '❌' };

  return (
    <div className={`printer-card ${status} animate-scale`} id={`printer-${printerId}`}>
      {/* Header row */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 6, width: '100%' }}>
        <span style={{ fontSize: '0.9rem' }}>{statusIcons[status] ?? '🖨'}</span>
        <span className="printer-name" style={{ flex: 1, textAlign: 'left', fontSize: '0.82rem' }}>
          {name || `Printer ${printerId}`}
        </span>
        <span className={`printer-status-text ${status}`}>{status}</span>
      </div>

      {/* Progress ring */}
      <ProgressRing percent={Number(jobProgressPercent)} status={status} />

      {/* Current job info */}
      {currentJobId ? (
        <div className="printer-job-info">
          <div>Job <strong style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-mono)' }}>#{currentJobId}</strong></div>
          <div>{currentJobPages} pages</div>
        </div>
      ) : (
        <div className="printer-job-info" style={{ color: 'var(--text-muted)' }}>
          {status === 'IDLE' ? 'Waiting for job…' : status === 'ERROR' ? 'Hardware error' : '—'}
        </div>
      )}

      {/* Progress bar (only when BUSY) */}
      {status === 'BUSY' && (
        <div style={{ width: '100%' }}>
          <div style={{
            height: 3, borderRadius: 2,
            background: 'var(--bg-surface)',
            overflow: 'hidden',
          }}>
            <div style={{
              height: '100%',
              width: `${jobProgressPercent}%`,
              background: 'linear-gradient(90deg, var(--accent), var(--accent-2))',
              borderRadius: 2,
              transition: 'width 0.4s ease',
              boxShadow: '0 0 8px var(--accent-glow)',
            }} />
          </div>
        </div>
      )}

      <div className="divider" style={{ width: '100%', margin: '2px 0' }} />

      {/* Stats row */}
      <div className="printer-stats">
        <div className="printer-stat">
          <span className="printer-stat-val glow-emerald" style={{ fontSize: '0.95rem' }}>{jobsCompleted}</span>
          <span className="printer-stat-label">Done</span>
        </div>
        <div className="printer-stat">
          <span className="printer-stat-val" style={{ fontSize: '0.95rem' }}>{totalPages}</span>
          <span className="printer-stat-label">Pages</span>
        </div>
        <div className="printer-stat">
          <span className="printer-stat-val" style={{ fontSize: '0.95rem' }}>
            {averagePrintTimeMs > 0 ? `${(averagePrintTimeMs / 1000).toFixed(1)}s` : '—'}
          </span>
          <span className="printer-stat-label">Avg</span>
        </div>
      </div>
    </div>
  );
}

export default function PrinterGrid() {
  const printers = useSimulationStore(s => s.printers);

  const busyCount  = printers.filter(p => p.status === 'BUSY').length;
  const idleCount  = printers.filter(p => p.status === 'IDLE').length;
  const errorCount = printers.filter(p => p.status === 'ERROR').length;
  const utilPct    = printers.length > 0 ? Math.round((busyCount / printers.length) * 100) : 0;

  return (
    <div className="card">
      <div className="card-header">
        <span className="card-title">🖨️ Printer Pool</span>
        <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
          {busyCount  > 0 && <span className="tag tag-accent">{busyCount} BUSY</span>}
          {idleCount  > 0 && <span className="tag tag-emerald">{idleCount} IDLE</span>}
          {errorCount > 0 && <span className="tag tag-rose">{errorCount} ERR</span>}
          {printers.length > 0 && (
            <span style={{ fontSize: '0.68rem', color: 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}>
              {utilPct}% util
            </span>
          )}
        </div>
      </div>

      {/* Utilisation bar */}
      {printers.length > 0 && (
        <div style={{ padding: '8px 16px 0', display: 'flex', alignItems: 'center', gap: 8 }}>
          <div style={{ flex: 1, height: 4, background: 'var(--bg-surface)', borderRadius: 2, overflow: 'hidden' }}>
            <div style={{
              height: '100%',
              width: `${utilPct}%`,
              background: utilPct >= 80
                ? 'linear-gradient(90deg, var(--amber), var(--rose))'
                : 'linear-gradient(90deg, var(--emerald), var(--teal))',
              borderRadius: 2,
              transition: 'width 0.5s ease',
            }} />
          </div>
          <span style={{ fontSize: '0.62rem', color: 'var(--text-muted)' }}>Utilisation</span>
        </div>
      )}

      {printers.length === 0 ? (
        <div className="empty-state">
          <div className="icon">🖨</div>
          <div>No printers yet — start a simulation</div>
        </div>
      ) : (
        <div className="printers-grid">
          {printers.map(p => <PrinterCard key={p.printerId} printer={p} />)}
        </div>
      )}
    </div>
  );
}
