import { useSimulationStore } from '../../store/simulationStore';

/** Shows every printer thread's state as a live Gantt-style row */
export default function ThreadStatusPanel() {
  const { printers, status, queueSize, algorithm, connected } = useSimulationStore();
  const isRunning = status === 'RUNNING';

  const threadStateColor = {
    BUSY:  { bg: 'rgba(99,102,241,0.15)', bar: 'linear-gradient(90deg, var(--accent), var(--accent-2))', text: 'var(--accent)' },
    IDLE:  { bg: 'rgba(16,185,129,0.08)', bar: 'var(--emerald)',  text: 'var(--emerald-2)' },
    ERROR: { bg: 'rgba(244,63,94,0.10)',  bar: 'var(--rose)',     text: 'var(--rose-2)' },
  };

  return (
    <div className="card">
      <div className="card-header">
        <span className="card-title">🧵 Thread Status</span>
        <div style={{ display: 'flex', gap: 6 }}>
          {['BUSY','IDLE','ERROR'].map(s => {
            const cnt = (printers || []).filter(p => p.status === s).length;
            if (!cnt) return null;
            const cls = s === 'BUSY' ? 'tag-accent' : s === 'IDLE' ? 'tag-emerald' : 'tag-rose';
            return <span key={s} className={`tag ${cls}`}>{cnt} {s}</span>;
          })}
        </div>
      </div>

      {/* Scheduler thread (synthetic) */}
      <div style={{ padding: '10px 16px 0' }}>
        <div style={{ fontSize: '0.62rem', color: 'var(--text-muted)', marginBottom: 6, textTransform: 'uppercase', letterSpacing: '0.06em', fontWeight: 600 }}>
          System Threads
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 5, marginBottom: 12 }}>
          {/* Scheduler thread */}
          <ThreadRow
            name="SchedulerThread"
            state={isRunning ? 'RUNNING' : status}
            detail={`algorithm: ${algorithm}`}
            color="var(--accent-2)"
          />
          {/* WebSocket broadcaster */}
          <ThreadRow
            name="WS Broadcaster"
            state={connected ? 'RUNNING' : 'STOPPED'}
            detail="100ms broadcast interval"
            color="var(--sky)"
          />
        </div>

        {/* Printer (consumer) threads */}
        <div style={{ fontSize: '0.62rem', color: 'var(--text-muted)', marginBottom: 6, textTransform: 'uppercase', letterSpacing: '0.06em', fontWeight: 600 }}>
          Printer Threads (Consumers)
        </div>
        {printers.length === 0 ? (
          <div style={{ padding: '14px 0', textAlign: 'center', color: 'var(--text-muted)', fontSize: '0.75rem' }}>
            No printer threads active
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
            {printers.map(p => {
              const sc = threadStateColor[p.status] ?? threadStateColor.IDLE;
              return (
                <div key={p.printerId} style={{
                  display: 'grid',
                  gridTemplateColumns: '120px 1fr 80px',
                  alignItems: 'center',
                  gap: 10,
                  padding: '7px 10px',
                  background: sc.bg,
                  border: `1px solid ${p.status === 'BUSY' ? 'rgba(99,102,241,0.25)' : p.status === 'ERROR' ? 'rgba(244,63,94,0.25)' : 'var(--border)'}`,
                  borderRadius: 8,
                  transition: 'all 0.3s ease',
                }}>
                  {/* Thread name */}
                  <div>
                    <div style={{ fontFamily: 'var(--font-mono)', fontSize: '0.7rem', color: sc.text, fontWeight: 600 }}>
                      {p.name || `Printer-${p.printerId}`}
                    </div>
                    <div style={{ fontSize: '0.58rem', color: 'var(--text-muted)', marginTop: 1 }}>
                      {p.status === 'BUSY'  ? `Job #${p.currentJobId} · ${p.currentJobPages}pp` :
                       p.status === 'IDLE'  ? 'WAITING — semaphore.acquire()' :
                       p.status === 'ERROR' ? 'BLOCKED — hardware fault' : '—'}
                    </div>
                  </div>

                  {/* Progress bar */}
                  <div style={{ height: 16, background: 'var(--bg-surface)', borderRadius: 4, overflow: 'hidden', border: '1px solid var(--border)' }}>
                    {p.status === 'BUSY' && (
                      <div style={{
                        height: '100%',
                        width: `${p.jobProgressPercent}%`,
                        background: sc.bar,
                        borderRadius: 4,
                        transition: 'width 0.4s ease',
                        display: 'flex', alignItems: 'center', paddingLeft: 6,
                      }}>
                        <span style={{ fontSize: '0.56rem', color: 'white', fontFamily: 'var(--font-mono)', fontWeight: 700, whiteSpace: 'nowrap' }}>
                          {p.jobProgressPercent}%
                        </span>
                      </div>
                    )}
                    {p.status === 'IDLE' && (
                      <div style={{
                        height: '100%', width: '100%',
                        background: 'repeating-linear-gradient(90deg, transparent 0px, transparent 6px, rgba(16,185,129,0.08) 6px, rgba(16,185,129,0.08) 12px)',
                        animation: 'idleStripe 2s linear infinite',
                      }} />
                    )}
                    {p.status === 'ERROR' && (
                      <div style={{ height: '100%', width: '100%', background: 'rgba(244,63,94,0.12)' }} />
                    )}
                  </div>

                  {/* Stats */}
                  <div style={{ textAlign: 'right' }}>
                    <div style={{ fontSize: '0.68rem', fontFamily: 'var(--font-head)', fontWeight: 700, color: 'var(--text-primary)' }}>
                      {p.jobsCompleted}
                    </div>
                    <div style={{ fontSize: '0.58rem', color: 'var(--text-muted)' }}>jobs done</div>
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {/* User (producer) threads hint */}
        <div style={{ fontSize: '0.62rem', color: 'var(--text-muted)', margin: '12px 0 6px', textTransform: 'uppercase', letterSpacing: '0.06em', fontWeight: 600 }}>
          User Threads (Producers)
        </div>
        <div style={{
          padding: '8px 10px',
          background: 'rgba(20,184,166,0.06)',
          border: '1px solid var(--border)',
          borderRadius: 8,
          display: 'flex',
          alignItems: 'center',
          gap: 10,
        }}>
          <div style={{ fontSize: '0.7rem', color: 'var(--teal-2)', fontFamily: 'var(--font-mono)', fontWeight: 600 }}>
            UserThread × N
          </div>
          <div style={{ flex: 1, fontSize: '0.62rem', color: 'var(--text-muted)' }}>
            {isRunning
              ? `Submitting jobs at interval → ${queueSize} currently buffered`
              : 'Sleeping — simulation not running'}
          </div>
          <div style={{
            width: 8, height: 8, borderRadius: '50%',
            background: isRunning ? 'var(--teal)' : 'var(--text-muted)',
            boxShadow: isRunning ? '0 0 8px var(--teal-glow)' : 'none',
            animation: isRunning ? 'pulse-dot 1.5s ease-in-out infinite' : 'none',
          }} />
        </div>
      </div>

      <div style={{ padding: '10px 16px 14px' }} />

      <style>{`
        @keyframes idleStripe {
          from { background-position: 0 0; }
          to   { background-position: 24px 0; }
        }
      `}</style>
    </div>
  );
}

function ThreadRow({ name, state, detail, color }) {
  const isRunning = state === 'RUNNING';
  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: '140px 1fr 56px',
      alignItems: 'center',
      gap: 10,
      padding: '6px 10px',
      background: isRunning ? `${color}10` : 'var(--bg-card)',
      border: `1px solid ${isRunning ? `${color}30` : 'var(--border)'}`,
      borderRadius: 7,
      transition: 'all 0.3s ease',
    }}>
      <div>
        <div style={{ fontFamily: 'var(--font-mono)', fontSize: '0.68rem', color: isRunning ? color : 'var(--text-muted)', fontWeight: 600 }}>
          {name}
        </div>
        <div style={{ fontSize: '0.56rem', color: 'var(--text-muted)', marginTop: 1 }}>{detail}</div>
      </div>

      {/* Indeterminate bar */}
      <div style={{ height: 4, background: 'var(--bg-surface)', borderRadius: 2, overflow: 'hidden' }}>
        {isRunning && (
          <div style={{
            height: '100%',
            width: '40%',
            background: color,
            borderRadius: 2,
            animation: 'indeterminate 1.5s ease-in-out infinite',
          }} />
        )}
      </div>

      <div style={{
        fontSize: '0.6rem', fontWeight: 700, textAlign: 'right',
        color: isRunning ? color : 'var(--text-muted)',
        textTransform: 'uppercase', letterSpacing: '0.04em',
      }}>
        {state}
      </div>

      <style>{`
        @keyframes indeterminate {
          0%   { transform: translateX(-100%); }
          100% { transform: translateX(350%); }
        }
      `}</style>
    </div>
  );
}
