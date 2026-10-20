package cn.course.community;
import java.util.*;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import static cn.course.community.ApiError.fail;
@Service
public class AccountService {
 final Store s;final PasswordEncoder encoder;AccountService(Store s,PasswordEncoder encoder){this.s=s;this.encoder=encoder;}
 public Map<String,Object> profile(String email){return s.one("SELECT id,email,display_name,profile_revision,password_changed_at FROM app_user WHERE email=?",email);}
 @Transactional public Map<String,Object> updateName(String email,String name,Long revision){
  if(name==null||revision==null||revision<0)throw fail(400,"请填写显示名称并刷新账号资料");
  name=name.strip();if(name.isBlank()||name.codePointCount(0,name.length())>40||name.codePoints().anyMatch(c->Character.isISOControl(c)||Character.getType(c)==Character.FORMAT))throw fail(400,"显示名称应为 1–40 个字符，不包含控制字符");
  var row=s.one("SELECT id,display_name,profile_revision FROM app_user WHERE email=? FOR UPDATE",email);
  if(s.id(row,"profile_revision")!=revision)throw fail(409,"账号资料已在其他窗口更新，请重新加载后核对");
  if(!name.equals(row.get("display_name"))){s.db.update("UPDATE app_user SET display_name=?,profile_revision=profile_revision+1 WHERE id=?",name,row.get("id"));s.audit(s.id(row,"id"),"ACCOUNT_PROFILE_CHANGED","account",s.id(row,"id"));}
  return profile(email);
 }
 record PasswordResult(int status,String message){}
 // Authentication failures return a result instead of throwing: the rate-limit update must commit.
 @Transactional public PasswordResult changePassword(String email,long version,String current,String next,String confirmation){
  if(current==null||current.isEmpty()||current.getBytes(StandardCharsets.UTF_8).length>72) return new PasswordResult(400,"请填写当前密码（最多 72 个 UTF-8 字节）");
  if(next==null||next.isBlank()||next.codePointCount(0,next.length())<12||next.getBytes(StandardCharsets.UTF_8).length>72||next.codePoints().anyMatch(Character::isISOControl))return new PasswordResult(400,"新密码至少 12 个字符，最多 72 个 UTF-8 字节，不含控制字符");
  if(!next.equals(confirmation))return new PasswordResult(400,"两次输入的新密码不一致");
  var row=s.one("SELECT id,password_hash,credential_version,password_failures,password_locked_until FROM app_user WHERE email=? FOR UPDATE",email);
  if(s.id(row,"credential_version")!=version)return new PasswordResult(401,"账号安全信息已变化，请重新登录");
  Instant now=Instant.now();String lock=(String)row.get("password_locked_until");
  if(lock!=null&&Instant.parse(lock).isAfter(now))return new PasswordResult(429,"当前密码验证次数过多，请在 15 分钟后重试");
  int failures=lock==null?((Number)row.get("password_failures")).intValue():0;
  if(!encoder.matches(current,(String)row.get("password_hash"))){
   failures++;s.db.update("UPDATE app_user SET password_failures=?,password_locked_until=? WHERE id=?",failures,failures>=5?now.plusSeconds(900).toString():null,row.get("id"));return new PasswordResult(failures>=5?429:400,failures>=5?"当前密码验证次数过多，请在 15 分钟后重试":"当前密码不正确");
  }
  if(encoder.matches(next,(String)row.get("password_hash")))return new PasswordResult(400,"新密码不能与当前密码相同");
  s.db.update("UPDATE app_user SET password_hash=?,credential_version=credential_version+1,password_changed_at=?,password_failures=0,password_locked_until=NULL WHERE id=?",encoder.encode(next),now.toString(),row.get("id"));
  s.audit(s.id(row,"id"),"ACCOUNT_PASSWORD_CHANGED","account",s.id(row,"id"));
  return new PasswordResult(200,"密码已修改，请使用新密码重新登录");
 }
}
