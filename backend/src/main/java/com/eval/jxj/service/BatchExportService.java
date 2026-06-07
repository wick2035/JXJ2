package com.eval.jxj.service;

import java.io.IOException;
import java.io.OutputStream;

public interface BatchExportService {
    void writeBatchExport(String batchId, OutputStream outputStream) throws IOException;
}
