package com.github.ak.fetcher.config;

import com.github.ak.fetcher.service.FundamentalFetchTask;
import com.github.ak.fetcher.service.HistoryDataFetchTask;
import com.github.ak.fetcher.service.SpotFetchTask;
import com.github.ak.fetcher.service.TaskExecutor;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TaskConfig {

    private final TaskExecutor taskExecutor;
    private final HistoryDataFetchTask historyDataFetchTask;
    private final FundamentalFetchTask fundamentalFetchTask;
    private final SpotFetchTask spotFetchTask;

    public TaskConfig(TaskExecutor taskExecutor,
                     HistoryDataFetchTask historyDataFetchTask,
                     FundamentalFetchTask fundamentalFetchTask,
                     SpotFetchTask spotFetchTask) {
        this.taskExecutor = taskExecutor;
        this.historyDataFetchTask = historyDataFetchTask;
        this.fundamentalFetchTask = fundamentalFetchTask;
        this.spotFetchTask = spotFetchTask;
    }

    @PostConstruct
    public void registerHandlers() {
        taskExecutor.registerHandler("HISTORY_DATA_FETCH", historyDataFetchTask::execute);
        taskExecutor.registerHandler("FUNDAMENTAL_FETCH", fundamentalFetchTask::execute);
        taskExecutor.registerHandler("SPOT_FETCH", spotFetchTask::execute);
    }
}
