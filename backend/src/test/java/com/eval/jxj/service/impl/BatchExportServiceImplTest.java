package com.eval.jxj.service.impl;

import com.eval.jxj.dto.response.BatchEvaluationTableVO;
import com.eval.jxj.dto.response.BatchRankingVO;
import com.eval.jxj.dto.response.BatchStatsVO;
import com.eval.jxj.dto.response.BatchVO;
import com.eval.jxj.entity.Award;
import com.eval.jxj.entity.BatchBasicScore;
import com.eval.jxj.entity.SysUser;
import com.eval.jxj.mapper.AwardMapper;
import com.eval.jxj.mapper.BatchBasicScoreMapper;
import com.eval.jxj.mapper.SysUserMapper;
import com.eval.jxj.service.BatchService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchExportServiceImplTest {

    @Mock
    private BatchService batchService;
    @Mock
    private BatchBasicScoreMapper basicScoreMapper;
    @Mock
    private AwardMapper awardMapper;
    @Mock
    private SysUserMapper userMapper;

    @Test
    void writeBatchExportUsesDynamicRankingColumnsAndEvaluationSheetStartingAtRowEight() throws Exception {
        BatchVO batch = new BatchVO();
        batch.setId("batch-1");
        batch.setName("2026 综合测评");
        when(batchService.getBatch("batch-1")).thenReturn(batch);

        BatchStatsVO stats = new BatchStatsVO();
        stats.setBatchId("batch-1");
        when(batchService.getStats("batch-1")).thenReturn(stats);

        BatchRankingVO ranking = new BatchRankingVO();
        ranking.setRank(1);
        ranking.setStudentLoginId("2321911001");
        ranking.setStudentName("Alice");
        ranking.setTotalScore(new BigDecimal("88.00"));
        Map<String, BigDecimal> categoryScores = new LinkedHashMap<>();
        categoryScores.put("research", new BigDecimal("12.00"));
        ranking.setCategoryScores(categoryScores);
        when(batchService.getRanking("batch-1")).thenReturn(List.of(ranking));

        BatchEvaluationTableVO table = new BatchEvaluationTableVO();
        BatchEvaluationTableVO.CategoryColumn category = new BatchEvaluationTableVO.CategoryColumn();
        category.setCode("research");
        category.setName("科研");
        BatchEvaluationTableVO.AwardColumn award = new BatchEvaluationTableVO.AwardColumn();
        award.setAwardId("award-r");
        award.setName("论文");
        category.setAwards(List.of(award));
        table.setCategories(List.of(category));

        BatchEvaluationTableVO.StudentRow row = new BatchEvaluationTableVO.StudentRow();
        row.setStudentLoginId("2321911001");
        row.setStudentName("Alice");
        row.setScores(Map.of("award-r", new BigDecimal("4.00")));
        row.setSubtotals(Map.of("research", new BigDecimal("4.00")));
        table.setRows(List.of(row));
        when(batchService.getEvaluationTable("batch-1")).thenReturn(table);
        when(awardMapper.selectBatchBasicAwards("batch-1")).thenReturn(List.of());

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new BatchExportServiceImpl(batchService, basicScoreMapper, awardMapper, userMapper)
                .writeBatchExport("batch-1", output);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
            Sheet rankingSheet = workbook.getSheet("分数排名");
            assertThat(rankingSheet.getRow(0).getCell(4).getStringCellValue()).isEqualTo("科研");
            assertThat(rankingSheet.getRow(1).getCell(4).getNumericCellValue()).isEqualTo(12.00);

            Sheet evaluationSheet = workbook.getSheet("综测考评表");
            assertThat(evaluationSheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("2026 综合测评");
            assertThat(evaluationSheet.getMergedRegions()).anySatisfy(region -> {
                assertThat(region.getFirstRow()).isEqualTo(0);
                assertThat(region.getLastRow()).isEqualTo(0);
            });
            Row firstDataRow = evaluationSheet.getRow(7);
            assertThat(firstDataRow.getCell(0).getStringCellValue()).isEqualTo("2321911001");
            assertThat(firstDataRow.getCell(2).getNumericCellValue()).isEqualTo(4.00);
            assertThat(firstDataRow.getCell(3).getNumericCellValue()).isEqualTo(4.00);
        }
    }

    @Test
    void exportIncludesOnlyTargetStudentsWithNonzeroBasicScoresAndKeepsStatsUnchanged() throws Exception {
        BatchVO batch = new BatchVO();
        batch.setId("batch-1");
        batch.setName("2026 综合测评");
        batch.setCategories(List.of(batchCategory("research", "50", "10"),
                batchCategory("service", "50", null)));
        when(batchService.getBatch("batch-1")).thenReturn(batch);

        BatchStatsVO stats = new BatchStatsVO();
        stats.setTotalDeclarations(2);
        stats.setApprovedCount(1);
        stats.setAverageScore(new BigDecimal("4.00"));
        when(batchService.getStats("batch-1")).thenReturn(stats);

        BatchRankingVO approved = new BatchRankingVO();
        approved.setStudentId("approved");
        approved.setStudentLoginId("1001");
        approved.setStudentName("Alice");
        approved.setTotalScore(new BigDecimal("4.00"));
        approved.setCategoryScores(Map.of("research", new BigDecimal("8.00"), "service", BigDecimal.ZERO));
        when(batchService.getRanking("batch-1")).thenReturn(List.of(approved));

        BatchEvaluationTableVO table = new BatchEvaluationTableVO();
        table.setCategories(List.of(category("research", "科研", "approved-award", "basic-r1", "basic-r2"),
                category("service", "服务", "basic-s")));
        BatchEvaluationTableVO.StudentRow approvedRow = new BatchEvaluationTableVO.StudentRow();
        approvedRow.setStudentId("approved");
        approvedRow.setStudentLoginId("1001");
        approvedRow.setStudentName("Alice");
        approvedRow.setScores(Map.of("approved-award", new BigDecimal("5.00"),
                "basic-r1", new BigDecimal("3.00")));
        approvedRow.setSubtotals(Map.of("research", new BigDecimal("8.00"), "service", BigDecimal.ZERO));
        table.setRows(List.of(approvedRow));
        when(batchService.getEvaluationTable("batch-1")).thenReturn(table);

        when(awardMapper.selectBatchBasicAwards("batch-1")).thenReturn(List.of(
                award("basic-r1", "research"), award("basic-r2", "research"), award("basic-s", "service")));
        when(basicScoreMapper.selectList(any())).thenReturn(Arrays.asList(
                basicScore("approved", "basic-r1", "3"),
                basicScore("basic-only", "basic-r1", "7"),
                basicScore("basic-only", "basic-r2", "6"),
                basicScore("basic-only", "basic-s", "8"),
                basicScore("pending", "basic-r1", "4"),
                basicScore("zero", "basic-r1", "0"),
                basicScore("outside", "basic-r1", "5")));
        when(userMapper.selectStudentsInBatchScope("batch-1")).thenReturn(List.of(
                student("approved", "1001", "Alice"),
                student("basic-only", "1002", "Bob"),
                student("pending", "1003", "Charlie"),
                student("zero", "1004", "David")));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new BatchExportServiceImpl(batchService, basicScoreMapper, awardMapper, userMapper)
                .writeBatchExport("batch-1", output);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
            Sheet ranking = workbook.getSheet("分数排名");
            assertThat(ranking.getLastRowNum()).isEqualTo(3);
            assertThat(ranking.getRow(1).getCell(1).getStringCellValue()).isEqualTo("1002");
            assertThat(ranking.getRow(1).getCell(0).getNumericCellValue()).isEqualTo(1);
            assertThat(ranking.getRow(1).getCell(3).getNumericCellValue()).isEqualTo(9);
            assertThat(ranking.getRow(1).getCell(4).getNumericCellValue()).isEqualTo(10);
            assertThat(ranking.getRow(1).getCell(5).getNumericCellValue()).isEqualTo(8);
            assertThat(ranking.getRow(1).getCell(6).getStringCellValue()).isEmpty();
            assertThat(ranking.getRow(2).getCell(1).getStringCellValue()).isEqualTo("1001");
            assertThat(ranking.getRow(2).getCell(0).getNumericCellValue()).isEqualTo(2);
            assertThat(ranking.getRow(3).getCell(1).getStringCellValue()).isEqualTo("1003");
            assertThat(ranking.getRow(3).getCell(3).getNumericCellValue()).isEqualTo(2);

            Sheet evaluation = workbook.getSheet("综测考评表");
            assertThat(evaluation.getLastRowNum()).isEqualTo(9);
            assertThat(evaluation.getRow(7).getCell(0).getStringCellValue()).isEqualTo("1001");
            assertThat(evaluation.getRow(8).getCell(0).getStringCellValue()).isEqualTo("1002");
            assertThat(evaluation.getRow(8).getCell(3).getNumericCellValue()).isEqualTo(7);
            assertThat(evaluation.getRow(8).getCell(4).getNumericCellValue()).isEqualTo(6);
            assertThat(evaluation.getRow(8).getCell(5).getNumericCellValue()).isEqualTo(10);
            assertThat(evaluation.getRow(8).getCell(6).getNumericCellValue()).isEqualTo(8);
            assertThat(evaluation.getRow(9).getCell(0).getStringCellValue()).isEqualTo("1003");
            assertThat(evaluation.getRow(9).getCell(3).getNumericCellValue()).isEqualTo(4);

            Sheet summary = workbook.getSheet("统计汇总");
            assertThat(summary.getRow(1).getCell(1).getNumericCellValue()).isEqualTo(2);
            assertThat(summary.getRow(4).getCell(1).getNumericCellValue()).isEqualTo(1);
            assertThat(summary.getRow(9).getCell(1).getNumericCellValue()).isEqualTo(4);
        }
    }

    private BatchVO.CategoryVO batchCategory(String code, String weight, String cap) {
        BatchVO.CategoryVO category = new BatchVO.CategoryVO();
        category.setCategory(code);
        category.setWeightPercent(new BigDecimal(weight));
        if (cap != null) category.setMaxScoreCap(new BigDecimal(cap));
        return category;
    }

    private BatchEvaluationTableVO.CategoryColumn category(String code, String name, String... awardIds) {
        BatchEvaluationTableVO.CategoryColumn category = new BatchEvaluationTableVO.CategoryColumn();
        category.setCode(code);
        category.setName(name);
        category.setAwards(Arrays.stream(awardIds).map(id -> {
            BatchEvaluationTableVO.AwardColumn award = new BatchEvaluationTableVO.AwardColumn();
            award.setAwardId(id);
            award.setName(id);
            return award;
        }).collect(java.util.stream.Collectors.toList()));
        return category;
    }

    private Award award(String id, String category) {
        Award award = new Award();
        award.setId(id);
        award.setCategory(category);
        return award;
    }

    private BatchBasicScore basicScore(String studentId, String awardId, String score) {
        BatchBasicScore basicScore = new BatchBasicScore();
        basicScore.setStudentId(studentId);
        basicScore.setAwardId(awardId);
        basicScore.setScore(new BigDecimal(score));
        return basicScore;
    }

    private SysUser student(String id, String loginId, String name) {
        SysUser student = new SysUser();
        student.setId(id);
        student.setLoginId(loginId);
        student.setName(name);
        return student;
    }
}
