package br.com.promova.github.connection.dto;

public record GithubConnectionTestResponse(
    boolean ok, String repoSlug, String authorLogin, String message) {}
