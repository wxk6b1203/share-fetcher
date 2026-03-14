package com.github.ak.fetcher.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.ak.fetcher.entity.Task;
import com.github.ak.fetcher.service.TaskExecutor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/task")
public class TaskController {

    private final TaskExecutor taskExecutor;
    private final ObjectMapper objectMapper;

    public TaskController(TaskExecutor taskExecutor, ObjectMapper objectMapper) {
        this.taskExecutor = taskExecutor;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/submit/{taskType}")
    public Mono<ResponseEntity<Map<String, Object>>> submitTask(
            @PathVariable String taskType,
            @RequestBody Map<String, Object> params) {

        try {
            String inputParams = objectMapper.writeValueAsString(params);

            return taskExecutor.submitTask(taskType, inputParams)
                    .map(taskId -> ResponseEntity.accepted().body(Map.of(
                            "taskId", taskId,
                            "status", "PENDING"
                    )));
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    @GetMapping("/status/{taskId}")
    public Mono<ResponseEntity<Task>> getTaskStatus(@PathVariable Long taskId) {
        Task task = taskExecutor.getTaskStatus(taskId);
        if (task == null) {
            return Mono.just(ResponseEntity.notFound().build());
        }
        return Mono.just(ResponseEntity.ok(task));
    }

    /**
     * 停止指定任务
     */
    @PostMapping("/cancel/{taskId}")
    public Mono<ResponseEntity<Map<String, Object>>> cancelTask(@PathVariable Long taskId) {
        boolean success = taskExecutor.cancelTask(taskId);
        if (success) {
            return Mono.just(ResponseEntity.ok(Map.of(
                    "taskId", taskId,
                    "status", "CANCELLED"
            )));
        } else {
            return Mono.just(ResponseEntity.badRequest().body(Map.of(
                    "taskId", taskId,
                    "message", "Task not found or already completed"
            )));
        }
    }

    /**
     * 分页获取任务列表
     */
    @GetMapping("/list")
    public Mono<ResponseEntity<Page<Task>>> getTaskList(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String status) {
        Page<Task> page = taskExecutor.getTaskList(pageNum, pageSize, status);
        return Mono.just(ResponseEntity.ok(page));
    }
}
