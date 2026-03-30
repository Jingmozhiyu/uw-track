package com.jing.monitor.controller;

import com.jing.monitor.common.Result;
import com.jing.monitor.model.dto.TaskRespDto;
import com.jing.monitor.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for authenticated task operations.
 */
@RestController
@RequestMapping("/api/tasks")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    /**
     * Lists all tasks for the current authenticated user.
     *
     * @return task list response
     */
    @GetMapping
    public Result<List<TaskRespDto>> list() {
        return Result.success(taskService.getAllTasks());
    }

    /**
     * Searches a course and returns synced section rows without creating subscriptions.
     *
     * @param courseName course keyword from client
     * @return synced sections for the frontend
     */
    @GetMapping("/search")
    public Result<List<TaskRespDto>> search(@RequestParam String courseName) {
        return Result.success(taskService.searchCourse(courseName));
    }

    /**
     * Adds one section subscription by validated 5-digit section id.
     *
     * @param sectionId business section id chosen by the frontend
     * @return created or existing subscription
     */
    @PostMapping
    public Result<TaskRespDto> add(@RequestParam String sectionId) {
        return Result.success(taskService.addSection(sectionId));
    }

    /**
     * Toggles task enabled state by subscription UUID.
     *
     * @param id task id
     * @return updated task
     */
    @PatchMapping("/{id}/toggle")
    public Result<TaskRespDto> toggleStatus(@PathVariable UUID id) {
        return Result.success(taskService.toggleTaskStatus(id));
    }

    /**
     * Deletes all tasks for a course display name under current user.
     *
     * @param courseDisplayName display name to delete
     * @return empty success result
     */
    @DeleteMapping
    public Result<Void> delete(@RequestParam String courseDisplayName){
        taskService.deleteTask(courseDisplayName);
        return Result.success();
    }
}
