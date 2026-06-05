import { useMemo } from 'react';
import { useSimulationStore } from '../store/simulationStore';

/* ────────────────────────────────────────────────────────────────────────────
 * OSProblemsSolver
 * Shows each classic OS problem side-by-side with its solution and
 * live counters pulled from the simulation state.
 * ─────────────────────────────────────────────────────────────────────────── */

export default function OSProblemsSolver() {
  const events   = useSimulationStore(s => s.events);
  const metrics  = useSimulationStore(s => s.metrics);
  const semPerm  = useSimulationStore(s => s.semaphorePermits);
  const semWait  = useSimulationStore(s => s.semaphoreWaiters);
  const queueSize = useSimulationStore(s => s.queueSize);
  const queueCap  = useSimulationStore(s => s.queueCapacity);
  const status    = useSimulationStore(s => s.status);

  // Derive live counters from event history
  const counters = useMemo(() => {
    let semWaits = 0, semAcqs = 0, semRels = 0;
    let paperJams = 0, recoveries = 0;
    for (const ev of events) {
      if (ev.eventType === 'SEMAPHORE_WAIT')     semWaits++;
      if (ev.eventType === 'SEMAPHORE_ACQUIRED') semAcqs++;
      if (ev.eventType === 'SEMAPHORE_RELEASED') semRels++;
      if (ev.eventType === 'PRINT_FAIL')         paperJams++;
      if (ev.eventType === 'PRINTER_RECOVER')    recoveries++;
    }
    return { semWaits, semAcqs, semRels, paperJams, recoveries };
  }, [events]);

  const running = status === 'RUNNING';

  const problems = [
    {
      id: 'race',
      icon: '⚡',
      color: '#f43f5e',
      title: 'Race Condition',
      problem: 'Multiple threads read/write shared data simultaneously without synchronization → corrupted queue, lost jobs, or double-printing.',
      solution: 'ReentrantLock (fair=true)',
      solutionDetail: 'Every enqueue/dequeue on PrintQueue is guarded by a fair ReentrantLock. Fair mode prevents thread starvation — the longest-waiting thread gets the lock next.',
      code: 'lock.lock();\ntry { queue.add(job); }\nfinally { lock.unlock(); }',
      status: 'SOLVED',
      statusColor: '#10b981',
      liveMetric: { label: 'Deadlocks detected', value: 0, good: v => v === 0 },
      counter: null,
    },
    {
      id: 'producer_consumer',
      icon: '🔄',
      color: '#6366f1',
      title: 'Producer-Consumer',
      problem: 'Printer threads spin-poll the queue wasting 100% CPU, OR miss wakeups and starve, OR both producer and consumer corrupt shared state.',
      solution: 'Counting Semaphore (custom)',
      solutionDetail: 'jobsAvailable semaphore starts at 0. UserThread calls release() after each enqueue (permits++, wakes one printer). PrinterThread calls acquire() — blocks in wait() if empty. Zero busy-waiting.',
      code: '// Producer (UserThread)\nsemaphore.release();   // permits++\n\n// Consumer (PrinterThread)\nsemaphore.acquire();   // blocks if 0',
      status: running ? 'ACTIVE' : 'READY',
      statusColor: running ? '#6366f1' : '#94a3b8',
      liveMetric: {
        label: 'Semaphore state',
        value: `${semPerm} permits, ${semWait} waiting`,
        good: () => true,
      },
      counter: [
        { label: 'release() calls', val: counters.semRels, color: '#6366f1' },
        { label: 'acquire() calls', val: counters.semAcqs, color: '#14b8a6' },
        { label: 'blocked waits',   val: counters.semWaits, color: '#f59e0b' },
      ],
    },
    {
      id: 'bounded_buffer',
      icon: '📦',
      color: '#0ea5e9',
      title: 'Bounded Buffer Overflow',
      problem: 'Without a capacity limit, producers fill memory infinitely. With a hard limit but no signalling, producers either spin-poll or crash on rejection.',
      solution: 'Fixed-capacity PrintQueue + rejection',
      solutionDetail: `Queue capacity is fixed at ${queueCap} slots. When full, the job is immediately rejected (not queued) and a JOB_REJECTED event is emitted. No overflow, no crash.`,
      code: `if (queue.size() >= capacity) {\n  publishEvent("JOB_REJECTED", ...);\n  return false; // reject gracefully\n}`,
      status: queueSize >= queueCap ? 'ACTIVE' : 'MONITORING',
      statusColor: queueSize >= queueCap ? '#f59e0b' : '#10b981',
      liveMetric: {
        label: 'Queue',
        value: `${queueSize}/${queueCap} (${metrics.totalJobsCompleted ?? 0} rejected)`,
        good: () => true,
      },
      counter: [
        { label: 'Jobs rejected',  val: metrics.totalRejected ?? 0, color: '#f43f5e' },
        { label: 'Jobs completed', val: metrics.totalJobsCompleted, color: '#10b981' },
      ],
    },
    {
      id: 'starvation',
      icon: '🌾',
      color: '#f59e0b',
      title: 'Starvation',
      problem: 'In pure priority scheduling, low-priority jobs wait forever if high-priority jobs keep arriving. SJF starves long jobs.',
      solution: 'Aging in HYBRID scheduler',
      solutionDetail: 'HYBRID mode starts with SJF (shortest first) but applies an aging factor: every 5 seconds a job waits, its effective priority increases. After 30s, even a 30-page job jumps ahead.',
      code: '// Effective priority with aging\nlong age = now - job.submittedAt;\nint boost = (int)(age / 5000);\nreturn job.priority + boost;',
      status: 'SOLVED',
      statusColor: '#f59e0b',
      liveMetric: {
        label: 'Avg wait time',
        value: `${((metrics.avgWaitingTimeMs ?? 0) / 1000).toFixed(1)}s`,
        good: v => true,
      },
      counter: null,
    },
    {
      id: 'deadlock',
      icon: '🔗',
      color: '#a78bfa',
      title: 'Deadlock',
      problem: 'If Thread A holds Lock1 and waits for Lock2 while Thread B holds Lock2 and waits for Lock1 → both freeze forever.',
      solution: 'Strict lock ordering + no circular waits',
      solutionDetail: 'Only ONE lock exists per resource (PrintQueue has one ReentrantLock). Threads never hold multiple locks simultaneously. No circular dependency possible → deadlock structurally impossible.',
      code: '// Safe: acquire only ONE lock at a time\nlock.lock();\ntry { /* critical section */ }\nfinally { lock.unlock(); }\n// Never nest locks!',
      status: 'PREVENTED',
      statusColor: '#10b981',
      liveMetric: { label: 'Deadlocks', value: 0, good: v => v === 0 },
      counter: null,
    },
    {
      id: 'hardware_fault',
      icon: '💥',
      color: '#f97316',
      title: 'Hardware Failure',
      problem: 'A printer jams mid-job. Without error handling, the thread crashes, the job is lost, and the printer stays in ERROR state permanently.',
      solution: 'Graceful error recovery',
      solutionDetail: 'On a paper jam (5% chance per page), the job is marked FAILED, the printer enters ERROR state, waits 5 simulated seconds (repair), then returns to IDLE — ready for the next job.',
      code: 'if (random.nextDouble() < 0.05) {\n  printer.failJob(time);\n  sleep(5000); // repair\n  printer.clearError();\n}',
      status: `${counters.paperJams} jams, ${counters.recoveries} fixed`,
      statusColor: counters.paperJams > 0 ? '#f97316' : '#10b981',
      liveMetric: {
        label: 'Failures / recoveries',
        value: `${counters.paperJams} / ${counters.recoveries}`,
        good: () => true,
      },
      counter: [
        { label: 'Paper jams',  val: counters.paperJams,  color: '#f97316' },
        { label: 'Recoveries', val: counters.recoveries, color: '#10b981' },
        { label: 'Failed jobs', val: metrics.totalJobsFailed ?? 0, color: '#f43f5e' },
      ],
    },
  ];

  return (
    <div className="card" style={{ gridColumn: '1 / -1' }}>
      <div className="card-header">
        <span className="card-title">🛡️ OS Problems → Solutions — What This Simulation Solves</span>
        <span className="tag tag-emerald">6 Problems Addressed</span>
      </div>

      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fill, minmax(340px, 1fr))',
        gap: 12,
        padding: 16,
      }}>
        {problems.map(p => <ProblemCard key={p.id} p={p} />)}
      </div>
    </div>
  );
}

