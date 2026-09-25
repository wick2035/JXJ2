package com.eval.jxj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.eval.jxj.service.BatchExportService;
import com.eval.jxj.service.BatchService;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BatchExportServiceImpl implements BatchExportService {

    private final BatchService batchService;
    private final BatchBasicScoreMapper basicScoreMapper;
    private final AwardMapper awardMapper;
    private final SysUserMapper userMapper;

    public BatchExportServiceImpl(BatchService batchService, BatchBasicScoreMapper basicScoreMapper,
                                  AwardMapper awardMapper, SysUserMapper userMapper) {
        this.batchService = batchService;
        this.basicScoreMapper = basicScoreMapper;
        this.awardMapper = awardMapper;
        this.userMapper = userMapper;
    }

    @Override
    public void writeBatchExport(String batchId, OutputStream outputStream) throws IOException {
        BatchVO batch = batchService.getBatch(batchId);
        BatchEvaluationTableVO evaluationTable = batchService.getEvaluationTable(batchId);
        List<BatchRankingVO> rankings = addBasicOnlyStudents(batch, batchService.getRanking(batchId), evaluationTable);
        try (Workbook workbook = new XSSFWorkbook()) {
            writeStatsSheet(workbook, batchService.getStats(batchId));
            writeRankingSheet(workbook, batch, rankings, evaluationTable);
            writeEvaluationSheet(workbook, batch, evaluationTable);
            workbook.write(outputStream);
        }
    }

    private List<BatchRankingVO> addBasicOnlyStudents(BatchVO batch, List<BatchRankingVO> rankings,
                                                       BatchEvaluationTableVO table) {
        List<Award> basicAwards = awardMapper.selectBatchBasicAwards(batch.getId());
        if (basicAwards.isEmpty()) {
            return rankings;
        }
        List<BatchBasicScore> basicScores = basicScoreMapper.selectList(new LambdaQueryWrapper<BatchBasicScore>()
                .eq(BatchBasicScore::getBatchId, batch.getId()));
        if (basicScores.isEmpty()) {
            return rankings;
        }

        Map<String, Award> awardById = basicAwards.stream()
                .collect(Collectors.toMap(Award::getId, Function.identity(), (a, b) -> a));
        Map<String, SysUser> studentById = userMapper.selectStudentsInBatchScope(batch.getId()).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity(), (a, b) -> a));
        Set<String> approvedStudentIds = rankings.stream()
                .map(BatchRankingVO::getStudentId).collect(Collectors.toSet());
        Set<String> candidateIds = basicScores.stream()
                .filter(score -> score.getScore() != null && score.getScore().signum() != 0)
                .filter(score -> awardById.containsKey(score.getAwardId()))
                .map(BatchBasicScore::getStudentId)
                .filter(studentId -> studentById.containsKey(studentId) && !approvedStudentIds.contains(studentId))
                .collect(Collectors.toSet());
        if (candidateIds.isEmpty()) {
            return rankings;
        }

        Map<String, BatchEvaluationTableVO.StudentRow> rowsById = new LinkedHashMap<>();
        Map<String, Map<String, BigDecimal>> rawScoresByStudent = new LinkedHashMap<>();
        Set<String> visibleCategories = table.getCategories().stream()
                .map(BatchEvaluationTableVO.CategoryColumn::getCode).collect(Collectors.toSet());
        Map<String, BigDecimal> capByCategory = batch.getCategories() == null ? Map.of()
                : batch.getCategories().stream()
                        .filter(category -> category.getMaxScoreCap() != null)
                        .collect(Collectors.toMap(BatchVO.CategoryVO::getCategory,
                                BatchVO.CategoryVO::getMaxScoreCap, (a, b) -> a));
        for (BatchBasicScore basicScore : basicScores) {
            String studentId = basicScore.getStudentId();
            Award award = awardById.get(basicScore.getAwardId());
            if (!candidateIds.contains(studentId) || award == null) {
                continue;
            }
            BatchEvaluationTableVO.StudentRow row = rowsById.computeIfAbsent(studentId, id -> {
                SysUser student = studentById.get(id);
                BatchEvaluationTableVO.StudentRow newRow = new BatchEvaluationTableVO.StudentRow();
                newRow.setStudentId(id);
                newRow.setStudentLoginId(student.getLoginId());
                newRow.setStudentName(student.getName());
                newRow.setScores(new LinkedHashMap<>());
                Map<String, BigDecimal> subtotals = new LinkedHashMap<>();
                visibleCategories.forEach(code -> subtotals.put(code, BigDecimal.ZERO));
                newRow.setSubtotals(subtotals);
                return newRow;
            });
            BigDecimal score = basicScore.getScore() == null ? BigDecimal.ZERO : basicScore.getScore();
            row.getScores().merge(award.getId(), score, BigDecimal::add);
            rawScoresByStudent.computeIfAbsent(studentId, id -> new LinkedHashMap<>())
                    .merge(award.getCategory(), score, BigDecimal::add);
            if (visibleCategories.contains(award.getCategory())) {
                row.getSubtotals().merge(award.getCategory(), score, BigDecimal::add);
            }
        }

        List<BatchRankingVO> exportRankings = new ArrayList<>(rankings);
        for (Map.Entry<String, BatchEvaluationTableVO.StudentRow> entry : rowsById.entrySet()) {
            String studentId = entry.getKey();
            Map<String, BigDecimal> rawScores = rawScoresByStudent.get(studentId);
            Map<String, BigDecimal> categoryScores = new LinkedHashMap<>();
            BigDecimal total = BigDecimal.ZERO;
            if (batch.getCategories() != null) {
                for (BatchVO.CategoryVO category : batch.getCategories()) {
                    BigDecimal capped = cap(rawScores.getOrDefault(category.getCategory(), BigDecimal.ZERO),
                            category.getMaxScoreCap());
                    categoryScores.put(category.getCategory(), capped);
                    total = total.add(capped.multiply(category.getWeightPercent())
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
                }
            }
            BatchEvaluationTableVO.StudentRow studentRow = entry.getValue();
            BatchRankingVO ranking = new BatchRankingVO();
            ranking.setStudentId(studentId);
            ranking.setStudentLoginId(studentRow.getStudentLoginId());
            ranking.setStudentName(studentRow.getStudentName());
            ranking.setCategoryScores(categoryScores);
            ranking.setTotalScore(total);
            exportRankings.add(ranking);

            for (BatchEvaluationTableVO.CategoryColumn category : table.getCategories()) {
                String code = category.getCode();
                studentRow.getSubtotals().put(code,
                        cap(studentRow.getSubtotals().get(code), capByCategory.get(code)));
            }
        }
        exportRankings.sort(Comparator
                .comparing(BatchRankingVO::getTotalScore, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(BatchRankingVO::getSubmittedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(BatchRankingVO::getStudentLoginId, Comparator.nullsLast(String::compareTo)));
        for (int i = 0; i < exportRankings.size(); i++) {
            exportRankings.get(i).setRank(i + 1);
        }
        List<BatchEvaluationTableVO.StudentRow> exportRows = new ArrayList<>(table.getRows());
        exportRows.addAll(rowsById.values());
        exportRows.sort(Comparator.comparing(BatchEvaluationTableVO.StudentRow::getStudentLoginId,
                Comparator.nullsLast(String::compareTo)));
        table.setRows(exportRows);
        return exportRankings;
    }

    private BigDecimal cap(BigDecimal score, BigDecimal limit) {
        return limit != null && score.compareTo(limit) > 0 ? limit : score;
    }

    private void writeStatsSheet(Workbook workbook, BatchStatsVO stats) {
        Sheet sheet = workbook.createSheet("统计汇总");
        writeRow(sheet, 0, Arrays.asList("指标", "数值"));
        writeRow(sheet, 1, Arrays.asList("申报总数", stats.getTotalDeclarations()));
        writeRow(sheet, 2, Arrays.asList("草稿", stats.getDraftCount()));
        writeRow(sheet, 3, Arrays.asList("待审核", stats.getSubmittedCount()));
        writeRow(sheet, 4, Arrays.asList("已通过", stats.getApprovedCount()));
        writeRow(sheet, 5, Arrays.asList("已驳回", stats.getRejectedCount()));
        writeRow(sheet, 6, Arrays.asList("已退回", stats.getReturnedCount()));
        writeRow(sheet, 7, Arrays.asList("待审任务", stats.getPendingAuditCount()));
        writeRow(sheet, 8, Arrays.asList("已处理任务", stats.getFinishedAuditCount()));
        writeRow(sheet, 9, Arrays.asList("平均分", stats.getAverageScore()));
        writeRow(sheet, 10, Arrays.asList("最高分", stats.getMaxScore()));
        writeRow(sheet, 11, Arrays.asList("最低分", stats.getMinScore()));
        writeRow(sheet, 12, Arrays.asList("审核进度(%)", stats.getAuditProgress()));
        autosize(sheet, 2);
    }

    private void writeRankingSheet(Workbook workbook,
                                   BatchVO batch,
                                   List<BatchRankingVO> rankings,
                                   BatchEvaluationTableVO evaluationTable) {
        Sheet sheet = workbook.createSheet("分数排名");
        List<String> categoryCodes = resolveRankingCategoryCodes(batch, rankings);
        Map<String, String> categoryNames = resolveCategoryNames(evaluationTable);

        List<Object> headers = new ArrayList<>(Arrays.asList("排名", "学号", "姓名", "总分"));
        for (String code : categoryCodes) {
            headers.add(categoryNames.getOrDefault(code, code));
        }
        headers.add("提交时间");
        writeRow(sheet, 0, headers);

        int rowIndex = 1;
        for (BatchRankingVO row : rankings) {
            List<Object> values = new ArrayList<>(Arrays.asList(
                    row.getRank(),
                    row.getStudentLoginId(),
                    row.getStudentName(),
                    row.getTotalScore()
            ));
            Map<String, BigDecimal> categoryScores = row.getCategoryScores() == null
                    ? Map.of()
                    : row.getCategoryScores();
            for (String code : categoryCodes) {
                values.add(categoryScores.get(code));
            }
            values.add(row.getSubmittedAt() == null ? "" : row.getSubmittedAt().toString());
            writeRow(sheet, rowIndex++, values);
        }
        autosize(sheet, headers.size());
    }

    private void writeEvaluationSheet(Workbook workbook, BatchVO batch, BatchEvaluationTableVO table) {
        Sheet sheet = workbook.createSheet("综测考评表");
        CellStyle centered = borderedCenteredStyle(workbook, false);
        CellStyle rotated = borderedCenteredStyle(workbook, true);
        CellStyle titleStyle = titleStyle(workbook);

        int columnCount = Math.max(3, 2 + countEvaluationScoreColumns(table) + 1);
        for (int i = 0; i < 7; i++) {
            sheet.createRow(i);
        }

        Cell titleCell = sheet.getRow(0).createCell(0);
        titleCell.setCellValue(batch.getName());
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, columnCount - 1));

        createMergedHeader(sheet, 1, 6, 0, 0, "学号", centered);
        createMergedHeader(sheet, 1, 6, 1, 1, "姓名", centered);

        int col = 2;
        for (BatchEvaluationTableVO.CategoryColumn category : table.getCategories()) {
            int startCol = col;
            for (BatchEvaluationTableVO.AwardColumn award : category.getAwards()) {
                createMergedHeader(sheet, 2, 6, col, col, award.getName(), rotated);
                col++;
            }
            createMergedHeader(sheet, 2, 6, col, col, "小计(分)", rotated);
            col++;
            createMergedHeader(sheet, 1, 1, startCol, col - 1, category.getName(), centered);
        }
        createMergedHeader(sheet, 1, 6, col, col, "签名", centered);

        int rowIndex = 7;
        for (BatchEvaluationTableVO.StudentRow rowData : table.getRows()) {
            Row row = sheet.createRow(rowIndex++);
            writeStyledCell(row, 0, rowData.getStudentLoginId(), centered);
            writeStyledCell(row, 1, rowData.getStudentName(), centered);
            int dataCol = 2;
            Map<String, BigDecimal> scores = rowData.getScores() == null ? Map.of() : rowData.getScores();
            Map<String, BigDecimal> subtotals = rowData.getSubtotals() == null ? Map.of() : rowData.getSubtotals();
            for (BatchEvaluationTableVO.CategoryColumn category : table.getCategories()) {
                for (BatchEvaluationTableVO.AwardColumn award : category.getAwards()) {
                    writeStyledCell(row, dataCol++, scores.get(award.getAwardId()), centered);
                }
                writeStyledCell(row, dataCol++, subtotals.get(category.getCode()), centered);
            }
            writeStyledCell(row, dataCol, "", centered);
        }

        sheet.setColumnWidth(0, 15 * 256);
        sheet.setColumnWidth(1, 12 * 256);
        for (int i = 2; i < columnCount - 1; i++) {
            sheet.setColumnWidth(i, 5 * 256);
        }
        sheet.setColumnWidth(columnCount - 1, 14 * 256);
        for (int i = 2; i <= 6; i++) {
            sheet.getRow(i).setHeightInPoints(24);
        }
    }

    private List<String> resolveRankingCategoryCodes(BatchVO batch, List<BatchRankingVO> rankings) {
        List<String> codes = new ArrayList<>();
        if (batch.getCategories() != null && !batch.getCategories().isEmpty()) {
            for (BatchVO.CategoryVO category : batch.getCategories()) {
                codes.add(category.getCategory());
            }
            return codes;
        }
        if (!rankings.isEmpty() && rankings.get(0).getCategoryScores() != null) {
            codes.addAll(rankings.get(0).getCategoryScores().keySet());
        }
        return codes;
    }

    private Map<String, String> resolveCategoryNames(BatchEvaluationTableVO table) {
        Map<String, String> names = new LinkedHashMap<>();
        if (table == null || table.getCategories() == null) {
            return names;
        }
        for (BatchEvaluationTableVO.CategoryColumn category : table.getCategories()) {
            names.put(category.getCode(), category.getName());
        }
        return names;
    }

    private int countEvaluationScoreColumns(BatchEvaluationTableVO table) {
        int count = 0;
        for (BatchEvaluationTableVO.CategoryColumn category : table.getCategories()) {
            count += category.getAwards().size() + 1;
        }
        return count;
    }

    private void createMergedHeader(Sheet sheet, int firstRow, int lastRow, int firstCol, int lastCol,
                                    String value, CellStyle style) {
        Cell cell = sheet.getRow(firstRow).createCell(firstCol);
        cell.setCellValue(value);
        cell.setCellStyle(style);
        if (firstRow != lastRow || firstCol != lastCol) {
            sheet.addMergedRegion(new CellRangeAddress(firstRow, lastRow, firstCol, lastCol));
        }
    }

    private CellStyle titleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle borderedCenteredStyle(Workbook workbook, boolean rotate) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setWrapText(true);
        if (rotate) {
            style.setRotation((short) 90);
        }
        return style;
    }

    private void writeStyledCell(Row row, int columnIndex, Object value, CellStyle style) {
        Cell cell = row.createCell(columnIndex);
        setCellValue(cell, value);
        cell.setCellStyle(style);
    }

    private void writeRow(Sheet sheet, int rowIndex, List<?> values) {
        Row row = sheet.createRow(rowIndex);
        for (int i = 0; i < values.size(); i++) {
            Cell cell = row.createCell(i);
            setCellValue(cell, values.get(i));
        }
    }

    private void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof BigDecimal) {
            cell.setCellValue(((BigDecimal) value).doubleValue());
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else {
            cell.setCellValue(String.valueOf(value));
        }
    }

    private void autosize(Sheet sheet, int columns) {
        for (int i = 0; i < columns; i++) {
            sheet.autoSizeColumn(i);
        }
    }
}
