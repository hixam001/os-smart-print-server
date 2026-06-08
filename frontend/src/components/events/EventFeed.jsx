import { useSimulationStore } from '../../store/simulationStore';

const EVENT_META = {
  JOB_SUBMITTED:      { label: 'Submitted',   color: 'var(--accent)',    bg: 'rgba(99,102,241,0.15)',  icon: '📥' },
  PRINT_START:        { label: 'Printing',    color: 'var(--teal-2)',    bg: 'rgba(20,184,166,0.15)',  icon: '🖨' },
  PRINTER_COMPUTING:  { label: 'Computing',   color: '#a78bfa',         bg: 'rgba(167,139,250,0.13)', icon: '⚙️' },
  PRINT_COMPLETED:    { label: 'Done',        color: 'var(--emerald-2)', bg: 'rgba(16,185,129,0.15)', icon: '✅' },
  PRINT_FAIL:         { label: 'Failed',      color: 'var(--rose-2)',    bg: 'rgba(244,63,94,0.15)',  icon: '❌' },
  PRINTER_REPAIRING:  { label: 'Repairing',   color: 'var(--orange)',    bg: 'rgba(249,115,22,0.13)', icon: '🔩' },
  PRINTER_RECOVER:    { label: 'Recovered',   color: 'var(--amber-2)',   bg: 'rgba(245,158,11,0.15)', icon: '🔧' },
  JOB_REJECTED:       { label: 'Rejected',    color: 'var(--rose)',      bg: 'rgba(244,63,94,0.12)',  icon: '⛔' },
  JOB_CANCELLED:      { label: 'Cancelled',   color: 'var(--amber)',     bg: 'rgba(245,158,11,0.12)', icon: '🚫' },
  UNKNOWN:            { label: 'Event',       color: 'var(--text-muted)', bg: 'rgba(148,163,184,0.10)', icon: '📌' },
};

function formatTime(ts) {
  return new Date(ts).toLocaleTimeString('en', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

function buildSummary(eventType, details) {
  switch (eventType) {
    case 'JOB_SUBMITTED':
      return (
        <>
          Job <strong>#{details.jobId}</strong> from User {details.userId} —{' '}
          {details.pageCount}pp {details.color ? '🎨 colour' : '📄 mono'}{' '}
          {details.priority > 5 && <span style={{ color: 'var(--amber-2)' }}>★ high-pri</span>}
        </>
      );
    case 'PRINT_START':
      return (
        <>
          <strong>{details.printerName ?? `Printer #${details.printerId}`}</strong> started Job{' '}
          <strong>#{details.jobId}</strong>
        </>
      );
    case 'PRINT_COMPLETED':
      return (
        <>
          Job <strong>#{details.jobId}</strong> finished · waited{' '}
          {details.waitingTimeMs != null ? `${(details.waitingTimeMs / 1000).toFixed(1)}s` : '?'}
          {details.turnaroundTimeMs != null && ` · turnaround ${(details.turnaroundTimeMs / 1000).toFixed(1)}s`}
        </>
      );
    case 'PRINT_FAIL':
      return (
        <>
          <strong>{details.printerName ?? `Printer #${details.printerId}`}</strong> failed —{' '}
          {details.reason ?? 'hardware error'}
        </>
      );
    case 'PRINTER_COMPUTING':
      return (
        <>
          <strong>{details.printerName ?? `Printer #${details.printerId}`}</strong> rendering Job{' '}
          <strong>#{details.jobId}</strong> — processing fonts &amp; layout
        </>
      );
    case 'PRINTER_REPAIRING':
      return (
        <>
          <strong>{details.printerName ?? `Printer #${details.printerId}`}</strong> clearing paper jam — repair in progress
        </>
      );
    case 'PRINTER_RECOVER':
      return <><strong>{details.printerName ?? `Printer #${details.printerId}`}</strong> recovered and is ready</>;
    case 'JOB_REJECTED':
      return <>Job <strong>#{details.jobId}</strong> rejected — queue at capacity</>;
    case 'JOB_CANCELLED':
      return <>Job <strong>#{details.jobId}</strong> cancelled</>;
    default:
      return <>{JSON.stringify(details)}</>;
  }
}

export default function EventFeed() {
  const events = useSimulationStore(s => s.events);

  const counts = events.reduce((acc, ev) => {
    acc[ev.eventType] = (acc[ev.eventType] || 0) + 1;
    return acc;
  }, {});

  return (
    <div className="card" style={{ display: 'flex', flexDirection: 'column', minHeight: 300 }}>
      <div className="card-header">
        <span className="card-title">📡 Event Stream</span>
        <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
          {Object.entries(counts).slice(0, 3).map(([type, count]) => {
            const meta = EVENT_META[type] ?? EVENT_META.UNKNOWN;
            return (
              <span key={type} style={{
                fontSize: '0.6rem', fontWeight: 700, padding: '2px 6px', borderRadius: 4,
                background: meta.bg, color: meta.color,
              }}>
                {meta.icon} {count}
              </span>
            );
          })}
          <span className="tag tag-accent">{events.length}</span>
        </div>
      </div>

      <div className="event-feed">
        {events.length === 0 ? (
          <div className="empty-state">
            <div className="icon">📡</div>
            <div>Events will appear here once the simulation starts</div>
          </div>
        ) : (
          events.map(ev => {
            const meta = EVENT_META[ev.eventType] ?? EVENT_META.UNKNOWN;
            return (
              <div key={ev.id} className="event-row">
                {}
                <div style={{
                  width: 26, height: 26, borderRadius: 8, flexShrink: 0,
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  background: meta.bg, fontSize: '0.75rem',
                }}>
                  {meta.icon}
                </div>

                {}
                <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 2, overflow: 'hidden' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    <span style={{
                      fontSize: '0.58rem', fontWeight: 700, letterSpacing: '0.04em',
                      textTransform: 'uppercase', color: meta.color,
                    }}>
                      {meta.label}
                    </span>
                  </div>
                  <span className="event-text">
                    {buildSummary(ev.eventType, ev.details)}
                  </span>
                </div>

                <span className="event-time">{formatTime(ev.timestamp)}</span>
              </div>
            );
          })
        )}
      </div>
    </div>
  );
}
