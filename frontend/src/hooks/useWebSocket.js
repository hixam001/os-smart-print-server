import { useEffect, useRef } from 'react';
import { useSimulationStore } from '../store/simulationStore';

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

              const snap    = msg.data ?? {};
              const details = snap.details ?? {};

              const normalised = {

                status:          details.status          ?? snap.status          ?? undefined,

                algorithm:       details.algorithm       ?? snap.currentScheduler ?? undefined,

                elapsedMs:       details.elapsedMs       ?? snap.currentTimeMs   ?? 0,
                simulationSpeed: details.simulationSpeed ?? snap.simulationSpeed ?? undefined,
                tick:            details.tick            ?? undefined,

                queueSize:     details.queueSize     ?? 0,
                queueCapacity: details.queueCapacity ?? undefined,
                totalEnqueued: details.totalEnqueued ?? undefined,
                totalDequeued: details.totalDequeued ?? undefined,
                totalRejected: details.totalRejected ?? undefined,
                queuedJobs:    details.queuedJobs    ?? [],

                printers: details.printers ?? [],

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

        ws.close();
      };
    }

    connect();

    return () => {
      mountedRef.current = false;
      clearTimeout(retryRef.current);
      wsRef.current?.close();
    };
  }, []);
}
