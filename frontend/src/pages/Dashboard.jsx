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
  useWebSocket();

  return (
    <div className="app-layout">
      <Navbar />

      <main className="main-content animate-slide">

        {}
        <SimulationControls />

        {}
        <ProcessFlowPanel />

        {}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 270px', gap: 12 }}
             className="col-auto-stack">
          <PrinterGrid />
          <PrintQueueViz />
          <SemaphoreWidget />
        </div>

        {}
        <JobJourneyPanel />

        {}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}
             className="col-auto-stack">
          <SemaphoreDeepDive />
          <ThreadStatusPanel />
        </div>

        {}
        <MetricsPanel />

        {}
        <Charts />

        {}
        <OSProblemsSolver />

        {}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 360px', gap: 12 }}
             className="col-auto-stack">
          <EventFeed />
          <OSProblemsPanel />
        </div>

      </main>
    </div>
  );
}
