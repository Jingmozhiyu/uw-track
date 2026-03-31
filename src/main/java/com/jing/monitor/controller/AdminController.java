package com.jing.monitor.controller;

import com.jing.monitor.common.Result;
import com.jing.monitor.model.dto.AdminSectionSubRespDto;
import com.jing.monitor.model.dto.AdminUserSubsRespDto;
import com.jing.monitor.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for admin-only user and subscription management.
 */
@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    /**
     * Lists every user email together with the subscribed course and section info.
     *
     * @return grouped admin-facing user subscription data
     */
    @GetMapping("/subscriptions")
    public Result<List<AdminUserSubsRespDto>> listAllSubscriptions() {
        return Result.success(adminService.getAllUserSubscriptions());
    }

    /**
     * Enables or disables one subscription from the admin dashboard.
     *
     * @param subscriptionId subscription UUID
     * @param enabled target enabled state
     * @return updated subscription row
     */
    @PatchMapping("/subscriptions/{subscriptionId}")
    public Result<AdminSectionSubRespDto> updateSubscription(
            @PathVariable UUID subscriptionId,
            @RequestParam boolean enabled
    ) {
        return Result.success(adminService.updateSubscriptionEnabled(subscriptionId, enabled));
    }
}
