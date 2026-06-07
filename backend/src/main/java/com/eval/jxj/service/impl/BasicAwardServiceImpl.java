package com.eval.jxj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eval.jxj.common.exception.BizException;
import com.eval.jxj.dto.response.BasicAwardImportResult;
import com.eval.jxj.dto.response.BatchBasicAwardVO;
import com.eval.jxj.dto.response.DeclarationVO;
import com.eval.jxj.entity.Award;
import com.eval.jxj.entity.BatchBasicAward;
import com.eval.jxj.entity.BatchBasicScore;
import com.eval.jxj.entity.Declaration;
import com.eval.jxj.entity.SysUser;
import com.eval.jxj.mapper.AwardMapper;
import com.eval.jxj.mapper.BatchBasicAwardMapper;
import com.eval.jxj.mapper.BatchBasicScoreMapper;
import com.eval.jxj.mapper.DeclarationMapper;
import com.eval.jxj.mapper.SysUserMapper;
import com.eval.jxj.service.BasicAwardService;
import com.eval.jxj.service.ScoreCalculationService;
import com.eval.jxj.util.SecurityUtil;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BasicAwardServiceImpl implements BasicAwardService {

    private static final String HEADER_LOGIN_ID = "学号";
    private static final String HEADER_PROJECT = "项目";
    private static final String HEADER_SCORE = "分数";

    private final AwardMapper awardMapper;
    private final BatchBasicAwardMapper batchBasicAwardMapper;
    private final BatchBasicScoreMapper batchBasicScoreMapper;
    private final SysUserMapper userMapper;
    private final DeclarationMapper declarationMapper;
    private final ScoreCalculationService scoreService;

    public BasicAwardServiceImpl(AwardMapper awardMapper,
                                 BatchBasicAwardMapper batchBasicAwardMapper,
                                 BatchBasicScoreMapper batchBasicScoreMapper,
                                 SysUserMapper userMapper,
                                 DeclarationMapper declarationMapper,
                                 ScoreCalculationService scoreService) {
        this.awardMapper = awardMapper;
        this.batchBasicAwardMapper = batchBasicAwardMapper;
        this.batchBasicScoreMapper = batchBasicScoreMapper;
        this.userMapper = userMapper;
        this.declarationMapper = declarationMapper;
        this.scoreService = scoreService;
    }

    @Override
    @Transactional
    public BasicAwardImportResult importScores(String batchId, String category, MultipartFile file) {
        validateImportFile(file);
        List<Award> basicAwards = awardMapper.selectList(new LambdaQueryWrapper<Award>()
                .eq(Award::getCategory, category)
                .eq(Award::getAwardType, "basic"));
        Map<String, Award> awardsByName = basicAwards.stream()
                .collect(Collectors.toMap(Award::getName, award -> award, (a, b) -> a, LinkedHashMap::new));

        List<SysUser> students = userMapper.selectList(new LambdaQueryWrapper<SysUser>().eq(SysUser::getRole, "student"));
        Map<String, SysUser> studentsByLoginId = students.stream()
                .collect(Collectors.toMap(SysUser::getLoginId, user -> user, (a, b) -> a));

        ImportReadResult read = readRows(file, awardsByName, studentsByLoginId);
        BasicAwardImportResult result = read.result;
        result.setProjectCount(read.awardIds.size());
        result.setStudentCount((int) read.validRows.stream().map(row -> row.studentId).distinct().count());
        if (result.getFailedCount() > 0) {
            return result;
        }
        if (read.validRows.isEmpty()) {
            return result;
        }

        List<String> awardIds = new ArrayList<>(read.awardIds);
        batchBasicAwardMapper.deleteByBatchAndAwardIds(batchId, awardIds);
        batchBasicScoreMapper.deleteByBatchAndAwardIds(batchId, awardIds);

        Map<String, Long> countByAward = read.validRows.stream()
                .collect(Collectors.groupingBy(row -> row.awardId, LinkedHashMap::new, Collectors.counting()));
        for (String awardId : awardIds) {
            Award award = read.awardsById.get(awardId);
            BatchBasicAward batchAward = new BatchBasicAward();
            batchAward.setBatchId(batchId);
            batchAward.setAwardId(awardId);
            batchAward.setCategory(award.getCategory());
            batchAward.setImportedBy(SecurityUtil.getCurrentUserId());
            batchAward.setImportedCount(countByAward.getOrDefault(awardId, 0L).intValue());
            batchBasicAwardMapper.insert(batchAward);
        }

        for (ImportRow row : read.validRows) {
            BatchBasicScore score = new BatchBasicScore();
            score.setBatchId(batchId);
            score.setAwardId(row.awardId);
            score.setStudentId(row.studentId);
            score.setScore(row.score);
            batchBasicScoreMapper.insert(score);
            result.addSuccess();
        }

        List<Declaration> declarations = declarationMapper.selectList(new LambdaQueryWrapper<Declaration>()
                .eq(Declaration::getBatchId, batchId));
        for (Declaration declaration : declarations) {
            scoreService.computeDeclarationScore(declaration);
        }
        return result;
    }

    @Override
    public List<BatchBasicAwardVO> listBatchBasicAwards(String batchId, String category) {
        List<Award> awards = awardMapper.selectBatchBasicAwards(batchId).stream()
                .filter(award -> !StringUtils.hasText(category) || category.equals(award.getCategory()))
                .sorted(Comparator.comparing(Award::getCategory).thenComparing(Award::getName))
                .collect(Collectors.toList());
        if (awards.isEmpty()) {
            return List.of();
        }

        List<SysUser> students = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRole, "student")
                .orderByAsc(SysUser::getLoginId));
        List<BatchBasicScore> scores = batchBasicScoreMapper.selectList(new LambdaQueryWrapper<BatchBasicScore>()
                .eq(BatchBasicScore::getBatchId, batchId));
        Map<String, BigDecimal> scoreByAwardStudent = scores.stream()
                .collect(Collectors.toMap(score -> score.getAwardId() + "::" + score.getStudentId(),
                        BatchBasicScore::getScore, (a, b) -> b));
        Map<String, BatchBasicAward> batchAwardByAwardId = batchBasicAwardMapper.selectList(
                        new LambdaQueryWrapper<BatchBasicAward>().eq(BatchBasicAward::getBatchId, batchId))
                .stream()
                .collect(Collectors.toMap(BatchBasicAward::getAwardId, item -> item, (a, b) -> b));

        return awards.stream().map(award -> {
            BatchBasicAwardVO vo = new BatchBasicAwardVO();
            vo.setAwardId(award.getId());
            vo.setAwardName(award.getName());
            vo.setCategory(award.getCategory());
            BatchBasicAward batchAward = batchAwardByAwardId.get(award.getId());
            if (batchAward != null) {
                vo.setImportedCount(batchAward.getImportedCount());
                vo.setUpdatedAt(batchAward.getUpdatedAt());
            }
            vo.setScores(students.stream().map(student -> {
                BatchBasicAwardVO.StudentScoreVO scoreVO = new BatchBasicAwardVO.StudentScoreVO();
                scoreVO.setStudentId(student.getId());
                scoreVO.setStudentLoginId(student.getLoginId());
                scoreVO.setStudentName(student.getName());
                scoreVO.setScore(scoreByAwardStudent.getOrDefault(award.getId() + "::" + student.getId(), BigDecimal.ZERO));
                return scoreVO;
            }).collect(Collectors.toList()));
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public List<DeclarationVO.BasicItemVO> listMyBasicItems(String batchId) {
        String studentId = SecurityUtil.getCurrentUserId();
        if (!StringUtils.hasText(studentId)) {
            return List.of();
        }
        List<Award> awards = awardMapper.selectBatchBasicAwards(batchId);
        if (awards == null || awards.isEmpty()) {
            return List.of();
        }
        List<BatchBasicScore> scores = batchBasicScoreMapper.selectStudentScores(batchId, studentId);
        Map<String, BigDecimal> scoreByAwardId = (scores == null ? List.<BatchBasicScore>of() : scores)
                .stream()
                .collect(Collectors.toMap(BatchBasicScore::getAwardId,
                        score -> score.getScore() != null ? score.getScore() : BigDecimal.ZERO,
                        (a, b) -> b));
        return awards.stream().map(award -> {
            DeclarationVO.BasicItemVO item = new DeclarationVO.BasicItemVO();
            item.setAwardId(award.getId());
            item.setAwardName(award.getName());
            item.setCategory(award.getCategory());
            BigDecimal score = scoreByAwardId.getOrDefault(award.getId(), BigDecimal.ZERO);
            item.setComputedScore(score);
            item.setFinalScore(score);
            item.setSource("basic");
            return item;
        }).collect(Collectors.toList());
    }

    private ImportReadResult readRows(MultipartFile file, Map<String, Award> awardsByName,
                                      Map<String, SysUser> studentsByLoginId) {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            Map<String, Integer> columns = readHeaderColumns(sheet.getRow(0), formatter);
            validateHeaders(columns);

            BasicAwardImportResult result = new BasicAwardImportResult();
            ImportReadResult read = new ImportReadResult(result);
            Set<String> seen = new LinkedHashSet<>();
            int lastRow = sheet.getLastRowNum();
            for (int rowIndex = 1; rowIndex <= lastRow; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (isEmptyRow(row, formatter)) {
                    continue;
                }
                int displayRow = rowIndex + 1;
                String loginId = value(row, columns, formatter, HEADER_LOGIN_ID);
                String project = value(row, columns, formatter, HEADER_PROJECT);
                String scoreText = value(row, columns, formatter, HEADER_SCORE);

                if (!StringUtils.hasText(loginId)) {
                    result.addFailed(displayRow, loginId, project, "学号不能为空");
                    continue;
                }
                if (!StringUtils.hasText(project)) {
                    result.addFailed(displayRow, loginId, project, "项目不能为空");
                    continue;
                }
                String duplicateKey = loginId + "::" + project;
                if (!seen.add(duplicateKey)) {
                    result.addFailed(displayRow, loginId, project, "同一文件内学号和项目重复");
                    continue;
                }
                SysUser student = studentsByLoginId.get(loginId);
                if (student == null) {
                    result.addFailed(displayRow, loginId, project, "学号不存在或不是学生");
                    continue;
                }
                Award award = awardsByName.get(project);
                if (award == null) {
                    result.addFailed(displayRow, loginId, project, "项目不存在、不是基础奖项或不属于所选类目");
                    continue;
                }
                BigDecimal score = parseScore(scoreText);
                if (score == null) {
                    result.addFailed(displayRow, loginId, project, "分数不能为空且必须为非负数字");
                    continue;
                }
                read.validRows.add(new ImportRow(award.getId(), student.getId(), score));
                read.awardIds.add(award.getId());
                read.awardsById.put(award.getId(), award);
            }
            return read;
        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            throw new BizException("读取导入文件失败");
        } catch (Exception e) {
            throw new BizException("导入文件格式不正确");
        }
    }

    private void validateImportFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("导入文件不能为空");
        }
        String fileName = file.getOriginalFilename();
        if (!StringUtils.hasText(fileName)) {
            throw new BizException("导入文件名不能为空");
        }
        String lowerName = fileName.toLowerCase();
        if (!lowerName.endsWith(".xlsx") && !lowerName.endsWith(".xls")) {
            throw new BizException("仅支持 .xls 或 .xlsx 文件");
        }
    }

    private Map<String, Integer> readHeaderColumns(Row headerRow, DataFormatter formatter) {
        if (headerRow == null) {
            throw new BizException("导入模板缺少表头");
        }
        Map<String, Integer> columns = new LinkedHashMap<>();
        short lastCell = headerRow.getLastCellNum();
        for (int cellIndex = 0; cellIndex < lastCell; cellIndex++) {
            String header = cellText(headerRow.getCell(cellIndex), formatter);
            if (StringUtils.hasText(header)) {
                columns.put(header.trim(), cellIndex);
            }
        }
        return columns;
    }

    private void validateHeaders(Map<String, Integer> columns) {
        List<String> missing = List.of(HEADER_LOGIN_ID, HEADER_PROJECT, HEADER_SCORE).stream()
                .filter(header -> !columns.containsKey(header))
                .collect(Collectors.toList());
        if (!missing.isEmpty()) {
            throw new BizException("导入模板缺少必填表头：" + String.join("、", missing));
        }
    }

    private boolean isEmptyRow(Row row, DataFormatter formatter) {
        if (row == null || row.getLastCellNum() < 0) {
            return true;
        }
        for (int i = 0; i < row.getLastCellNum(); i++) {
            if (StringUtils.hasText(cellText(row.getCell(i), formatter))) {
                return false;
            }
        }
        return true;
    }

    private String value(Row row, Map<String, Integer> columns, DataFormatter formatter, String header) {
        Integer cellIndex = columns.get(header);
        if (cellIndex == null) {
            return null;
        }
        String text = cellText(row.getCell(cellIndex), formatter);
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private String cellText(Cell cell, DataFormatter formatter) {
        return cell == null ? "" : formatter.formatCellValue(cell);
    }

    private BigDecimal parseScore(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(text.trim());
            return value.compareTo(BigDecimal.ZERO) < 0 ? null : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static class ImportReadResult {
        private final BasicAwardImportResult result;
        private final List<ImportRow> validRows = new ArrayList<>();
        private final Set<String> awardIds = new LinkedHashSet<>();
        private final Map<String, Award> awardsById = new LinkedHashMap<>();

        private ImportReadResult(BasicAwardImportResult result) {
            this.result = result;
        }
    }

    private static class ImportRow {
        private final String awardId;
        private final String studentId;
        private final BigDecimal score;

        private ImportRow(String awardId, String studentId, BigDecimal score) {
            this.awardId = awardId;
            this.studentId = studentId;
            this.score = score;
        }
    }
}
