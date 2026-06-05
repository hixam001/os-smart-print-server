import axios from 'axios';

// Relative URL: works in dev (Vite proxy → :8080) and in prod (embedded in Spring Boot at same origin)
const api = axios.create({ baseURL: '/api/simulation' });

export const SimulationAPI = {
  start:     (config)  => api.post('/start', config),
  stop:      ()        => api.post('/stop'),
  pause:     ()        => api.post('/pause'),
  resume:    ()        => api.post('/resume'),
  reset:     ()        => api.post('/reset'),
  configure: (payload) => api.put('/configure', payload),
  getState:  ()        => api.get('/state'),
  export:    ()        => api.get('/export', { params: { format: 'csv', type: 'jobs' }, responseType: 'text' }),
  metrics:   (params)  => api.get('/metrics', { params }),
};
