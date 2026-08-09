package br.com.promova.github.support;

/** Raised when GitHub returns a successful response that does not match the expected shape. */
public class GithubPayloadException extends RuntimeException {
  public GithubPayloadException(String message) {
    super(message);
  }
}
