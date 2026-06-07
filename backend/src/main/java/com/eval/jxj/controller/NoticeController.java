package com.eval.jxj.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eval.jxj.common.Result;
import com.eval.jxj.dto.request.NoticeSaveRequest;
import com.eval.jxj.dto.response.NoticeVO;
import com.eval.jxj.service.NoticeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/notices")
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @GetMapping("/my")
    public Result<Page<NoticeVO>> listMy(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int size,
                                         @RequestParam(required = false) Boolean unconfirmedOnly) {
        return Result.ok(noticeService.listMyNotices(page, size, unconfirmedOnly));
    }

    @GetMapping("/my/unconfirmed-count")
    public Result<Long> countMyUnconfirmed() {
        return Result.ok(noticeService.countMyUnconfirmed());
    }

    @GetMapping("/my/{id}")
    public Result<NoticeVO> getMy(@PathVariable String id) {
        return Result.ok(noticeService.getMyNotice(id));
    }

    @PutMapping("/my/{id}/confirm")
    public Result<?> confirmMy(@PathVariable String id) {
        noticeService.confirmMyNotice(id);
        return Result.ok();
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Page<NoticeVO>> listAdmin(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size,
                                            @RequestParam(required = false) String status) {
        return Result.ok(noticeService.listAdminNotices(page, size, status));
    }

    @PostMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<NoticeVO> create(@Valid @RequestBody NoticeSaveRequest request) {
        return Result.ok(noticeService.createNotice(request));
    }

    @PutMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<NoticeVO> update(@PathVariable String id, @Valid @RequestBody NoticeSaveRequest request) {
        return Result.ok(noticeService.updateNotice(id, request));
    }

    @PutMapping("/admin/{id}/withdraw")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<?> withdraw(@PathVariable String id) {
        noticeService.withdrawNotice(id);
        return Result.ok();
    }
}
