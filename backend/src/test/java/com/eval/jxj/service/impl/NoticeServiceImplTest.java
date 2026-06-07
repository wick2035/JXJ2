package com.eval.jxj.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.eval.jxj.dto.request.NoticeSaveRequest;
import com.eval.jxj.dto.response.NoticeVO;
import com.eval.jxj.entity.Notice;
import com.eval.jxj.entity.NoticeRecipient;
import com.eval.jxj.entity.SysUser;
import com.eval.jxj.mapper.NoticeMapper;
import com.eval.jxj.mapper.NoticeRecipientMapper;
import com.eval.jxj.mapper.SysUserMapper;
import com.eval.jxj.security.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoticeServiceImplTest {

    @Mock
    private NoticeMapper noticeMapper;
    @Mock
    private NoticeRecipientMapper recipientMapper;
    @Mock
    private SysUserMapper userMapper;

    @BeforeEach
    void setUp() {
        LoginUser loginUser = new LoginUser();
        loginUser.setId("admin-1");
        loginUser.setRole("admin");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createSpecifiedNoticeCreatesRecipientsForUniqueSelectedUsers() {
        when(userMapper.selectById("student-1")).thenReturn(user("student-1", "S001", "张三", "student"));
        when(userMapper.selectById("teacher-1")).thenReturn(user("teacher-1", "T001", "李老师", "teacher"));

        NoticeServiceImpl service = new NoticeServiceImpl(noticeMapper, recipientMapper, userMapper);
        NoticeSaveRequest request = request("评优材料补充", "请在周五前补充证明材料。", "specified",
                Arrays.asList("student-1", "teacher-1", "student-1"));

        service.createNotice(request);

        ArgumentCaptor<Notice> noticeCaptor = ArgumentCaptor.forClass(Notice.class);
        verify(noticeMapper).insert(noticeCaptor.capture());
        assertThat(noticeCaptor.getValue().getTitle()).isEqualTo("评优材料补充");
        assertThat(noticeCaptor.getValue().getContent()).isEqualTo("请在周五前补充证明材料。");
        assertThat(noticeCaptor.getValue().getTargetType()).isEqualTo("specified");
        assertThat(noticeCaptor.getValue().getStatus()).isEqualTo("published");
        assertThat(noticeCaptor.getValue().getCreatedBy()).isEqualTo("admin-1");

        ArgumentCaptor<NoticeRecipient> recipientCaptor = ArgumentCaptor.forClass(NoticeRecipient.class);
        verify(recipientMapper, org.mockito.Mockito.times(2)).insert(recipientCaptor.capture());
        assertThat(recipientCaptor.getAllValues()).extracting(NoticeRecipient::getUserId)
                .containsExactly("student-1", "teacher-1");
        assertThat(recipientCaptor.getAllValues()).extracting(NoticeRecipient::getConfirmed)
                .containsExactly(0, 0);
    }

    @Test
    void createAllNoticeSnapshotsAllExistingUsers() {
        when(userMapper.selectList(any(Wrapper.class))).thenReturn(Arrays.asList(
                user("admin-1", "admin", "管理员", "admin"),
                user("student-1", "S001", "张三", "student"),
                user("teacher-1", "T001", "李老师", "teacher")
        ));

        NoticeServiceImpl service = new NoticeServiceImpl(noticeMapper, recipientMapper, userMapper);
        service.createNotice(request("系统维护", "今晚 22:00 维护。", "all", Collections.emptyList()));

        ArgumentCaptor<NoticeRecipient> recipientCaptor = ArgumentCaptor.forClass(NoticeRecipient.class);
        verify(recipientMapper, org.mockito.Mockito.times(3)).insert(recipientCaptor.capture());
        assertThat(recipientCaptor.getAllValues()).extracting(NoticeRecipient::getUserId)
                .containsExactly("admin-1", "student-1", "teacher-1");
    }

    @Test
    void confirmMyNoticeMarksCurrentUsersRecipientAsConfirmed() {
        setCurrentUser("student-1", "student");
        Notice notice = notice("notice-1", "published", "old title", "old content");
        NoticeRecipient recipient = recipient("recipient-1", "notice-1", "student-1", 0);
        when(noticeMapper.selectById("notice-1")).thenReturn(notice);
        when(recipientMapper.selectOne(any(Wrapper.class))).thenReturn(recipient);

        NoticeServiceImpl service = new NoticeServiceImpl(noticeMapper, recipientMapper, userMapper);
        service.confirmMyNotice("notice-1");

        ArgumentCaptor<NoticeRecipient> captor = ArgumentCaptor.forClass(NoticeRecipient.class);
        verify(recipientMapper).updateById(captor.capture());
        assertThat(captor.getValue().getConfirmed()).isEqualTo(1);
        assertThat(captor.getValue().getConfirmedAt()).isNotNull();
    }

    @Test
    void updateContentResetsExistingRecipientConfirmationAndAddsNewRecipient() {
        Notice existing = notice("notice-1", "published", "旧标题", "旧内容");
        NoticeRecipient kept = recipient("recipient-1", "notice-1", "student-1", 1);
        kept.setConfirmedAt(LocalDateTime.now().minusDays(1));
        when(noticeMapper.selectById("notice-1")).thenReturn(existing);
        when(recipientMapper.selectList(any(Wrapper.class))).thenReturn(Collections.singletonList(kept));
        when(userMapper.selectById("student-1")).thenReturn(user("student-1", "S001", "张三", "student"));
        when(userMapper.selectById("teacher-1")).thenReturn(user("teacher-1", "T001", "李老师", "teacher"));

        NoticeServiceImpl service = new NoticeServiceImpl(noticeMapper, recipientMapper, userMapper);
        service.updateNotice("notice-1", request("新标题", "新内容", "specified", Arrays.asList("student-1", "teacher-1")));

        ArgumentCaptor<NoticeRecipient> updateCaptor = ArgumentCaptor.forClass(NoticeRecipient.class);
        verify(recipientMapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getId()).isEqualTo("recipient-1");
        assertThat(updateCaptor.getValue().getConfirmed()).isEqualTo(0);
        assertThat(updateCaptor.getValue().getConfirmedAt()).isNull();

        ArgumentCaptor<NoticeRecipient> insertCaptor = ArgumentCaptor.forClass(NoticeRecipient.class);
        verify(recipientMapper).insert(insertCaptor.capture());
        assertThat(insertCaptor.getValue().getUserId()).isEqualTo("teacher-1");
    }

    @Test
    void withdrawHidesNoticeFromMyListButAdminCanStillSeeIt() {
        setCurrentUser("student-1", "student");
        Notice withdrawn = notice("notice-1", "withdrawn", "撤回公告", "内容");
        when(recipientMapper.selectOne(any(Wrapper.class))).thenReturn(
                recipient("recipient-1", "notice-1", "student-1", 0));
        when(noticeMapper.selectById("notice-1")).thenReturn(withdrawn);

        NoticeServiceImpl service = new NoticeServiceImpl(noticeMapper, recipientMapper, userMapper);
        NoticeVO myNotice = service.getMyNotice("notice-1");

        assertThat(myNotice).isNull();

        setCurrentUser("admin-1", "admin");
        when(noticeMapper.selectPage(any(), any(Wrapper.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(service.listAdminNotices(1, 10, "withdrawn")).isNotNull();
    }

    @Test
    void specifiedNoticeRequiresAtLeastOneRecipient() {
        NoticeServiceImpl service = new NoticeServiceImpl(noticeMapper, recipientMapper, userMapper);

        assertThatThrownBy(() -> service.createNotice(
                request("空收件人", "内容", "specified", Collections.emptyList())))
                .hasMessage("指定用户公告至少选择一个接收人");
        verify(noticeMapper, never()).insert(any(Notice.class));
    }

    private static NoticeSaveRequest request(String title, String content, String targetType, List<String> recipientUserIds) {
        NoticeSaveRequest request = new NoticeSaveRequest();
        request.setTitle(title);
        request.setContent(content);
        request.setTargetType(targetType);
        request.setRecipientUserIds(recipientUserIds);
        return request;
    }

    private static SysUser user(String id, String loginId, String name, String role) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setLoginId(loginId);
        user.setName(name);
        user.setRole(role);
        return user;
    }

    private static Notice notice(String id, String status, String title, String content) {
        Notice notice = new Notice();
        notice.setId(id);
        notice.setTitle(title);
        notice.setContent(content);
        notice.setTargetType("specified");
        notice.setStatus(status);
        notice.setCreatedBy("admin-1");
        notice.setCreatedAt(LocalDateTime.now());
        notice.setUpdatedAt(LocalDateTime.now());
        return notice;
    }

    private static NoticeRecipient recipient(String id, String noticeId, String userId, int confirmed) {
        NoticeRecipient recipient = new NoticeRecipient();
        recipient.setId(id);
        recipient.setNoticeId(noticeId);
        recipient.setUserId(userId);
        recipient.setConfirmed(confirmed);
        return recipient;
    }

    private static void setCurrentUser(String id, String role) {
        LoginUser loginUser = new LoginUser();
        loginUser.setId(id);
        loginUser.setRole(role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));
    }
}
