package br.com.promova.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GithubFilePatch(
    String filename,
    String status,
    int additions,
    int deletions,
    int changes,
    @JsonProperty("blob_url") String blobUrl,
    @JsonProperty("raw_url") String rawUrl,
    @JsonProperty("patch_preview") String patchPreview) {}
