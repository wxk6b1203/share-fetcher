package com.github.ak.fetcher.config;

import com.github.ak.fetcher.service.HistoryDataFetchTask;
import com.github.ak.fetcher.service.TaskExecutor;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TaskConfig {

    private final TaskExecutor taskExecutor;
    private final HistoryDataFetchTask historyDataFetchTask;

    public TaskConfig(TaskExecutor taskExecutor, HistoryDataFetchTask historyDataFetchTask) {
        this.taskExecutor = taskExecutor;
        this.historyDataFetchTask = historyDataFetchTask;
    }

    @PostConstruct
    public void registerHandlers() {
        taskExecutor.registerHandler("HISTORY_DATA_FETCH", historyDataFetchTask::execute);
    }
}