function ProblemCard({ p }) {
  return (
    <div style={{
      background: 'var(--bg-card)',
      border: `1px solid ${p.color}33`,
      borderRadius: 12,
      overflow: 'hidden',
      display: 'flex',
      flexDirection: 'column',
      transition: 'all 0.22s ease',
    }}
      onMouseEnter={e => e.currentTarget.style.borderColor = `${p.color}88`}
      onMouseLeave={e => e.currentTarget.style.borderColor = `${p.color}33`}
    >
      {/* Card header */}
      <div style={{
        background: `linear-gradient(135deg, ${p.color}18, ${p.color}08)`,
        borderBottom: `1px solid ${p.color}22`,
        padding: '12px 14px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <span style={{ fontSize: '1.3rem' }}>{p.icon}</span>
          <div>
            <div style={{ fontFamily: 'var(--font-head)', fontWeight: 700, fontSize: '0.88rem', color: p.color }}>
              {p.title}
            </div>
            <div style={{ fontSize: '0.6rem', color: 'var(--text-muted)', fontWeight: 600, letterSpacing: '0.04em' }}>
              CLASSIC OS PROBLEM
            </div>
          </div>
        </div>
        <StatusPill text={p.status} color={p.statusColor} />
      </div>

      <div style={{ padding: 14, flex: 1, display: 'flex', flexDirection: 'column', gap: 10 }}>

        {/* Problem */}
        <div>
          <Label color="#f43f5e">❌ The Problem</Label>
          <p style={{ fontSize: '0.72rem', color: 'var(--text-secondary)', lineHeight: 1.55, margin: 0 }}>
            {p.problem}
          </p>
        </div>

        {/* Solution */}
        <div>
          <Label color="#10b981">✅ Our Solution: <span style={{ color: '#34d399', fontWeight: 800 }}>{p.solution}</span></Label>
          <p style={{ fontSize: '0.72rem', color: 'var(--text-secondary)', lineHeight: 1.55, margin: '0 0 6px' }}>
            {p.solutionDetail}
          </p>

          {/* Code block */}
          <pre style={{
            background: 'rgba(0,0,0,0.3)', border: '1px solid var(--border)',
            borderRadius: 6, padding: '8px 10px',
            fontSize: '0.62rem', fontFamily: 'var(--font-mono)',
            color: '#a5f3fc', overflowX: 'auto', margin: 0, lineHeight: 1.55,
          }}>
            {p.code}
          </pre>
        </div>

        {/* Live counters */}
        {p.counter && (
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            {p.counter.map(c => (
              <div key={c.label} style={{
                flex: '1 1 80px',
                background: `${c.color}11`, border: `1px solid ${c.color}33`,
                borderRadius: 8, padding: '6px 10px',
                textAlign: 'center',
              }}>
                <div style={{ fontFamily: 'var(--font-head)', fontSize: '1.2rem', fontWeight: 800, color: c.color }}>
                  {c.val}
                </div>
                <div style={{ fontSize: '0.58rem', color: 'var(--text-muted)', fontWeight: 600, letterSpacing: '0.04em' }}>
                  {c.label}
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Live metric */}
        <div style={{
          display: 'flex', alignItems: 'center', gap: 6,
          padding: '6px 10px', borderRadius: 8,
          background: 'var(--bg-surface)', border: '1px solid var(--border)',
          fontSize: '0.68rem',
        }}>
          <span style={{ width: 6, height: 6, borderRadius: '50%', background: p.statusColor, flexShrink: 0,
            boxShadow: `0 0 6px ${p.statusColor}88` }} />
          <span style={{ color: 'var(--text-muted)', fontWeight: 600 }}>{p.liveMetric.label}:</span>
          <span style={{ color: 'var(--text-primary)', fontWeight: 700, fontFamily: 'var(--font-mono)', fontSize: '0.66rem' }}>
            {String(p.liveMetric.value)}
          </span>
        </div>
      </div>
    </div>
  );
}

function Label({ children, color }) {
  return (
    <div style={{
      fontSize: '0.62rem', fontWeight: 700, letterSpacing: '0.05em',
      color: color ?? 'var(--text-muted)', marginBottom: 4,
    }}>
      {children}
    </div>
  );
}

function StatusPill({ text, color }) {
  return (
    <div style={{
      padding: '3px 10px', borderRadius: 20,
      background: `${color}18`, border: `1px solid ${color}44`,
      fontSize: '0.6rem', fontWeight: 800, color,
      letterSpacing: '0.04em', whiteSpace: 'nowrap',
    }}>
      {text}
    </div>
  );
}
