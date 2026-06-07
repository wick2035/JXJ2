package com.eval.jxj.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eval.jxj.dto.request.NoticeSaveRequest;
import com.eval.jxj.dto.response.NoticeVO;

public interface NoticeService {
    Page<NoticeVO> listMyNotices(int page, int size, Boolean unconfirmedOnly);
    NoticeVO getMyNotice(String id);
    long countMyUnconfirmed();
    void confirmMyNotice(String id);
    Page<NoticeVO> listAdminNotices(int page, int size, String status);
    NoticeVO createNotice(NoticeSaveRequest request);
    NoticeVO updateNotice(String id, NoticeSaveRequest request);
    void withdrawNotice(String id);
}
