package cn.course.community;

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * 首页「待办」：把散在批改中心、消息中心、知识社区和证据核验里、此刻还没处理完的事按身份汇总。
 * 只读当前状态（不是事件流），处理掉一项，下次读取就少一项。每组返回总数和最早的 5 条。
 */
@Service
public class TodayService {
 static final int LIMIT = 5;
 static final Duration DUE_SOON = Duration.ofHours(48);
 final Store s;
 TodayService(Store s){this.s=s;}

 static final String FROM_SUBMISSION=" FROM submission v JOIN entry e ON e.id=v.entry_id JOIN assignment a ON a.id=e.assignment_id JOIN app_user u ON u.id=e.author_id ";
 static final String LATEST="NOT EXISTS(SELECT 1 FROM submission nv WHERE nv.entry_id=v.entry_id AND nv.version_no>v.version_no)";
 static final String GRADED="EXISTS(SELECT 1 FROM evaluation g WHERE g.submission_id=v.id AND g.active=TRUE)";
 static final String TEACHER_DECIDED="EXISTS(SELECT 1 FROM verification z WHERE z.evidence_id=x.id AND (z.verifier_role='TEACHER' OR (z.verifier_role IS NULL AND EXISTS(SELECT 1 FROM course_member cm WHERE cm.course_id=a.course_id AND cm.user_id=z.verifier_id AND cm.role='TEACHER'))))";

 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Map<String,Object> today(long cid,long uid){
  String role=s.role(cid,uid);
  boolean teacher=role.equals("TEACHER"),staff=teacher||role.equals("ASSISTANT");
  var groups=new ArrayList<Map<String,Object>>();
  if(staff){
   groups.add(group("review",pendingReviews(cid)));
   if(teacher){
    groups.add(group("responses",studentResponses(cid)));
    groups.add(group("publications",pendingPublications(cid)));
    groups.add(group("disputes",disputedEvidence(cid)));
   }
   groups.add(group("due",dueSoonForStaff(cid)));
  }else{
   groups.add(group("feedback",feedbackToHandle(cid,uid)));
   groups.add(group("due",dueSoonForStudent(cid,uid)));
   groups.add(group("verify",evidenceToVerify(cid,uid)));
  }
  return Map.of("role",role,"groups",groups);
 }

 Map<String,Object> group(String key,List<Map<String,Object>> rows){
  var g=new LinkedHashMap<String,Object>();g.put("key",key);g.put("count",rows.size());g.put("items",rows.subList(0,Math.min(LIMIT,rows.size())));return g;
 }

 // 待批改：每篇作品的最新版本还没有正式评价，先交的排前面（与批改中心「只列最新版本」同一口径）。
 List<Map<String,Object>> pendingReviews(long cid){
  return s.db.queryForList("SELECT v.id AS submission_id,v.title,v.version_no,v.created_at,u.display_name AS author_name,a.title AS assignment_title"+FROM_SUBMISSION+"WHERE a.course_id=? AND "+LATEST+" AND NOT "+GRADED+" ORDER BY v.created_at,v.id",cid);
 }

 // 学生回应待确认：批注还没解决，最近一次动作是作者本人的（我已修改 / 回复 / 不采纳），教师还没跟进。
 // 打开时进入这篇作品的最新版本：上一版的批注会作为「历史批注」出现在那里，可以直接标记已解决。
 List<Map<String,Object>> studentResponses(long cid){
  return s.db.queryForList("SELECT f.id AS feedback_id,f.body AS feedback_body,f.indicator_code,f.paragraph_id,la.action_type,la.body AS reply,la.created_at,v.title,v.version_no,u.display_name AS author_name,"
   +"(SELECT lv.id FROM submission lv WHERE lv.entry_id=e.id ORDER BY lv.version_no DESC LIMIT 1) AS submission_id"
   +" FROM feedback f JOIN submission v ON v.id=f.submission_id JOIN entry e ON e.id=v.entry_id JOIN assignment a ON a.id=e.assignment_id JOIN app_user u ON u.id=e.author_id"
   +" JOIN feedback_action la ON la.feedback_id=f.id AND la.id=(SELECT MAX(x.id) FROM feedback_action x WHERE x.feedback_id=f.id)"
   +" WHERE a.course_id=? AND f.resolved=FALSE AND la.user_id=e.author_id AND la.action_type IN ('ADOPT','REPLY','IGNORE') ORDER BY la.created_at,la.id",cid);
 }

 List<Map<String,Object>> pendingPublications(long cid){
  return s.db.queryForList("SELECT p.id AS publication_id,p.submission_id,p.requested_at,v.title,v.version_no,u.display_name AS author_name FROM publication p JOIN submission v ON v.id=p.submission_id JOIN entry e ON e.id=v.entry_id JOIN assignment a ON a.id=e.assignment_id JOIN app_user u ON u.id=e.author_id WHERE a.course_id=? AND p.state='PENDING' ORDER BY p.requested_at,p.id",cid);
 }

