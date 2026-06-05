import { useSimulationStore } from '../store/simulationStore';

const PROBLEMS = [
  {
    id: 'producer-consumer',
    icon: '🔄',
    title: 'Producer-Consumer',
    color: 'var(--teal-2)',
    description:
      'User threads (producers) generate print jobs and enqueue them. Printer threads (consumers) dequeue and print. A counting semaphore gates access to the shared bounded queue.',
    getStatus: (s) => s.queueSize > 0 ? 'active' : 'idle',
    getDetail: (s) =>
      `${s.queueSize} jobs in buffer · ${s.totalEnqueued} produced · ${s.totalDequeued} consumed`,
  },
  {
    id: 'scheduling',
    icon: '📋',
    title: 'CPU Scheduling',
    color: 'var(--accent)',
    description:
      'Three algorithms: FCFS (fair, arrival order), SJF (minimises avg wait by favouring short jobs), HYBRID (SJF with aging — promotes long-waiting jobs to prevent starvation).',
    getStatus: (s) => s.status === 'RUNNING' ? 'active' : 'idle',
    getDetail: (s) =>
      `Algorithm: ${s.algorithm} · ${s.metrics.totalJobsCompleted} jobs scheduled · ${s.metrics.throughputJobsPerMin} j/min`,
  },
  {
    id: 'starvation',
    icon: '⏳',
    title: 'Starvation & Aging',
    color: 'var(--amber-2)',
    description:
      'Under pure SJF, long jobs can starve indefinitely. HYBRID counters this with aging: jobs waiting >7s turn amber 🟡, >15s turn red 🔴 and jump to the front of the queue.',
    getStatus: (s) => {
      const critical = s.queuedJobs.filter(j => (s.elapsedMs - j.submittedAt) > 15000);
      const aged     = s.queuedJobs.filter(j => (s.elapsedMs - j.submittedAt) > 7000);
      return critical.length > 0 ? 'warning' : aged.length > 0 ? 'active' : 'idle';
    },
    getDetail: (s) => {
      const aged     = s.queuedJobs.filter(j => (s.elapsedMs - j.submittedAt) > 7000).length;
      const critical = s.queuedJobs.filter(j => (s.elapsedMs - j.submittedAt) > 15000).length;
      return `${critical} critical 🔴 · ${aged} aging 🟡 · avg wait ${(s.metrics.avgWaitingTimeMs / 1000).toFixed(1)}s`;
    },
  },
  {
    id: 'mutex',
    icon: '🔒',
    title: 'Mutual Exclusion',
    color: 'var(--accent-2)',
    description:
      'PrintQueue uses a fair ReentrantLock so only one thread modifies the queue at a time. The Database uses CopyOnWriteArrayList for lock-free concurrent reads without blocking.',
    getStatus: (s) => s.status === 'RUNNING' ? 'active' : 'idle',
    getDetail: (s) => {
      const busy = (s.printers || []).filter(p => p.status === 'BUSY').length;
      return `${busy} threads holding printer lock concurrently · ReentrantLock (fair=true)`;
    },
  },
  {
    id: 'deadlock',
    icon: '⛔',
    title: 'Deadlock Prevention',
    color: 'var(--rose-2)',
    description:
      'Deadlock is avoided by: (1) always acquiring locks in a consistent order, (2) non-blocking tryLock with timeouts for cross-resource ops, (3) no circular dependencies between queue and printers.',
    getStatus: (s) => s.metrics.totalJobsFailed > 0 ? 'warning' : 'idle',
    getDetail: (s) =>
      `${s.metrics.totalJobsFailed} failed · ${s.metrics.totalJobsCancelled} cancelled · 0 deadlocks detected`,
  },
  {
    id: 'resource-util',
    icon: '📊',
    title: 'Resource Utilisation',
    color: 'var(--emerald-2)',
    description:
      'Multiple printer threads share the CPU-bound print workload. Metrics track throughput, average turnaround, and busy/idle ratio per printer to detect bottlenecks.',
    getStatus: (s) => s.status === 'RUNNING' ? 'active' : 'idle',
    getDetail: (s) => {
      const busy  = (s.printers || []).filter(p => p.status === 'BUSY').length;
      const total = (s.printers || []).length;
      const util  = total > 0 ? Math.round((busy / total) * 100) : 0;
      return `${s.metrics.throughputJobsPerMin} j/min · ${busy}/${total} printers busy · ${util}% utilisation`;
    },
  },
];

export default function OSProblemsPanel() {
  const state = useSimulationStore();

  return (
    <div className="card">
      <div className="card-header">
        <span className="card-title">🎓 OS Concepts Demonstrated</span>
        <span className="tag tag-accent">6 problems</span>
      </div>
      <div className="card-body">
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(270px, 1fr))', gap: 10 }}>
          {PROBLEMS.map(({ id, icon, title, color, description, getStatus, getDetail }) => {
            const problemStatus = getStatus(state);
            const detail = getDetail(state);
            const isActive  = problemStatus === 'active';
            const isWarning = problemStatus === 'warning';

            return (
              <div
                key={id}
                id={`os-problem-${id}`}
                className={`problem-card ${problemStatus}`}
                style={{ '--problem-color': color }}
              >
                {/* Header */}
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <div style={{
                    width: 34, height: 34, borderRadius: 9,
                    background: isActive || isWarning
                      ? `${color}18`
                      : 'var(--bg-surface)',
                    border: `1px solid ${isActive || isWarning ? color + '40' : 'var(--border)'}`,
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    fontSize: '1.1rem', flexShrink: 0,
                    transition: 'var(--transition)',
                  }}>
                    {icon}
                  </div>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontFamily: 'var(--font-head)', fontWeight: 600, fontSize: '0.88rem', color }}>
                      {title}
                    </div>
                  </div>
                  {/* Status indicator */}
                  <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                    <div
                      className={`problem-indicator ${problemStatus}`}
                      style={{
                        background: isActive  ? 'var(--emerald)'
                                  : isWarning ? 'var(--amber)'
                                  : 'var(--text-muted)',
                        boxShadow: isActive  ? '0 0 8px var(--emerald)'
                                 : isWarning ? '0 0 8px var(--amber)'
                                 : 'none',
                      }}
                    />
                    <span style={{
                      fontSize: '0.58rem', color: isActive  ? 'var(--emerald-2)'
                                                : isWarning ? 'var(--amber-2)'
                                                : 'var(--text-muted)',
                      fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.05em',
                    }}>
                      {isActive ? 'active' : isWarning ? 'warn' : 'idle'}
                    </span>
                  </div>
                </div>

                {/* Description */}
                <p style={{ fontSize: '0.7rem', color: 'var(--text-muted)', lineHeight: 1.6, margin: 0 }}>
                  {description}
                </p>

                {/* Live detail */}
                <div style={{
                  fontFamily: 'var(--font-mono)',
                  fontSize: '0.65rem',
                  color: isActive || isWarning ? color : 'var(--text-muted)',
                  borderTop: '1px solid var(--border)',
                  paddingTop: 7,
                  lineHeight: 1.5,
                }}>
                  {detail}
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
