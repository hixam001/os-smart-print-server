# 🏗️ **MASTER PROMPT: Smart Print Server Scheduler - Backend/API Development**

**Author:** Senior Software Engineer
**Version:** 1.0
**Date:** 2026
**Status:** Production Development Guide

---

## **Executive Summary**

This master prompt serves as the authoritative guide for developing the backend and API layer of the Smart Print Server Scheduler system. It encapsulates architectural decisions, coding standards, testing requirements, and integration patterns that ensure a robust, scalable, and maintainable system.

Any developer working on the backend components **MUST** adhere to this prompt. This is not a suggestion—it is the specification.

---

## **Part 1: Development Mindset & Principles**

### **1.1 Core Philosophy**

You are building a **production-grade operating systems simulator**. This is not a toy project or proof of concept. Every line of code must reflect:

- **Correctness over cleverness:** A simple, correct solution beats a complex, "clever" one
- **Testability first:** Code must be independently testable
- **Observability throughout:** Every component must have logging and metrics
- **Defensive programming:** Assume nothing about inputs; validate everything
- **Performance awareness:** Measure everything; optimize for real bottlenecks

### **1.2 Non-Negotiable Requirements**

1. **Thread Safety is Mandatory**
   - Every shared resource must be protected
   - Race conditions are production bugs, not "maybe" bugs
   - Use proper synchronization primitives (locks, semaphores, conditions)

2. **No Silent Failures**
   - Every exception must be logged with context
   - Errors must propagate to the frontend clearly
   - Graceful degradation is acceptable; silent failures are not

3. **Performance Baselines**
   - WebSocket message latency: < 100ms from backend change to client update
   - REST endpoint response: < 200ms for all endpoints
   - Simulator must handle 100,000+ jobs without memory leaks
   - Metrics calculation must complete within 1 second

4. **Code Quality Standards**
   - No warnings from compiler
   - SonarQube quality gate must pass (A rating minimum)
   - Code coverage minimum: 80% (critical paths 95%)
   - Every method has clear purpose and documentation

5. **Operational Readiness**
   - Health check endpoint must be available
   - Structured logging (JSON format) for all important events
   - Metrics for monitoring system health
   - Clear error messages for debugging

---

## **Part 2: Architecture & Design Constraints**

### **2.1 Technology Stack (Non-Negotiable)**

```
Java Version:        11+ (target 17 for modern features)
Build Tool:          Maven 3.8+
Framework:           Spring Boot 2.7+ (or 3.x if available)
WebSocket:           Spring WebSocket
REST API:            Spring MVC with proper REST conventions
Serialization:       Jackson (JSON)
Testing:             JUnit 5 + Mockito + TestNG
Logging:             SLF4J + Logback
Database (Optional): PostgreSQL 12+ with JDBC driver
```

### **2.2 Architecture Layers**

```
┌─────────────────────────────────────────────────┐
│  HTTP/WebSocket Layer (Spring Controllers)      │  ← Public Interface
├─────────────────────────────────────────────────┤
│  Service Layer (Business Logic)                  │  ← Orchestration
├─────────────────────────────────────────────────┤
│  Simulator Core (Threading, Scheduling, Metrics)│  ← OS Simulation
├─────────────────────────────────────────────────┤
│  Data Access Layer (Optional: Repositories)      │  ← Persistence
└─────────────────────────────────────────────────┘
```

**Critical Rule:** Each layer has ONE responsibility. No layer skips another. Follow dependency injection religiously.

### **2.3 Threading Model**

**Three Thread Pool Categories:**

1. **User Threads** (Producers)
   - Fixed size: configurable (default 3)
   - Each simulates one user submitting print jobs
   - Sleeps between submissions (configurable interval)

2. **Printer Threads** (Consumers)
   - Fixed size: configurable (default 2)
   - Each simulates one physical printer
   - Blocks on semaphore when no jobs available

3. **Scheduler Thread** (Orchestrator)
   - Single thread
   - Makes scheduling decisions
   - Runs every 100ms (or when jobs change)
   - NON-BLOCKING: never locks for extended periods

**Golden Rule:** Never start a thread without a clear shutdown mechanism. Graceful shutdown is mandatory.

### **2.4 Synchronization Strategy**

**Use This Hierarchy (in order of preference):**

1. **Immutable Objects** (no synchronization needed)
2. **ReentrantLock + Condition Variables** (for explicit control)
3. **CountingSemaphore (custom)** (for OS concept demonstration)
4. **Collections.synchronizedXxx** (last resort)
5. **Synchronized blocks** (only for simple cases)

**Critical Pattern:**
```
lock.lock();
try {
    // Critical section
    // NEVER return or throw without finally
} finally {
    lock.unlock();  // MANDATORY
}
```

**Never use:**
- Naked `synchronized` (use ReentrantLock)
- Object.wait()/notify() directly (use Condition)
- Thread.sleep() in critical section
- Nested locks (can cause deadlock)

---

## **Part 3: Core Component Specifications**

### **3.1 CountingSemaphore Class**

**Responsibility:** Manage bounded resource access with explicit wait/signal operations

**Interface Specification:**
```java
public class CountingSemaphore {
    /**
     * Creates semaphore with given initial count.
     * Used for: empty (queue capacity), full (jobs in queue), mutex (1)
     */
    public CountingSemaphore(int initialCount)
    
    /**
     * Acquires one unit. Blocks if count becomes 0.
     * Must be paired with signal().
     * @throws InterruptedException if thread interrupted while waiting
     */
    public void wait() throws InterruptedException
    
    /**
     * Releases one unit. Wakes one blocked thread (if any).
     * ALWAYS call in finally block.
     */
    public void signal()
    
    /**
     * Returns current count. For monitoring only (may change immediately).
     * @return current count value
     */
    public int getValue()
    
    /**
     * Returns list of thread IDs currently waiting on this semaphore.
     * For visualization and debugging.
     * @return List of waiting thread IDs (empty if none)
     */
    public List<String> getWaitingThreadIds()
}
```

