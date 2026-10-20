package cn.course.community;
import java.util.*;
import java.security.Principal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.bind.annotation.*;
import static cn.course.community.ApiError.fail;

@Service
public class ProgressWorkbench {
 final Store s;final CommunityService domain;
 ProgressWorkbench(Store s,CommunityService domain){this.s=s;this.domain=domain;}
 static final String JOIN=" FROM entry e JOIN assignment a ON a.id=e.assignment_id JOIN app_user u ON u.id=e.author_id ";
 static final String SELECT="SELECT e.id,e.title,e.author_id,e.assignment_id,e.revision,e.updated_at,a.title AS assignment_title,a.rubric_version AS assignment_rubric,u.display_name AS author_name,"+
 "(SELECT MAX(v.version_no) FROM submission v WHERE v.entry_id=e.id) AS latest_submitted_version,"+
 "(SELECT v.version_no FROM submission v JOIN evaluation g ON g.submission_id=v.id AND g.active=TRUE WHERE v.entry_id=e.id ORDER BY v.version_no DESC LIMIT 1) AS latest_graded_version,"+
 "(SELECT g.revision_no FROM submission v JOIN evaluation g ON g.submission_id=v.id AND g.active=TRUE WHERE v.entry_id=e.id ORDER BY v.version_no DESC LIMIT 1) AS latest_evaluation_revision,"+
 "(SELECT v.rubric_version FROM submission v JOIN evaluation g ON g.submission_id=v.id AND g.active=TRUE WHERE v.entry_id=e.id ORDER BY v.version_no DESC LIMIT 1) AS latest_graded_rubric,"+
 "(SELECT COUNT(*) FROM submission v WHERE v.entry_id=e.id) AS submission_count,"+
 "(SELECT COUNT(*) FROM submission v WHERE v.entry_id=e.id AND NOT EXISTS(SELECT 1 FROM evaluation g WHERE g.submission_id=v.id AND g.active=TRUE)) AS pending_count,"+
 "(SELECT COUNT(*) FROM feedback f JOIN submission v ON v.id=f.submission_id WHERE v.entry_id=e.id) AS feedback_total,"+
 "(SELECT COUNT(*) FROM feedback f JOIN submission v ON v.id=f.submission_id WHERE v.entry_id=e.id AND f.resolved=FALSE) AS feedback_open,"+
 "(SELECT COUNT(*) FROM evidence x JOIN submission v ON v.id=x.submission_id WHERE v.entry_id=e.id AND x.state='PASS') AS evidence_passed,"+
 "(SELECT g.q_final FROM submission v JOIN evaluation g ON g.submission_id=v.id AND g.active=TRUE WHERE v.entry_id=e.id ORDER BY v.version_no DESC LIMIT 1) AS latest_score,"+
 "(SELECT g.dimensions_json FROM submission v JOIN evaluation g ON g.submission_id=v.id AND g.active=TRUE WHERE v.entry_id=e.id ORDER BY v.version_no DESC LIMIT 1) AS latest_dimensions_json";
 boolean staff(String role){return Set.of("TEACHER","ASSISTANT").contains(role);}
 String marks(int n){return String.join(",",Collections.nCopies(n,"?"));}
 void versions(List<Map<String,Object>> rows){for(var v:rows){Object dims=v.remove("dimensions_json");v.put("dimensions",dims==null?Map.of():domain.decode(dims));}}
 record QueryScope(String role,String base,List<Object> args,String condition,String order){}
 QueryScope scope(long cid,long uid,String q,long aid,long author,String state,String sort){
  String role=s.role(cid,uid);boolean staff=staff(role);
  if(q==null||q.length()>80||aid<0||author<0||!Set.of("ALL","DRAFT","PENDING","FEEDBACK").contains(state)||!Set.of("recent","quality","feedback","pending").contains(sort))throw fail(400,"进展查询参数不正确");
  if(!staff&&author!=0&&author!=uid)throw fail(403,"只能查看本人的学习轨迹");
  var args=new ArrayList<Object>();args.add(cid);String where="a.course_id=?";
  if(!staff||author>0){where+=" AND e.author_id=?";args.add(staff?author:uid);}
  if(aid>0){where+=" AND a.id=?";args.add(aid);}
  if(!q.isBlank()){String term="%"+q.trim().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";where+=" AND (LOWER(e.title) LIKE ? ESCAPE '!' OR LOWER(u.display_name) LIKE ? ESCAPE '!' OR LOWER(a.title) LIKE ? ESCAPE '!')";for(int i=0;i<3;i++)args.add(term);}
  String base=SELECT+JOIN+"WHERE "+where;
  String condition=switch(state){case "DRAFT"->"submission_count=0";case "PENDING"->"pending_count>0";case "FEEDBACK"->"feedback_open>0";default->"1=1";};
  String order=switch(sort){case "quality"->"CASE WHEN latest_score IS NULL THEN 1 ELSE 0 END,latest_score DESC,id DESC";case "feedback"->"feedback_open DESC,updated_at DESC,id DESC";case "pending"->"pending_count DESC,updated_at DESC,id DESC";default->"updated_at DESC,id DESC";};
  return new QueryScope(role,base,List.copyOf(args),condition,order);
 }
 Map<String,Object> summary(QueryScope scope){String base=scope.base();var args=scope.args();
  return s.one("SELECT COUNT(*) AS entry_count,COALESCE(SUM(submission_count),0) AS submissions,AVG(latest_score) AS latest_average,COUNT(latest_score) AS graded_entries,COALESCE(SUM(feedback_open),0) AS open_feedback,COALESCE(SUM(pending_count),0) AS pending_versions,COALESCE(SUM(CASE WHEN submission_count=0 THEN 1 ELSE 0 END),0) AS draft_entries,COALESCE(SUM(CASE WHEN pending_count>0 THEN 1 ELSE 0 END),0) AS pending_entries,COALESCE(SUM(CASE WHEN feedback_open>0 THEN 1 ELSE 0 END),0) AS feedback_entries FROM ("+base+") scoped",args.toArray());
 }
 int count(QueryScope scope){String base=scope.base(),condition=scope.condition();var args=scope.args();
  return s.db.queryForObject("SELECT COUNT(*) FROM ("+base+") scoped WHERE "+condition,Integer.class,args.toArray());
 }
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Map<String,Object> list(long cid,long uid,String q,long aid,long author,String state,String sort,int page){
  if(page<1||page>100000)throw fail(400,"进展查询参数不正确");
  var scope=scope(cid,uid,q,aid,author,state,sort);String role=scope.role(),base=scope.base(),condition=scope.condition(),order=scope.order();boolean staff=staff(role);var args=scope.args();var summary=summary(scope);int total=count(scope);
  var paged=new ArrayList<>(args);paged.add(20);paged.add((page-1)*20);
  var rows=s.db.queryForList("SELECT * FROM ("+base+") scoped WHERE "+condition+" ORDER BY "+order+" LIMIT ? OFFSET ?",paged.toArray());
  for(var e:rows){Object d=e.remove("latest_dimensions_json");e.put("latest_dimensions",d==null?Map.of():domain.decode(d));e.put("feedback_resolved",((Number)e.get("feedback_total")).longValue()-((Number)e.get("feedback_open")).longValue());e.put("versions",new ArrayList<Map<String,Object>>());}
  if(!rows.isEmpty()){
   var ids=rows.stream().map(e->e.get("id")).toArray();
   var recent=s.db.queryForList("SELECT * FROM (SELECT v.id,v.entry_id,v.version_no,v.created_at,g.q_final,ROW_NUMBER() OVER(PARTITION BY v.entry_id ORDER BY v.version_no DESC) AS rn FROM submission v LEFT JOIN evaluation g ON g.submission_id=v.id AND g.active=TRUE WHERE v.entry_id IN ("+marks(ids.length)+")) ranked WHERE rn<=5 ORDER BY entry_id,version_no",ids);
   var byId=new HashMap<Long,Map<String,Object>>();for(var e:rows)byId.put(s.id(e,"id"),e);for(var v:recent){v.remove("rn");((List<Map<String,Object>>)byId.get(s.id(v,"entry_id")).get("versions")).add(v);}
  }
  var assignments=s.db.queryForList("SELECT id,title FROM assignment WHERE course_id=? ORDER BY id DESC",cid);
  var authors=staff?s.db.queryForList("SELECT DISTINCT u.id,u.display_name,CASE WHEN cm.user_id IS NULL THEN FALSE ELSE TRUE END AS current_member FROM entry e JOIN assignment a ON a.id=e.assignment_id JOIN app_user u ON u.id=e.author_id LEFT JOIN course_member cm ON cm.course_id=a.course_id AND cm.user_id=e.author_id WHERE a.course_id=? ORDER BY u.display_name,u.id",cid):List.of();
  return Map.of("items",rows,"summary",summary,"total",total,"page",page,"pageSize",20,"staff",staff,"assignments",assignments,"authors",authors);
 }
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Map<String,Object> detail(long eid,long uid,int versionPage,int feedbackPage,String feedbackState){
  var e=s.one("SELECT e.id,e.author_id,e.title,e.revision,e.updated_at,a.course_id,a.id AS assignment_id,a.title AS assignment_title,u.display_name AS author_name"+JOIN+"WHERE e.id=?",eid);
  String role=s.role(s.id(e,"course_id"),uid);if(!staff(role)&&s.id(e,"author_id")!=uid)throw fail(403,"只能查看本人的学习轨迹");
  if(versionPage<1||versionPage>100000||feedbackPage<1||feedbackPage>100000||!Set.of("OPEN","ALL","RESOLVED").contains(feedbackState))throw fail(400,"轨迹详情查询参数不正确");
  var versions=s.db.queryForList("SELECT v.id,v.version_no,v.created_at,g.q_final,g.dimensions_json,g.revision_no AS evaluation_revision FROM submission v LEFT JOIN evaluation g ON g.submission_id=v.id AND g.active=TRUE WHERE v.entry_id=? ORDER BY v.version_no DESC LIMIT 20 OFFSET ?",eid,(versionPage-1)*20);versions(versions);
  String filter=feedbackState.equals("ALL")?"":feedbackState.equals("OPEN")?" AND f.resolved=FALSE":" AND f.resolved=TRUE";
  String feedbackFrom=" FROM feedback f JOIN submission v ON v.id=f.submission_id JOIN entry e ON e.id=v.entry_id JOIN app_user u ON u.id=f.teacher_id WHERE v.entry_id=?";
  var feedback=s.db.queryForList("SELECT f.id,f.submission_id,f.paragraph_id,f.original_text,f.indicator_code,f.body,f.resolved,f.created_at,v.version_no,u.display_name AS teacher_name,(SELECT z.action_type FROM feedback_action z WHERE z.feedback_id=f.id AND z.user_id=e.author_id AND z.action_type IN ('REPLY','ADOPT','IGNORE') ORDER BY z.id DESC LIMIT 1) AS author_action"+feedbackFrom+filter+" ORDER BY f.id DESC LIMIT 20 OFFSET ?",eid,(feedbackPage-1)*20);
  return Map.of("entry",e,"versions",versions,"versionTotal",s.db.queryForObject("SELECT COUNT(*) FROM submission WHERE entry_id=?",Integer.class,eid),"versionPage",versionPage,"feedback",feedback,"feedbackTotal",s.db.queryForObject("SELECT COUNT(*)"+feedbackFrom+filter,Integer.class,eid),"feedbackPage",feedbackPage,"pageSize",20,"role",role);
 }
}
@RestController @RequestMapping("/api")
class ProgressWorkbenchController {
 final ProgressWorkbench w;final Store s;ProgressWorkbenchController(ProgressWorkbench w,Store s){this.w=w;this.s=s;}
 @GetMapping("/courses/{cid}/progress-workbench") public Object list(@PathVariable long cid,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") long assignmentId,@RequestParam(defaultValue="0") long authorId,@RequestParam(defaultValue="ALL") String state,@RequestParam(defaultValue="recent") String sort,@RequestParam(defaultValue="1") int page,Principal p){return w.list(cid,s.uid(p.getName()),q,assignmentId,authorId,state,sort,page);}
 @GetMapping("/progress-entries/{id}") public Object detail(@PathVariable long id,@RequestParam(defaultValue="1") int versionPage,@RequestParam(defaultValue="1") int feedbackPage,@RequestParam(defaultValue="OPEN") String feedbackState,Principal p){return w.detail(id,s.uid(p.getName()),versionPage,feedbackPage,feedbackState);}
}
