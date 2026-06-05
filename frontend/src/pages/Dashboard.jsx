import { useWebSocket }      from '../hooks/useWebSocket';
import Navbar               from '../components/layout/Navbar';
import SimulationControls   from '../components/control/SimulationControls';
import PrinterGrid          from '../components/printers/PrinterGrid';
import ThreadStatusPanel    from '../components/printers/ThreadStatusPanel';
import PrintQueueViz        from '../components/queue/PrintQueueViz';
import SemaphoreWidget      from '../components/queue/SemaphoreWidget';
import SemaphoreDeepDive    from '../components/queue/SemaphoreDeepDive';
import MetricsPanel         from '../components/metrics/MetricsPanel';
import Charts               from '../components/charts/Charts';
import EventFeed            from '../components/events/EventFeed';
import OSProblemsPanel      from '../components/OSProblemsPanel';
import OSProblemsSolver     from '../components/OSProblemsSolver';
import ProcessFlowPanel     from '../components/ProcessFlowPanel';
import JobJourneyPanel      from '../components/jobs/JobJourneyPanel';

export default function Dashboard() {
  useWebSocket(); // connect WebSocket once at top level

  return (
    <div className="app-layout">
      <Navbar />

      <main className="main-content animate-slide">

        {/* ── Row 1: Simulation Controls ─────────────────────────────── */}
        <SimulationControls />

        {/* ── Row 2: Process Flow Pipeline (full-width) ───────────────── */}
        <ProcessFlowPanel />

        {/* ── Row 3: Printers + Queue + Semaphore mini-widget ─────────── */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 270px', gap: 12 }}
             className="col-auto-stack">
          <PrinterGrid />
          <PrintQueueViz />
          <SemaphoreWidget />
        </div>

        {/* ── Row 4: Job Journey (full-width) ─────────────────────────── */}
        <JobJourneyPanel />

        {/* ── Row 5: Semaphore Deep-Dive + Thread Status ──────────────── */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}
             className="col-auto-stack">
          <SemaphoreDeepDive />
          <ThreadStatusPanel />
        </div>

        {/* ── Row 6: Metrics KPIs (full-width) ────────────────────────── */}
        <MetricsPanel />

        {/* ── Row 7: Live Charts ──────────────────────────────────────── */}
        <Charts />

        {/* ── Row 8: OS Problems Solver — 6-card explainer (full-width) ─ */}
        <OSProblemsSolver />

        {/* ── Row 9: Event Feed + Original OS Concepts badges ─────────── */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 360px', gap: 12 }}
             className="col-auto-stack">
          <EventFeed />
          <OSProblemsPanel />
        </div>

      </main>
    </div>
  );
}