**Implementation Notes:**
- Use ReentrantLock internally + Condition variable
- Throw InterruptedException (don't swallow)
- Track wait queue for monitoring
- Validate count never goes negative

**Testing Requirements:**
- Unit tests with concurrent access (10+ threads)
- Verify no deadlocks with stress test (10K+ signal/wait operations)
- Verify FIFO ordering of wait queue
- Verify no spurious wakeups

---

### **3.2 PrintQueue Class (Circular Bounded Buffer)**

**Responsibility:** Hold PrintJob objects with thread-safe enqueue/dequeue

**Interface Specification:**
```java
public class PrintQueue {
    /**
     * Creates queue with given capacity.
     * No synchronization here—caller manages via semaphores.
     */
    public PrintQueue(int capacity)
    
    /**
     * Adds job to rear of queue.
     * Assumes caller already acquired "empty" semaphore.
     * @throws IllegalStateException if queue is full (shouldn't happen)
     */
    public void enqueue(PrintJob job)
    
    /**
     * Removes and returns job from front (FCFS).
     * Assumes caller already acquired "full" semaphore.
     * @return next job in FIFO order
     * @throws NoSuchElementException if queue is empty (shouldn't happen)
     */
    public PrintJob dequeueFromFront()
    
    /**
     * Finds and removes job with minimum pageCount (SJF).
     * Scans entire queue - O(n) operation.
     * Assumes caller already acquired "full" semaphore.
     * @return job with minimum pageCount
     * @throws NoSuchElementException if queue is empty
     */
    public PrintJob dequeueShortestJob()
    
    /**
     * Dequeues specific job (used for fairness/aging).
     * Assumes caller already acquired "full" semaphore.
     * @param job the job to remove
     * @throws NoSuchElementException if job not in queue
     */
    public void dequeue(PrintJob job)
    
    /**
     * Returns job at front without removing.
     * Safe even without semaphore (read-only).
     */
    public PrintJob peek()
    
    /**
     * Returns all jobs currently in queue (snapshot).
     * @return unmodifiable list of jobs
     */
    public List<PrintJob> getAllJobs()
    
    /**
     * @return current number of jobs in queue
     */
    public int size()
    
    /**
     * @return true if queue has no jobs
     */
    public boolean isEmpty()
    
    /**
     * @return true if queue is at capacity
     */
    public boolean isFull()
    
    /**
     * @return capacity of queue
     */
    public int getCapacity()
}
```

**Implementation Notes:**
- Use circular array internally (front and rear pointers)
- NOT thread-safe (semaphores provide synchronization)
- Handle wraparound in circular buffer correctly
- Validate state invariants in assertions

**Testing Requirements:**
- Enqueue/dequeue in circular pattern maintains invariants
- SJF dequeue returns shortest job correctly
- Queue never accepts more than capacity jobs
- All jobs eventually retrievable

---

### **3.3 UserThread Class (Producer)**

**Responsibility:** Simulate user submitting print jobs to queue

**Interface Specification:**
```java
public class UserThread extends Thread {
    /**
     * Creates a user thread that generates jobs.
     * @param userId unique identifier for this user
     * @param printQueue shared queue to add jobs to
     * @param emptySymaphore decremented on enqueue
     * @param fullSemaphore incremented on enqueue
     * @param mutexSemaphore protects queue modifications
     * @param jobInterval milliseconds between job submissions (e.g., 3000)
     */
    public UserThread(String userId, PrintQueue printQueue,
                      CountingSemaphore emptySem, CountingSemaphore fullSem,
                      CountingSemaphore mutexSem, long jobInterval)
    
    /**
     * Main loop: generate jobs, place in queue.
     * Handles InterruptedException to support graceful shutdown.
     */
    @Override
    public void run()
    
    /**
     * Signals thread to stop. Waits up to 5 seconds for graceful exit.
     * @return true if thread stopped, false if timeout
     */
    public boolean stopGracefully()
    
    /**
     * @return number of jobs submitted by this user
     */
    public int getJobsSubmitted()
}
```

**Run Loop Logic:**
```
1. Generate random interval: jobInterval ± 10% (add randomness)
2. Sleep for that interval
3. Create PrintJob with:
   - Unique jobId (e.g., "Job-" + sequenceNumber)
   - This user's ID
   - Random title from predefined list
   - Random pageCount: 1-100
   - Random colorMode: COLOR or BLACK_AND_WHITE
   - currentTime as createdTime
4. Wait on "empty" semaphore (blocks if queue full)
5. Acquire "mutex" semaphore (critical section)
   a. Enqueue job
   b. Set job state to QUEUED
   c. Record queuedTime = now
6. Release "mutex" semaphore
7. Signal "full" semaphore (one more job available)
8. Log event (job queued)
9. Broadcast event to frontend
10. Repeat
```

**Shutdown Behavior:**
- Catches InterruptedException
- Sets flag indicating thread should stop
- Completes current job submission
- Exits cleanly

**Testing Requirements:**
- Generates jobs at configured interval
- Jobs have correct properties
- Respects semaphore constraints
- Handles shutdown gracefully
- No resource leaks

---

### **3.4 PrinterThread Class (Consumer)**

**Responsibility:** Simulate printer taking jobs from queue and printing them

**Interface Specification:**
```java
public class PrinterThread extends Thread {
    /**
     * Creates a printer thread that processes jobs.
     * @param printerId unique identifier for this printer
     * @param printQueue shared queue to remove jobs from
     * @param fullSemaphore decremented on dequeue
     * @param emptySemaphore incremented on dequeue
     * @param mutexSemaphore protects queue modifications
     * @param scheduler decision-making for job selection
     * @param metricsCollector for tracking job completion
     */
    public PrinterThread(String printerId, PrintQueue printQueue,
                         CountingSemaphore fullSem, CountingSemaphore emptySem,
                         CountingSemaphore mutexSem, Scheduler scheduler,
                         MetricsCollector metricsCollector)
    
    /**
     * Main loop: wait for jobs, print them.
     */
    @Override
    public void run()
    
    /**
     * Signals thread to stop gracefully.
     * @return true if stopped, false if timeout
     */
    public boolean stopGracefully()
    
    /**
     * @return number of jobs completed by this printer
     */
    public int getJobsCompleted()
    
    /**
     * @return cumulative milliseconds spent printing
     */
    public long getTotalProcessingTime()
    
    /**
     * @return true if currently printing
     */
    public boolean isBusy()
}
```

**Run Loop Logic:**
```
1. Wait on "full" semaphore (blocks if no jobs)
2. Acquire "mutex" semaphore (critical section)
   a. Ask scheduler to select next job from queue
   b. If FCFS: dequeueFromFront()
   c. If SJF: dequeueShortestJob()
   d. If HYBRID: scheduler.selectJob(queue)
   e. Dequeue selected job from queue
3. Release "mutex" semaphore
4. Signal "empty" semaphore (one slot freed)
5. Update job state to PRINTING
6. Record startPrintTime = now
7. Log event (job started printing)
8. Broadcast event
9. Simulate printing:
   a. processingTime = job.pageCount * 100ms (or configurable)
   b. Sleep for processingTime
   c. During sleep, periodically update progress (log every 1 second)
10. Complete job:
    a. Update job state to COMPLETED
    b. Record completedTime = now
    c. Calculate metrics (waiting time, turnaround time)
    d. Report to MetricsCollector
    e. Log event (job completed)
    f. Broadcast event
11. Repeat
```

**Shutdown Behavior:**
- Completes current job before shutting down
- Doesn't start new job if shutdown signaled
- Exits cleanly

**Testing Requirements:**
- Correctly simulates print time
- Updates job state through lifecycle
- Respects semaphore constraints
- Metrics reported correctly
- Scheduler decisions honored

---

### **3.5 Scheduler Interface & Implementations**

**Responsibility:** Make decisions about which job to process next

**Interface Specification:**
```java
public interface Scheduler {
    /**
     * Given current queue state, select which job to process next.
     * This is a DECISION-MAKING interface, not execution.
     * Actual dequeuing happens in PrinterThread.
     * 
     * @param availableJobs jobs currently in queue (unmodifiable snapshot)
     * @return selected PrintJob to process next
     * @throws NoSuchElementException if queue is empty
     */
    PrintJob selectNextJob(List<PrintJob> availableJobs) 
        throws NoSuchElementException;
    
    /**
     * Human-readable name of this scheduler for logging/UI.
     * @return e.g., "FCFS", "SJF", "HYBRID"
     */
    String getName();
}
```

**3.5.1 FCFS Scheduler**
```java
public class FCFSScheduler implements Scheduler {
    /**
     * Returns FIRST job in the list (FIFO order).
     * Selection is O(1).
     */
    @Override
    public PrintJob selectNextJob(List<PrintJob> availableJobs) {
        if (availableJobs.isEmpty()) {
            throw new NoSuchElementException("Queue is empty");
        }
        return availableJobs.get(0);  // First in line
    }
    
    @Override
    public String getName() { return "FCFS"; }
}
```

**Implementation Notes:**
- Simplest scheduler
- Guarantees fairness (no job skipped)
- Can be inefficient for mixed workloads
- Serve as baseline for comparison

**Testing Requirements:**
- Always selects first job
- Handles empty queue correctly
- No side effects

---

**3.5.2 SJF Scheduler**
```java
public class SJFScheduler implements Scheduler {
    /**
     * Returns job with MINIMUM pageCount.
     * Selection is O(n) - scans entire queue.
     * 
     * @throws NoSuchElementException if queue empty (checked by interface)
     */
    @Override
    public PrintJob selectNextJob(List<PrintJob> availableJobs) {
        if (availableJobs.isEmpty()) {
            throw new NoSuchElementException("Queue is empty");
        }
        
        return availableJobs.stream()
            .min(Comparator.comparingInt(PrintJob::getPageCount))
            .orElseThrow();
    }
    
    @Override
    public String getName() { return "SJF"; }
}
```

**Implementation Notes:**
- Optimal for single-server systems (proven theory)
- Improves average waiting time
- Can cause starvation (large jobs wait indefinitely)
- Needs aging mechanism in production

**Testing Requirements:**
- Selects job with minimum pageCount
- Correct with ties (multiple same-size jobs)
- Handles single job correctly
- No side effects

---

**3.5.3 Hybrid Scheduler (SJF + Aging)**
```java
public class HybridScheduler implements Scheduler {
    private static final long AGING_THRESHOLD_MS = 30_000;  // 30 seconds
    private static final int MAX_SKIP_COUNT = 3;
    
    /**
     * Decision logic:
     * 1. Check for aged jobs (waiting > 30s) → return first aged job
     * 2. Track how many times each job was skipped → promote if > 3
     * 3. Otherwise use SJF (shortest job first)
     * 
     * This balances optimality (SJF) with fairness (aging).
     */
    @Override
    public PrintJob selectNextJob(List<PrintJob> availableJobs) {
        if (availableJobs.isEmpty()) {
            throw new NoSuchElementException("Queue is empty");
        }
        
        long now = System.currentTimeMillis();
        
        // Step 1: Check for aged jobs
        Optional<PrintJob> agedJob = availableJobs.stream()
            .filter(job -> {
                long waitTime = now - job.getQueuedTime();
                return waitTime > AGING_THRESHOLD_MS;
            })
            .findFirst();
        
        if (agedJob.isPresent()) {
            // Aged job found - promote it
            logger.info("Aging triggered for job: " + agedJob.get().getJobId());
            return agedJob.get();
        }
        
        // Step 2: Check skip count for fairness
        Optional<PrintJob> frequentlySkipped = availableJobs.stream()
            .filter(job -> job.getSkipCount() > MAX_SKIP_COUNT)
            .findFirst();
        
        if (frequentlySkipped.isPresent()) {
            logger.info("Fair selection due to skip count: " 
                + frequentlySkipped.get().getJobId());
            return frequentlySkipped.get();
        }
        
        // Step 3: Default to SJF
        return availableJobs.stream()
            .min(Comparator.comparingInt(PrintJob::getPageCount))
            .orElseThrow();
    }
    
    @Override
    public String getName() { return "HYBRID"; }
}
```

**Implementation Notes:**
- Combines optimal SJF with fairness mechanisms
- Aging threshold: 30 seconds (configurable)
- Skip count tracks how many times job bypassed
- PrintJob must track skipCount (increment on each SJF selection that skips it)

**Testing Requirements:**
- Respects aging threshold
- Respects skip count
- Falls back to SJF when no aging/skip issues
- Handles edge cases (empty queue, all jobs aged, etc.)

---

### **3.6 MetricsCollector Class**

**Responsibility:** Accumulate and calculate performance metrics

**Interface Specification:**
```java
public class MetricsCollector {
    /**
     * Creates metrics collector (synchronized internally).
     */
    public MetricsCollector()
    
    /**
     * Record job creation event.
     * Called by UserThread when job created.
     */
    public void recordJobCreated(PrintJob job)
    
    /**
     * Record job queued event.
     * Called by UserThread when job added to queue.
     */
    public void recordJobQueued(PrintJob job)
    
    /**
     * Record job printing started.
     * Called by PrinterThread when job starts printing.
     */
    public void recordJobPrintingStarted(PrintJob job, String printerId)
    
    /**
     * Record job completion.
     * Called by PrinterThread when job finishes.
     * Calculates waiting time and turnaround time.
     */
    public void recordJobCompleted(PrintJob job)
    
    /**
     * Returns current aggregated metrics snapshot.
     * Thread-safe: returns copy of current state.
     * @return metrics at this moment in time
     */
    public PerformanceMetrics getCurrentMetrics()
    
    /**
     * Returns all completed jobs (for export/analysis).
     * @return unmodifiable list of finished jobs
     */
    public List<PrintJob> getCompletedJobs()
    
    /**
     * Resets all metrics (called on simulator reset).
     */
    public void reset()
}
```

**Metrics to Calculate:**

1. **Average Waiting Time**
   - Definition: avg(job.queuedTime - job.createdTime) for all completed jobs
   - Formula: sum(waiting times) / number of jobs

2. **Average Turnaround Time**
   - Definition: avg(job.completedTime - job.createdTime) for all completed jobs
   - Formula: sum(turnaround times) / number of jobs

3. **CPU Utilization**
   - Definition: total time printers were busy / total simulation time
   - Formula: sum(printer.processingTime) / elapsedSimulationTime × 100%

4. **Queue Utilization**
   - Definition: average occupancy of queue / queue capacity
   - Formula: sum(occupancy at each moment) / total moments

5. **Fairness Index (Jain's Index)**
   - Definition: How fairly jobs are treated regardless of size
   - Formula: (Σ waiting_time_i)² / (n × Σ (waiting_time_i)²)
   - Range: 0-1 where 1 = perfect fairness

6. **Max/Min Waiting Times**
   - Maximum: longest any job waited
   - Minimum: shortest wait (usually 0 for first job)

**Testing Requirements:**
- Metrics calculated correctly (manually verify formula)
- Thread-safe access (multiple printers reading simultaneously)
- Reset properly clears all data
- Handles edge cases (no jobs completed yet, etc.)

---

### **3.7 PrintServerSimulator Class (Orchestrator)**

**Responsibility:** Initialize, manage, and coordinate entire simulation

**Interface Specification:**
```java
public class PrintServerSimulator {
    /**
     * Creates simulator with default configuration.
     */
    public PrintServerSimulator()
    
    /**
     * Initializes and starts simulation with given config.
     * Spawns all user and printer threads.
     * Starts scheduler thread.
     * @param config simulation configuration
     * @throws IllegalStateException if already running
     */
    public void start(SimulationConfig config) throws IllegalStateException
    
    /**
     * Pauses simulation (all threads suspended).
     * Semaphore state preserved.
     * Queue state preserved.
     * Can call resume() to continue.
     */
    public void pause()
    
    /**
     * Resumes from paused state.
     * All threads resume from where they paused.
     */
    public void resume()
    
    /**
     * Stops simulation and cleans up all resources.
     * Gracefully shuts down all threads (waits up to 10 seconds each).
     * Clears queue and resets semaphores.
     * Metrics can be retrieved after stop.
     */
    public void stop()
    
    /**
     * Resets to initial state.
     * Stops simulation if running.
     * Clears all jobs and metrics.
     * Can call start() again after reset.
     */
    public void reset()
    
    /**
     * Changes scheduling algorithm at runtime.
     * Takes effect on next scheduler decision.
     * @param algorithmName "FCFS", "SJF", or "HYBRID"
     * @throws IllegalArgumentException if invalid name
     */
    public void setScheduler(String algorithmName)
    
    /**
     * Returns current simulation state snapshot.
     * Thread-safe: returns copy of state.
     * @return SimulationState with all current data
     */
    public SimulationState getSimulationState()
    
    /**
     * @return current simulation time (milliseconds elapsed)
     */
    public long getCurrentTime()
    
    /**
     * @return true if simulation is currently running
     */
    public boolean isRunning()
    
    /**
     * @return true if simulation is paused
     */
    public boolean isPaused()
}
```

**Initialization Sequence:**
```
1. Validate configuration
2. Create shared data structures:
   - PrintQueue(capacity)
   - CountingSemaphore("empty", capacity)
   - CountingSemaphore("full", 0)
   - CountingSemaphore("mutex", 1)
   - MetricsCollector
3. Create and start user threads (configurable count)
4. Create and start printer threads (configurable count)
5. Set simulation state to RUNNING
6. Start broadcasting timer (every 100ms: get state, broadcast)
```

**Shutdown Sequence:**
```
1. Set state to STOPPING
2. Stop accepting new jobs (signal for user threads to exit)
3. Wait for all user threads to stop (timeout: 5 sec each)
4. Wait for all printer threads to finish current jobs (timeout: 60 sec each)
5. Clear queue
6. Reset semaphores
7. Set state to STOPPED
```

**Testing Requirements:**
- Lifecycle: start → pause → resume → stop → reset
- Thread creation/destruction
- Graceful shutdown (no orphaned threads)
- State transitions valid
- Broadcasting continues while running
- Metrics accumulated correctly

---

### **3.8 SimulationState Data Model**

**Responsibility:** Hold snapshot of current simulation state for frontend

**Interface Specification:**
```java
public class SimulationState {
    // Identification
    private long currentTime;           // Simulation elapsed time (ms)
    private SimulationStatus status;    // RUNNING, PAUSED, STOPPED
    
    // Queue State
    private List<PrintJob> queueSnapshot;    // Jobs in queue
    private int queueCapacity;
    
    // Printer State
    private List<PrinterStatus> printers;    // Each printer's state
    
    // Semaphore State
    private SemaphoreState semaphores;  // empty, full, mutex values
    
    // Metrics
    private PerformanceMetrics metrics;
    
    // Recent Events
    private List<SimulationEvent> recentEvents;  // Last 50 events
    
    // Configuration
    private String currentScheduler;    // "FCFS", "SJF", "HYBRID"
    private double simulationSpeed;     // Speed multiplier
}

public enum SimulationStatus {
    RUNNING,    // Actively simulating
    PAUSED,     // Paused but can resume
    STOPPED,    // Stopped (can reset and start again)
    ERROR       // Fatal error occurred
}

public class PrinterStatus {
    private String printerId;
    private PrinterActivityStatus status;  // IDLE, BUSY, ERROR
    private String currentJobId;            // null if IDLE
    private int jobsCompleted;
    private int progressPercent;            // 0-100 if BUSY
    private long timeOnCurrentJob;          // ms
}

public class SemaphoreState {
    private int emptyValue;          // Number of free queue slots
    private int fullValue;           // Number of jobs in queue
    private int mutexValue;          // 1 = unlocked, 0 = locked
    private List<String> emptyWaitList;  // Thread IDs waiting
    private List<String> fullWaitList;
    private List<String> mutexWaitList;
}

public class SimulationEvent {
    private long timestamp;          // Simulation time when event occurred
    private EventType type;          // CREATED, QUEUED, PRINTING, COMPLETED
    private String jobId;
    private String userId;            // For CREATED events
    private String printerId;         // For PRINTING events
    private String description;       // Human-readable message
}

public enum EventType {
    CREATED,     // Job created by user
    QUEUED,      // Job added to queue
    PRINTING,    // Printer started printing
    COMPLETED,   // Job finished printing
    STALLED      // Job waiting too long (aging detected)
}
```

**JSON Serialization:**
- Must serialize to JSON without errors
- All timestamps in milliseconds
- All IDs as strings
- Enums as string names (uppercase)
- Dates can be ISO-8601 if needed
- NO circular references

**Testing Requirements:**
- Serializes to valid JSON
- Deserializes back to identical state
- No null pointer exceptions
- Handles empty collections correctly

---

## **Part 4: REST API Specification**

### **4.1 API Conventions**

**Base URL:** `http://localhost:8080/api`

**HTTP Methods:**
- `POST` for state-changing operations (start, pause, reset)
- `GET` for read-only operations (metrics, state)
- `PUT` for updates (change configuration)

**Response Format:**
```json
{
  "success": true,
  "timestamp": 1234567890,
  "data": { /* Response payload */ },
  "error": null  // null if success, error object if failed
}
```

**Error Response:**
```json
{
  "success": false,
  "timestamp": 1234567890,
  "data": null,
  "error": {
    "code": "SIMULATION_ALREADY_RUNNING",
    "message": "Cannot start: simulation already running",
    "details": { /* Additional context */ }
  }
}
```

**HTTP Status Codes:**
- 200: Success (GET, idempotent operations)
- 201: Created (if applicable)
- 202: Accepted (long-running operations)
- 400: Bad request (invalid input)
- 409: Conflict (illegal state transition)
- 500: Server error (unexpected exception)

---

### **4.2 REST Endpoints Specification**

#### **Endpoint 1: Start Simulation**

```
POST /api/simulation/start
Content-Type: application/json

Request Body:
{
  "numUsers": 3,
  "numPrinters": 2,
  "queueCapacity": 10,
  "jobInterval": 3000,          // milliseconds
  "algorithm": "HYBRID",        // "FCFS", "SJF", "HYBRID"
  "colorJobRatio": 0.5,         // 0.0-1.0
  "smallJobPercentage": 0.3     // Job size distribution
}

Response (202 Accepted):
{
  "success": true,
  "data": {
    "simulationId": "sim-20260601-120000",
    "status": "RUNNING",
    "startTime": 1234567890
  }
}

Errors:
- 409 if already running: SIMULATION_ALREADY_RUNNING
- 400 if config invalid: INVALID_CONFIGURATION
  - numUsers must be 1-20
  - numPrinters must be 1-20
  - queueCapacity must be 5-100
  - jobInterval must be 100-10000ms
  - algorithm must be FCFS, SJF, or HYBRID
```

**Business Logic:**
1. Validate all configuration parameters
2. Check if simulation already running (conflict error)
3. Create PrintServerSimulator with config
4. Call simulator.start()
5. Start WebSocket broadcasting thread
6. Return success with simulationId
7. Log START event

---

#### **Endpoint 2: Pause Simulation**

```
POST /api/simulation/pause

Response (200 OK):
{
  "success": true,
  "data": {
    "status": "PAUSED",
    "pausedAt": 1234567890
  }
}

Errors:
- 409 if not running: SIMULATION_NOT_RUNNING
- 409 if already paused: SIMULATION_ALREADY_PAUSED
```

**Business Logic:**
1. Check if simulation is running
2. Call simulator.pause()
3. Return current state
4. Stop WebSocket broadcasts (or reduce frequency)

---

#### **Endpoint 3: Resume Simulation**

```
POST /api/simulation/resume

Response (200 OK):
{
  "success": true,
  "data": {
    "status": "RUNNING",
    "resumedAt": 1234567890
  }
}

Errors:
- 409 if not paused: SIMULATION_NOT_PAUSED
- 409 if not exist: SIMULATION_NOT_FOUND
```

**Business Logic:**
1. Check if simulation is paused
2. Call simulator.resume()
3. Resume WebSocket broadcasts
4. Return current state

---

#### **Endpoint 4: Reset Simulation**

```
POST /api/simulation/reset

Response (200 OK):
{
  "success": true,
  "data": {
    "status": "STOPPED",
    "resetAt": 1234567890
  }
}

Errors:
- None (idempotent - always succeeds)
```

**Business Logic:**
1. Call simulator.reset()
2. Clear metrics
3. Clear events
4. Return initial state
5. Log RESET event

---

#### **Endpoint 5: Configure Simulation**

```
PUT /api/simulation/configure
Content-Type: application/json

Request Body:
{
  "algorithm": "SJF",
  "jobInterval": 2000,
  "simulationSpeed": 2.0    // Speed multiplier (0.5x, 1x, 2x, etc.)
}

Response (200 OK):
{
  "success": true,
  "data": {
    "appliedChanges": {
      "algorithm": "SJF",
      "jobInterval": 2000,
      "simulationSpeed": 2.0
    }
  }
}

Errors:
- 400 if invalid values: INVALID_CONFIGURATION
- 409 if invalid state transition: INVALID_STATE
```

**Business Logic:**
1. Validate new configuration
2. Check if simulation running (some changes only allowed when paused)
3. Apply changes to simulator
4. Update thread behaviors (interval, speed multiplier)
5. Log CONFIGURATION_CHANGED event

---

#### **Endpoint 6: Get Current State**

```
GET /api/simulation/state

Response (200 OK):
{
  "success": true,
  "data": {
    "currentTime": 45000,
    "status": "RUNNING",
    "queue": [
      {
        "jobId": "Job-23",
        "userId": "User-1",
        "pageCount": 10,
        "state": "QUEUED",
        "createdTime": 42000,
        "queuedTime": 43000
      }
    ],
    "printers": [
      {
        "printerId": "Printer-1",
        "status": "BUSY",
        "currentJobId": "Job-22",
        "progressPercent": 60,
        "jobsCompleted": 11
      }
    ],
    "semaphores": {
      "empty": 2,
      "full": 8,
      "mutex": 1
    },
    "metrics": {
      "totalJobsSubmitted": 23,
      "jobsCompleted": 18,
      "avgWaitingTime": 12.5,
      "avgTurnaroundTime": 24.3,
      "cpuUtilization": 87.0,
      "fairnessIndex": 0.82
    },
    "recentEvents": [ /* array of events */ ]
  }
}

Errors:
- None (always returns current state)
```

**Business Logic:**
1. Call simulator.getSimulationState()
2. Serialize to JSON
3. Return to client

**Caching:** No caching. Always return fresh state.

---

#### **Endpoint 7: Get Metrics**

```
GET /api/simulation/metrics
Query Parameters:
  - groupBy: "none", "printer", "scheduler" (optional, default: "none")
  - timeRange: "all", "last-hour", "last-minute" (optional, default: "all")

Response (200 OK):
{
  "success": true,
  "data": {
    "summary": {
      "totalJobs": 23,
      "completedJobs": 18,
      "avgWaitingTime": 12.5,
      "avgTurnaroundTime": 24.3,
      "maxWaitingTime": 45.2,
      "minWaitingTime": 0.1,
      "stdDeviation": 8.7,
      "cpuUtilization": 87.0,
      "queueUtilization": 65.0,
      "fairnessIndex": 0.82
    },
    "byPrinter": [
      {
        "printerId": "Printer-1",
        "jobsProcessed": 12,
        "avgProcessingTime": 8.5,
        "utilization": 85.0
      }
    ]
  }
}
```

---

#### **Endpoint 8: Export Data**

```
GET /api/simulation/export
Query Parameters:
  - format: "csv" or "json" (required)
  - type: "jobs" or "metrics" (required)

Response (200 OK - with appropriate Content-Type):

For CSV:
Content-Type: text/csv
Content-Disposition: attachment; filename="jobs.csv"

jobId,userId,pageCount,createdTime,queuedTime,startTime,completedTime,waitingTime,turnaroundTime
Job-1,User-1,10,0,100,200,1200,100,1200

For JSON:
Content-Type: application/json

{
  "success": true,
  "data": {
    "jobs": [ /* array of job objects */ ],
    "exportedAt": 1234567890,
    "totalRecords": 1000
  }
}
```

---

#### **Endpoint 9: Health Check**

```
GET /api/health

Response (200 OK):
{
  "success": true,
  "data": {
    "status": "UP",
    "timestamp": 1234567890,
    "components": {
      "simulator": "UP",
      "websocket": "UP",
      "database": "UP"  // if using
    }
  }
}

Response (503 Service Unavailable):
{
  "success": false,
  "data": {
    "status": "DOWN",
    "timestamp": 1234567890
  },
  "error": {
    "code": "DATABASE_UNAVAILABLE",
    "message": "Cannot connect to database"
  }
}
```

---

## **Part 5: WebSocket Specification**

### **5.1 Connection Protocol**

**WebSocket Endpoint:** `ws://localhost:8080/ws/simulation`

**Connection Request:**
```
GET /ws/simulation HTTP/1.1
Host: localhost:8080
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Key: [random key]
Sec-WebSocket-Version: 13
```

**Connection Acknowledgment (server sends immediately):**
```json
{
  "type": "CONNECTED",
  "timestamp": 1234567890,
  "clientId": "client-uuid-12345",
  "message": "Connected to simulation broker"
}
```

---

### **5.2 Message Types**

**Type 1: STATE_UPDATE (periodic, every 100ms)**
```json
{
  "type": "STATE_UPDATE",
  "timestamp": 1234567890,
  "data": {
    // Full SimulationState JSON
    "currentTime": 45000,
    "status": "RUNNING",
    "queue": [ /* jobs */ ],
    "printers": [ /* printers */ ],
    "semaphores": { /* semaphore values */ },
    "metrics": { /* metrics */ }
  }
}
```

**Type 2: EVENT (on significant events)**
```json
{
  "type": "EVENT",
  "timestamp": 1234567890,
  "event": {
    "eventType": "COMPLETED",
    "jobId": "Job-20",
    "printerId": "Printer-1",
    "description": "Job-20 completed on Printer-1",
    "details": {
      "waitingTime": 10.5,
      "turnaroundTime": 22.3,
      "pageCount": 5
    }
  }
}
```

**Type 3: ERROR**
```json
{
  "type": "ERROR",
  "timestamp": 1234567890,
  "error": {
    "code": "INTERNAL_ERROR",
    "message": "Unexpected error occurred",
    "details": "Full stack trace (debug mode only)"
  }
}
```

**Type 4: HEARTBEAT (every 30 seconds, if no updates)**
```json
{
  "type": "HEARTBEAT",
  "timestamp": 1234567890
}
```

---

### **5.3 Broadcasting Strategy**

**Frequency:** Every 100ms (10 times per second)

**Optimization:**
- Only send if state actually changed (delta updates)
- Batch multiple events into single message
- Limit queue snapshot to recent jobs (not all)
- Limit events to last 50 (for timeline)

**Concurrency:**
- WebSocket broadcaster runs on dedicated thread
- Reads current state atomically
- Serializes to JSON
- Sends to all connected clients
- Handles client disconnections gracefully

---

## **Part 6: Logging & Monitoring**

### **6.1 Logging Strategy**

**Format:** JSON structured logging with SLF4J + Logback

**Log Levels:**
- **ERROR:** Critical issues (exceptions, state inconsistencies)
- **WARN:** Concerning conditions (high waiting times, potential starvation)
- **INFO:** Significant events (simulation start/stop, metrics milestones)
- **DEBUG:** Detailed flow (thread decisions, semaphore operations)
- **TRACE:** Very detailed (every job state change) - DISABLED in production

**Log Message Format:**
```
[TIMESTAMP] [LEVEL] [CLASS] [THREAD] - Message
{
  "event": "job_completed",
  "jobId": "Job-20",
  "printerId": "Printer-1",
  "waitingTime": 10.5,
  "turnaroundTime": 22.3,
  "timestamp": 1234567890
}
```

**Key Events to Log:**

| Event | Level | Info to Include |
|-------|-------|-----------------|
| Simulation started | INFO | numUsers, numPrinters, algorithm |
| Job created | DEBUG | jobId, pageCount, colorMode |
| Job queued | DEBUG | jobId, waitTime (0), queueSize |
| Printing started | INFO | jobId, printerId |
| Job completed | INFO | jobId, waitingTime, turnaroundTime |
| Aging triggered | WARN | jobId, waitTime (exceeded) |
| Starvation detected | WARN | jobId, waitTime (critical) |
| Exception | ERROR | Full stack trace, context |

---

### **6.2 Metrics for Monitoring**

**Prometheus-style metrics (if monitoring enabled):**

```
# Simulation metrics
simulation_jobs_submitted_total 23
simulation_jobs_completed_total 18
simulation_current_queue_size 5
simulation_avg_waiting_time_ms 12500
simulation_avg_turnaround_time_ms 24300
simulation_cpu_utilization_percent 87.0

# Printer metrics (per printer)
printer_jobs_processed_total{printer_id="Printer-1"} 12
printer_utilization_percent{printer_id="Printer-1"} 85.0

# WebSocket metrics
websocket_connections_active 3
websocket_messages_sent_total 450
websocket_message_latency_ms 45

# JVM metrics
jvm_memory_used_bytes 512000000
jvm_threads_live 25
```

---

## **Part 7: Error Handling & Validation**

### **7.1 Input Validation**

**For every API request:**

1. **Check null/missing fields**
   - All required fields present
   - No null values for primitives

2. **Range validation**
   - numUsers: 1-20
   - numPrinters: 1-20
   - queueCapacity: 5-100
   - jobInterval: 100-10000ms

3. **Enum validation**
   - algorithm: FCFS | SJF | HYBRID
   - status: RUNNING | PAUSED | STOPPED

4. **Type validation**
   - All numbers are actually numbers
   - All booleans are actually booleans

5. **Business logic validation**
   - Can't start if already running
   - Can't pause if not running
   - Can't resume if not paused

**Validation pattern:**
```java
if (!isValid(request)) {
    throw new ValidationException("Invalid request", details);
}
```

---

### **7.2 Exception Handling**

**Principle:** Never let exceptions escape to client without proper formatting

**Exception Hierarchy:**
```
Exception
├── SimulationException (application-specific)
│   ├── SimulationNotRunningException
│   ├── SimulationAlreadyRunningException
│   ├── InvalidConfigurationException
│   └── SchedulerException
├── ValidationException (input validation)
├── InternalServerError (unexpected)
└── (No bare RuntimeException!)
```

**Global Exception Handler (Spring @ControllerAdvice):**
- Catches all exceptions
- Formats as proper JSON error response
- Logs with full context
- Returns appropriate HTTP status code
- Never exposes stack traces to client (except debug mode)

---

## **Part 8: Testing Requirements**

### **8.1 Unit Testing**

**Coverage Target:** 80%+ backend code

**Test Categories:**

1. **Core Data Structures**
   - PrintQueue: enqueue/dequeue operations, circular wraparound
   - CountingSemaphore: wait/signal with concurrent access
   - PrintJob: lifecycle state changes
   - Printer: state management

2. **Scheduling Algorithms**
   - FCFS: always selects first job
   - SJF: always selects minimum pageCount
   - Hybrid: respects aging and skip count
   - All handle empty queue correctly

3. **Threading**
   - UserThread: generates jobs at correct interval
   - PrinterThread: processes jobs correctly
   - No race conditions (stress test with 10+ threads)
   - No deadlocks (10K+ operations)
   - Graceful shutdown

4. **Metrics**
   - Waiting time calculated correctly
   - Turnaround time calculated correctly
   - Fairness index formula correct
   - CPU utilization accurate

5. **REST API**
   - All endpoints return 200 for valid requests
   - All endpoints return 400 for invalid input
   - All endpoints return 409 for invalid state
   - Response format matches specification

**Test Tools:**
- JUnit 5 for structure
- Mockito for mocking dependencies
- AssertJ for fluent assertions
- Awaitility for async testing

---

### **8.2 Integration Testing**

**End-to-End Flows:**

1. Start → Verify simulation initializes
2. Start → Pause → Resume → Verify state preserved
3. Start → Configure (change algorithm) → Verify new algorithm used
4. Start → Run 30 seconds → Stop → Verify metrics calculated
5. Full producer-consumer cycle → Verify no deadlocks/race conditions

**Concurrency Testing:**
- Multiple threads accessing queue simultaneously
- Multiple printers competing for jobs
- Stress test: 1,000,000 jobs with 100 concurrent operations
- Verify system remains consistent

---

### **8.3 Performance Testing**

**Baselines:**

| Metric | Target | Acceptable |
|--------|--------|-----------|
| REST endpoint latency | < 50ms | < 200ms |
| WebSocket update latency | < 100ms | < 500ms |
| Metrics calculation time | < 100ms | < 1s |
| Memory usage (100K jobs) | < 500MB | < 1GB |
| Job processing throughput | 1000+ jobs/s | 500+ jobs/s |

**Tools:**
- JMH for microbenchmarks
- Load testing with multiple clients
- Memory profiling with JProfiler/YourKit

---

## **Part 9: Production Readiness Checklist**

### **Code Quality**
- ✅ No warnings from Maven compile
- ✅ SonarQube quality gate passes (A rating)
- ✅ Code review approved (2 reviewers minimum)
- ✅ Checkstyle rules enforced
- ✅ No dead code
- ✅ Proper error handling throughout

### **Testing**
- ✅ Unit tests: 80%+ coverage
- ✅ Integration tests: all critical paths
- ✅ Performance tests: baseline met
- ✅ Load tests: verified stability at scale
- ✅ All tests passing on CI/CD

### **Documentation**
- ✅ JavaDoc for all public APIs
- ✅ README with setup instructions
- ✅ Architecture documentation
- ✅ API specification (this document)
- ✅ Troubleshooting guide

### **Operations**
- ✅ Health check endpoint implemented
- ✅ Structured logging configured
- ✅ Monitoring metrics exposed
- ✅ Graceful shutdown implemented
- ✅ No resource leaks in tests

### **Security**
- ✅ Input validation on all endpoints
- ✅ No SQL injection (if using database)
- ✅ No XXE attacks (XML parsing)
- ✅ CORS configured appropriately
- ✅ Rate limiting considered (optional)

---

## **Part 10: Development Workflow**

### **Before You Start:**
1. Read this entire document
2. Set up development environment
3. Clone repository
4. Create feature branch: `feature/dev3-api-websocket`
5. Set up IDE (IntelliJ IDEA recommended for Spring Boot)

### **While Developing:**
1. Write tests FIRST (TDD approach)
2. Implement code to pass tests
3. Run full test suite locally before pushing
4. Follow logging and error handling patterns
5. Use meaningful commit messages

### **Before Committing:**
```bash
# Compile without warnings
mvn clean compile

# Run all tests
mvn test

# Check coverage
mvn jacoco:report

# Run static analysis
mvn sonar:sonar

# Review code locally
# Then commit and push
```

### **Code Review Checklist:**
- Does it follow this specification?
- Are error cases handled?
- Is logging appropriate?
- Are tests included?
- Does it have JavaDoc?
- Is there a performance concern?
- Could another developer understand it?

---

## **Part 11: Assumptions & Constraints**

### **Assumptions**
1. Java 11+ available in deployment environment
2. Single-machine deployment (no distributed system concerns)
3. Simulation runs in-memory (no persistence required)
4. Frontend is React (single trusted consumer)
5. All users are developer/tester (not adversarial)

### **Constraints**
1. Queue capacity fixed (not dynamically resizable)
2. No persistence of historical simulations (unless using optional database)
3. Single simulator instance per backend (no clustering)
4. WebSocket is best-effort (no guaranteed delivery, but that's fine for UI)
5. Simulation time is wall-clock time (no "virtual time")

---

## **Part 12: Escalation & Support**

**If you encounter issues:**

1. **Thread-related issues?** → Dev 2 (threading expert)
2. **API/WebSocket issues?** → Dev 3 (your component)
3. **Data structure issues?** → Dev 1 (core models)
4. **Performance issues?** → Check logging first, then escalate
5. **Design questions?** → Senior engineer review

**Communication:**
- Slack: #print-scheduler-dev
- Daily standup: 9 AM
- Weekly sync: Friday 2 PM

---

## **Conclusion**

This master prompt is your specification, your guide, and your quality bar. Follow it rigorously. When in doubt, it is the source of truth.

**Remember:**
- Correctness > Cleverness
- Simple > Complex
- Tested > Untested
- Documented > Mysterious

Build production-quality code. Your future self will thank you.

---

**Document Version:** 1.0
**Last Updated:** 2026
**Next Review:** After first week of development

