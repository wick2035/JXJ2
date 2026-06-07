package com.eval.jxj.service.impl;

import com.eval.jxj.dto.response.BasicAwardImportResult;
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
import com.eval.jxj.service.ScoreCalculationService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BasicAwardServiceImplTest {

    @Mock
    private AwardMapper awardMapper;
    @Mock
    private BatchBasicAwardMapper batchBasicAwardMapper;
    @Mock
    private BatchBasicScoreMapper batchBasicScoreMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private DeclarationMapper declarationMapper;
    @Mock
    private ScoreCalculationService scoreService;

    @InjectMocks
    private BasicAwardServiceImpl service;

    @Test
    void importScoresWritesBasicScoresAndRecalculatesExistingDeclarations() throws Exception {
        Award attendance = basicAward("award-1", "morality", "上课出勤");
        SysUser student = student("student-1", "S001");
        Declaration declaration = new Declaration();
        declaration.setId("decl-1");
        declaration.setBatchId("batch-1");
        declaration.setStudentId("student-1");

        when(awardMapper.selectList(any())).thenReturn(List.of(attendance));
        when(userMapper.selectList(any())).thenReturn(List.of(student));
        when(declarationMapper.selectList(any())).thenReturn(List.of(declaration));

        BasicAwardImportResult result = service.importScores("batch-1", "morality",
                excel("S001", "上课出勤", "3.5"));

        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isZero();
        verify(batchBasicAwardMapper).deleteByBatchAndAwardIds(eq("batch-1"), eq(List.of("award-1")));
        verify(batchBasicScoreMapper).deleteByBatchAndAwardIds(eq("batch-1"), eq(List.of("award-1")));
        verify(batchBasicAwardMapper).insert(any(BatchBasicAward.class));

        ArgumentCaptor<BatchBasicScore> scoreCaptor = ArgumentCaptor.forClass(BatchBasicScore.class);
        verify(batchBasicScoreMapper).insert(scoreCaptor.capture());
        assertThat(scoreCaptor.getValue().getBatchId()).isEqualTo("batch-1");
        assertThat(scoreCaptor.getValue().getAwardId()).isEqualTo("award-1");
        assertThat(scoreCaptor.getValue().getStudentId()).isEqualTo("student-1");
        assertThat(scoreCaptor.getValue().getScore()).isEqualByComparingTo("3.5");
        verify(scoreService).computeDeclarationScore(declaration);
    }

    @Test
    void importScoresRejectsInvalidRowsWithoutWritingAnything() throws Exception {
        Award attendance = basicAward("award-1", "morality", "上课出勤");

        when(awardMapper.selectList(any())).thenReturn(List.of(attendance));
        when(userMapper.selectList(any())).thenReturn(List.of());

        BasicAwardImportResult result = service.importScores("batch-1", "morality",
                excel("S404", "上课出勤", "3.5"));

        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.getErrors()).extracting(BasicAwardImportResult.RowError::getReason)
                .anyMatch(reason -> reason.contains("学号不存在"));
        verify(batchBasicAwardMapper, never()).insert(any());
        verify(batchBasicScoreMapper, never()).insert(any());
        verify(scoreService, never()).computeDeclarationScore(any());
    }

    @Test
    void importScoresRejectsDuplicateStudentAndProjectRows() throws Exception {
        Award attendance = basicAward("award-1", "morality", "上课出勤");
        SysUser student = student("student-1", "S001");

        when(awardMapper.selectList(any())).thenReturn(List.of(attendance));
        when(userMapper.selectList(any())).thenReturn(List.of(student));

        BasicAwardImportResult result = service.importScores("batch-1", "morality",
                excel(
                        new String[]{"S001", "上课出勤", "3.5"},
                        new String[]{"S001", "上课出勤", "4.0"}));

        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.getErrors()).extracting(BasicAwardImportResult.RowError::getReason)
                .anyMatch(reason -> reason.contains("重复"));
        verify(batchBasicAwardMapper, never()).insert(any());
        verify(batchBasicScoreMapper, never()).insert(any());
    }

    @Test
    void importScoresCoversMultipleProjectsInOneFile() throws Exception {
        Award attendance = basicAward("award-1", "morality", "上课出勤");
        Award dorm = basicAward("award-2", "morality", "宿舍卫生");
        SysUser student = student("student-1", "S001");

        when(awardMapper.selectList(any())).thenReturn(List.of(attendance, dorm));
        when(userMapper.selectList(any())).thenReturn(List.of(student));
        when(declarationMapper.selectList(any())).thenReturn(List.of());

        BasicAwardImportResult result = service.importScores("batch-1", "morality",
                excel(
                        new String[]{"S001", "上课出勤", "3.5"},
                        new String[]{"S001", "宿舍卫生", "2.0"}));

        assertThat(result.getSuccessCount()).isEqualTo(2);
        verify(batchBasicAwardMapper, times(2)).insert(any(BatchBasicAward.class));
        verify(batchBasicScoreMapper, times(2)).insert(any(BatchBasicScore.class));
        verify(batchBasicAwardMapper).deleteByBatchAndAwardIds(eq("batch-1"), eq(List.of("award-1", "award-2")));
        verify(batchBasicScoreMapper).deleteByBatchAndAwardIds(eq("batch-1"), eq(List.of("award-1", "award-2")));
    }

    private static Award basicAward(String id, String category, String name) {
        Award award = new Award();
        award.setId(id);
        award.setCategory(category);
        award.setName(name);
        award.setAwardType("basic");
        return award;
    }

    private static SysUser student(String id, String loginId) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setLoginId(loginId);
        user.setRole("student");
        return user;
    }

    private static MockMultipartFile excel(String loginId, String project, String score) throws Exception {
        return excel(new String[]{loginId, project, score});
    }

    private static MockMultipartFile excel(String[]... rows) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("basic");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("学号");
            header.createCell(1).setCellValue("项目");
            header.createCell(2).setCellValue("分数");
            for (int i = 0; i < rows.length; i++) {
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(rows[i][0]);
                row.createCell(1).setCellValue(rows[i][1]);
                row.createCell(2).setCellValue(rows[i][2]);
            }
            workbook.write(out);
            return new MockMultipartFile("file", "basic.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }
}
