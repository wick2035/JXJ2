package com.eval.jxj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eval.jxj.common.exception.BizException;
import com.eval.jxj.dto.request.DeclarationSaveRequest;
import com.eval.jxj.dto.response.DeclarationVO;
import com.eval.jxj.entity.*;
import com.eval.jxj.mapper.*;
import com.eval.jxj.service.BatchAssignmentService;
import com.eval.jxj.service.DeclarationService;
import com.eval.jxj.service.ScoreCalculationService;
import com.eval.jxj.util.SecurityUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DeclarationServiceImpl implements DeclarationService {

    private final DeclarationMapper declarationMapper;
    private final DeclarationItemMapper itemMapper;
    private final DeclarationAttachmentMapper attachmentMapper;
    private final AuditRecordMapper auditRecordMapper;
    private final SysUserMapper userMapper;
    private final EvalBatchMapper batchMapper;
    private final AwardMapper awardMapper;
    private final AwardCategoryMapper awardCategoryMapper;
    private final AwardLevelDefMapper levelDefMapper;
    private final BatchBasicScoreMapper basicScoreMapper;
    private final ScoreCalculationService scoreService;
    private final BatchAssignmentService assignmentService;
    private final AuditAssignmentMapper assignmentMapper;

    @Value("${upload.path}")
    private String uploadPath;

    public DeclarationServiceImpl(DeclarationMapper declarationMapper, DeclarationItemMapper itemMapper,
                                  DeclarationAttachmentMapper attachmentMapper, AuditRecordMapper auditRecordMapper,
                                  SysUserMapper userMapper, EvalBatchMapper batchMapper,
                                  AwardMapper awardMapper, AwardCategoryMapper awardCategoryMapper,
                                  AwardLevelDefMapper levelDefMapper,
                                  BatchBasicScoreMapper basicScoreMapper,
                                  ScoreCalculationService scoreService,
                                  BatchAssignmentService assignmentService,
                                  AuditAssignmentMapper assignmentMapper) {
        this.declarationMapper = declarationMapper;
        this.itemMapper = itemMapper;
        this.attachmentMapper = attachmentMapper;
        this.auditRecordMapper = auditRecordMapper;
        this.userMapper = userMapper;
        this.batchMapper = batchMapper;
        this.awardMapper = awardMapper;
        this.awardCategoryMapper = awardCategoryMapper;
        this.levelDefMapper = levelDefMapper;
        this.basicScoreMapper = basicScoreMapper;
        this.scoreService = scoreService;
        this.assignmentService = assignmentService;
        this.assignmentMapper = assignmentMapper;
    }

    @Override
    public Page<DeclarationVO> listDeclarations(String batchId, String status, String keyword, int page, int size) {
        LambdaQueryWrapper<Declaration> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(batchId)) {
            wrapper.eq(Declaration::getBatchId, batchId);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(Declaration::getStatus, status);
        }
        String role = SecurityUtil.getCurrentRole();
        if ("student".equals(role)) {
            wrapper.eq(Declaration::getStudentId, SecurityUtil.getCurrentUserId());
        } else if ("teacher".equals(role)) {
            // 教师只能看到分配给自己或自己审核过的申报
            String uid = SecurityUtil.getCurrentUserId();
            wrapper.and(w -> w
                    .apply("exists (select 1 from audit_assignment aa where aa.declaration_id = declaration.id and aa.reviewer_id = {0})", uid)
                    .or()
                    .apply("exists (select 1 from audit_record ar where ar.declaration_id = declaration.id and ar.reviewer_id = {0})", uid));
        }
        wrapper.orderByDesc(Declaration::getCreatedAt);

        Page<Declaration> result = declarationMapper.selectPage(new Page<>(page, size), wrapper);
        Page<DeclarationVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::toSimpleVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public DeclarationVO getDeclaration(String id) {
        Declaration decl = declarationMapper.selectById(id);
        if (decl == null) throw new BizException("申报记录不存在");
        assertCanViewDeclaration(decl);
        return toDetailVO(decl);
    }

    @Override
    @Transactional
    public DeclarationVO saveDeclaration(DeclarationSaveRequest request) {
        try {
            return saveDeclaration(request, null);
        } catch (IOException e) {
            throw new BizException("附件保存失败");
        }
    }

    @Override
    @Transactional
    public DeclarationVO saveDeclaration(DeclarationSaveRequest request, MultipartFile[] files) throws IOException {
        String studentId = SecurityUtil.getCurrentUserId();

        // Find or create declaration
        Declaration decl = declarationMapper.selectOne(
                new LambdaQueryWrapper<Declaration>()
                        .eq(Declaration::getBatchId, request.getBatchId())
                        .eq(Declaration::getStudentId, studentId));

        if (decl == null) {
            EvalBatch batch = batchMapper.selectById(request.getBatchId());
            if (batch == null || !isBatchPublished(batch.getStatus())) {
                throw new BizException("批次不存在或未开放");
            }
            Declaration deleted = declarationMapper.selectDeletedByBatchAndStudent(request.getBatchId(), studentId);
            if (deleted != null) {
                assignmentMapper.cancelPendingByDeclarationId(deleted.getId());
                declarationMapper.restoreDeleted(deleted.getId());
                deleted.setIsDeleted(0);
                deleted.setStatus("draft");
                deleted.setTotalScore(null);
                deleted.setMoralityScore(null);
                deleted.setAbilityScore(null);
                deleted.setSportsScore(null);
                deleted.setSubmittedAt(null);
                deleteItemsWithAttachments(deleted.getId());
                decl = deleted;
            } else {
                decl = new Declaration();
                decl.setBatchId(request.getBatchId());
                decl.setStudentId(studentId);
                decl.setStatus("draft");
                declarationMapper.insert(decl);
            }
        } else if (!"draft".equals(decl.getStatus()) && !"returned".equals(decl.getStatus())) {
            throw new BizException("当前状态不可编辑");
        }

        List<DeclarationItem> existingItems = itemMapper.selectList(
                new LambdaQueryWrapper<DeclarationItem>().eq(DeclarationItem::getDeclarationId, decl.getId()));
        Map<String, DeclarationItem> existingById = existingItems.stream()
                .filter(item -> StringUtils.hasText(item.getId()))
                .collect(Collectors.toMap(DeclarationItem::getId, item -> item));
        Map<String, String> attachmentOwnerItemIds = loadAttachmentOwnerItemIds(existingItems);
        restoreMissingItemIdsFromAttachments(request.getItems(), attachmentOwnerItemIds);
        Set<String> requestedItemIds = new HashSet<>();
        if (request.getItems() != null) {
            for (DeclarationSaveRequest.ItemData itemData : request.getItems()) {
                if (StringUtils.hasText(itemData.getId())) {
                    requestedItemIds.add(itemData.getId());
                }
            }
        }

        for (DeclarationItem existing : existingItems) {
            if (!requestedItemIds.contains(existing.getId())) {
                deleteItemWithAttachments(existing);
            }
        }

        if (request.getItems() != null) {
            for (DeclarationSaveRequest.ItemData itemData : request.getItems()) {
                DeclarationItem item = StringUtils.hasText(itemData.getId())
                        ? existingById.get(itemData.getId()) : null;
                boolean isNew = item == null;
                if (isNew) {
                    item = new DeclarationItem();
                }
                String category = resolveItemCategory(itemData);
                validateCategoryExists(category);
                item.setDeclarationId(decl.getId());
                item.setCategory(category);
                item.setAwardId(itemData.getAwardId());
                item.setLevelId(itemData.getLevelId());
                item.setCustomAwardName(itemData.getCustomAwardName());
                item.setCustomLevelName(itemData.getCustomLevelName());
                item.setCustomBaseScore(itemData.getCustomBaseScore());
                item.setUseDowngrade(itemData.getUseDowngrade() != null ? itemData.getUseDowngrade() : 0);
                item.setDescription(itemData.getDescription());
                item.setSortOrder(itemData.getSortOrder() != null ? itemData.getSortOrder() : 0);

                scoreService.computeItemScore(item, decl.getBatchId());
                if (isNew) {
                    itemMapper.insert(item);
                } else {
                    itemMapper.updateById(item);
                }
                syncAttachments(item.getId(), itemData.getAttachments(), files);
            }
        }

        scoreService.computeDeclarationScore(decl);
        return toDetailVO(decl);
    }

    @Override
    @Transactional
    public DeclarationVO submitDeclaration(String id) {
        Declaration decl = declarationMapper.selectById(id);
        if (decl == null) throw new BizException("申报记录不存在");
        if (!"draft".equals(decl.getStatus()) && !"returned".equals(decl.getStatus())) {
            throw new BizException("当前状态不可提交");
        }

        EvalBatch batch = batchMapper.selectById(decl.getBatchId());
        if (batch == null) {
            throw new BizException("批次未开放");
        }
        // 退回(returned)的申报可随时重新提交；其余需批次已发布。
        // open_timed 还要落在 [startDate, endDate] 窗口内；open 为长期开放，不校验日期。
        boolean returned = "returned".equals(decl.getStatus());
        if (!returned) {
            String batchStatus = batch.getStatus();
            boolean timed = "open_timed".equals(batchStatus);
            if (!timed && !"open".equals(batchStatus)) {
                throw new BizException("批次未开放");
            }
            if (timed) {
                LocalDate today = LocalDate.now();
                if (batch.getStartDate() != null && today.isBefore(batch.getStartDate())) {
                    throw new BizException("申报尚未开始");
                }
                if (batch.getEndDate() != null && today.isAfter(batch.getEndDate())) {
                    throw new BizException("申报已截止");
                }
            }
        }

        // Recompute scores
        List<DeclarationItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<DeclarationItem>().eq(DeclarationItem::getDeclarationId, id));
        validateItemAttachments(items);
        for (DeclarationItem item : items) {
            scoreService.computeItemScore(item, decl.getBatchId());
            itemMapper.updateById(item);
        }
        scoreService.computeDeclarationScore(decl);

        decl.setStatus("submitted");
        decl.setSubmittedAt(LocalDateTime.now());
        declarationMapper.updateById(decl);
        assignmentService.generateForDeclaration(decl.getId(), false);
        return toDetailVO(decl);
    }

    @Override
    @Transactional
    public DeclarationVO submitDeclaration(DeclarationSaveRequest request, MultipartFile[] files) throws IOException {
        DeclarationVO saved = saveDeclaration(request, files);
        return submitDeclaration(saved.getId());
    }

    @Override
    @Transactional
    public DeclarationVO withdrawDeclaration(String id) {
        Declaration decl = declarationMapper.selectById(id);
        if (decl == null) throw new BizException("申报记录不存在");

        if (!"student".equals(SecurityUtil.getCurrentRole())
                || !String.valueOf(decl.getStudentId()).equals(SecurityUtil.getCurrentUserId())) {
            throw new BizException("无权操作");
        }
        if (!"submitted".equals(decl.getStatus())) {
            throw new BizException("当前状态不可撤回");
        }

        long auditCount = countAuditRecords(decl.getId());
        if (auditCount > 0) {
            throw new BizException("申报已进入审核，无法撤回");
        }

        assignmentMapper.cancelPendingByDeclarationId(id);
        declarationMapper.update(null, new LambdaUpdateWrapper<Declaration>()
                .eq(Declaration::getId, id)
                .set(Declaration::getStatus, "draft")
                .set(Declaration::getSubmittedAt, null));

        Declaration reloaded = declarationMapper.selectById(id);
        return toDetailVO(reloaded != null ? reloaded : decl);
    }

    @Override
    @Transactional
    public void deleteDeclaration(String id) {
        Declaration decl = declarationMapper.selectById(id);
        if (decl == null) throw new BizException("申报记录不存在");

        String role = SecurityUtil.getCurrentRole();
        if ("student".equals(role)) {
            if (!String.valueOf(decl.getStudentId()).equals(SecurityUtil.getCurrentUserId())) {
                throw new BizException("无权操作");
            }
            if (!"draft".equals(decl.getStatus()) && !"submitted".equals(decl.getStatus())) {
                throw new BizException("已审核的记录不可删除");
            }
        } else if (!"admin".equals(role)) {
            throw new BizException("无权删除该申报");
        }
        assignmentMapper.cancelPendingByDeclarationId(id);
        deleteItemsWithAttachments(id);
        declarationMapper.deleteById(id);
    }

    /** 批次是否已发布（可填报）：按日期开放(open_timed) 或 长期开放(open) 均视为已发布。 */
    private boolean isBatchPublished(String status) {
        return "open_timed".equals(status) || "open".equals(status);
    }

    private DeclarationVO toSimpleVO(Declaration decl) {
        DeclarationVO vo = new DeclarationVO();
        BeanUtils.copyProperties(decl, vo);

        SysUser student = userMapper.selectById(decl.getStudentId());
        if (student != null) {
            vo.setStudentName(student.getName());
            vo.setStudentLoginId(student.getLoginId());
        }

        EvalBatch batch = batchMapper.selectById(decl.getBatchId());
        if (batch != null) {
            vo.setBatchName(batch.getName());
        }
        populateClassRank(decl, student, vo);
        populateWorkflowState(decl, vo);
        return vo;
    }

    /** 学生本人视角下，已通过(approved)申报在同班级(年级+班级)中的最终总分排名；其余情况留空。 */
    private void populateClassRank(Declaration decl, SysUser student, DeclarationVO vo) {
        if (!"student".equals(SecurityUtil.getCurrentRole())
                || !"approved".equals(decl.getStatus())
                || decl.getTotalScore() == null
                || student == null
                || !StringUtils.hasText(student.getClassName())) {
            return;
        }
        int total = declarationMapper.countApprovedClassmates(
                decl.getBatchId(), student.getGrade(), student.getClassName());
        int ahead = declarationMapper.countApprovedClassmatesAhead(
                decl.getBatchId(), student.getGrade(), student.getClassName(), decl.getTotalScore());
        vo.setClassRankTotal(total);
        vo.setClassRank(ahead + 1);
    }

    private void populateWorkflowState(Declaration decl, DeclarationVO vo) {
        if (!"submitted".equals(decl.getStatus())) {
            vo.setStage(computeStage(decl, 0));
            vo.setCanWithdraw(false);
            return;
        }

        long auditCount = countAuditRecords(decl.getId());
        vo.setStage(computeStage(decl, auditCount));
        vo.setCanWithdraw(auditCount == 0);
    }

    private String computeStage(Declaration decl, long auditCount) {
        String status = decl.getStatus();
        if ("draft".equals(status)) {
            return "pending_submit";
        }
        if ("submitted".equals(status)) {
            if (auditCount > 0) {
                return "reviewing";
            }
            return assignmentMapper.countByDeclarationId(decl.getId()) > 0 ? "assigned" : "submitted_unassigned";
        }
        if ("approved".equals(status)) {
            return "approved";
        }
        if ("rejected".equals(status) || "returned".equals(status)) {
            return "rejected";
        }
        return status;
    }

    private long countAuditRecords(String declarationId) {
        Long count = auditRecordMapper.selectCount(new LambdaQueryWrapper<AuditRecord>()
                .eq(AuditRecord::getDeclarationId, declarationId));
        return count == null ? 0 : count;
    }

    private void assertCanViewDeclaration(Declaration decl) {
        String role = SecurityUtil.getCurrentRole();
        String userId = SecurityUtil.getCurrentUserId();
        if ("admin".equals(role)) {
            return;
        }
        if ("student".equals(role) && String.valueOf(decl.getStudentId()).equals(userId)) {
            return;
        }
        if ("teacher".equals(role)) {
            boolean hasAssignment = assignmentMapper.selectByDeclarationAndReviewer(decl.getId(), userId) != null;
            boolean hasAuditRecord = auditRecordMapper.countByDeclarationAndReviewer(decl.getId(), userId) > 0;
            if (hasAssignment || hasAuditRecord) {
                return;
            }
        }
        throw new BizException("无权查看当前申报");
    }

    private void validateItemAttachments(List<DeclarationItem> items) {
        for (DeclarationItem item : items) {
            Long count = attachmentMapper.selectCount(new LambdaQueryWrapper<DeclarationAttachment>()
                    .eq(DeclarationAttachment::getDeclarationItemId, item.getId()));
            if (count == null || count == 0) {
                throw new BizException("申报明细必须上传附件");
            }
        }
    }

    private void syncAttachments(String itemId, List<DeclarationSaveRequest.AttachmentData> requested,
                                 MultipartFile[] files) throws IOException {
        if (requested == null) {
            return;
        }
        if (!StringUtils.hasText(itemId)) {
            throw new BizException("申报明细保存失败，无法绑定附件");
        }

        List<DeclarationAttachment> existing = attachmentMapper.selectList(
                new LambdaQueryWrapper<DeclarationAttachment>()
                        .eq(DeclarationAttachment::getDeclarationItemId, itemId));
        Set<String> keepIds = requested.stream()
                .map(DeclarationSaveRequest.AttachmentData::getId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        Set<String> existingIds = existing.stream()
                .map(DeclarationAttachment::getId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        for (String keepId : keepIds) {
            if (!existingIds.contains(keepId)) {
                throw new BizException("附件不存在或不属于当前明细，请刷新后重试");
            }
        }

        for (DeclarationAttachment attachment : existing) {
            if (!keepIds.contains(attachment.getId())) {
                deleteAttachment(attachment);
            }
        }

        MultipartFile[] uploadFiles = files == null ? new MultipartFile[0] : files;
        for (DeclarationSaveRequest.AttachmentData attachmentData : requested) {
            if (attachmentData.getFileIndex() == null) {
                continue;
            }
            int fileIndex = attachmentData.getFileIndex();
            if (fileIndex < 0 || fileIndex >= uploadFiles.length) {
                throw new BizException("附件索引无效");
            }
            saveNewAttachment(itemId, uploadFiles[fileIndex]);
        }
    }

    private Map<String, String> loadAttachmentOwnerItemIds(List<DeclarationItem> existingItems) {
        Map<String, String> ownerItemIds = new HashMap<>();
        for (DeclarationItem item : existingItems) {
            if (!StringUtils.hasText(item.getId())) {
                continue;
            }
            List<DeclarationAttachment> attachments = attachmentMapper.selectList(
                    new LambdaQueryWrapper<DeclarationAttachment>()
                            .eq(DeclarationAttachment::getDeclarationItemId, item.getId()));
            for (DeclarationAttachment attachment : attachments) {
                if (StringUtils.hasText(attachment.getId())) {
                    ownerItemIds.put(attachment.getId(), item.getId());
                }
            }
        }
        return ownerItemIds;
    }

    private void restoreMissingItemIdsFromAttachments(List<DeclarationSaveRequest.ItemData> items,
                                                      Map<String, String> attachmentOwnerItemIds) {
        if (items == null || items.isEmpty() || attachmentOwnerItemIds.isEmpty()) {
            return;
        }
        Set<String> usedItemIds = items.stream()
                .map(DeclarationSaveRequest.ItemData::getId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        for (DeclarationSaveRequest.ItemData itemData : items) {
            if (StringUtils.hasText(itemData.getId()) || itemData.getAttachments() == null) {
                continue;
            }
            String inferredItemId = null;
            for (DeclarationSaveRequest.AttachmentData attachmentData : itemData.getAttachments()) {
                if (!StringUtils.hasText(attachmentData.getId())) {
                    continue;
                }
                String ownerItemId = attachmentOwnerItemIds.get(attachmentData.getId());
                if (!StringUtils.hasText(ownerItemId)) {
                    continue;
                }
                if (inferredItemId == null) {
                    inferredItemId = ownerItemId;
                } else if (!inferredItemId.equals(ownerItemId)) {
                    throw new BizException("附件归属不一致，请刷新后重试");
                }
            }
            if (StringUtils.hasText(inferredItemId)) {
                if (usedItemIds.contains(inferredItemId)) {
                    throw new BizException("附件重复绑定，请刷新后重试");
                }
                itemData.setId(inferredItemId);
                usedItemIds.add(inferredItemId);
            }
        }
    }

    private void deleteItemWithAttachments(DeclarationItem item) {
        List<DeclarationAttachment> attachments = attachmentMapper.selectList(
                new LambdaQueryWrapper<DeclarationAttachment>()
                        .eq(DeclarationAttachment::getDeclarationItemId, item.getId()));
        for (DeclarationAttachment attachment : attachments) {
            deleteAttachment(attachment);
        }
        itemMapper.deleteById(item.getId());
    }

    private void deleteItemsWithAttachments(String declarationId) {
        List<DeclarationItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<DeclarationItem>()
                        .eq(DeclarationItem::getDeclarationId, declarationId));
        for (DeclarationItem item : items) {
            deleteItemWithAttachments(item);
        }
    }

    private void deleteAttachment(DeclarationAttachment attachment) {
        deletePhysicalFile(attachment.getFilePath());
        attachmentMapper.deleteById(attachment.getId());
    }

    private void saveNewAttachment(String itemId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BizException("附件不能为空");
        }
        validateAttachmentFile(file);

        String originalName = file.getOriginalFilename();
        String storedName = UUID.randomUUID() + extractExtension(originalName);
        File dir = new File(uploadPath, itemId).getAbsoluteFile();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new BizException("附件目录创建失败");
        }
        File target = new File(dir, storedName);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        DeclarationAttachment attachment = new DeclarationAttachment();
        attachment.setDeclarationItemId(itemId);
        attachment.setFileName(originalName);
        attachment.setFilePath("/uploads/" + itemId + "/" + storedName);
        attachment.setFileSize(file.getSize());
        attachment.setMimeType(file.getContentType());
        attachmentMapper.insert(attachment);
    }

    private void validateAttachmentFile(MultipartFile file) {
        String contentType = file.getContentType();
        String originalName = file.getOriginalFilename();
        String ext = extractExtension(originalName).toLowerCase();
        boolean image = contentType != null && contentType.toLowerCase().startsWith("image/");
        boolean pdf = "application/pdf".equalsIgnoreCase(contentType) || ".pdf".equals(ext);
        boolean imageExt = Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp").contains(ext);
        if (!image && !pdf && !imageExt) {
            throw new BizException("附件仅支持图片或PDF文件");
        }
    }

    private String extractExtension(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf("."));
    }

    private void deletePhysicalFile(String filePath) {
        if (!StringUtils.hasText(filePath) || !filePath.startsWith("/uploads/")) {
            return;
        }
        try {
            File root = new File(uploadPath).getCanonicalFile();
            String relativePath = filePath.substring("/uploads/".length());
            File target = new File(root, relativePath).getCanonicalFile();
            if (target.getPath().startsWith(root.getPath()) && target.isFile()) {
                target.delete();
            }
        } catch (IOException ignored) {
            // Database state is authoritative; stale files can be cleaned separately.
        }
    }

    private DeclarationVO toDetailVO(Declaration decl) {
        DeclarationVO vo = toSimpleVO(decl);

        // Load items
        List<DeclarationItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<DeclarationItem>()
                        .eq(DeclarationItem::getDeclarationId, decl.getId())
                        .orderByAsc(DeclarationItem::getSortOrder));

        // Pre-load award and level names
        Map<String, String> awardNames = new java.util.HashMap<>();
        Map<String, String> levelNames = new java.util.HashMap<>();
        for (DeclarationItem item : items) {
            if (item.getAwardId() != null && !awardNames.containsKey(item.getAwardId())) {
                Award award = awardMapper.selectById(item.getAwardId());
                if (award != null) awardNames.put(item.getAwardId(), award.getName());
            }
            if (item.getLevelId() != null && !levelNames.containsKey(item.getLevelId())) {
                AwardLevelDef level = levelDefMapper.selectById(item.getLevelId());
                if (level != null) levelNames.put(item.getLevelId(), level.getName());
            }
        }

        vo.setItems(items.stream().map(item -> {
            DeclarationVO.DeclarationItemVO ivo = new DeclarationVO.DeclarationItemVO();
            BeanUtils.copyProperties(item, ivo);
            ivo.setAwardName(awardNames.get(item.getAwardId()));
            ivo.setLevelName(levelNames.get(item.getLevelId()));

            List<DeclarationAttachment> atts = attachmentMapper.selectList(
                    new LambdaQueryWrapper<DeclarationAttachment>()
                            .eq(DeclarationAttachment::getDeclarationItemId, item.getId()));
            ivo.setAttachments(atts.stream().map(a -> {
                DeclarationVO.AttachmentVO avo = new DeclarationVO.AttachmentVO();
                BeanUtils.copyProperties(a, avo);
                return avo;
            }).collect(Collectors.toList()));
            return ivo;
        }).collect(Collectors.toList()));

        List<DeclarationVO.BasicItemVO> basicItems = loadBasicItems(decl);
        vo.setBasicItems(basicItems);

        Map<String, java.math.BigDecimal> categoryTotals = items.stream()
                .collect(Collectors.groupingBy(DeclarationItem::getCategory,
                        Collectors.reducing(java.math.BigDecimal.ZERO,
                                item -> item.getFinalScore() != null ? item.getFinalScore() : java.math.BigDecimal.ZERO,
                                java.math.BigDecimal::add)));
        for (DeclarationVO.BasicItemVO basicItem : basicItems) {
            categoryTotals.merge(
                    basicItem.getCategory(),
                    basicItem.getFinalScore() != null ? basicItem.getFinalScore() : java.math.BigDecimal.ZERO,
                    java.math.BigDecimal::add);
        }
        vo.setCategoryScores(categoryTotals
                .entrySet()
                .stream()
                .map(entry -> {
                    DeclarationVO.CategoryScoreVO score = new DeclarationVO.CategoryScoreVO();
                    score.setCategory(entry.getKey());
                    score.setRawScore(entry.getValue());
                    return score;
                })
                .collect(Collectors.toList()));

        // Load audit records
        List<AuditRecord> audits = auditRecordMapper.selectList(
                new LambdaQueryWrapper<AuditRecord>()
                        .eq(AuditRecord::getDeclarationId, decl.getId())
                        .orderByDesc(AuditRecord::getCreatedAt));
        vo.setAuditRecords(audits.stream().map(a -> {
            DeclarationVO.AuditRecordVO arvo = new DeclarationVO.AuditRecordVO();
            BeanUtils.copyProperties(a, arvo);
            SysUser reviewer = userMapper.selectById(a.getReviewerId());
            if (reviewer != null) arvo.setReviewerName(reviewer.getName());
            return arvo;
        }).collect(Collectors.toList()));

        return vo;
    }

    private List<DeclarationVO.BasicItemVO> loadBasicItems(Declaration decl) {
        List<Award> basicAwards = awardMapper.selectBatchBasicAwards(decl.getBatchId());
        if (basicAwards == null || basicAwards.isEmpty()) {
            return List.of();
        }
        List<BatchBasicScore> scores = basicScoreMapper.selectStudentScores(decl.getBatchId(), decl.getStudentId());
        Map<String, java.math.BigDecimal> scoreByAwardId = (scores == null ? List.<BatchBasicScore>of() : scores)
                .stream()
                .collect(Collectors.toMap(BatchBasicScore::getAwardId,
                        score -> score.getScore() != null ? score.getScore() : java.math.BigDecimal.ZERO,
                        (a, b) -> b));
        return basicAwards.stream().map(award -> {
            DeclarationVO.BasicItemVO item = new DeclarationVO.BasicItemVO();
            item.setAwardId(award.getId());
            item.setAwardName(award.getName());
            item.setCategory(award.getCategory());
            java.math.BigDecimal score = scoreByAwardId.getOrDefault(award.getId(), java.math.BigDecimal.ZERO);
            item.setComputedScore(score);
            item.setFinalScore(score);
            item.setSource("basic");
            return item;
        }).collect(Collectors.toList());
    }

    private void validateCategoryExists(String code) {
        if (!StringUtils.hasText(code)) {
            throw new BizException("类别不能为空");
        }
        AwardCategory category = awardCategoryMapper.selectOne(new LambdaQueryWrapper<AwardCategory>()
                .eq(AwardCategory::getCode, code));
        if (category == null) {
            throw new BizException("类别不存在");
        }
    }

    private String resolveItemCategory(DeclarationSaveRequest.ItemData itemData) {
        if (StringUtils.hasText(itemData.getCategory())) {
            return itemData.getCategory();
        }
        if (StringUtils.hasText(itemData.getAwardId())) {
            Award award = awardMapper.selectById(itemData.getAwardId());
            if (award != null && StringUtils.hasText(award.getCategory())) {
                return award.getCategory();
            }
        }
        return itemData.getCategory();
    }
}
