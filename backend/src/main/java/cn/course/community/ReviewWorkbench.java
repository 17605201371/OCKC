package cn.course.community;
import java.util.*;
import java.security.Principal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import static cn.course.community.ApiError.fail;

@Service
public class ReviewWorkbench {
 final Store s;ReviewWorkbench(Store s){this.s=s;}
 record Scope(String role,String base,List<Object> args,String condition,String order){}
 Scope scope(long cid,long uid,String q,long aid,long author,String state,String drafts,String evidence,String versions,String sort){
  String role=s.role(cid,uid);if(!Set.of("TEACHER","ASSISTANT").contains(role))throw fail(403,"批改队列仅教师或助教可见");
  if(q==null||q.length()>80||aid<0||author<0||!Set.of("ALL","PENDING","GRADED").contains(state)||!Set.of("ALL","MINE","NONE").contains(drafts)||!Set.of("ALL","PENDING","DISPUTED").contains(evidence)||!Set.of("ALL","LATEST").contains(versions)||!Set.of("oldest","recent","evidence").contains(sort))throw fail(400,"评审查询参数不正确");
  if(!role.equals("TEACHER")&&!drafts.equals("ALL"))throw fail(403,"助教不能筛选教师私有评分草稿");
  var args=new ArrayList<Object>(List.of(uid,role.equals("TEACHER"),cid));
  String sql="SELECT v.id,v.entry_id,v.title,v.version_no,v.created_at,v.rubric_version,e.author_id,u.display_name AS author_name,a.id AS assignment_id,a.title AS assignment_title,g.q_final,g.revision_no AS evaluation_revision,d.updated_at AS review_draft_updated_at,CASE WHEN d.submission_id IS NOT NULL AND d.base_evaluation_revision<>(SELECT COUNT(*) FROM evaluation gx WHERE gx.submission_id=v.id) THEN TRUE ELSE FALSE END AS draft_stale,"+
   "CASE WHEN NOT EXISTS(SELECT 1 FROM submission newer WHERE newer.entry_id=v.entry_id AND newer.version_no>v.version_no) THEN TRUE ELSE FALSE END AS is_latest,"+
   "(SELECT COUNT(*) FROM evidence x WHERE x.submission_id=v.id AND x.state='PENDING') AS pending_evidence,(SELECT COUNT(*) FROM evidence x WHERE x.submission_id=v.id AND x.state='DISPUTED') AS disputed_evidence "+
   "FROM submission v JOIN entry e ON e.id=v.entry_id JOIN assignment a ON a.id=e.assignment_id JOIN app_user u ON u.id=e.author_id LEFT JOIN evaluation g ON g.submission_id=v.id AND g.active=TRUE LEFT JOIN review_draft d ON d.submission_id=v.id AND d.teacher_id=? AND ?=TRUE WHERE a.course_id=?";
  if(aid>0){sql+=" AND a.id=?";args.add(aid);}if(author>0){sql+=" AND e.author_id=?";args.add(author);}
  if(!q.isBlank()){String term="%"+q.trim().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";sql+=" AND (LOWER(v.title) LIKE ? ESCAPE '!' OR LOWER(u.display_name) LIKE ? ESCAPE '!' OR LOWER(a.title) LIKE ? ESCAPE '!')";for(int i=0;i<3;i++)args.add(term);}
  if(versions.equals("LATEST"))sql+=" AND NOT EXISTS(SELECT 1 FROM submission nv WHERE nv.entry_id=v.entry_id AND nv.version_no>v.version_no)";
  String condition=switch(state){case "PENDING"->"q_final IS NULL";case "GRADED"->"q_final IS NOT NULL";default->"1=1";};
  if(!drafts.equals("ALL"))condition+=drafts.equals("MINE")?" AND review_draft_updated_at IS NOT NULL":" AND review_draft_updated_at IS NULL";
  if(!evidence.equals("ALL"))condition+=evidence.equals("PENDING")?" AND pending_evidence>0":" AND disputed_evidence>0";
  String order=switch(sort){case "recent"->"created_at DESC,id DESC";case "evidence"->"disputed_evidence DESC,pending_evidence DESC,created_at,id";default->"created_at,id";};
  return new Scope(role,sql,List.copyOf(args),condition,order);
 }
 Map<String,Object> summary(Scope scope){return s.one("SELECT COUNT(*) AS submission_count,COALESCE(SUM(CASE WHEN q_final IS NULL THEN 1 ELSE 0 END),0) AS pending_count,COUNT(q_final) AS graded_count,COALESCE(SUM(CASE WHEN review_draft_updated_at IS NOT NULL THEN 1 ELSE 0 END),0) AS my_draft_count,COALESCE(SUM(CASE WHEN pending_evidence>0 THEN 1 ELSE 0 END),0) AS pending_evidence_count,COALESCE(SUM(CASE WHEN disputed_evidence>0 THEN 1 ELSE 0 END),0) AS disputed_evidence_count FROM ("+scope.base()+") scoped",scope.args().toArray());}
 int count(Scope scope,String condition){return s.db.queryForObject("SELECT COUNT(*) FROM ("+scope.base()+") scoped WHERE "+condition,Integer.class,scope.args().toArray());}
 List<Map<String,Object>> rows(Scope scope,String condition,String order,int limit,int offset){var args=new ArrayList<>(scope.args());args.add(limit);args.add(offset);return s.db.queryForList("SELECT * FROM ("+scope.base()+") scoped WHERE "+condition+" ORDER BY "+order+" LIMIT ? OFFSET ?",args.toArray());}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Map<String,Object> list(long cid,long uid,String q,long aid,long author,String state,String drafts,String evidence,String versions,String sort,int page){
  if(page<1||page>100000)throw fail(400,"评审页码不正确");var scope=scope(cid,uid,q,aid,author,state,drafts,evidence,versions,sort);int total=count(scope,scope.condition());int pages=Math.max(1,(total+19)/20),actualPage=Math.min(page,pages);
  var result=new LinkedHashMap<String,Object>();result.put("items",rows(scope,scope.condition(),scope.order(),20,(actualPage-1)*20));result.put("summary",summary(scope));result.put("total",total);result.put("page",actualPage);result.put("pageSize",20);result.put("teacher",scope.role().equals("TEACHER"));
  result.put("assignments",s.db.queryForList("SELECT id,title FROM assignment WHERE course_id=? ORDER BY id DESC",cid));
  result.put("authors",s.db.queryForList("SELECT DISTINCT u.id,u.display_name,CASE WHEN cm.user_id IS NULL THEN FALSE ELSE TRUE END AS current_member FROM entry e JOIN submission v ON v.entry_id=e.id JOIN assignment a ON a.id=e.assignment_id JOIN app_user u ON u.id=e.author_id LEFT JOIN course_member cm ON cm.course_id=a.course_id AND cm.user_id=e.author_id WHERE a.course_id=? ORDER BY u.display_name,u.id",cid));return result;
 }
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Map<String,Object> dashboard(long cid,long uid){var scope=scope(cid,uid,"",0,0,"ALL","ALL","ALL","ALL","oldest");return Map.of("summary",summary(scope),"items",rows(scope,"1=1","CASE WHEN q_final IS NULL THEN 0 ELSE 1 END,created_at,id",6,0));}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Map<String,Object> next(long cid,long uid,String q,long aid,long author,String drafts,String evidence,String versions,String sort,long exclude){
  if(exclude<0)throw fail(400,"提交编号不正确");var scope=scope(cid,uid,q,aid,author,"PENDING",drafts,evidence,versions,sort);
  if(exclude>0){var own=s.db.queryForList("SELECT v.id FROM submission v JOIN entry e ON e.id=v.entry_id JOIN assignment a ON a.id=e.assignment_id WHERE v.id=? AND a.course_id=?",exclude,cid);if(own.isEmpty())throw fail(404,"当前提交不属于这门课程");}
  // Exclusion is numeric and validated. Re-read live state; this is not a reservation or automatic grading.
  String condition=scope.condition()+" AND id<>"+exclude;var items=rows(scope,condition,scope.order(),1,0);var out=new LinkedHashMap<String,Object>();out.put("item",items.isEmpty()?null:items.getFirst());out.put("remaining",count(scope,condition));return out;
 }
}
@RestController @RequestMapping("/api/courses/{cid}/review-workbench")
class ReviewWorkbenchController {
 final ReviewWorkbench w;final Store s;ReviewWorkbenchController(ReviewWorkbench w,Store s){this.w=w;this.s=s;}
 @GetMapping public ResponseEntity<Object> list(@PathVariable long cid,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") long assignmentId,@RequestParam(defaultValue="0") long authorId,@RequestParam(defaultValue="ALL") String state,@RequestParam(defaultValue="ALL") String drafts,@RequestParam(defaultValue="ALL") String evidence,@RequestParam(defaultValue="ALL") String versions,@RequestParam(defaultValue="oldest") String sort,@RequestParam(defaultValue="1") int page,Principal p){return ResponseEntity.ok().header("Cache-Control","no-store").body(w.list(cid,s.uid(p.getName()),q,assignmentId,authorId,state,drafts,evidence,versions,sort,page));}
 @GetMapping("/dashboard") public ResponseEntity<Object> dashboard(@PathVariable long cid,Principal p){return ResponseEntity.ok().header("Cache-Control","no-store").body(w.dashboard(cid,s.uid(p.getName())));}
 @GetMapping("/next") public ResponseEntity<Object> next(@PathVariable long cid,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") long assignmentId,@RequestParam(defaultValue="0") long authorId,@RequestParam(defaultValue="ALL") String drafts,@RequestParam(defaultValue="ALL") String evidence,@RequestParam(defaultValue="ALL") String versions,@RequestParam(defaultValue="ALL") String state,@RequestParam(defaultValue="oldest") String sort,@RequestParam(defaultValue="0") long excludeId,Principal p){if(!Set.of("ALL","PENDING","GRADED").contains(state))throw fail(400,"评审状态不正确");return ResponseEntity.ok().header("Cache-Control","no-store").body(w.next(cid,s.uid(p.getName()),q,assignmentId,authorId,drafts,evidence,versions,sort,excludeId));}
}
