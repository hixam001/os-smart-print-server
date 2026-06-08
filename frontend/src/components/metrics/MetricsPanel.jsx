import { useSimulationStore } from '../../store/simulationStore';

function StatCard({ id, label, value, sub, accent, icon, barPct }) {
  return (
    <div className="stat-card" id={id} style={{ '--accent-color': accent }}>
      {icon && <div className="stat-icon">{icon}</div>}
      <div className="stat-label">{label}</div>
      <div className="stat-value" style={accent ? { color: accent } : {}}>
        {value}
      </div>
      {sub && <div className="stat-sub">{sub}</div>}
      {barPct !== undefined && (
        <div className="stat-bar">
          <div
            className="stat-bar-fill"
            style={{
              width: `${Math.min(100, barPct)}%`,
              background: accent ?? 'var(--accent)',
            }}
          />
        </div>
      )}
    </div>
  );
}

export default function MetricsPanel() {
  const { metrics, totalEnqueued, totalRejected, printers } = useSimulationStore();
  const {
    totalJobsCompleted, totalJobsFailed, totalJobsCancelled,
    avgWaitingTimeMs, avgTurnaroundTimeMs, avgPageCount,
    throughputJobsPerMin, colorJobRatio,
  } = metrics;

  const totalFinished = totalJobsCompleted + totalJobsFailed + totalJobsCancelled;
  const rejRate  = totalEnqueued > 0 ? ((totalRejected / totalEnqueued) * 100) : 0;
  const failRate = totalFinished > 0 ? ((totalJobsFailed / totalFinished) * 100) : 0;
  const busyCount = (printers || []).filter(p => p.status === 'BUSY').length;
  const utilPct   = printers?.length > 0 ? (busyCount / printers.length) * 100 : 0;

  return (
    <div className="card">
      <div className="card-header">
        <span className="card-title">📊 Performance Metrics</span>
        <span style={{ fontSize: '0.68rem', color: 'var(--text-muted)' }}>
          {totalEnqueued} total submissions
        </span>
      </div>
      <div className="card-body" style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>

        {}
        <div className="row-4">
          <StatCard
            id="metric-completed"
            icon="✅"
            label="Completed"
            value={totalJobsCompleted}
            sub="jobs finished"
            accent="var(--emerald-2)"
            barPct={totalEnqueued > 0 ? (totalJobsCompleted / totalEnqueued) * 100 : 0}
          />
          <StatCard
            id="metric-failed"
            icon="❌"
            label="Failed"
            value={totalJobsFailed}
            sub={`${failRate.toFixed(1)}% fail rate`}
            accent={totalJobsFailed > 0 ? 'var(--rose-2)' : undefined}
            barPct={failRate}
          />
          <StatCard
            id="metric-rejected"
            icon="⛔"
            label="Rejected"
            value={totalRejected}
            sub={`${rejRate.toFixed(1)}% of submissions`}
            accent={totalRejected > 0 ? 'var(--amber-2)' : undefined}
            barPct={rejRate}
          />
          <StatCard
            id="metric-throughput"
            icon="⚡"
            label="Throughput"
            value={throughputJobsPerMin}
            sub="jobs / sim-min"
            accent="var(--accent)"
            barPct={Math.min(100, throughputJobsPerMin * 5)}
          />
        </div>

        <div className="divider" />

        {}
        <div className="row-4">
          <StatCard
            id="metric-avgwait"
            icon="⏳"
            label="Avg Wait"
            value={avgWaitingTimeMs > 0 ? `${(avgWaitingTimeMs / 1000).toFixed(2)}s` : '—'}
            sub="from submit → print start"
            accent="var(--sky)"
          />
          <StatCard
            id="metric-turnaround"
            icon="🔄"
            label="Avg Turnaround"
            value={avgTurnaroundTimeMs > 0 ? `${(avgTurnaroundTimeMs / 1000).toFixed(2)}s` : '—'}
            sub="submit → completion"
            accent="var(--accent-3)"
          />
          <StatCard
            id="metric-avgpages"
            icon="📄"
            label="Avg Pages"
            value={avgPageCount > 0 ? avgPageCount.toFixed(1) : '—'}
            sub="per job"
          />
          <StatCard
            id="metric-utilisation"
            icon="🖨"
            label="Printer Util"
            value={`${utilPct.toFixed(0)}%`}
            sub={`${busyCount} / ${printers?.length ?? 0} printing`}
            accent={utilPct >= 80 ? 'var(--amber-2)' : 'var(--teal-2)'}
            barPct={utilPct}
          />
        </div>

        {}
        {colorJobRatio > 0 && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '4px 0' }}>
            <span style={{ fontSize: '0.65rem', color: 'var(--text-muted)', minWidth: 80 }}>Colour ratio</span>
            <div style={{ flex: 1, height: 6, background: 'var(--bg-surface)', borderRadius: 3, overflow: 'hidden' }}>
              <div style={{
                height: '100%',
                width: `${colorJobRatio * 100}%`,
                background: 'linear-gradient(90deg, var(--accent-2), var(--accent))',
                borderRadius: 3,
                transition: 'width 0.5s ease',
              }} />
            </div>
            <span style={{ fontSize: '0.7rem', fontFamily: 'var(--font-mono)', color: 'var(--accent-2)', minWidth: 36 }}>
              {(colorJobRatio * 100).toFixed(0)}%
            </span>
            <div style={{ flex: 1, height: 6, background: 'var(--teal)', opacity: 0.2, borderRadius: 3, overflow: 'hidden' }}>
              <div style={{
                height: '100%',
                width: `${(1 - colorJobRatio) * 100}%`,
                background: 'var(--teal)',
                borderRadius: 3,
              }} />
            </div>
            <span style={{ fontSize: '0.65rem', color: 'var(--text-muted)', minWidth: 38 }}>Mono</span>
          </div>
        )}
      </div>
    </div>
  );
}
