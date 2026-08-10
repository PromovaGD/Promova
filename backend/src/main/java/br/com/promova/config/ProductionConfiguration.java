package br.com.promova.config;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@Profile("prod")
public class ProductionConfiguration implements BeanFactoryPostProcessor, EnvironmentAware {
  private static final String[] REQUIRED_ENVIRONMENT = {
    "PROMOVA_DB_URL",
    "PROMOVA_DB_USERNAME",
    "PROMOVA_DB_PASSWORD",
    "PROMOVA_CORS_ALLOWED_ORIGINS"
  };

  private Environment environment;

  @Override
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  @Override
  public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    String missing =
        Arrays.stream(REQUIRED_ENVIRONMENT)
            .filter(name -> !hasText(environment.getProperty(name)))
            .collect(Collectors.joining(", "));

    if (!missing.isEmpty()) {
      throw new BeanInitializationException(
          "Production startup requires these environment variables: " + missing);
    }
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
