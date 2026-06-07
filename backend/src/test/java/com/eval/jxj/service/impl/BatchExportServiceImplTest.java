package com.eval.jxj.service.impl;

import com.eval.jxj.dto.response.BatchEvaluationTableVO;
import com.eval.jxj.dto.response.BatchRankingVO;
import com.eval.jxj.dto.response.BatchStatsVO;
import com.eval.jxj.dto.response.BatchVO;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchExportServiceImplTest {

    @Mock
    private BatchService batchService;

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

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new BatchExportServiceImpl(batchService).writeBatchExport("batch-1", output);

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
}
