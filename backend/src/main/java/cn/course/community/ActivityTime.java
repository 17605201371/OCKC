package cn.course.community;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import static cn.course.community.ApiError.fail;

/**
 * 停留时长：前端每 30 秒上报一次「这段时间里页面在前台且有人在操作」的秒数，这里累加。
 * 服务端按上次上报到现在的真实间隔封顶，所以同一对象开两个窗口、或者连续重放请求，都不会比墙钟时间记得多。
 */
@Service
public class ActivityTime {
 static final int MAX_BEAT=60,SLACK=2;
 final Store s;ActivityTime(Store s){this.s=s;}

 @Transactional
 public Map<String,Object> beat(long uid,String kind,long target,int seconds){
  if(!Set.of("WRITE","GRADE").contains(kind)||target<=0||seconds<1||seconds>MAX_BEAT)throw fail(400,"停留时长参数不正确");
  if(kind.equals("WRITE")){
   var e=s.one("SELECT e.author_id,a.course_id FROM entry e JOIN assignment a ON a.id=e.assignment_id WHERE e.id=?",target);
   s.role(s.id(e,"course_id"),uid);
   if(s.id(e,"author_id")!=uid)throw fail(403,"只记录本人作品的做题时间");
  }else{
   var v=s.one("SELECT a.course_id FROM submission v JOIN entry e ON e.id=v.entry_id JOIN assignment a ON a.id=e.assignment_id WHERE v.id=?",target);
   s.teacher(s.id(v,"course_id"),uid);
  }
  String now=Store.now();
  var row=locked(uid,kind,target);
  if(row==null){
   try{s.db.update("INSERT INTO activity_time(user_id,kind,target_id,seconds,first_at,last_at) VALUES(?,?,?,?,?,?)",uid,kind,target,seconds,now,now);return Map.of("seconds",seconds);}
   catch(DuplicateKeyException race){row=locked(uid,kind,target);}
  }
  long elapsed=Math.max(0,Duration.between(Instant.parse((String)row.get("last_at")),Instant.parse(now)).getSeconds());
  int credit=(int)Math.min(seconds,elapsed+SLACK);
  s.db.update("UPDATE activity_time SET seconds=seconds+?,last_at=? WHERE id=?",credit,now,s.id(row,"id"));
  return Map.of("seconds",((Number)row.get("seconds")).intValue()+credit);
 }
 Map<String,Object> locked(long uid,String kind,long target){
  var rows=s.db.queryForList("SELECT id,seconds,last_at FROM activity_time WHERE user_id=? AND kind=? AND target_id=? FOR UPDATE",uid,kind,target);
  return rows.isEmpty()?null:rows.getFirst();
 }
}

@RestController @RequestMapping("/api")
class ActivityTimeController {
 final ActivityTime time;final Store s;ActivityTimeController(ActivityTime time,Store s){this.time=time;this.s=s;}
 record Beat(@NotBlank @Pattern(regexp="WRITE|GRADE") String kind,@NotNull @Min(1) Long targetId,@NotNull @Min(1) @Max(60) Integer seconds){}
 @PostMapping("/activity/heartbeat") public Object beat(@Valid @RequestBody Beat b,Principal p){return time.beat(s.uid(p.getName()),b.kind(),b.targetId(),b.seconds());}
}
