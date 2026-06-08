import { useSimulationStore } from '../../store/simulationStore';

export default function Navbar() {
  const { connected, status, algorithm, simulationSpeed, elapsedMs, tick } =
    useSimulationStore();

  const formatElapsed = (ms) => {
    const s = Math.floor(ms / 1000);
    const m = Math.floor(s / 60);
    const h = Math.floor(m / 60);
    if (h > 0) return `${h}h ${String(m % 60).padStart(2,'0')}m ${String(s % 60).padStart(2,'0')}s`;
    if (m > 0) return `${m}m ${String(s % 60).padStart(2,'0')}s`;
    return `${s}s`;
  };

  const algoColors = { FCFS: 'var(--teal-2)', SJF: 'var(--accent)', HYBRID: 'var(--accent-2)', PRIORITY: 'var(--amber-2)' };
  const algoColor = algoColors[algorithm] ?? 'var(--text-secondary)';

  return (
    <nav className="navbar">
      <div className="navbar-brand">
        <div className="logo-icon">🖨️</div>
        <div>
          <span style={{ letterSpacing: '-0.03em' }}>OS Print Scheduler</span>
          <span style={{ marginLeft: 8, fontSize: '0.65rem', color: 'var(--text-muted)', fontFamily: 'var(--font-mono)', fontWeight: 400 }}>
            v2.0
          </span>
        </div>
      </div>

      <div className="navbar-right">
        {}
        <div style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: '0.7rem', color: 'var(--text-muted)' }}>
          <span style={{ fontFamily: 'var(--font-mono)' }}>tick</span>
          <span style={{ fontFamily: 'var(--font-mono)', color: 'var(--text-secondary)' }}>#{tick}</span>
        </div>

        {}
        <div style={{
          display: 'flex', alignItems: 'center', gap: 6,
          padding: '3px 10px', borderRadius: 6,
          background: 'var(--bg-card)', border: '1px solid var(--border)',
          fontSize: '0.72rem', color: 'var(--text-secondary)'
        }}>
          <span style={{ color: 'var(--text-muted)' }}>⏱</span>
          <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 600 }}>
            {formatElapsed(elapsedMs)}
          </span>
        </div>

        {}
        <div style={{
          display: 'flex', alignItems: 'center', gap: 0,
          background: 'var(--bg-card)', border: '1px solid var(--border)',
          borderRadius: 7, overflow: 'hidden', fontSize: '0.7rem'
        }}>
          <span style={{ padding: '3px 10px', color: algoColor, fontFamily: 'var(--font-head)', fontWeight: 700, borderRight: '1px solid var(--border)' }}>
            {algorithm}
          </span>
          <span style={{ padding: '3px 10px', fontFamily: 'var(--font-mono)', color: 'var(--text-secondary)' }}>
            {simulationSpeed}×
          </span>
        </div>

        {}
        <span className={`status-badge ${status}`}>{status}</span>

        {}
        <div style={{ display: 'flex', alignItems: 'center', gap: 5, fontSize: '0.68rem', color: 'var(--text-secondary)' }}>
          <div className={`conn-dot ${connected ? 'connected' : ''}`} />
          {connected ? 'WS Live' : 'Reconnecting…'}
        </div>
      </div>
    </nav>
  );
}
