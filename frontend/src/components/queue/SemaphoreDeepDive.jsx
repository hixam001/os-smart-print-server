import { useRef, useEffect } from 'react';
import { useSimulationStore } from '../../store/simulationStore';

/* ────────────────────────────────────────────────────────────────────────────
 * SemaphoreDeepDive
 * Detailed visualisation of the counting semaphore:
 *  - Permit dots (available vs consumed)
 *  - Blocked waiter count (threads stuck in acquire())
 *  - Live operation log (WAIT / ACQUIRED / RELEASED)
 *  - Mini sparkline of permit count over time
 * ─────────────────────────────────────────────────────────────────────────── */

const MAX_OPS = 20;

const OP_STYLE = {
  SEMAPHORE_WAIT:     { color: '#f59e0b', icon: '⏳', label: 'WAIT',     desc: 'Printer blocked — acquire() waiting for permit' },
  SEMAPHORE_ACQUIRED: { color: '#14b8a6', icon: '🔓', label: 'ACQUIRED', desc: 'Permit granted — printer dequeues job' },
  SEMAPHORE_RELEASED: { color: '#6366f1', icon: '🔔', label: 'RELEASED', desc: 'User enqueued job — release() called, permit++' },
};

export default function SemaphoreDeepDive() {
  const permits  = useSimulationStore(s => s.semaphorePermits);
  const waiters  = useSimulationStore(s => s.semaphoreWaiters);
  const history  = useSimulationStore(s => s.semaphoreHistory);
  const events   = useSimulationStore(s => s.events);
  const queueCap = useSimulationStore(s => s.queueCapacity);
  const canvasRef = useRef(null);

  // Filter only semaphore events from the event feed
  const semOps = events
    .filter(e => OP_STYLE[e.eventType])
    .slice(0, MAX_OPS);

  // Draw sparkline on canvas
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas || history.length < 2) return;
    const ctx = canvas.getContext('2d');
    const W = canvas.width, H = canvas.height;
    ctx.clearRect(0, 0, W, H);

    const maxP = Math.max(...history.map(h => h.permits), 1);

    // Permits line
    ctx.beginPath();
    ctx.strokeStyle = '#14b8a6';
    ctx.lineWidth = 2;
    history.forEach((h, i) => {
      const x = (i / (history.length - 1)) * W;
      const y = H - (h.permits / maxP) * (H - 4) - 2;
      i === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y);
    });
    ctx.stroke();

    // Fill under permits line
    ctx.lineTo(W, H); ctx.lineTo(0, H); ctx.closePath();
    ctx.fillStyle = 'rgba(20,184,166,0.08)';
    ctx.fill();

    // Waiters line
    if (history.some(h => h.waiters > 0)) {
      const maxW = Math.max(...history.map(h => h.waiters), 1);
      ctx.beginPath();
      ctx.strokeStyle = '#f59e0b';
      ctx.lineWidth = 1.5;
      ctx.setLineDash([3, 3]);
      history.forEach((h, i) => {
        const x = (i / (history.length - 1)) * W;
        const y = H - (h.waiters / maxW) * (H - 4) - 2;
        i === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y);
      });
      ctx.stroke();
      ctx.setLineDash([]);
    }
  }, [history]);

  const capacity = queueCap || 20;

  return (
    <div className="card">
      <div className="card-header">
        <span className="card-title">🔐 Counting Semaphore — Deep Dive</span>
        <div style={{ display: 'flex', gap: 6 }}>
          {waiters > 0 && (
            <span className="tag tag-amber" style={{ animation: 'pulse-dot 1.5s ease-in-out infinite' }}>
              ⏳ {waiters} blocked
            </span>
          )}
          <span className="tag tag-teal">{permits} permits</span>
        </div>
      </div>

      <div style={{ padding: '12px 16px', display: 'flex', flexDirection: 'column', gap: 14 }}>

        {/* Theory callout */}
        <div style={{
          background: 'rgba(99,102,241,0.07)', border: '1px solid rgba(99,102,241,0.2)',
          borderRadius: 10, padding: '10px 14px', fontSize: '0.72rem', lineHeight: 1.6,
          color: 'var(--text-secondary)',
        }}>
          <strong style={{ color: 'var(--accent)' }}>How it works: </strong>
          Each time a <span style={{ color: '#6366f1' }}>UserThread</span> enqueues a job, it calls{' '}
          <code style={{ color: '#14b8a6', background: 'rgba(20,184,166,0.1)', padding: '0 4px', borderRadius: 3 }}>release()</code>
          {' '}→ permits++, waking one blocked printer.
          Each <span style={{ color: '#8b5cf6' }}>PrinterThread</span> calls{' '}
          <code style={{ color: '#14b8a6', background: 'rgba(20,184,166,0.1)', padding: '0 4px', borderRadius: 3 }}>acquire()</code>
          {' '}— if permits == 0, it blocks in <code style={{ color: '#f59e0b', background: 'rgba(245,158,11,0.1)', padding: '0 4px', borderRadius: 3 }}>wait()</code>.
          This solves the <strong style={{ color: '#f43f5e' }}>Producer-Consumer problem</strong> without busy-waiting.
        </div>

        {/* Permit dots */}
        <div>
          <div style={{ fontSize: '0.65rem', color: 'var(--text-muted)', fontWeight: 700, letterSpacing: '0.06em', textTransform: 'uppercase', marginBottom: 6 }}>
            Permit Pool — {permits}/{capacity} available
          </div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 5 }}>
            {Array.from({ length: capacity }).map((_, i) => {
              const isActive = i < permits;
              const isWaiting = i < waiters;
              return (
                <div key={i} style={{
                  width: 14, height: 14, borderRadius: '50%',
                  background: isActive ? '#14b8a6' : isWaiting ? '#f59e0b33' : 'var(--bg-surface)',
                  border: `1.5px solid ${isActive ? '#14b8a6' : isWaiting ? '#f59e0b' : 'var(--border)'}`,
                  boxShadow: isActive ? '0 0 6px rgba(20,184,166,0.5)' : 'none',
                  transition: 'all 0.3s ease',
                  animation: isActive && i === permits - 1 ? 'permitPop 0.3s cubic-bezier(0.34,1.56,0.64,1)' : 'none',
                }} />
              );
            })}
          </div>
          <div style={{ display: 'flex', gap: 12, marginTop: 6, fontSize: '0.6rem', color: 'var(--text-muted)' }}>
            <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
              <div style={{ width: 8, height: 8, borderRadius: '50%', background: '#14b8a6' }} /> Available permit
            </span>
            <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
              <div style={{ width: 8, height: 8, borderRadius: '50%', border: '1.5px solid #f59e0b' }} /> Waiter (blocked)
            </span>
            <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
              <div style={{ width: 8, height: 8, borderRadius: '50%', background: 'var(--bg-surface)', border: '1.5px solid var(--border)' }} /> Empty slot
            </span>
          </div>
        </div>

        {/* Waiters display */}
        {waiters > 0 && (
          <div style={{
            background: 'rgba(245,158,11,0.08)', border: '1px solid rgba(245,158,11,0.25)',
            borderRadius: 8, padding: '8px 12px',
            fontSize: '0.72rem', color: '#fbbf24',
            display: 'flex', alignItems: 'center', gap: 8,
          }}>
            <span style={{ fontSize: '1.1rem' }}>⏳</span>
            <div>
              <div style={{ fontWeight: 700 }}>{waiters} printer thread{waiters > 1 ? 's' : ''} blocked in acquire()</div>
              <div style={{ fontSize: '0.65rem', color: 'var(--text-muted)', marginTop: 2 }}>
                Thread called acquire() but permits == 0 → entered wait() on monitor lock
              </div>
            </div>
          </div>
        )}

        {/* Sparkline */}
        {history.length > 2 && (
          <div>
            <div style={{ fontSize: '0.65rem', color: 'var(--text-muted)', fontWeight: 700, letterSpacing: '0.06em', textTransform: 'uppercase', marginBottom: 4 }}>
              Permit History
            </div>
            <div style={{ position: 'relative', height: 48, background: 'var(--bg-surface)', borderRadius: 6, overflow: 'hidden', border: '1px solid var(--border)' }}>
              <canvas ref={canvasRef} width={400} height={48} style={{ width: '100%', height: '100%' }} />
              <div style={{ position: 'absolute', bottom: 3, right: 6, display: 'flex', gap: 10, fontSize: '0.55rem', color: 'var(--text-muted)' }}>
                <span style={{ color: '#14b8a6' }}>— permits</span>
                <span style={{ color: '#f59e0b' }}>--- waiters</span>
              </div>
            </div>
          </div>
        )}

        {/* Operation log */}
        <div>
          <div style={{ fontSize: '0.65rem', color: 'var(--text-muted)', fontWeight: 700, letterSpacing: '0.06em', textTransform: 'uppercase', marginBottom: 6 }}>
            Live Operations
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 3, maxHeight: 220, overflowY: 'auto' }}>
            {semOps.length === 0 ? (
              <div style={{ color: 'var(--text-muted)', fontSize: '0.72rem', padding: '8px 0' }}>
                No semaphore events yet — start simulation
              </div>
            ) : semOps.map(ev => {
              const op = OP_STYLE[ev.eventType];
              const d  = ev.details ?? {};
              return (
                <div key={ev.id} style={{
                  display: 'flex', alignItems: 'flex-start', gap: 8,
                  padding: '5px 8px', borderRadius: 6,
                  background: `${op.color}0d`, border: `1px solid ${op.color}30`,
                  animation: 'fadeIn 0.25s ease',
                }}>
                  <span style={{ fontSize: '0.9rem', flexShrink: 0 }}>{op.icon}</span>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                      <span style={{ fontSize: '0.65rem', fontWeight: 800, color: op.color, letterSpacing: '0.05em' }}>{op.label}</span>
                      <span style={{ fontSize: '0.62rem', color: 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}>
                        {d.thread ?? '—'}
                      </span>
                      <span style={{ fontSize: '0.6rem', color: 'var(--text-secondary)' }}>
                        {d.permits != null ? `permits=${d.permits}` : ''} {d.waiters != null ? `waiters=${d.waiters}` : ''}
                      </span>
                    </div>
                    <div style={{ fontSize: '0.6rem', color: 'var(--text-muted)', marginTop: 1 }}>{op.desc}</div>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}
