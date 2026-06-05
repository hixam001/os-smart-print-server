import { useEffect, useRef } from 'react';
import { useSimulationStore } from '../store/simulationStore';

// Use relative ws:// so Vite dev-server proxy handles it (avoids CORS issues).
// In production, swap to an explicit ws://your-host/ws/simulation.
const WS_URL = (() => {
  const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${proto}//${window.location.host}/ws/simulation`;
})();

const RECONNECT_DELAY = 3000;

export function useWebSocket() {
  const wsRef      = useRef(null);
  const retryRef   = useRef(null);
  const mountedRef = useRef(true);

  const { setConnected, setHeartbeat, applyStateUpdate, pushEvent } =
    useSimulationStore();

  useEffect(() => {
    mountedRef.current = true;

    function connect() {
      if (!mountedRef.current) return;

      const ws = new WebSocket(WS_URL);
      wsRef.current = ws;

      ws.onopen = () => {
        if (!mountedRef.current) { ws.close(); return; }
        console.info('[WS] Connected →', WS_URL);
      };

      ws.onmessage = (e) => {
        try {
          const msg = JSON.parse(e.data);

          switch (msg.type) {
            case 'CONNECTED':
              setConnected(true, msg.clientId);
              break;

            case 'STATE_UPDATE': {
              /*
               * The backend sends:
               *   { type:'STATE_UPDATE', data: SimulationSnapshot }
               *
               * SimulationSnapshot has:
               *   status, currentTimeMs, currentScheduler, simulationSpeed, message, details
               *
               * details (SimulationState) has:
               *   status, elapsedMs, algorithm, simulationSpeed, tick,
               *   queueSize, queueCapacity, totalEnqueued, totalDequeued, totalRejected,
               *   printers, queuedJobs, metrics
               *
               * We normalise both shapes into what the store expects.
               */
              const snap    = msg.data ?? {};
              const details = snap.details ?? {};   // SimulationState (may be empty when STOPPED)

              const normalised = {
                // status — prefer details.status (a SimulationStatus enum string)
                status:          details.status          ?? snap.status          ?? undefined,
                // algorithm — SimulationState uses 'algorithm', Snapshot uses 'currentScheduler'
                algorithm:       details.algorithm       ?? snap.currentScheduler ?? undefined,
                // elapsedMs — SimulationState uses 'elapsedMs', Snapshot uses 'currentTimeMs'
                elapsedMs:       details.elapsedMs       ?? snap.currentTimeMs   ?? 0,
                simulationSpeed: details.simulationSpeed ?? snap.simulationSpeed ?? undefined,
                tick:            details.tick            ?? undefined,

                // Queue (only in SimulationState)
                queueSize:     details.queueSize     ?? 0,
                queueCapacity: details.queueCapacity ?? undefined,
                totalEnqueued: details.totalEnqueued ?? undefined,
                totalDequeued: details.totalDequeued ?? undefined,
                totalRejected: details.totalRejected ?? undefined,
                queuedJobs:    details.queuedJobs    ?? [],

                // Printers (only in SimulationState)
                printers: details.printers ?? [],

                // Metrics (only in SimulationState)
                metrics: details.metrics ?? undefined,
              };

              applyStateUpdate(normalised);
              break;
            }

            case 'EVENT':
              pushEvent(msg);
              break;

            case 'HEARTBEAT':
              setHeartbeat(msg.timestamp ?? Date.now());
              break;

            default:
              // unknown message type — ignore silently
              break;
          }
        } catch (err) {
          console.warn('[WS] parse error', err);
        }
      };

      ws.onclose = (evt) => {
        setConnected(false);
        console.info('[WS] Disconnected (code=%d) — retrying in %dms', evt.code, RECONNECT_DELAY);
        if (mountedRef.current) {
          retryRef.current = setTimeout(connect, RECONNECT_DELAY);
        }
      };

      ws.onerror = () => {
        // onerror is always followed by onclose, so just close — reconnect loop handles the rest
        ws.close();
      };
    }

    connect();

    return () => {
      mountedRef.current = false;
      clearTimeout(retryRef.current);
      wsRef.current?.close();
    };
  }, []); // intentionally empty — connect once per mount
}
