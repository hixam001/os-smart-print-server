import { useState } from 'react';
import { SimulationAPI } from '../../api/simulationApi';
import { useSimulationStore } from '../../store/simulationStore';

const DEFAULT_CONFIG = {
  numUsers:           3,     // 3 users submitting jobs
  numPrinters:        2,     // 2 physical printers
  queueCapacity:      15,    // bounded buffer of 15
  jobIntervalMs:      5000,  // one job every ~5s per user
  algorithm:          'HYBRID',
  colorJobRatio:      0.4,
  smallJobPercentage: 0.6,
  simulationSpeed:    2.0,   // 2× = watchable with real print delays
};

const ALGO_INFO = {
  FCFS:   { label: 'FCFS',   desc: 'First-Come First-Served — fair ordering by arrival', color: 'var(--teal-2)' },
  SJF:    { label: 'SJF',    desc: 'Shortest Job First — minimises avg wait time',        color: 'var(--accent)' },
  HYBRID: { label: 'HYBRID', desc: 'SJF + Aging — prevents starvation of long jobs',      color: 'var(--accent-2)' },
};

export default function SimulationControls() {
  const { status, algorithm, reset: resetStore } = useSimulationStore();
  const [config, setConfig]   = useState(DEFAULT_CONFIG);
  const [loading, setLoading] = useState('');
  const [error, setError]     = useState('');
  const [showAdvanced, setShowAdvanced] = useState(false);

  const isRunning = status === 'RUNNING';
  const isPaused  = status === 'PAUSED';
  const isStopped = status === 'STOPPED';
  const isActive  = isRunning || isPaused;

  const call = async (action, fn) => {
    setLoading(action); setError('');
    try { await fn(); }
    catch (e) { setError(e.response?.data?.message ?? e.message ?? 'Request failed'); }
    finally { setLoading(''); }
  };

  const handleStart  = () => call('start',  () => SimulationAPI.start(config));
  const handlePause  = () => call('pause',  SimulationAPI.pause);
  const handleResume = () => call('resume', SimulationAPI.resume);
  const handleStop   = () => call('stop',   SimulationAPI.stop);
  const handleReset  = () => call('reset',  async () => { await SimulationAPI.reset(); resetStore(); });

  const handleAlgoChange = async (algo) => {
    setConfig(c => ({ ...c, algorithm: algo }));
    if (isActive) await call('configure', () => SimulationAPI.configure({ algorithm: algo }));
  };

  const handleSpeedChange = async (speed) => {
    const v = parseFloat(speed);
    setConfig(c => ({ ...c, simulationSpeed: v }));
    if (isActive) await call('configure', () => SimulationAPI.configure({ simulationSpeed: v }));
  };

  const handleExport = async () => {
    try {
      const res = await SimulationAPI.export();
      const blob = new Blob([res.data], { type: 'text/csv' });
      const url  = URL.createObjectURL(blob);
      const a    = document.createElement('a');
      a.href = url; a.download = 'simulation-results.csv'; a.click();
      URL.revokeObjectURL(url);
    } catch(e) { setError('Export failed'); }
  };

  const speedPct = ((config.simulationSpeed - 0.5) / 9.5) * 100;

  return (
    <div className="card">
      <div className="card-header">
        <span className="card-title">
          ⚙️ Simulation Controls
        </span>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          {error && (
            <span style={{ fontSize: '0.7rem', color: 'var(--rose-2)', maxWidth: 280, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              ⚠ {error}
            </span>
          )}
          <button
            className="btn btn-ghost"
            style={{ fontSize: '0.68rem', padding: '4px 10px' }}
            onClick={() => setShowAdvanced(v => !v)}
          >
            {showAdvanced ? '▲ Less' : '▼ Advanced'}
          </button>
        </div>
      </div>

      <div className="card-body" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>

        {/* ── Row 1: Main action buttons ── */}
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
          {isStopped && (
            <button id="btn-start" className="btn btn-primary" onClick={handleStart} disabled={!!loading}>
              {loading === 'start' ? <SpinIcon /> : '▶'} Start Simulation
            </button>
          )}
          {isRunning && (
            <button id="btn-pause" className="btn btn-warning" onClick={handlePause} disabled={!!loading}>
              {loading === 'pause' ? <SpinIcon /> : '⏸'} Pause
            </button>
          )}
          {isPaused && (
            <button id="btn-resume" className="btn btn-primary" onClick={handleResume} disabled={!!loading}>
              {loading === 'resume' ? <SpinIcon /> : '▶'} Resume
            </button>
          )}
          {isActive && (
            <button id="btn-stop" className="btn btn-danger" onClick={handleStop} disabled={!!loading}>
              {loading === 'stop' ? <SpinIcon /> : '⏹'} Stop
            </button>
          )}
          <button id="btn-reset" className="btn btn-ghost" onClick={handleReset} disabled={!!loading || isRunning}>
            {loading === 'reset' ? <SpinIcon /> : '↺'} Reset
          </button>
          <div style={{ flex: 1 }} />
          <button id="btn-export" className="btn btn-teal" onClick={handleExport} disabled={isStopped && !isActive}>
            ⬇ Export CSV
          </button>
        </div>

        {/* ── Row 2: Algorithm selector ── */}
        <div style={{ display: 'flex', alignItems: 'flex-start', gap: 14, flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
            <span style={{ fontSize: '0.65rem', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.06em', fontWeight: 600 }}>
              Scheduling Algorithm
            </span>
            <div className="algo-tabs">
              {Object.entries(ALGO_INFO).map(([key, info]) => (
                <button
                  key={key}
                  id={`algo-${key}`}
                  className={`algo-tab ${(config.algorithm === key) ? 'active' : ''}`}
                  onClick={() => handleAlgoChange(key)}
                  title={info.desc}
                  style={config.algorithm === key ? { '--btn-color': info.color } : {}}
                >
                  {info.label}
                </button>
              ))}
            </div>
            {config.algorithm && ALGO_INFO[config.algorithm] && (
              <span style={{ fontSize: '0.68rem', color: 'var(--text-muted)', maxWidth: 300 }}>
                {ALGO_INFO[config.algorithm].desc}
              </span>
            )}
          </div>

          {/* Simulation speed */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 5, minWidth: 180, flex: 1, maxWidth: 240 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ fontSize: '0.65rem', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.06em', fontWeight: 600 }}>
                Simulation Speed
              </span>
              <span style={{ fontSize: '0.78rem', color: 'var(--accent)', fontFamily: 'var(--font-mono)', fontWeight: 600 }}>
                {config.simulationSpeed}×
              </span>
            </div>
            <input
              className="slider"
              type="range"
              min="0.5" max="10" step="0.5"
              value={config.simulationSpeed}
              onChange={e => handleSpeedChange(e.target.value)}
              style={{ background: `linear-gradient(90deg, var(--accent) ${speedPct}%, var(--bg-surface) 0%)` }}
            />
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.6rem', color: 'var(--text-muted)' }}>
              <span>0.5× (slow)</span>
              <span>10× (fast)</span>
            </div>
          </div>
        </div>

        {/* ── Row 3: Advanced config (stopped only) ── */}
        {(isStopped && showAdvanced) && (
          <div style={{ animation: 'slideUp 0.25s ease' }}>
            <div className="section-header">Configuration</div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))', gap: 10 }}>
              {[
                { label: 'Users',        key: 'numUsers',      min: 1,   max: 20,   step: 1,    suffix: '' },
                { label: 'Printers',     key: 'numPrinters',   min: 1,   max: 10,   step: 1,    suffix: '' },
                { label: 'Queue Cap',    key: 'queueCapacity', min: 5,   max: 200,  step: 5,    suffix: ' slots' },
                { label: 'Job Interval', key: 'jobIntervalMs', min: 100, max: 5000, step: 100,  suffix: 'ms' },
                { label: 'Color Ratio',  key: 'colorJobRatio', min: 0,   max: 1,    step: 0.05, suffix: '' },
                { label: 'Small Jobs %', key: 'smallJobPercentage', min: 0, max: 1, step: 0.05, suffix: '' },
              ].map(({ label, key, min, max, step, suffix }) => (
                <div key={key} style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                  <label style={{ fontSize: '0.65rem', color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                    {label}
                  </label>
                  <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
                    <input
                      className="input"
                      type="number"
                      min={min} max={max} step={step}
                      value={config[key]}
                      onChange={e => setConfig(c => ({ ...c, [key]: parseFloat(e.target.value) }))}
                      style={{ width: '100%', paddingRight: suffix ? 32 : 10 }}
                    />
                    {suffix && (
                      <span style={{ position: 'absolute', right: 8, fontSize: '0.6rem', color: 'var(--text-muted)', pointerEvents: 'none' }}>
                        {suffix}
                      </span>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

function SpinIcon() {
  return (
    <span style={{ display: 'inline-block', animation: 'spin 0.8s linear infinite' }}>
      ⟳
      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
    </span>
  );
}
