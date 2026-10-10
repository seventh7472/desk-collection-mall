package com.example.mall.common;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
@Configuration
public class CommonWebConfig implements WebMvcConfigurer {
 private final InternalGuard guard;
 public CommonWebConfig(InternalGuard guard){this.guard=guard;}
 @Override public void addInterceptors(InterceptorRegistry registry){registry.addInterceptor(guard).addPathPatterns("/internal/**");}
}
