package com.example.demo.dto;

import java.util.List;
import lombok.Data;

@Data
public class ScanRequestDTO {
    public Long batchId;
    public List<String> serials;
}