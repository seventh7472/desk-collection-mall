package com.example.mall.common;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
@Component
public class InternalGuard implements HandlerInterceptor {
 @Value("${INTERNAL_KEY:mall-lab-internal-key-change-me}") private String key;
 @Override public boolean preHandle(HttpServletRequest req,HttpServletResponse res,Object handler) throws Exception {
  String supplied=req.getHeader("X-Internal-Key");
  if(supplied==null || !MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8))) {
   res.setStatus(403);res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"message\":\"禁止访问内部接口\"}");return false;
  }
  return true;
 }
}
