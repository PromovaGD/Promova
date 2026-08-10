package br.com.promova.evidence.service;

import br.com.promova.evidence.dto.EvidenceResponse;

public record EvidenceCaptureResult(EvidenceResponse evidence, boolean created) {}
