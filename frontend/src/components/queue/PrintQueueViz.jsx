import { useSimulationStore } from '../../store/simulationStore';

/** OS-problem highlight: shows the bounded-buffer queue visually */
export default function PrintQueueViz() {
  const {
    queueSize, queueCapacity, totalEnqueued, totalDequeued,
    totalRejected, queuedJobs, elapsedMs,
  } = useSimulationStore();

  const fill   = queueCapacity > 0 ? (queueSize / queueCapacity) * 100 : 0;
  const danger = fill > 75;
  const warn   = fill > 50;

  const getAging = (job) => {
    const waited = elapsedMs - job.submittedAt;
    if (waited > 15000) return 'aging-crit';
    if (waited > 7000)  return 'aging-warn';
    return job.color ? 'color-job' : 'mono-job';
  };

  const fillColor = danger
    ? 'linear-gradient(90deg, var(--amber), var(--rose))'
    : warn
      ? 'linear-gradient(90deg, var(--teal), var(--amber))'
      : 'linear-gradient(90deg, var(--teal), var(--accent))';

  return (
    <div className="card">
      <div className="card-header">
        <span className="card-title">📋 Print Queue — Bounded Buffer</span>
        <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
          <span className={`tag ${danger ? 'tag-rose' : warn ? 'tag-amber' : 'tag-teal'}`}>
            {queueSize} / {queueCapacity}
          </span>
          {totalRejected > 0 && (
            <span className="tag tag-rose">⚠ {totalRejected} dropped</span>
          )}
        </div>
      </div>

      <div className="queue-section">
        {/* Capacity bar with segment markers */}
        <div style={{ marginBottom: 10 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
            <span style={{ fontSize: '0.68rem', color: danger ? 'var(--rose-2)' : warn ? 'var(--amber-2)' : 'var(--text-secondary)' }}>
              {fill.toFixed(0)}% full
              {danger && ' — ⚠ Near capacity!'}
            </span>
            <span style={{ fontSize: '0.68rem', color: 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}>
              {queueCapacity - queueSize} free slots
            </span>
          </div>
          <div className="queue-bar-bg">
            <div
              className={`queue-bar-fill ${danger ? 'danger' : ''}`}
              style={{ width: `${fill}%`, background: fillColor }}
            />
          </div>
          {/* Segment markers at 25%, 50%, 75% */}
          <div style={{ position: 'relative', height: 8, marginTop: -6, marginBottom: 2 }}>
            {[25, 50, 75].map(pct => (
              <div key={pct} style={{
                position: 'absolute',
                left: `${pct}%`,
                top: 0, bottom: 0,
                width: 1,
                background: 'var(--border)',
                zIndex: 1,
              }} />
            ))}
          </div>
        </div>

        {/* Queue stats row */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 8, marginBottom: 12 }}>
          {[
            { label: 'Produced',  val: totalEnqueued, icon: '↓', color: 'var(--teal-2)' },
            { label: 'Consumed',  val: totalDequeued, icon: '↑', color: 'var(--accent)' },
            { label: 'Rejected',  val: totalRejected, icon: '✕', color: 'var(--rose-2)' },
          ].map(({ label, val, icon, color }) => (
            <div key={label} style={{
              background: 'var(--bg-card)',
              border: '1px solid var(--border)',
              borderRadius: 8,
              padding: '8px 10px',
              display: 'flex',
              flexDirection: 'column',
              gap: 2,
            }}>
              <span style={{ fontSize: '0.6rem', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em', fontWeight: 600 }}>
                {icon} {label}
              </span>
              <span style={{ fontFamily: 'var(--font-head)', fontWeight: 700, fontSize: '1.2rem', color }}>
                {val}
              </span>
            </div>
          ))}
        </div>

        {/* Job chips — horizontal scrollable */}
        {queuedJobs.length === 0 ? (
          <div style={{
            display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
            gap: 6, minHeight: 72, color: 'var(--text-muted)', fontSize: '0.78rem',
          }}>
            <span style={{ fontSize: '1.4rem', opacity: 0.35 }}>📭</span>
            <span>Queue is empty</span>
          </div>
        ) : (
          <>
            <div className="queue-jobs-scroll">
              {queuedJobs.map(job => {
                const ageClass = getAging(job);
                const waited   = Math.max(0, elapsedMs - job.submittedAt);
                return (
                  <div
                    key={job.jobId}
                    className={`job-chip ${ageClass}`}
                    id={`job-chip-${job.jobId}`}
                    title={`Job #${job.jobId} | User ${job.userId} | ${job.pageCount}pp | Priority ${job.priority} | Waited ${(waited / 1000).toFixed(1)}s`}
                  >
                    <span className="job-chip-id">#{job.jobId}</span>
                    <span className="job-chip-pages">{job.pageCount}</span>
                    <span className="job-chip-icon">
                      {job.color ? '🎨' : '📄'}
                      {job.priority > 5 && ' ★'}
                    </span>
                    {ageClass === 'aging-crit' && (
                      <span style={{ fontSize: '0.55rem', color: 'var(--rose-2)' }}>🔴</span>
                    )}
                    {ageClass === 'aging-warn' && (
                      <span style={{ fontSize: '0.55rem', color: 'var(--amber-2)' }}>🟡</span>
                    )}
                  </div>
                );
              })}
            </div>

            {/* Legend */}
            <div style={{ display: 'flex', gap: 10, marginTop: 8, flexWrap: 'wrap' }}>
              {[
                { dot: 'var(--accent-2)', label: '🎨 Colour' },
                { dot: 'var(--teal)',     label: '📄 Mono' },
                { dot: 'var(--amber)',    label: '🟡 Aging >7s' },
                { dot: 'var(--rose)',     label: '🔴 Critical >15s' },
              ].map(({ label }) => (
                <span key={label} style={{ fontSize: '0.62rem', color: 'var(--text-muted)' }}>{label}</span>
              ))}
            </div>
          </>
        )}
      </div>
    </div>
  );
}
