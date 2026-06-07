package com.eval.jxj.controller;

import com.eval.jxj.common.Result;
import com.eval.jxj.dto.response.BasicAwardImportResult;
import com.eval.jxj.dto.response.BatchBasicAwardVO;
import com.eval.jxj.dto.response.DeclarationVO;
import com.eval.jxj.service.BasicAwardService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/batches/{batchId}/basic-awards")
public class BasicAwardController {

    private final BasicAwardService basicAwardService;

    public BasicAwardController(BasicAwardService basicAwardService) {
        this.basicAwardService = basicAwardService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<BatchBasicAwardVO>> list(@PathVariable String batchId,
                                                @RequestParam(required = false) String category) {
        return Result.ok(basicAwardService.listBatchBasicAwards(batchId, category));
    }

    @GetMapping("/mine")
    public Result<List<DeclarationVO.BasicItemVO>> listMine(@PathVariable String batchId) {
        return Result.ok(basicAwardService.listMyBasicItems(batchId));
    }

    @PostMapping("/import")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<BasicAwardImportResult> importScores(@PathVariable String batchId,
                                                       @RequestParam String category,
                                                       @RequestParam("file") MultipartFile file) {
        return Result.ok(basicAwardService.importScores(batchId, category, file));
    }

    @GetMapping("/import-template")
    @PreAuthorize("hasRole('ADMIN')")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"basic-award-import-template.xlsx\"");
        try (Workbook workbook = new XSSFWorkbook(); ServletOutputStream out = response.getOutputStream()) {
            Sheet sheet = workbook.createSheet("基础分导入");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("学号");
            header.createCell(1).setCellValue("项目");
            header.createCell(2).setCellValue("分数");
            Row sample = sheet.createRow(1);
            sample.createCell(0).setCellValue("S2026001");
            sample.createCell(1).setCellValue("上课出勤");
            sample.createCell(2).setCellValue(3.5);
            for (int i = 0; i < 3; i++) {
                sheet.setColumnWidth(i, 18 * 256);
            }
            workbook.write(out);
        }
    }
}
