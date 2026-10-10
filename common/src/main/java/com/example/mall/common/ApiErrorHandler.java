package com.example.mall.common;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DuplicateKeyException;
import java.util.Map;
@RestControllerAdvice
public class ApiErrorHandler {
 @ExceptionHandler(ApiException.class) public ResponseEntity<?> api(ApiException e){return ResponseEntity.status(e.getStatus()).body(Map.of("message",e.getMessage()));}
 @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class, IllegalArgumentException.class}) public ResponseEntity<?> bad(Exception e){return ResponseEntity.badRequest().body(Map.of("message","参数格式或取值不正确"));}
 @ExceptionHandler(DuplicateKeyException.class) public ResponseEntity<?> duplicate(DuplicateKeyException e){return ResponseEntity.status(409).body(Map.of("message","记录已经存在，请勿重复提交"));}
}
