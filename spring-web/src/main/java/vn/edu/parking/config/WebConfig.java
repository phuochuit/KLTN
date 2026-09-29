package vn.edu.parking.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import vn.edu.parking.service.ResidentImageStorage;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final ResidentImageStorage storage;
    public WebConfig(ResidentImageStorage storage) { this.storage = storage; }
    @Override public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
            .addResourceLocations(storage.getUploadRoot().toUri().toString());
    }
}
