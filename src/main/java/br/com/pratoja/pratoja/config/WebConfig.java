package br.com.pratoja.pratoja.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
import java.nio.file.Path;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Value("${pratoja.upload-dir}") private String uploadDir;
    @Override public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(uploadDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}
