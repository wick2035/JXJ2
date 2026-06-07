package com.eval.jxj.controller;

import com.eval.jxj.common.Result;
import com.eval.jxj.dto.request.SecondaryPasswordChangeRequest;
import com.eval.jxj.service.SecondaryPasswordService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/config")
@PreAuthorize("hasRole('ADMIN')")
public class ConfigController {

    private final SecondaryPasswordService secondaryPasswordService;

    public ConfigController(SecondaryPasswordService secondaryPasswordService) {
        this.secondaryPasswordService = secondaryPasswordService;
    }

    @PutMapping("/secondary-password")
    public Result<?> changeSecondaryPassword(@RequestBody SecondaryPasswordChangeRequest request) {
        secondaryPasswordService.change(request.getOldPassword(), request.getNewPassword());
        return Result.ok();
    }
}
