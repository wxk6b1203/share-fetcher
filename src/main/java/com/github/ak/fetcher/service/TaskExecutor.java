package com.github.ak.fetcher.service;

import com.github.ak.fetcher.entity.Task;
import com.github.ak.fetcher.mapper.TaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TaskExecutor {
    private static final Logger log = LoggerFactory.getLogger(TaskExecutor.class);

    private final TaskMapper taskMapper;
    private final Map<String, TaskHandler> handlers = new ConcurrentHashMap<>();

    public TaskExecutor(TaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    public void registerHandler(String taskType, TaskHandler handler) {
        handlers.put(taskType, handler);
    }

    public Mono<Long> submitTask(String taskType, String inputParams) {
        // 创建任务记录
        Task task = new Task();
        task.setTaskType(taskType);
        task.setInputParams(inputParams);
        task.setStatus("PENDING");
        task.setCreatedAt(LocalDateTime.now());
        taskMapper.insert(task);

        Long taskId = task.getId();
        log.info("Task submitted: type={}, id={}", taskType, taskId);

        // 使用虚拟线程执行任务
        executeTaskAsync(taskId, taskType, inputParams);

        return Mono.just(taskId);
    }

    public void executeTaskAsync(Long taskId, String taskType, String inputParams) {
        Thread.startVirtualThread(() -> {
            TaskHandler handler = handlers.get(taskType);
            if (handler == null) {
                log.error("No handler found for task type: {}", taskType);
                updateTaskStatus(taskId, "FAILED", "No handler for task type: " + taskType);
                return;
            }

            try {
                updateTaskStatus(taskId, "RUNNING", null);
                String result = handler.execute(inputParams);
                updateTaskStatus(taskId, "COMPLETED", result);
                log.info("Task completed: id={}, result={}", taskId, result);
            } catch (Exception e) {
                log.error("Task failed: id={}", taskId, e);
                updateTaskStatus(taskId, "FAILED", e.getMessage());
            }
        });
    }

    private void updateTaskStatus(Long taskId, String status, String result) {
        // 同步更新任务状态
        Task task = taskMapper.selectById(taskId);
        if (task != null) {
            task.setStatus(status);
            if (status.equals("RUNNING")) {
                task.setStartedAt(LocalDateTime.now());
            } else if (status.equals("COMPLETED") || status.equals("FAILED")) {
                task.setFinishedAt(LocalDateTime.now());
            }
            if (result != null) {
                task.setResult(result);
            }
            taskMapper.updateById(task);
        }
    }

    public Task getTaskStatus(Long taskId) {
        return taskMapper.selectById(taskId);
    }

    public interface TaskHandler {
        String execute(String inputParams);
    }
}
