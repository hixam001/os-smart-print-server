import { useSimulationStore } from '../store/simulationStore';

/**
 * ProcessFlowPanel — animates the full OS pipeline:
 * Users → Queue → Scheduler → Printers → Database
 */
export default function ProcessFlowPanel() {
  const { status, printers, queueSize, metrics, algorithm } = useSimulationStore();

  const isRunning   = status === 'RUNNING';
  const busyCount   = (printers || []).filter(p => p.status === 'BUSY').length;
  const idleCount   = (printers || []).filter(p => p.status === 'IDLE').length;
  const errorCount  = (printers || []).filter(p => p.status === 'ERROR').length;
  const totalDone   = metrics?.totalJobsCompleted ?? 0;

  const nodes = [
    {
      id: 'users',
      icon: '👤',
      label: 'User Threads',
      sub: 'Producers',
      color: 'var(--teal)',
      glow: 'var(--teal-glow)',
      active: isRunning,
      detail: 'Submit print jobs',
    },
    {
      id: 'queue',
      icon: '📋',
      label: 'Bounded Buffer',
      sub: `${queueSize} queued`,
      color: 'var(--accent)',
      glow: 'var(--accent-glow)',
      active: queueSize > 0,
      detail: 'PrintQueue (semaphore)',
    },
    {
      id: 'scheduler',
      icon: '⚖️',
      label: 'Scheduler',
      sub: algorithm,
      color: 'var(--accent-2)',
      glow: 'rgba(139,92,246,0.3)',
      active: isRunning,
      detail: 'Dispatches to printers',
    },
    {
      id: 'printers',
      icon: '🖨',
      label: 'Printer Threads',
      sub: 'Consumers',
      color: 'var(--sky)',
      glow: 'rgba(14,165,233,0.3)',
      active: busyCount > 0,
      detail: `${busyCount} busy · ${idleCount} idle${errorCount > 0 ? ` · ${errorCount} err` : ''}`,
    },
    {
      id: 'database',
      icon: '🗄',
      label: 'Database',
      sub: `${totalDone} done`,
      color: 'var(--emerald)',
      glow: 'rgba(16,185,129,0.3)',
      active: totalDone > 0,
      detail: 'CopyOnWriteArrayList',
    },
  ];

  return (
    <div className="card">
      <div className="card-header">
        <span className="card-title">🔁 System Process Flow</span>
        <span className="tag tag-teal">{isRunning ? 'Running' : status}</span>
      </div>

      {/* Flow diagram */}
      <div style={{
        padding: '14px 16px',
        display: 'flex',
        alignItems: 'center',
        gap: 0,
        overflowX: 'auto',
      }}>
        {nodes.map((node, idx) => (
          <div key={node.id} style={{ display: 'flex', alignItems: 'center', flex: idx < nodes.length - 1 ? '1 1 auto' : 'none' }}>
            {/* Node box */}
            <div
              id={`flow-${node.id}`}
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                gap: 6,
                minWidth: 90,
                flexShrink: 0,
              }}
            >
              {/* Icon circle */}
              <div style={{
                width: 56, height: 56,
                borderRadius: 16,
                background: node.active ? `${node.color}18` : 'var(--bg-card)',
                border: `1.5px solid ${node.active ? node.color : 'var(--border)'}`,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                fontSize: '1.5rem',
                boxShadow: node.active ? `0 0 18px ${node.glow}` : 'none',
                transition: 'all 0.4s ease',
                animation: node.active ? 'flowPulse 2.5s ease-in-out infinite' : 'none',
                '--flow-glow': node.glow,
              }}>
                {node.icon}
              </div>

              {/* Labels */}
              <div style={{ textAlign: 'center' }}>
                <div style={{
                  fontFamily: 'var(--font-head)',
                  fontWeight: 600,
                  fontSize: '0.75rem',
                  color: node.active ? node.color : 'var(--text-secondary)',
                  transition: 'color 0.3s',
                }}>
                  {node.label}
                </div>
                <div style={{
                  fontFamily: 'var(--font-mono)',
                  fontSize: '0.62rem',
                  color: 'var(--text-muted)',
                  marginTop: 1,
                }}>
                  {node.sub}
                </div>
              </div>
            </div>

            {/* Arrow connector */}
            {idx < nodes.length - 1 && (
              <div style={{
                flex: 1,
                height: 2,
                margin: '0 4px',
                position: 'relative',
                background: 'var(--border)',
                minWidth: 20,
                marginBottom: 24,
              }}>
                {/* Animated flow pulse */}
                {isRunning && (
                  <div style={{
                    position: 'absolute',
                    inset: 0,
                    borderRadius: 1,
                    background: `linear-gradient(90deg, transparent, ${nodes[idx].color}, transparent)`,
                    backgroundSize: '200% 100%',
                    animation: 'flowArrow 1.8s linear infinite',
                  }} />
                )}
                {/* Arrowhead */}
                <div style={{
                  position: 'absolute',
                  right: -5, top: '50%',
                  transform: 'translateY(-50%)',
                  width: 0, height: 0,
                  borderTop: '5px solid transparent',
                  borderBottom: '5px solid transparent',
                  borderLeft: `7px solid ${isRunning ? nodes[idx].color : 'var(--border)'}`,
                  transition: 'border-left-color 0.3s',
                }} />
              </div>
            )}
          </div>
        ))}
      </div>

      {/* Bottom row: sub-details */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(5, 1fr)',
        gap: 1,
        borderTop: '1px solid var(--border)',
      }}>
        {nodes.map((node) => (
          <div key={node.id} style={{
            padding: '7px 10px',
            borderRight: '1px solid var(--border)',
            lastChild: { borderRight: 'none' },
          }}>
            <div style={{ fontSize: '0.58rem', color: 'var(--text-muted)', lineHeight: 1.5, fontFamily: 'var(--font-mono)' }}>
              {node.detail}
            </div>
          </div>
        ))}
      </div>

      <style>{`
        @keyframes flowArrow {
          from { background-position: -100% 0; }
          to   { background-position: 100% 0; }
        }
        @keyframes flowPulse {
          0%,100% { box-shadow: 0 0 14px var(--flow-glow); }
          50%      { box-shadow: 0 0 28px var(--flow-glow); }
        }
      `}</style>
    </div>
  );
}
