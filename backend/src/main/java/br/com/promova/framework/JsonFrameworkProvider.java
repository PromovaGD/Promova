package br.com.promova.framework;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class JsonFrameworkProvider implements FrameworkProvider {
  private final ObjectMapper objectMapper;
  private final Resource frameworkResource;

  public JsonFrameworkProvider(
      ObjectMapper objectMapper,
      @Value("${promova.framework.path:classpath:career-framework.json}") Resource frameworkResource) {
    this.objectMapper = objectMapper;
    this.frameworkResource = frameworkResource;
  }

  @Override
  public CareerFramework load() {
    try {
      CareerFramework careerFramework =
          objectMapper.readValue(frameworkResource.getInputStream(), CareerFramework.class);
      if (careerFramework.levels() == null || careerFramework.levels().isEmpty()) {
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Career framework has no levels");
      }
      return careerFramework;
    } catch (IOException exception) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Could not load career framework", exception);
    }
  }
}
