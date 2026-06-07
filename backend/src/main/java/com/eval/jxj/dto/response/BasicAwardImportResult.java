package com.eval.jxj.dto.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BasicAwardImportResult {
    private int successCount;
    private int failedCount;
    private int projectCount;
    private int studentCount;
    private List<RowError> errors = new ArrayList<>();

    public void addSuccess() {
        successCount++;
    }

    public void addFailed(int row, String loginId, String project, String reason) {
        failedCount++;
        errors.add(new RowError(row, loginId, project, reason));
    }

    @Data
    public static class RowError {
        private final int row;
        private final String loginId;
        private final String project;
        private final String reason;
    }
}
