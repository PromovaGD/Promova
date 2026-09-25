package br.com.promova.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class PageRequestFactory {
  public static final int DEFAULT_PAGE_SIZE = 25;
  public static final int MAX_PAGE_SIZE = 50;

  private PageRequestFactory() {}

  public static Pageable create(Integer page, Integer pageSize) {
    int resolvedPage = page == null ? 1 : page;
    int resolvedPageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
    if (resolvedPage < 1) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be at least 1");
    }
    if (resolvedPageSize < 1 || resolvedPageSize > MAX_PAGE_SIZE) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "pageSize must be between 1 and " + MAX_PAGE_SIZE);
    }
    return PageRequest.of(resolvedPage - 1, resolvedPageSize);
  }
}
