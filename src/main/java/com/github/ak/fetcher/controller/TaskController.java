package com.github.ak.fetcher.controller;

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
}
