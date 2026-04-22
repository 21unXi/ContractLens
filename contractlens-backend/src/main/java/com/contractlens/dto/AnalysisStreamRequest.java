package com.contractlens.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AnalysisStreamRequest {

    @Size(max = 2000, message = "追问内容不能超过2000个字符")
    private String message;
}
