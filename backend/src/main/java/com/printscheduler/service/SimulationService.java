package com.printscheduler.service;

import com.printscheduler.api.dto.ConfigUpdateRequest;
import com.printscheduler.model.SimulationConfig;
import com.printscheduler.model.SimulationSnapshot;

public interface SimulationService {

    void start(SimulationConfig config);

    void pause();

    void resume();

    void stop();

    void reset();

    void configure(ConfigUpdateRequest request);

    SimulationSnapshot getState();

    boolean isRunning();

    boolean isPaused();

    String exportJobsCsv();
}
