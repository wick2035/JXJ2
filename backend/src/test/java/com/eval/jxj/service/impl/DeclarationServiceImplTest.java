package com.eval.jxj.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eval.jxj.common.exception.BizException;
import com.eval.jxj.dto.request.DeclarationSaveRequest;
import com.eval.jxj.dto.response.DeclarationVO;
import com.eval.jxj.entity.AwardCategory;
import com.eval.jxj.entity.Award;
import com.eval.jxj.entity.Declaration;
import com.eval.jxj.entity.DeclarationAttachment;
import com.eval.jxj.entity.DeclarationItem;
import com.eval.jxj.entity.EvalBatch;
import com.eval.jxj.mapper.AuditAssignmentMapper;
import com.eval.jxj.mapper.AuditRecordMapper;
import com.eval.jxj.mapper.AwardCategoryMapper;
import com.eval.jxj.mapper.AwardLevelDefMapper;
import com.eval.jxj.mapper.AwardMapper;
import com.eval.jxj.mapper.DeclarationAttachmentMapper;
import com.eval.jxj.mapper.DeclarationItemMapper;
import com.eval.jxj.mapper.DeclarationMapper;
import com.eval.jxj.mapper.EvalBatchMapper;
import com.eval.jxj.mapper.SysUserMapper;
import com.eval.jxj.security.LoginUser;
import com.eval.jxj.service.BatchAssignmentService;
import com.eval.jxj.service.ScoreCalculationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeclarationServiceImplTest {

    @Mock
    private DeclarationMapper declarationMapper;
    @Mock
    private DeclarationItemMapper itemMapper;
    @Mock
    private DeclarationAttachmentMapper attachmentMapper;
    @Mock
    private AuditRecordMapper auditRecordMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private EvalBatchMapper batchMapper;
    @Mock
    private AwardMapper awardMapper;
    @Mock
    private AwardCategoryMapper awardCategoryMapper;
    @Mock
    private AwardLevelDefMapper levelDefMapper;
    @Mock
    private ScoreCalculationService scoreService;
    @Mock
    private BatchAssignmentService assignmentService;
    @Mock
    private AuditAssignmentMapper assignmentMapper;

    @InjectMocks
    private DeclarationServiceImpl service;

    @TempDir
    Path uploadDir;

    @BeforeEach
    void setUpMybatisPlusMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Declaration.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void submitDeclaration_rejectsItemsWithoutAttachments() {
        Declaration declaration = declaration("decl-1", "student-1", "draft");

        EvalBatch batch = new EvalBatch();
        batch.setId("batch-1");
        batch.setStatus("open");

        DeclarationItem item = new DeclarationItem();
        item.setId("item-1");
        item.setDeclarationId("decl-1");
        item.setCategory("morality");

        when(declarationMapper.selectById("decl-1")).thenReturn(declaration);
        when(batchMapper.selectById("batch-1")).thenReturn(batch);
        when(itemMapper.selectList(any())).thenReturn(List.of(item));
        when(attachmentMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.submitDeclaration("decl-1"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void listDeclarationsMarksSubmittedAssignedAsWithdrawable() {
        setCurrentUser("student-1", "student");
        Declaration declaration = declaration("decl-1", "student-1", "submitted");
        Page<Declaration> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(declaration));

        when(declarationMapper.selectPage(any(), any(Wrapper.class))).thenReturn(page);
        when(auditRecordMapper.selectCount(any())).thenReturn(0L);
        when(assignmentMapper.countByDeclarationId("decl-1")).thenReturn(1);

        Page<DeclarationVO> result = service.listDeclarations(null, null, null, 1, 10);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getStage()).isEqualTo("assigned");
        assertThat(result.getRecords().get(0).getCanWithdraw()).isTrue();
    }

    @Test
    void listDeclarationsMarksSubmittedWithAuditRecordAsReviewingAndNotWithdrawable() {
        setCurrentUser("student-1", "student");
        Declaration declaration = declaration("decl-1", "student-1", "submitted");
        Page<Declaration> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(declaration));

        when(declarationMapper.selectPage(any(), any(Wrapper.class))).thenReturn(page);
        when(auditRecordMapper.selectCount(any())).thenReturn(1L);

        Page<DeclarationVO> result = service.listDeclarations(null, null, null, 1, 10);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getStage()).isEqualTo("reviewing");
        assertThat(result.getRecords().get(0).getCanWithdraw()).isFalse();
    }

    @Test
    void withdrawDeclarationReturnsSubmittedDeclarationToDraftBeforeAuditStarts() {
        setCurrentUser("student-1", "student");
        Declaration submitted = declaration("decl-1", "student-1", "submitted");
        submitted.setSubmittedAt(LocalDateTime.now());
        Declaration draft = declaration("decl-1", "student-1", "draft");

        when(declarationMapper.selectById("decl-1")).thenReturn(submitted, draft);
        when(auditRecordMapper.selectCount(any())).thenReturn(0L);
        when(itemMapper.selectList(any())).thenReturn(List.of());
        when(auditRecordMapper.selectList(any())).thenReturn(List.of());

        DeclarationVO result = service.withdrawDeclaration("decl-1");

        verify(assignmentMapper).cancelPendingByDeclarationId("decl-1");
        verify(declarationMapper).update(eq(null), any());
        assertThat(result.getStatus()).isEqualTo("draft");
        assertThat(result.getStage()).isEqualTo("pending_submit");
        assertThat(result.getCanWithdraw()).isFalse();
    }

    @Test
    void withdrawDeclarationRejectsAnotherStudentsDeclaration() {
        setCurrentUser("student-2", "student");
        when(declarationMapper.selectById("decl-1")).thenReturn(declaration("decl-1", "student-1", "submitted"));

        assertThatThrownBy(() -> service.withdrawDeclaration("decl-1"))
                .isInstanceOf(BizException.class)
                .hasMessage("无权操作");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void teacherListDeclarationsRestrictsToOwnReviewScope() {
        setCurrentUser("teacher-1", "teacher");
        Page<Declaration> page = new Page<>(1, 10, 0);
        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        when(declarationMapper.selectPage(any(), captor.capture())).thenReturn(page);

        service.listDeclarations(null, null, null, 1, 10);

        String sql = captor.getValue().getCustomSqlSegment();
        assertThat(sql).contains("audit_assignment");
        assertThat(sql).contains("audit_record");
    }

    @Test
    void teacherCannotViewDeclarationWithoutAssignmentOrAuditRecord() {
        setCurrentUser("teacher-1", "teacher");
        when(declarationMapper.selectById("decl-1")).thenReturn(declaration("decl-1", "student-1", "approved"));
        when(assignmentMapper.selectByDeclarationAndReviewer("decl-1", "teacher-1")).thenReturn(null);
        when(auditRecordMapper.countByDeclarationAndReviewer("decl-1", "teacher-1")).thenReturn(0);

        assertThatThrownBy(() -> service.getDeclaration("decl-1"))
                .isInstanceOf(BizException.class)
                .hasMessage("无权查看当前申报");
    }

    @Test
    void withdrawDeclarationReturnsDraftWithExistingAttachments() {
        setCurrentUser("student-1", "student");
        Declaration submitted = declaration("decl-1", "student-1", "submitted");
        Declaration draft = declaration("decl-1", "student-1", "draft");

        DeclarationItem item = item("item-1", "decl-1");
        DeclarationAttachment attachment = attachment("att-1", "item-1", "/uploads/item-1/proof.pdf");

        when(declarationMapper.selectById("decl-1")).thenReturn(submitted, draft);
        when(auditRecordMapper.selectCount(any())).thenReturn(0L);
        when(itemMapper.selectList(any())).thenReturn(List.of(item));
        when(attachmentMapper.selectList(any())).thenReturn(List.of(attachment));
        when(auditRecordMapper.selectList(any())).thenReturn(List.of());

        DeclarationVO result = service.withdrawDeclaration("decl-1");

        assertThat(result.getStatus()).isEqualTo("draft");
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getAttachments()).extracting(DeclarationVO.AttachmentVO::getId)
                .containsExactly("att-1");
        verify(attachmentMapper, never()).deleteById("att-1");
    }

    @Test
    void withdrawDeclarationRejectsDeclarationAfterAuditStarts() {
        setCurrentUser("student-1", "student");
        when(declarationMapper.selectById("decl-1")).thenReturn(declaration("decl-1", "student-1", "submitted"));
        when(auditRecordMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.withdrawDeclaration("decl-1"))
                .isInstanceOf(BizException.class)
                .hasMessage("申报已进入审核，无法撤回");
    }

    @Test
    void saveDeclaration_restoresSoftDeletedDeclarationInsteadOfInserting() throws Exception {
        setCurrentUser("student-1", "student");
        ReflectionTestUtils.setField(service, "uploadPath", uploadDir.toString());

        Declaration deleted = declaration("decl-1", "student-1", "submitted");
        deleted.setIsDeleted(1);
        deleted.setSubmittedAt(java.time.LocalDateTime.now());

        EvalBatch batch = new EvalBatch();
        batch.setId("batch-1");
        batch.setStatus("open");

        AwardCategory category = new AwardCategory();
        category.setCode("ability");

        when(declarationMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(declarationMapper.selectDeletedByBatchAndStudent("batch-1", "student-1")).thenReturn(deleted);
        when(batchMapper.selectById("batch-1")).thenReturn(batch);
        when(awardCategoryMapper.selectOne(any())).thenReturn(category);
        when(itemMapper.selectList(any())).thenReturn(List.of());
        doAnswer(invocation -> {
            DeclarationItem item = invocation.getArgument(0);
            item.setId("item-1");
            return 1;
        }).when(itemMapper).insert(any(DeclarationItem.class));

        MockMultipartFile proof = new MockMultipartFile(
                "files", "proof.pdf", "application/pdf", "proof".getBytes());

        service.saveDeclaration(requestWithOneAttachment(), new MockMultipartFile[]{proof});

        verify(declarationMapper).restoreDeleted(eq("decl-1"));
        verify(assignmentMapper).cancelPendingByDeclarationId("decl-1");
        verify(declarationMapper, never()).insert(any(Declaration.class));
        verify(attachmentMapper).insert(any(DeclarationAttachment.class));
    }

    @Test
    void saveDeclaration_infersBlankCategoryFromPresetAward() throws Exception {
        setCurrentUser("student-1", "student");

        Declaration declaration = declaration("decl-1", "student-1", "draft");

        Award award = new Award();
        award.setId("award-1");
        award.setCategory("ability");

        AwardCategory category = new AwardCategory();
        category.setCode("ability");

        when(declarationMapper.selectOne(any(Wrapper.class))).thenReturn(declaration);
        when(itemMapper.selectList(any())).thenReturn(List.of());
        when(awardMapper.selectById("award-1")).thenReturn(award);
        when(awardCategoryMapper.selectOne(any())).thenReturn(category);
        doAnswer(invocation -> {
            DeclarationItem item = invocation.getArgument(0);
            item.setId("item-1");
            return 1;
        }).when(itemMapper).insert(any(DeclarationItem.class));

        service.saveDeclaration(requestWithBlankCategoryPresetAward(), null);

        verify(itemMapper).insert(org.mockito.ArgumentMatchers.argThat(item ->
                "ability".equals(item.getCategory()) && "award-1".equals(item.getAwardId())));
    }

    @Test
    void saveDeclaration_rejectsBlankCategoryForCustomAward() {
        setCurrentUser("student-1", "student");

        Declaration declaration = declaration("decl-1", "student-1", "draft");

        when(declarationMapper.selectOne(any(Wrapper.class))).thenReturn(declaration);
        when(itemMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.saveDeclaration(requestWithBlankCategoryCustomAward(), null))
                .isInstanceOf(BizException.class)
                .hasMessage("类别不能为空");
    }

    @Test
    void saveDeclaration_keepsRequestedExistingAttachment() throws Exception {
        setCurrentUser("student-1", "student");
        ReflectionTestUtils.setField(service, "uploadPath", uploadDir.toString());

        Declaration declaration = declaration("decl-1", "student-1", "draft");
        DeclarationItem item = item("item-1", "decl-1");
        DeclarationAttachment attachment = attachment("att-keep", "item-1", "/uploads/item-1/proof.pdf");
        AwardCategory category = category("ability");

        when(declarationMapper.selectOne(any(Wrapper.class))).thenReturn(declaration);
        when(itemMapper.selectList(any())).thenReturn(List.of(item), List.of(item));
        when(awardCategoryMapper.selectOne(any())).thenReturn(category);
        when(attachmentMapper.selectList(any())).thenReturn(List.of(attachment), List.of(attachment));
        when(auditRecordMapper.selectList(any())).thenReturn(List.of());

        DeclarationVO result = service.saveDeclaration(requestWithExistingAttachment("item-1", "att-keep"), null);

        verify(attachmentMapper, never()).deleteById("att-keep");
        verify(attachmentMapper, never()).insert(any(DeclarationAttachment.class));
        assertThat(result.getItems().get(0).getAttachments()).extracting(DeclarationVO.AttachmentVO::getId)
                .containsExactly("att-keep");
    }

    @Test
    void saveDeclaration_restoresMissingItemIdFromReturnedAttachment() throws Exception {
        setCurrentUser("student-1", "student");
        ReflectionTestUtils.setField(service, "uploadPath", uploadDir.toString());

        Declaration declaration = declaration("decl-1", "student-1", "draft");
        DeclarationItem item = item("item-1", "decl-1");
        DeclarationAttachment attachment = attachment("att-keep", "item-1", "/uploads/item-1/proof.pdf");
        AwardCategory category = category("ability");

        when(declarationMapper.selectOne(any(Wrapper.class))).thenReturn(declaration);
        when(itemMapper.selectList(any())).thenReturn(List.of(item), List.of(item));
        when(awardCategoryMapper.selectOne(any())).thenReturn(category);
        when(attachmentMapper.selectList(any())).thenReturn(List.of(attachment), List.of(attachment), List.of(attachment));
        when(auditRecordMapper.selectList(any())).thenReturn(List.of());

        DeclarationVO result = service.saveDeclaration(requestWithExistingAttachment(null, "att-keep"), null);

        verify(itemMapper).updateById(org.mockito.ArgumentMatchers.argThat(updated -> "item-1".equals(updated.getId())));
        verify(itemMapper, never()).deleteById("item-1");
        verify(itemMapper, never()).insert(any(DeclarationItem.class));
        verify(attachmentMapper, never()).deleteById("att-keep");
        assertThat(result.getItems().get(0).getId()).isEqualTo("item-1");
        assertThat(result.getItems().get(0).getAttachments()).extracting(DeclarationVO.AttachmentVO::getId)
                .containsExactly("att-keep");
    }

    @Test
    void saveDeclaration_deletesExistingAttachmentMissingFromRequest() throws Exception {
        setCurrentUser("student-1", "student");
        ReflectionTestUtils.setField(service, "uploadPath", uploadDir.toString());

        Files.createDirectories(uploadDir.resolve("item-1"));
        Path proof = uploadDir.resolve("item-1").resolve("proof.pdf");
        Files.write(proof, "proof".getBytes());

        Declaration declaration = declaration("decl-1", "student-1", "draft");
        DeclarationItem item = item("item-1", "decl-1");
        DeclarationAttachment attachment = attachment("att-delete", "item-1", "/uploads/item-1/proof.pdf");
        AwardCategory category = category("ability");

        when(declarationMapper.selectOne(any(Wrapper.class))).thenReturn(declaration);
        when(itemMapper.selectList(any())).thenReturn(List.of(item), List.of(item));
        when(awardCategoryMapper.selectOne(any())).thenReturn(category);
        when(attachmentMapper.selectList(any())).thenReturn(List.of(attachment), List.of(attachment), List.of());
        when(auditRecordMapper.selectList(any())).thenReturn(List.of());

        DeclarationVO result = service.saveDeclaration(requestWithoutAttachments("item-1"), null);

        verify(attachmentMapper).deleteById("att-delete");
        assertThat(Files.exists(proof)).isFalse();
        assertThat(result.getItems().get(0).getAttachments()).isEmpty();
    }

    @Test
    void saveDeclaration_keepsExistingAttachmentAndAddsUploadedAttachment() throws Exception {
        setCurrentUser("student-1", "student");
        ReflectionTestUtils.setField(service, "uploadPath", uploadDir.toString());

        Declaration declaration = declaration("decl-1", "student-1", "draft");
        DeclarationItem item = item("item-1", "decl-1");
        DeclarationAttachment existing = attachment("att-existing", "item-1", "/uploads/item-1/old.pdf");
        DeclarationAttachment uploaded = attachment("att-uploaded", "item-1", "/uploads/item-1/new.pdf");
        AwardCategory category = category("ability");

        when(declarationMapper.selectOne(any(Wrapper.class))).thenReturn(declaration);
        when(itemMapper.selectList(any())).thenReturn(List.of(item), List.of(item));
        when(awardCategoryMapper.selectOne(any())).thenReturn(category);
        when(attachmentMapper.selectList(any())).thenReturn(List.of(existing), List.of(existing), List.of(existing, uploaded));
        when(auditRecordMapper.selectList(any())).thenReturn(List.of());

        MockMultipartFile proof = new MockMultipartFile(
                "files", "new.pdf", "application/pdf", "new".getBytes());

        DeclarationVO result = service.saveDeclaration(
                requestWithExistingAndNewAttachment("item-1", "att-existing"),
                new MockMultipartFile[]{proof});

        verify(attachmentMapper, never()).deleteById("att-existing");
        verify(attachmentMapper, times(1)).insert(any(DeclarationAttachment.class));
        assertThat(result.getItems().get(0).getAttachments()).extracting(DeclarationVO.AttachmentVO::getId)
                .containsExactly("att-existing", "att-uploaded");
    }

    @Test
    void deleteDeclaration_cancelsPendingAssignmentsAndDeletesItemsWithAttachments() {
        setCurrentUser("student-1", "student");
        ReflectionTestUtils.setField(service, "uploadPath", uploadDir.toString());

        Declaration declaration = declaration("decl-1", "student-1", "draft");

        DeclarationItem item = new DeclarationItem();
        item.setId("item-1");
        item.setDeclarationId("decl-1");

        DeclarationAttachment attachment = new DeclarationAttachment();
        attachment.setId("att-1");
        attachment.setDeclarationItemId("item-1");
        attachment.setFilePath("/uploads/item-1/proof.pdf");

        when(declarationMapper.selectById("decl-1")).thenReturn(declaration);
        when(itemMapper.selectList(any())).thenReturn(List.of(item));
        when(attachmentMapper.selectList(any())).thenReturn(List.of(attachment));

        service.deleteDeclaration("decl-1");

        verify(assignmentMapper).cancelPendingByDeclarationId("decl-1");
        verify(attachmentMapper).deleteById("att-1");
        verify(itemMapper).deleteById("item-1");
        verify(declarationMapper).deleteById("decl-1");
    }

    private static DeclarationSaveRequest requestWithExistingAttachment(String itemId, String attachmentId) {
        DeclarationSaveRequest.AttachmentData attachment = new DeclarationSaveRequest.AttachmentData();
        attachment.setId(attachmentId);

        DeclarationSaveRequest.ItemData item = baseItem(itemId);
        item.setAttachments(List.of(attachment));

        DeclarationSaveRequest request = new DeclarationSaveRequest();
        request.setBatchId("batch-1");
        request.setItems(List.of(item));
        return request;
    }

    private static DeclarationSaveRequest requestWithoutAttachments(String itemId) {
        DeclarationSaveRequest.ItemData item = baseItem(itemId);
        item.setAttachments(List.of());

        DeclarationSaveRequest request = new DeclarationSaveRequest();
        request.setBatchId("batch-1");
        request.setItems(List.of(item));
        return request;
    }

    private static DeclarationSaveRequest requestWithExistingAndNewAttachment(String itemId, String attachmentId) {
        DeclarationSaveRequest.AttachmentData existing = new DeclarationSaveRequest.AttachmentData();
        existing.setId(attachmentId);

        DeclarationSaveRequest.AttachmentData uploaded = new DeclarationSaveRequest.AttachmentData();
        uploaded.setFileIndex(0);

        DeclarationSaveRequest.ItemData item = baseItem(itemId);
        item.setAttachments(List.of(existing, uploaded));

        DeclarationSaveRequest request = new DeclarationSaveRequest();
        request.setBatchId("batch-1");
        request.setItems(List.of(item));
        return request;
    }

    private static DeclarationSaveRequest.ItemData baseItem(String itemId) {
        DeclarationSaveRequest.ItemData item = new DeclarationSaveRequest.ItemData();
        item.setId(itemId);
        item.setCategory("ability");
        item.setCustomAwardName("Competition");
        item.setCustomBaseScore(new BigDecimal("1.0"));
        item.setSortOrder(0);
        return item;
    }

    private static DeclarationSaveRequest requestWithOneAttachment() {
        DeclarationSaveRequest.AttachmentData attachment = new DeclarationSaveRequest.AttachmentData();
        attachment.setFileIndex(0);

        DeclarationSaveRequest.ItemData item = new DeclarationSaveRequest.ItemData();
        item.setCategory("ability");
        item.setCustomAwardName("Competition");
        item.setCustomBaseScore(new BigDecimal("1.0"));
        item.setSortOrder(0);
        item.setAttachments(List.of(attachment));

        DeclarationSaveRequest request = new DeclarationSaveRequest();
        request.setBatchId("batch-1");
        request.setItems(List.of(item));
        return request;
    }

    private static DeclarationSaveRequest requestWithBlankCategoryPresetAward() {
        DeclarationSaveRequest.ItemData item = new DeclarationSaveRequest.ItemData();
        item.setCategory("");
        item.setAwardId("award-1");
        item.setLevelId("level-1");
        item.setSortOrder(0);
        item.setAttachments(List.of());

        DeclarationSaveRequest request = new DeclarationSaveRequest();
        request.setBatchId("batch-1");
        request.setItems(List.of(item));
        return request;
    }

    private static DeclarationSaveRequest requestWithBlankCategoryCustomAward() {
        DeclarationSaveRequest.ItemData item = new DeclarationSaveRequest.ItemData();
        item.setCategory("");
        item.setCustomAwardName("Custom");
        item.setCustomBaseScore(new BigDecimal("1.0"));
        item.setSortOrder(0);
        item.setAttachments(List.of());

        DeclarationSaveRequest request = new DeclarationSaveRequest();
        request.setBatchId("batch-1");
        request.setItems(List.of(item));
        return request;
    }

    private static Declaration declaration(String id, String studentId, String status) {
        Declaration declaration = new Declaration();
        declaration.setId(id);
        declaration.setBatchId("batch-1");
        declaration.setStudentId(studentId);
        declaration.setStatus(status);
        return declaration;
    }

    private static DeclarationItem item(String id, String declarationId) {
        DeclarationItem item = new DeclarationItem();
        item.setId(id);
        item.setDeclarationId(declarationId);
        item.setCategory("ability");
        item.setCustomAwardName("Competition");
        item.setCustomBaseScore(new BigDecimal("1.0"));
        return item;
    }

    private static DeclarationAttachment attachment(String id, String itemId, String filePath) {
        DeclarationAttachment attachment = new DeclarationAttachment();
        attachment.setId(id);
        attachment.setDeclarationItemId(itemId);
        attachment.setFileName(filePath.substring(filePath.lastIndexOf('/') + 1));
        attachment.setFilePath(filePath);
        attachment.setFileSize(100L);
        attachment.setMimeType("application/pdf");
        return attachment;
    }

    private static AwardCategory category(String code) {
        AwardCategory category = new AwardCategory();
        category.setCode(code);
        return category;
    }

    private static void setCurrentUser(String id, String role) {
        LoginUser loginUser = new LoginUser();
        loginUser.setId(id);
        loginUser.setRole(role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));
    }
}
