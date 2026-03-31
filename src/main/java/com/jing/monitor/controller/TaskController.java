package com.jing.monitor.controller;

import com.jing.monitor.common.Result;
import com.jing.monitor.model.dto.TaskRespDto;
import com.jing.monitor.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
     * Soft-deletes one task by disabling the current user's subscription for that section.
     *
     * @param sectionId validated 5-digit section id
     * @return empty success result
     */
    @DeleteMapping
    public Result<Void> delete(@RequestParam String sectionId){
        taskService.deleteTask(sectionId);
        return Result.success();
    }
}
