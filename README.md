# OS Smart Print Server Simulator

A full-stack, real-time visualization dashboard demonstrating core Operating System concepts including Producer-Consumer synchronization, counting semaphores, multithreading, and CPU/Job scheduling algorithms (FCFS, SJF, and Hybrid Aging).

## 🛠️ Software Dependencies

To compile and run this project, you only need two pieces of software installed on your machine:

1. **Java 17 (or newer):** Required to compile and run the backend OS kernel logic.
   * *Verify installation:* Run `java -version` in your terminal/command prompt.
2. **Node.js (v18 or newer):** Required to compile the React frontend during the build step.
   * *Verify installation:* Run `node -v` and `npm -v` in your terminal/command prompt.

*(Note: Apache Maven is included inside the project folder, so you do not need to install Maven manually).*

## 🚀 How to Compile and Run

The project is designed to be easily built into a single, cross-platform executable file. We have provided automated scripts for both Windows and Linux/macOS.

### For Windows Users

1. **Compile the Project (Run Once):**
   Double-click the `build.bat` file, or run it from the Command Prompt:
   ```cmd
   build.bat
   ```
   *This will download Node modules, build the React frontend, package the Spring Boot backend, and generate a single `smart-print-server.jar` file. This takes about 60 seconds.*

2. **Run the Project:**
   Double-click the `run.bat` file, or run it from the Command Prompt:
   ```cmd
   run.bat
   ```
   *This will start the server and automatically open your default web browser to the dashboard.*

### For Linux / macOS Users

1. **Compile the Project (Run Once):**
   Open your terminal and run the build script:
   ```bash
   ./build.sh
   ```
   *If you get a permission denied error, make the script executable first: `chmod +x build.sh`*

2. **Run the Project:**
   Start the compiled application by running:
   ```bash
   ./run.sh
   ```
   *If you get a permission denied error, make the script executable first: `chmod +x run.sh`*

## 📖 Usage Instructions

1. **Launch the Application:** Once you execute the run script, the dashboard will automatically open in your browser at `http://localhost:8080`.
2. **Configure the OS Parameters:** On the left control panel, you can adjust the system simulation constraints:
   * **Users (Producers):** Number of concurrent threads generating print jobs.
   * **Printers (Consumers):** Number of hardware threads processing pages.
   * **Queue Capacity:** The strict size of the Bounded Buffer (Counting Semaphore permits).
   * **Algorithm:** Choose between First-Come First-Served (FCFS), Shortest Job First (SJF), or the custom HYBRID (SJF + Aging) scheduler.
3. **Start the Simulation:** Click **Start Simulation**.
4. **Observe OS Concepts in Real-Time:**
   * Watch jobs travel through the **Swim-Lane** from Submission -> Queue -> Printing.
   * Monitor the **Semaphore Deep Dive** to see how the OS blocks user threads when permits run out (queue is full) and wakes them when hardware frees up space.
   * View the live **OS Problem Solvers** panel to see the exact number of race conditions prevented by the system's mutual exclusion locks.
5. **Stop / Reset:** Use the Stop button to pause the simulation, and Reset to clear the queue and start over with new configurations.
6. **Exit:** To shut down the server, simply close the terminal window where the script is running, or press `Ctrl+C`.
