package br.com.promova.evidence.dto;

import java.util.List;
import java.util.Map;

public record EvidenceInboxResponse(
    List<EvidenceInboxItemResponse> items, int page, int pageSize, long total,
    Map<String, Long> counts) {}
