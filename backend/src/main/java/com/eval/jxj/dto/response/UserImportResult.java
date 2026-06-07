package com.eval.jxj.dto.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class UserImportResult {
    private int successCount;
    private int skippedCount;
    private int failedCount;
    private List<RowError> errors = new ArrayList<>();

    public void success() {
        successCount++;
    }

    public void addSuccess() {
        success();
    }

    public void skipped(int row, String reason) {
        skippedCount++;
        errors.add(new RowError(row, null, reason));
    }

    public void addSkipped(int row, String loginId, String reason) {
        skippedCount++;
        errors.add(new RowError(row, loginId, reason));
    }

    public void failed(int row, String reason) {
        failedCount++;
        errors.add(new RowError(row, null, reason));
    }

    public void addFailed(int row, String loginId, String reason) {
        failedCount++;
        errors.add(new RowError(row, loginId, reason));
    }

    @Data
    public static class RowError {
        private final int row;
        private final String loginId;
        private final String reason;

        public RowError(int row, String loginId, String reason) {
            this.row = row;
            this.loginId = loginId;
            this.reason = reason;
        }
    }
}
