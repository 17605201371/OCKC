package cn.course.community;
import java.time.Instant;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import static cn.course.community.ApiError.fail;
@Service
public class AssignmentManagement {
 final Store s;final ObjectMapper json;final NotificationService notices;
 AssignmentManagement(Store s,ObjectMapper json,NotificationService notices){this.s=s;this.json=json;this.notices=notices;}
 public record Edit(@NotBlank @Size(max=160) String title,@NotBlank @Size(max=4000) String requirements,@NotBlank String dueAt,Integer maxSubmissions,Integer maxEvaluations,@NotBlank @Size(max=1000) String reason,@Min(0) int revision){}
 public record State(@Pattern(regexp="CLOSE|REOPEN") @NotNull String action,@NotBlank @Size(max=1000) String reason,@Min(0) int revision){}
 Map<String,Object> access(long aid,long uid,boolean lock){var a=s.one("SELECT * FROM assignment WHERE id=?"+(lock?" FOR UPDATE":""),aid);s.teacher(s.id(a,"course_id"),uid);return a;}
 void revision(Map<String,Object>a,int value){if(((Number)a.get("management_revision")).intValue()!=value)throw fail(409,"作业规则已被另一位教师修改。当前输入仍保留，请重新载入后核对再保存");}
 static Map<String,Object> snapshot(Map<String,Object>a){var out=new LinkedHashMap<String,Object>();for(String k:List.of("id","title","requirements","due_at","rubric_version","management_revision","manually_closed","max_submissions","max_evaluations","kind","evidence_policy"))out.put(k,a.get(k));return out;}
 String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
 Map<String,Object> changed(Map<String,Object>before,long uid,String action,String reason){long aid=s.id(before,"id"),cid=s.id(before,"course_id");var after=s.one("SELECT * FROM assignment WHERE id=?",aid);s.db.update("INSERT INTO assignment_change(assignment_id,revision_no,actor_id,action_type,reason,before_json,after_json,created_at) VALUES(?,?,?,?,?,?,?,?)",aid,after.get("management_revision"),uid,action,reason.trim(),encode(snapshot(before)),encode(snapshot(after)),Store.now());s.audit(uid,"ASSIGNMENT_"+action,"assignment",aid);String title=action.equals("CLOSE")?"教师已手动结束作业":action.equals("REOPEN")?"教师已重新开放作业":"作业要求或截止时间已更新";for(var m:s.db.queryForList("SELECT user_id FROM course_member WHERE course_id=? AND role<>'TEACHER'",cid))notices.send(s.id(m,"user_id"),cid,"ASSIGNMENT_CHANGE","MEMBER",title,"assignment",aid,"",uid);return after;}
 @Transactional public Map<String,Object> edit(long aid,long uid,Edit r){var a=access(aid,uid,true);revision(a,r.revision());Instant due;try{due=Instant.parse(r.dueAt());}catch(Exception e){throw fail(422,"截止时间格式错误");}if(!due.isAfter(Instant.now()))throw fail(422,"新的截止时间须晚于当前时间；如需立即停止提交，请使用结束作业");
  // 未传次数上限时保持原值，避免旧客户端把限制悄悄清成 0。
  int maxSub=r.maxSubmissions()==null?((Number)a.get("max_submissions")).intValue():r.maxSubmissions();
  int maxEval=r.maxEvaluations()==null?((Number)a.get("max_evaluations")).intValue():r.maxEvaluations();
  if(maxSub<0||maxSub>50)throw fail(422,"提交次数上限须在 0（不限）到 50 之间");
  if(maxEval<0||maxEval>20)throw fail(422,"批阅次数上限须在 0（不限）到 20 之间");
  if(a.get("title").equals(r.title().trim())&&a.get("requirements").equals(r.requirements().trim())&&Instant.parse((String)a.get("due_at")).equals(due)&&((Number)a.get("max_submissions")).intValue()==maxSub&&((Number)a.get("max_evaluations")).intValue()==maxEval)throw fail(422,"任务要求、截止时间与次数上限均未变化");
  s.db.update("UPDATE assignment SET title=?,requirements=?,due_at=?,max_submissions=?,max_evaluations=?,management_revision=management_revision+1,updated_at=? WHERE id=?",r.title().trim(),r.requirements().trim(),due.toString(),maxSub,maxEval,Store.now(),aid);return changed(a,uid,"EDIT",r.reason());}
 @Transactional public Map<String,Object> state(long aid,long uid,State r){var a=access(aid,uid,true);revision(a,r.revision());boolean close=r.action().equals("CLOSE");if(Boolean.TRUE.equals(a.get("manually_closed"))==close)throw fail(422,close?"作业已经手动结束":"作业未被手动结束；如已自然截止，请先延长截止时间");if(!close&&!Instant.parse((String)a.get("due_at")).isAfter(Instant.now()))throw fail(422,"原截止时间已过，请先调整截止时间，再重新开放");s.db.update("UPDATE assignment SET manually_closed=?,management_revision=management_revision+1,updated_at=? WHERE id=?",close,Store.now(),aid);return changed(a,uid,r.action(),r.reason());}
 public List<Map<String,Object>> history(long aid,long uid){access(aid,uid,false);var rows=s.db.queryForList("SELECT h.*,u.display_name AS actor_name FROM assignment_change h JOIN app_user u ON u.id=h.actor_id WHERE h.assignment_id=? ORDER BY h.revision_no DESC LIMIT 100",aid);for(var r:rows){try{r.put("before",json.readTree((String)r.remove("before_json")));r.put("after",json.readTree((String)r.remove("after_json")));}catch(Exception e){throw new IllegalStateException(e);}}return rows;}
}
@RestController @RequestMapping("/api/assignments/{aid}")
class AssignmentManagementController {
 final Store s;final AssignmentManagement m;
 AssignmentManagementController(Store s,AssignmentManagement m){this.s=s;this.m=m;}
 @PutMapping public Object edit(@PathVariable long aid,@Valid @RequestBody AssignmentManagement.Edit r,Principal p){return m.edit(aid,s.uid(p.getName()),r);}
 @PostMapping("/state") public Object state(@PathVariable long aid,@Valid @RequestBody AssignmentManagement.State r,Principal p){return m.state(aid,s.uid(p.getName()),r);}
 @GetMapping("/history") public Object history(@PathVariable long aid,Principal p){return m.history(aid,s.uid(p.getName()));}
}
