package com.github.ak.fetcher.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.ak.fetcher.entity.Task;
import com.github.ak.fetcher.mapper.TaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class TaskExecutor {
    private static final Logger log = LoggerFactory.getLogger(TaskExecutor.class);

    private final TaskMapper taskMapper;
    private final Map<String, TaskHandler> handlers = new ConcurrentHashMap<>();
    // 存储运行中的任务线程
    private final Map<Long, AtomicBoolean> runningTasks = new ConcurrentHashMap<>();
    // 存储运行中的任务处理器实例（用于取消）
    private final Map<Long, TaskHandler> runningTaskHandlers = new ConcurrentHashMap<>();

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

    // 使用 ThreadLocal 存储当前任务 ID，供 handler 更新进度使用
    private static final ThreadLocal<Long> currentTaskId = new ThreadLocal<>();

    public void executeTaskAsync(Long taskId, String taskType, String inputParams) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        runningTasks.put(taskId, cancelled);
        currentTaskId.set(taskId);

        Thread.startVirtualThread(() -> {
            TaskHandler handler = handlers.get(taskType);
            if (handler == null) {
                log.error("No handler found for task type: {}", taskType);
                updateTaskStatus(taskId, "FAILED", "No handler for task type: " + taskType);
                runningTasks.remove(taskId);
                currentTaskId.remove();
                return;
            }

            // 保存 handler 实例用于取消
            runningTaskHandlers.put(taskId, handler);

            try {
                updateTaskStatus(taskId, "RUNNING", null);
                String result = handler.execute(inputParams);
                if (cancelled.get()) {
                    updateTaskStatus(taskId, "CANCELLED", "Task cancelled by user");
                } else {
                    updateTaskStatus(taskId, "COMPLETED", result);
                }
                log.info("Task completed: id={}, result={}", taskId, result);
            } catch (Exception e) {
                log.error("Task failed: id={}", taskId, e);
                updateTaskStatus(taskId, "FAILED", e.getMessage());
            } finally {
                runningTasks.remove(taskId);
                runningTaskHandlers.remove(taskId);
                currentTaskId.remove();
            }
        });
    }

    /**
     * 获取当前正在执行的任务 ID
     */
    public Long getCurrentTaskId() {
        return currentTaskId.get();
    }

    /**
     * 更新当前任务的进度（仅在任务执行过程中有效）
     */
    public void updateCurrentTaskProgress(String progress) {
        Long taskId = currentTaskId.get();
        if (taskId != null) {
            Task task = taskMapper.selectById(taskId);
            if (task != null && "RUNNING".equals(task.getStatus())) {
                task.setResult(progress);
                taskMapper.updateById(task);
            }
        }
    }

    /**
     * 停止指定任务
     */
    public boolean cancelTask(Long taskId) {
        AtomicBoolean cancelled = runningTasks.get(taskId);
        if (cancelled != null) {
            cancelled.set(true);

            // 尝试调用 handler 的 cancel 方法
            TaskHandler handler = runningTaskHandlers.get(taskId);
            if (handler instanceof CancellableTask) {
                ((CancellableTask) handler).cancel();
            }

            Task task = taskMapper.selectById(taskId);
            if (task != null && "RUNNING".equals(task.getStatus())) {
                task.setStatus("CANCELLED");
                task.setFinishedAt(LocalDateTime.now());
                taskMapper.updateById(task);
            }
            log.info("Task cancelled: id={}", taskId);
            return true;
        }
        return false;
    }

    /**
     * 分页获取任务列表
     */
    public Page<Task> getTaskList(int pageNum, int pageSize, String status) {
        Page<Task> page = new Page<>(pageNum, pageSize);
        QueryWrapper<Task> queryWrapper = new QueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            queryWrapper.eq("status", status);
        }
        queryWrapper.orderByDesc("created_at");
        return taskMapper.selectPage(page, queryWrapper);
    }

    private void updateTaskStatus(Long taskId, String status, String result) {
        // 同步更新任务状态
        Task task = taskMapper.selectById(taskId);
        if (task != null) {
            task.setStatus(status);
            if (status.equals("RUNNING")) {
                task.setStartedAt(LocalDateTime.now());
            } else if (status.equals("COMPLETED") || status.equals("FAILED") || status.equals("CANCELLED")) {
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

    /**
     * 可取消的任务处理器接口
     */
    public interface CancellableTask {
        void cancel();
    }
}
