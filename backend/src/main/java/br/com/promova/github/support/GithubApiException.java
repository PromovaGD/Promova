package br.com.promova.github.support;

import com.fasterxml.jackson.databind.JsonNode;

public class GithubApiException extends RuntimeException {
  private final int statusCode;
  private final JsonNode responseBody;

  public GithubApiException(int statusCode, JsonNode responseBody, String message) {
    super(message);
    this.statusCode = statusCode;
    this.responseBody = responseBody;
  }

  public int statusCode() {
    return statusCode;
  }

  public JsonNode responseBody() {
    return responseBody;
  }
}
