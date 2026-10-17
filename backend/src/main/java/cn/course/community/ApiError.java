package cn.course.community;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.slf4j.LoggerFactory;
@RestControllerAdvice
public class ApiError {
 public static class Failure extends RuntimeException {final int status;Failure(int status,String message){super(message);this.status=status;}}
 public static Failure fail(int status,String message){return new Failure(status,message);}
 @ExceptionHandler(Failure.class) ResponseEntity<?> known(Failure e){return ResponseEntity.status(e.status).body(Map.of("message",e.getMessage()));}
 @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class,IllegalArgumentException.class}) ResponseEntity<?> invalid(Exception e){return ResponseEntity.badRequest().body(Map.of("message","参数格式不正确，请检查必填项、字数和取值范围"));}
 @ExceptionHandler(Exception.class) ResponseEntity<?> unknown(Exception e){String id=UUID.randomUUID().toString();LoggerFactory.getLogger(ApiError.class).error("API failure {}",id,e);return ResponseEntity.internalServerError().body(Map.of("message","服务异常，请联系维护人员","traceId",id));}
}
