import { useMemo } from 'react';
import { useSimulationStore } from '../../store/simulationStore';

const STAGE_DEFS = [
  { key: 'SUBMITTED',   label: 'Submitted',   icon: '📝', color: '#6366f1', desc: 'UserThread calls enqueue()' },
  { key: 'QUEUED',      label: 'In Queue',    icon: '📋', color: '#0ea5e9', desc: 'Waiting in bounded buffer' },
  { key: 'SEM_WAIT',    label: 'Sem Wait',    icon: '🔒', color: '#f59e0b', desc: 'Printer blocked on acquire()' },
  { key: 'SEM_ACQ',     label: 'Sem Acq',     icon: '🔓', color: '#14b8a6', desc: 'Permit granted — dequeue()' },
  { key: 'PRINTING',    label: 'Printing',    icon: '🖨️', color: '#8b5cf6', desc: 'Page-by-page with delay' },
  { key: 'DONE',        label: 'Done',        icon: '✅', color: '#10b981', desc: 'Recorded in Database' },
];

const FAIL_STAGES = {
  REJECTED:  { icon: '🚫', color: '#f43f5e', label: 'Rejected',  desc: 'Queue full — bounded buffer overflow prevented' },
  FAILED:    { icon: '💥', color: '#ef4444', label: 'Failed',    desc: 'Hardware error / paper jam' },
  CANCELLED: { icon: '⛔', color: '#f97316', label: 'Cancelled', desc: 'Simulation stopped mid-job' },
};

const EVENT_TO_STAGE = {
  JOB_SUBMITTED:   'SUBMITTED',
  JOB_REJECTED:    'REJECTED',
  SEMAPHORE_WAIT:  'SEM_WAIT',
  SEMAPHORE_ACQUIRED: 'SEM_ACQ',
  PRINT_START:     'PRINTING',
  PRINT_COMPLETED: 'DONE',
  PRINT_FAIL:      'FAILED',
  JOB_CANCELLED:   'CANCELLED',
};

