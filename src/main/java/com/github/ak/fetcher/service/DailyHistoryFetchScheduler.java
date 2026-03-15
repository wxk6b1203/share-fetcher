package com.github.ak.fetcher.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 定时任务：每天4点拉取当天所有股票的历史行情
 */
@Component
public class DailyHistoryFetchScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailyHistoryFetchScheduler.class);

    private final TaskExecutor taskExecutor;

    public DailyHistoryFetchScheduler(TaskExecutor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    /**
     * 每天凌晨4点执行
     * 拉取当天所有股票的历史行情数据
     * 使用现有的任务系统管理执行
     */
    @Scheduled(cron = "0 0 16 * * ?")
    public void fetchDailyHistory() {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        log.info("定时任务开始: 拉取 {} 的历史行情", today);

        try {
            // 构建任务参数：只拉取当天数据
            String inputParams = String.format(
                    "{\"startDate\": \"%s\", \"endDate\": \"%s\", \"adjust\": \"qfq\"}",
                    today, today
            );

            // 通过现有的任务系统提交任务
            // HISTORY_DATA_FETCH 任务类型已经在 TaskConfig 中注册
            // 任务会异步执行，由 TaskExecutor 管理执行状态和进度
            taskExecutor.submitTask("HISTORY_DATA_FETCH", inputParams)
                    .subscribe(taskId -> log.info("定时任务已提交, taskId: {}", taskId));

            log.info("定时任务已提交: 拉取 {} 的历史行情", today);

        } catch (Exception e) {
            log.error("定时任务提交失败: 拉取历史行情", e);
        }
    }
}
