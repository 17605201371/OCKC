package cn.course.community;
import java.security.Principal;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import static cn.course.community.ApiError.fail;
@RestController @RequestMapping("/api/account")
class AccountController {
 final AccountService accounts;AccountController(AccountService accounts){this.accounts=accounts;}
 record ProfileRequest(String displayName,Long revision){}
 record PasswordRequest(String currentPassword,String newPassword,String confirmation){}
 @GetMapping public ResponseEntity<?> profile(Principal p){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(accounts.profile(p.getName()));}
 @PutMapping("/profile") public Object profile(@RequestBody ProfileRequest body,Principal p){return accounts.updateName(p.getName(),body.displayName(),body.revision());}
 @PutMapping("/password") public ResponseEntity<?> password(@RequestBody PasswordRequest body,Authentication auth,HttpServletRequest req){
  if(!(auth.getPrincipal() instanceof AccountPrincipal p))throw fail(403,"请使用账号密码重新登录后修改密码");
  var result=accounts.changePassword(p.getUsername(),p.credentialVersion,body.currentPassword(),body.newPassword(),body.confirmation());
  if(result.status()==200||result.status()==401){var session=req.getSession(false);if(session!=null)session.invalidate();SecurityContextHolder.clearContext();}
  var response=ResponseEntity.status(result.status()).cacheControl(CacheControl.noStore());if(result.status()==429)response.header("Retry-After","900");
  return response.body(Map.of("message",result.message()));
 }
}