function formatMs(ms) {
  if (!ms || ms <= 0) return '—';
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(1)}s`;
}

export default function JobJourneyPanel() {
  const events    = useSimulationStore(s => s.events);
  const status    = useSimulationStore(s => s.status);

  const jobs = useMemo(() => {
    const map = new Map();

    for (const ev of [...events].reverse()) {
      const d   = ev.details ?? {};
      const jid = d.jobId;
      const et  = ev.eventType;

      if (et === 'JOB_SUBMITTED' && jid != null) {
        if (!map.has(jid)) {
          map.set(jid, {
            jobId:    jid,
            userId:   d.userId,
            pages:    d.pageCount,
            color:    d.isColor,
            priority: d.priority,
            stages:   ['SUBMITTED'],
            terminal: null,
            ts:       ev.timestamp,
            waitMs:   null,
            printMs:  null,
          });
        }
      } else if (et === 'JOB_REJECTED') {
        const key = `rejected-${ev.timestamp}`;
        map.set(key, {
          jobId:    null,
          userId:   d.userId,
          pages:    d.pageCount,
          color:    d.isColor,
          stages:   ['SUBMITTED'],
          terminal: 'REJECTED',
          ts:       ev.timestamp,
          waitMs:   0,
          printMs:  null,
        });
      } else if (jid != null && map.has(jid)) {
        const job = map.get(jid);
        const stage = EVENT_TO_STAGE[et];

        if (stage && !job.stages.includes(stage)) {
          job.stages.push(stage);
        }

        if (et === 'PRINT_COMPLETED') {
          job.terminal = 'DONE';
          job.waitMs   = d.waitingTimeMs;
          job.printMs  = d.turnaroundTimeMs;
        } else if (et === 'PRINT_FAIL') {
          job.terminal = 'FAILED';
        } else if (et === 'JOB_CANCELLED') {
          job.terminal = 'CANCELLED';
        }
      }
    }

    return [...map.values()].reverse().slice(0, 12);
  }, [events]);

  return (
    <div className="card" style={{ gridColumn: '1 / -1' }}>
      <div className="card-header">
        <span className="card-title">🛤️ Job Journey — How Each Job Travels Through the System</span>
        <span className="tag tag-accent">{jobs.length} recent jobs</span>
      </div>

      {}
      <div style={{ display: 'flex', gap: 8, padding: '10px 16px 0', flexWrap: 'wrap' }}>
        {STAGE_DEFS.map(s => (
          <div key={s.key} style={{ display: 'flex', alignItems: 'center', gap: 5, fontSize: '0.68rem', color: 'var(--text-secondary)' }}>
            <div style={{ width: 8, height: 8, borderRadius: '50%', background: s.color }} />
            <span style={{ color: s.color, fontWeight: 600 }}>{s.label}</span>
            <span style={{ color: 'var(--text-muted)', fontSize: '0.6rem' }}>— {s.desc}</span>
          </div>
        ))}
      </div>

      <div style={{ padding: '12px 16px', overflowX: 'auto' }}>
        {jobs.length === 0 ? (
          <div className="empty-state">
            <div className="icon">🛤️</div>
            <div>Start the simulation to watch jobs flow through the system</div>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6, minWidth: 640 }}>
            {}
            <div style={{
              display: 'grid',
              gridTemplateColumns: '80px 60px 50px 40px 1fr 100px',
              gap: 8,
              padding: '0 8px 4px',
              borderBottom: '1px solid var(--border)',
              fontSize: '0.62rem',
              color: 'var(--text-muted)',
              fontWeight: 700,
              letterSpacing: '0.05em',
              textTransform: 'uppercase',
            }}>
              <span>Job ID</span>
              <span>User</span>
              <span>Pages</span>
              <span>Pri</span>
              <span>Journey</span>
              <span>Timing</span>
            </div>

            {jobs.map(job => (
              <JobRow key={job.jobId ?? job.ts} job={job} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function JobRow({ job }) {
  const allStages = [...job.stages];
  if (job.terminal && !allStages.includes(job.terminal)) allStages.push(job.terminal);

  const isTerminal = job.terminal != null;
  const termInfo   = job.terminal ? FAIL_STAGES[job.terminal] : null;

  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: '80px 60px 50px 40px 1fr 100px',
      gap: 8,
      padding: '6px 8px',
      background: 'var(--bg-card)',
      borderRadius: 8,
      border: '1px solid var(--border)',
      alignItems: 'center',
      animation: 'fadeIn 0.3s ease',
    }}>
      {}
      <span style={{ fontFamily: 'var(--font-mono)', fontSize: '0.7rem', color: 'var(--accent)' }}>
        {job.jobId != null ? `#${job.jobId}` : '(rej)'}
      </span>

      {}
      <span style={{ fontSize: '0.72rem', color: 'var(--text-secondary)' }}>
        User-{job.userId ?? '?'}
      </span>

      {}
      <span style={{ fontSize: '0.72rem', color: 'var(--text-secondary)' }}>
        {job.pages ?? '?'} pg
        {job.color && <span style={{ marginLeft: 3, fontSize: '0.6rem', color: '#a78bfa' }}>🎨</span>}
      </span>

      {}
      <PriorityBubble p={job.priority} />

      {}
      <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
        {STAGE_DEFS.filter(s => allStages.includes(s.key)).map((s, i, arr) => (
          <div key={s.key} style={{ display: 'flex', alignItems: 'center', gap: 3 }}>
            <StageChip s={s} reached={true} isLast={i === arr.length - 1 && !isTerminal} />
            {i < arr.length - 1 && <Arrow active={true} />}
          </div>
        ))}
        {}
        {termInfo && (
          <>
            <Arrow active={true} color={termInfo.color} />
            <div style={{
              display: 'flex', alignItems: 'center', gap: 4,
              background: `${termInfo.color}1a`, border: `1px solid ${termInfo.color}55`,
              borderRadius: 5, padding: '2px 7px',
              fontSize: '0.62rem', fontWeight: 700, color: termInfo.color,
            }}>
              {termInfo.icon} {termInfo.label}
            </div>
          </>
        )}
        {}
        {!isTerminal && STAGE_DEFS.filter(s => !allStages.includes(s.key)).map((s, i) => (
          <div key={s.key} style={{ display: 'flex', alignItems: 'center', gap: 3 }}>
            <Arrow active={false} />
            <StageChip s={s} reached={false} />
          </div>
        ))}
      </div>

      {}
      <div style={{ fontSize: '0.62rem', color: 'var(--text-muted)', lineHeight: 1.5, textAlign: 'right' }}>
        {job.waitMs != null && <div>Wait: <span style={{ color: 'var(--amber-2)' }}>{formatMs(job.waitMs)}</span></div>}
        {job.printMs != null && <div>Total: <span style={{ color: 'var(--emerald-2)' }}>{formatMs(job.printMs)}</span></div>}
      </div>
    </div>
  );
}

function StageChip({ s, reached, isLast }) {
  return (
    <div style={{
      display: 'flex', alignItems: 'center', gap: 4,
      background: reached ? `${s.color}22` : 'var(--bg-surface)',
      border: `1px solid ${reached ? s.color + '55' : 'var(--border)'}`,
      borderRadius: 5, padding: '2px 7px',
      fontSize: '0.6rem', fontWeight: 700,
      color: reached ? s.color : 'var(--text-muted)',
      whiteSpace: 'nowrap',
      animation: reached && isLast ? 'pulse-glow 2s ease-in-out infinite' : 'none',
    }}>
      {s.icon} {s.label}
    </div>
  );
}

function Arrow({ active, color }) {
  return (
    <span style={{
      color: active ? (color ?? 'var(--border-bright)') : 'var(--text-dim)',
      fontSize: '0.7rem',
      opacity: active ? 1 : 0.3,
    }}>›</span>
  );
}

function PriorityBubble({ p }) {
  if (!p) return <span style={{ color: 'var(--text-muted)', fontSize: '0.72rem' }}>—</span>;
  const color = p >= 5 ? '#f43f5e' : p >= 4 ? '#f59e0b' : p >= 3 ? '#6366f1' : '#14b8a6';
  return (
    <div style={{
      width: 20, height: 20, borderRadius: '50%',
      background: `${color}22`, border: `1px solid ${color}55`,
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      fontSize: '0.6rem', fontWeight: 800, color,
    }}>{p}</div>
  );
}
