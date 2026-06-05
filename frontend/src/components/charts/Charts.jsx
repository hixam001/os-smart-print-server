import {
  AreaChart, Area,
  LineChart, Line,
  BarChart, Bar,
  XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer, ReferenceLine,
} from 'recharts';
import { useSimulationStore } from '../../store/simulationStore';

const TOOLTIP_STYLE = {
  backgroundColor: 'var(--bg-card)',
  border: '1px solid rgba(99,132,255,0.25)',
  borderRadius: 8,
  fontSize: '0.72rem',
  color: 'var(--text-primary)',
  boxShadow: '0 8px 32px rgba(0,0,0,0.5)',
};

const AXIS_TICK = { fill: 'var(--text-muted)', fontSize: 10 };

function ChartCard({ id, title, hint, children, badge }) {
  return (
    <div className="card" style={{ flex: 1, minWidth: 220 }} id={id}>
      <div className="card-header">
        <span className="card-title">{title}</span>
        {badge && <span className="tag tag-accent">{badge}</span>}
      </div>
      <div style={{ padding: '10px 6px 4px' }}>
        <ResponsiveContainer width="100%" height={150}>
          {children}
        </ResponsiveContainer>
      </div>
      {hint && (
        <div style={{ padding: '4px 16px 10px', fontSize: '0.62rem', color: 'var(--text-muted)', lineHeight: 1.5 }}>
          {hint}
        </div>
      )}
    </div>
  );
}

const gradDef = (id, color, opacity = 0.35) => (
  <defs>
    <linearGradient id={id} x1="0" y1="0" x2="0" y2="1">
      <stop offset="5%"  stopColor={color} stopOpacity={opacity} />
      <stop offset="95%" stopColor={color} stopOpacity={0} />
    </linearGradient>
  </defs>
);

export default function Charts() {
  const {
    throughputHistory, waitTimeHistory,
    queueSizeHistory, rejectedHistory,
    metrics, queueCapacity,
  } = useSimulationStore();

  const avgWait = metrics?.avgWaitingTimeMs ?? 0;

  return (
    <div className="card">
      <div className="card-header">
        <span className="card-title">📈 Live Charts</span>
        <span style={{ fontSize: '0.62rem', color: 'var(--text-muted)' }}>last 60 data points · 100ms interval</span>
      </div>
      <div style={{ padding: 14, display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))', gap: 12 }}>

        {/* Throughput */}
        <ChartCard
          id="chart-throughput"
          title="⚡ Throughput"
          hint="OS scheduler efficiency — jobs completed per simulated minute"
          badge={`${metrics?.throughputJobsPerMin ?? 0} j/m`}
        >
          <AreaChart data={throughputHistory}>
            {gradDef('tpGrad', '#6366f1')}
            <CartesianGrid strokeDasharray="3 3" stroke="rgba(99,132,255,0.07)" />
            <XAxis dataKey="t" hide />
            <YAxis width={30} tick={AXIS_TICK} />
            <Tooltip
              contentStyle={TOOLTIP_STYLE}
              formatter={v => [`${v}`, 'jobs/min']}
              labelFormatter={() => ''}
            />
            <Area
              type="monotone" dataKey="v"
              stroke="#6366f1" strokeWidth={2}
              fill="url(#tpGrad)" dot={false}
              activeDot={{ r: 4, fill: '#6366f1', stroke: '#080b14', strokeWidth: 2 }}
            />
          </AreaChart>
        </ChartCard>

        {/* Queue depth */}
        <ChartCard
          id="chart-queue"
          title="📋 Queue Depth"
          hint="Bounded buffer occupancy — spikes indicate producer faster than consumer"
          badge={`cap ${queueCapacity}`}
        >
          <AreaChart data={queueSizeHistory}>
            {gradDef('qGrad', '#14b8a6')}
            <CartesianGrid strokeDasharray="3 3" stroke="rgba(20,184,166,0.07)" />
            <XAxis dataKey="t" hide />
            <YAxis width={30} tick={AXIS_TICK} domain={[0, queueCapacity || 'auto']} />
            <Tooltip
              contentStyle={TOOLTIP_STYLE}
              formatter={v => [`${v}`, 'jobs']}
              labelFormatter={() => ''}
            />
            <ReferenceLine
              y={Math.round(queueCapacity * 0.75)}
              stroke="rgba(245,158,11,0.4)"
              strokeDasharray="4 3"
              label={{ value: '75%', fill: 'var(--amber)', fontSize: 9 }}
            />
            <Area
              type="monotone" dataKey="v"
              stroke="#14b8a6" strokeWidth={2}
              fill="url(#qGrad)" dot={false}
              activeDot={{ r: 4, fill: '#14b8a6', stroke: '#080b14', strokeWidth: 2 }}
            />
          </AreaChart>
        </ChartCard>

        {/* Avg wait time */}
        <ChartCard
          id="chart-waittime"
          title="⏱ Avg Wait Time"
          hint="Starvation indicator — sustained high values mean some jobs are not getting CPU"
        >
          <LineChart data={waitTimeHistory}>
            <CartesianGrid strokeDasharray="3 3" stroke="rgba(245,158,11,0.07)" />
            <XAxis dataKey="t" hide />
            <YAxis width={38} tick={AXIS_TICK} tickFormatter={v => `${(v/1000).toFixed(1)}s`} />
            <Tooltip
              contentStyle={TOOLTIP_STYLE}
              formatter={v => [`${(v / 1000).toFixed(2)}s`, 'avg wait']}
              labelFormatter={() => ''}
            />
            {avgWait > 0 && (
              <ReferenceLine
                y={avgWait}
                stroke="rgba(245,158,11,0.5)"
                strokeDasharray="4 3"
                label={{ value: 'avg', fill: 'var(--amber)', fontSize: 9 }}
              />
            )}
            <Line
              type="monotone" dataKey="v"
              stroke="#f59e0b" strokeWidth={2} dot={false}
              activeDot={{ r: 4, fill: '#f59e0b', stroke: '#080b14', strokeWidth: 2 }}
            />
          </LineChart>
        </ChartCard>

        {/* Cumulative rejections */}
        <ChartCard
          id="chart-rejected"
          title="⛔ Rejections"
          hint="Dropped jobs when bounded buffer is full — indicates system overload"
          badge={metrics?.totalJobsFailed > 0 ? `${metrics.totalJobsFailed} failed` : null}
        >
          <AreaChart data={rejectedHistory}>
            {gradDef('rejGrad', '#f43f5e', 0.3)}
            <CartesianGrid strokeDasharray="3 3" stroke="rgba(244,63,94,0.07)" />
            <XAxis dataKey="t" hide />
            <YAxis width={30} tick={AXIS_TICK} />
            <Tooltip
              contentStyle={TOOLTIP_STYLE}
              formatter={v => [`${v}`, 'cumulative']}
              labelFormatter={() => ''}
            />
            <Area
              type="monotone" dataKey="v"
              stroke="#f43f5e" strokeWidth={2}
              fill="url(#rejGrad)" dot={false}
              activeDot={{ r: 4, fill: '#f43f5e', stroke: '#080b14', strokeWidth: 2 }}
            />
          </AreaChart>
        </ChartCard>

      </div>
    </div>
  );
}
