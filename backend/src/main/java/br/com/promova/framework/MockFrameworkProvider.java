package br.com.promova.framework;

import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MockFrameworkProvider implements FrameworkProvider {
  @Override
  public CareerFramework load() {
    return new CareerFramework(
        Map.of(
            "L3",
            new CareerLevel("Works with guidance, limited ownership"),
            "L4",
            new CareerLevel("Works independently, improves systems, measurable impact")));
  }
}