 // 有争议的证据：只列还能裁定的（最新版本、提交尚未正式评分）。
 List<Map<String,Object>> disputedEvidence(long cid){
  return s.db.queryForList("SELECT x.id AS evidence_id,x.source_title,x.claim_text,v.title,v.version_no,u.display_name AS author_name FROM evidence x JOIN submission v ON v.id=x.submission_id JOIN entry e ON e.id=v.entry_id JOIN assignment a ON a.id=e.assignment_id JOIN app_user u ON u.id=e.author_id WHERE a.course_id=? AND x.state='DISPUTED' AND "+LATEST+" AND NOT "+GRADED+" ORDER BY x.id",cid);
 }

 List<Map<String,Object>> openAssignments(long cid){
  var now=Instant.now();
  var out=new ArrayList<Map<String,Object>>();
  for(var a:s.db.queryForList("SELECT id,title,due_at FROM assignment WHERE course_id=? AND COALESCE(free_form,FALSE)=FALSE AND COALESCE(manually_closed,FALSE)=FALSE ORDER BY due_at,id",cid)){
   Instant due;try{due=Instant.parse((String)a.get("due_at"));}catch(Exception e){continue;}
   if(due.isAfter(now)&&!due.isAfter(now.plus(DUE_SOON)))out.add(a);
  }
  return out;
 }

 // 快截止：48 小时内截止、还有学生没交任何版本的作业，写明还差几人。
 List<Map<String,Object>> dueSoonForStaff(long cid){
  int students=s.db.queryForObject("SELECT COUNT(*) FROM course_member WHERE course_id=? AND role='STUDENT'",Integer.class,cid);
  var out=new ArrayList<Map<String,Object>>();
  for(var a:openAssignments(cid)){
   int submitted=s.db.queryForObject("SELECT COUNT(DISTINCT e.author_id) FROM entry e JOIN course_member m ON m.user_id=e.author_id AND m.course_id=? AND m.role='STUDENT' WHERE e.assignment_id=? AND EXISTS(SELECT 1 FROM submission v WHERE v.entry_id=e.id)",Integer.class,cid,a.get("id"));
   if(submitted>=students)continue;
   var row=new LinkedHashMap<String,Object>(a);row.put("assignment_id",a.get("id"));row.put("missing",students-submitted);row.put("students",students);out.add(row);
  }
  return out;
 }

 // 学生要处理的批注：自己作品上还没解决、自己也还没回应过（或教师在自己回应之后又有动作）的批注。
 List<Map<String,Object>> feedbackToHandle(long cid,long uid){
  return s.db.queryForList("SELECT f.id AS feedback_id,f.body AS feedback_body,f.indicator_code,f.paragraph_id,f.created_at,v.title,v.version_no,a.id AS assignment_id,a.title AS assignment_title FROM feedback f JOIN submission v ON v.id=f.submission_id JOIN entry e ON e.id=v.entry_id JOIN assignment a ON a.id=e.assignment_id"
   +" WHERE a.course_id=? AND e.author_id=? AND f.resolved=FALSE AND NOT EXISTS(SELECT 1 FROM feedback_action la WHERE la.feedback_id=f.id AND la.id=(SELECT MAX(x.id) FROM feedback_action x WHERE x.feedback_id=f.id) AND la.user_id=e.author_id)"
   +" AND COALESCE(e.state,'OPEN')<>'PASSED' ORDER BY f.created_at,f.id",cid,uid);
 }

 List<Map<String,Object>> dueSoonForStudent(long cid,long uid){
  var out=new ArrayList<Map<String,Object>>();
  for(var a:openAssignments(cid)){
   int mine=s.db.queryForObject("SELECT COUNT(*) FROM entry e WHERE e.assignment_id=? AND e.author_id=? AND EXISTS(SELECT 1 FROM submission v WHERE v.entry_id=e.id)",Integer.class,a.get("id"),uid);
   if(mine>0)continue;
   var row=new LinkedHashMap<String,Object>(a);row.put("assignment_id",a.get("id"));
   row.put("started",s.db.queryForObject("SELECT COUNT(*) FROM entry WHERE assignment_id=? AND author_id=?",Integer.class,a.get("id"),uid)>0);
   out.add(row);
  }
  return out;
 }

 // 可以帮同学核验的证据：与证据核验页的规则一致（不是自己的、自己没核验过、教师没裁定、提交没评分、最新版本）。
 List<Map<String,Object>> evidenceToVerify(long cid,long uid){
  return s.db.queryForList("SELECT x.id AS evidence_id,x.source_title,x.claim_text,x.state,v.title,u.display_name AS author_name FROM evidence x JOIN submission v ON v.id=x.submission_id JOIN entry e ON e.id=v.entry_id JOIN assignment a ON a.id=e.assignment_id JOIN app_user u ON u.id=e.author_id"
   +" WHERE a.course_id=? AND x.state IN ('PENDING','DISPUTED') AND e.author_id<>? AND "+LATEST+" AND NOT "+GRADED
   +" AND NOT EXISTS(SELECT 1 FROM verification z WHERE z.evidence_id=x.id AND z.verifier_id=?) AND NOT "+TEACHER_DECIDED+" ORDER BY x.id",cid,uid,uid);
 }
}

@RestController @RequestMapping("/api/courses/{cid}")
class TodayController {
 final Store s;final TodayService t;
 TodayController(Store s,TodayService t){this.s=s;this.t=t;}
 @GetMapping("/today") public Object today(@PathVariable long cid,Principal p){return t.today(cid,s.uid(p.getName()));}
}
