import { create } from 'zustand';

const MAX_HISTORY = 60;
const MAX_EVENTS  = 100;

export const useSimulationStore = create((set, get) => ({

  connected:   false,
  clientId:    null,
  lastHeartbeat: null,

  status:        'STOPPED',
  algorithm:     'FCFS',
  simulationSpeed: 1.0,
  elapsedMs:     0,
  tick:          0,

  queueSize:     0,
  queueCapacity: 20,
  totalEnqueued: 0,
  totalDequeued: 0,
  totalRejected: 0,
  queuedJobs:    [],

  printers: [],

  metrics: {
    totalJobsCompleted:  0,
    totalJobsFailed:     0,
    totalJobsCancelled:  0,
    avgWaitingTimeMs:    0,
    avgTurnaroundTimeMs: 0,
    avgPageCount:        0,
    throughputJobsPerMin: 0,
    colorJobRatio:       0,
  },

  throughputHistory:  [],
  waitTimeHistory:    [],
  queueSizeHistory:   [],
  rejectedHistory:    [],

  semaphorePermits:  0,
  semaphoreWaiters:  0,
  semaphoreHistory:  [],

  events: [],

  setConnected: (connected, clientId = null) =>
    set({ connected, clientId }),

  setHeartbeat: (ts) => set({ lastHeartbeat: ts }),

  applyStateUpdate: (data) => {
    const prev = get();
    const now  = Date.now();

    const addPoint = (arr, v) => {
      const next = [...arr, { t: now, v }];
      return next.length > MAX_HISTORY ? next.slice(-MAX_HISTORY) : next;
    };

    set({
      status:          data.status          ?? prev.status,
      algorithm:       data.algorithm       ?? prev.algorithm,
      simulationSpeed: data.simulationSpeed ?? prev.simulationSpeed,
      elapsedMs:       data.elapsedMs       ?? prev.elapsedMs,
      tick:            data.tick            ?? prev.tick,

      queueSize:     data.queueSize     ?? 0,
      queueCapacity: data.queueCapacity ?? prev.queueCapacity,
      totalEnqueued: data.totalEnqueued ?? prev.totalEnqueued,
      totalDequeued: data.totalDequeued ?? prev.totalDequeued,
      totalRejected: data.totalRejected ?? prev.totalRejected,
      queuedJobs:    data.queuedJobs    ?? [],

      printers: data.printers ?? prev.printers,
      metrics:  data.metrics  ?? prev.metrics,

      throughputHistory: addPoint(prev.throughputHistory, data.metrics?.throughputJobsPerMin ?? 0),
      waitTimeHistory:   addPoint(prev.waitTimeHistory,   data.metrics?.avgWaitingTimeMs     ?? 0),
      queueSizeHistory:  addPoint(prev.queueSizeHistory,  data.queueSize                      ?? 0),
      rejectedHistory:   addPoint(prev.rejectedHistory,   data.totalRejected                  ?? 0),

      semaphorePermits: data.semaphorePermits ?? prev.semaphorePermits,
      semaphoreWaiters: data.semaphoreWaiters ?? prev.semaphoreWaiters,
      semaphoreHistory: (() => {
        const next = [...prev.semaphoreHistory, {
          t:       now,
          permits: data.semaphorePermits ?? prev.semaphorePermits,
          waiters: data.semaphoreWaiters ?? prev.semaphoreWaiters,
        }];
        return next.length > MAX_HISTORY ? next.slice(-MAX_HISTORY) : next;
      })(),
    });
  },

  pushEvent: (event) => {
    const newEvent = {
      id:        `${Date.now()}-${Math.random()}`,
      timestamp: event.timestamp ?? Date.now(),
      eventType: event.event?.eventType ?? 'UNKNOWN',
      details:   event.event?.details   ?? {},
    };
    set((s) => ({
      events: [newEvent, ...s.events].slice(0, MAX_EVENTS),
    }));
  },

  reset: () => set({
    status: 'STOPPED', algorithm: 'FCFS', simulationSpeed: 1.0,
    elapsedMs: 0, tick: 0,
    queueSize: 0, totalEnqueued: 0, totalDequeued: 0, totalRejected: 0,
    queuedJobs: [], printers: [],
    metrics: { totalJobsCompleted: 0, totalJobsFailed: 0, totalJobsCancelled: 0,
               avgWaitingTimeMs: 0, avgTurnaroundTimeMs: 0, avgPageCount: 0,
               throughputJobsPerMin: 0, colorJobRatio: 0 },
    throughputHistory: [], waitTimeHistory: [], queueSizeHistory: [], rejectedHistory: [],
    semaphorePermits: 0, semaphoreWaiters: 0, semaphoreHistory: [],
    events: [],
  }),
}));
