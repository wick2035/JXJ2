package com.eval.jxj.dto.request;

import lombok.Data;
import java.util.List;

@Data
public class OperationLogDeleteRequest {
    private List<String> ids;
    private String secondaryPassword;
}
