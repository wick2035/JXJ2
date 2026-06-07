package com.eval.jxj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eval.jxj.common.exception.BizException;
import com.eval.jxj.dto.request.NoticeSaveRequest;
import com.eval.jxj.dto.response.NoticeVO;
import com.eval.jxj.entity.Notice;
import com.eval.jxj.entity.NoticeRecipient;
import com.eval.jxj.entity.SysUser;
import com.eval.jxj.mapper.NoticeMapper;
import com.eval.jxj.mapper.NoticeRecipientMapper;
import com.eval.jxj.mapper.SysUserMapper;
import com.eval.jxj.service.NoticeService;
import com.eval.jxj.util.SecurityUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class NoticeServiceImpl implements NoticeService {

    private static final String TARGET_ALL = "all";
    private static final String TARGET_SPECIFIED = "specified";
    private static final String STATUS_PUBLISHED = "published";
    private static final String STATUS_WITHDRAWN = "withdrawn";

    private final NoticeMapper noticeMapper;
    private final NoticeRecipientMapper recipientMapper;
    private final SysUserMapper userMapper;

    public NoticeServiceImpl(NoticeMapper noticeMapper,
                             NoticeRecipientMapper recipientMapper,
                             SysUserMapper userMapper) {
        this.noticeMapper = noticeMapper;
        this.recipientMapper = recipientMapper;
        this.userMapper = userMapper;
    }

    @Override
    public Page<NoticeVO> listMyNotices(int page, int size, Boolean unconfirmedOnly) {
        String userId = SecurityUtil.getCurrentUserId();
        List<NoticeRecipient> recipients = selectRecipients(new LambdaQueryWrapper<NoticeRecipient>()
                .eq(NoticeRecipient::getUserId, userId));
        List<NoticeVO> rows = new ArrayList<>();
        for (NoticeRecipient recipient : recipients) {
            if (Boolean.TRUE.equals(unconfirmedOnly) && Integer.valueOf(1).equals(recipient.getConfirmed())) {
                continue;
            }
            Notice notice = noticeMapper.selectById(recipient.getNoticeId());
            if (notice == null || STATUS_WITHDRAWN.equals(notice.getStatus())) {
                continue;
            }
            rows.add(toVO(notice, recipient, false));
        }
        rows.sort(Comparator.comparing(NoticeVO::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return pageOf(rows, page, size);
    }

    @Override
    public NoticeVO getMyNotice(String id) {
        NoticeRecipient recipient = findRecipient(id, SecurityUtil.getCurrentUserId());
        if (recipient == null) {
            return null;
        }
        Notice notice = noticeMapper.selectById(id);
        if (notice == null || STATUS_WITHDRAWN.equals(notice.getStatus())) {
            return null;
        }
        return toVO(notice, recipient, false);
    }

    @Override
    public long countMyUnconfirmed() {
        String userId = SecurityUtil.getCurrentUserId();
        List<NoticeRecipient> recipients = selectRecipients(new LambdaQueryWrapper<NoticeRecipient>()
                .eq(NoticeRecipient::getUserId, userId)
                .eq(NoticeRecipient::getConfirmed, 0));
        return recipients.stream()
                .map(recipient -> noticeMapper.selectById(recipient.getNoticeId()))
                .filter(notice -> notice != null && STATUS_PUBLISHED.equals(notice.getStatus()))
                .count();
    }

    @Override
    public void confirmMyNotice(String id) {
        Notice notice = noticeMapper.selectById(id);
        if (notice == null || STATUS_WITHDRAWN.equals(notice.getStatus())) {
            throw new BizException("公告不存在或已撤回");
        }
        NoticeRecipient recipient = findRecipient(id, SecurityUtil.getCurrentUserId());
        if (recipient == null) {
            throw new BizException("无权查看该公告");
        }
        if (Integer.valueOf(1).equals(recipient.getConfirmed())) {
            return;
        }
        recipient.setConfirmed(1);
        recipient.setConfirmedAt(LocalDateTime.now());
        recipientMapper.updateById(recipient);
    }

    @Override
    public Page<NoticeVO> listAdminNotices(int page, int size, String status) {
        LambdaQueryWrapper<Notice> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            wrapper.eq(Notice::getStatus, status);
        }
        wrapper.orderByDesc(Notice::getCreatedAt);
        Page<Notice> result = noticeMapper.selectPage(new Page<>(page, size), wrapper);
        Page<NoticeVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream()
                .map(notice -> toVO(notice, null, true))
                .collect(Collectors.toList()));
        return voPage;
    }

    @Override
    @Transactional
    public NoticeVO createNotice(NoticeSaveRequest request) {
        List<String> recipientIds = resolveRecipientIds(request);
        Notice notice = new Notice();
        notice.setTitle(request.getTitle());
        notice.setContent(request.getContent());
        notice.setTargetType(request.getTargetType());
        notice.setStatus(STATUS_PUBLISHED);
        notice.setCreatedBy(SecurityUtil.getCurrentUserId());
        noticeMapper.insert(notice);
        saveNewRecipients(notice.getId(), recipientIds);
        return toVO(notice, null, true);
    }

    @Override
    @Transactional
    public NoticeVO updateNotice(String id, NoticeSaveRequest request) {
        Notice notice = mustGetPublishedNotice(id);
        boolean contentChanged = !equalsText(notice.getTitle(), request.getTitle())
                || !equalsText(notice.getContent(), request.getContent());
        List<String> recipientIds = resolveRecipientIds(request);

        notice.setTitle(request.getTitle());
        notice.setContent(request.getContent());
        notice.setTargetType(request.getTargetType());
        noticeMapper.updateById(notice);

        syncRecipients(id, recipientIds, contentChanged);
        return toVO(notice, null, true);
    }

    @Override
    public void withdrawNotice(String id) {
        Notice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw new BizException("公告不存在");
        }
        notice.setStatus(STATUS_WITHDRAWN);
        notice.setWithdrawnAt(LocalDateTime.now());
        noticeMapper.updateById(notice);
    }

    private Notice mustGetPublishedNotice(String id) {
        Notice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw new BizException("公告不存在");
        }
        if (STATUS_WITHDRAWN.equals(notice.getStatus())) {
            throw new BizException("已撤回公告不能编辑");
        }
        return notice;
    }

    private List<String> resolveRecipientIds(NoticeSaveRequest request) {
        if (!TARGET_ALL.equals(request.getTargetType()) && !TARGET_SPECIFIED.equals(request.getTargetType())) {
            throw new BizException("公告范围无效");
        }
        if (TARGET_ALL.equals(request.getTargetType())) {
            return userMapper.selectList(new LambdaQueryWrapper<SysUser>())
                    .stream()
                    .map(SysUser::getId)
                    .collect(Collectors.toList());
        }
        Set<String> ids = request.getRecipientUserIds() == null
                ? new LinkedHashSet<>()
                : request.getRecipientUserIds().stream()
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            throw new BizException("指定用户公告至少选择一个接收人");
        }
        for (String userId : ids) {
            if (userMapper.selectById(userId) == null) {
                throw new BizException("接收人不存在");
            }
        }
        return new ArrayList<>(ids);
    }

    private void saveNewRecipients(String noticeId, List<String> recipientIds) {
        for (String userId : recipientIds) {
            NoticeRecipient recipient = new NoticeRecipient();
            recipient.setNoticeId(noticeId);
            recipient.setUserId(userId);
            recipient.setConfirmed(0);
            recipientMapper.insert(recipient);
        }
    }

    private void syncRecipients(String noticeId, List<String> recipientIds, boolean contentChanged) {
        List<NoticeRecipient> existing = selectRecipients(new LambdaQueryWrapper<NoticeRecipient>()
                .eq(NoticeRecipient::getNoticeId, noticeId));
        Set<String> nextIds = new LinkedHashSet<>(recipientIds);

        for (NoticeRecipient recipient : existing) {
            if (!nextIds.contains(recipient.getUserId())) {
                recipientMapper.deleteById(recipient.getId());
                continue;
            }
            if (contentChanged) {
                recipient.setConfirmed(0);
                recipient.setConfirmedAt(null);
                recipientMapper.updateById(recipient);
            }
            nextIds.remove(recipient.getUserId());
        }
        saveNewRecipients(noticeId, new ArrayList<>(nextIds));
    }

    private NoticeRecipient findRecipient(String noticeId, String userId) {
        return recipientMapper.selectOne(new LambdaQueryWrapper<NoticeRecipient>()
                .eq(NoticeRecipient::getNoticeId, noticeId)
                .eq(NoticeRecipient::getUserId, userId));
    }

    private NoticeVO toVO(Notice notice, NoticeRecipient currentRecipient, boolean includeRecipients) {
        NoticeVO vo = new NoticeVO();
        BeanUtils.copyProperties(notice, vo);
        SysUser creator = notice.getCreatedBy() == null ? null : userMapper.selectById(notice.getCreatedBy());
        if (creator != null) {
            vo.setCreatorName(creator.getName());
        }
        if (currentRecipient != null) {
            vo.setConfirmed(currentRecipient.getConfirmed());
            vo.setConfirmedAt(currentRecipient.getConfirmedAt());
        }

        List<NoticeRecipient> recipients = selectRecipients(new LambdaQueryWrapper<NoticeRecipient>()
                .eq(NoticeRecipient::getNoticeId, notice.getId()));
        vo.setRecipientCount(recipients.size());
        int confirmedCount = (int) recipients.stream()
                .filter(recipient -> Integer.valueOf(1).equals(recipient.getConfirmed()))
                .count();
        vo.setConfirmedCount(confirmedCount);
        vo.setUnconfirmedCount(recipients.size() - confirmedCount);
        vo.setRecipientUserIds(recipients.stream().map(NoticeRecipient::getUserId).collect(Collectors.toList()));
        if (includeRecipients) {
            vo.setRecipients(recipients.stream().map(this::toRecipientVO).collect(Collectors.toList()));
        }
        return vo;
    }

    private NoticeVO.RecipientVO toRecipientVO(NoticeRecipient recipient) {
        NoticeVO.RecipientVO vo = new NoticeVO.RecipientVO();
        SysUser user = userMapper.selectById(recipient.getUserId());
        vo.setId(recipient.getUserId());
        if (user != null) {
            vo.setLoginId(user.getLoginId());
            vo.setName(user.getName());
            vo.setRole(user.getRole());
        }
        vo.setConfirmed(recipient.getConfirmed());
        vo.setConfirmedAt(recipient.getConfirmedAt());
        return vo;
    }

    private List<NoticeRecipient> selectRecipients(LambdaQueryWrapper<NoticeRecipient> wrapper) {
        List<NoticeRecipient> recipients = recipientMapper.selectList(wrapper);
        return recipients == null ? new ArrayList<>() : recipients;
    }

    private Page<NoticeVO> pageOf(List<NoticeVO> rows, int page, int size) {
        int current = Math.max(page, 1);
        int pageSize = Math.max(size, 1);
        int from = Math.min((current - 1) * pageSize, rows.size());
        int to = Math.min(from + pageSize, rows.size());
        Page<NoticeVO> result = new Page<>(current, pageSize, rows.size());
        result.setRecords(rows.subList(from, to));
        return result;
    }

    private boolean equalsText(String left, String right) {
        return String.valueOf(left).equals(String.valueOf(right));
    }
}
