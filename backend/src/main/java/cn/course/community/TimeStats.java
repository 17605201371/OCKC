package cn.course.community;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import static cn.course.community.ApiError.fail;

/**
 * 用时统计：把 activity_time 里的做题、批改秒数按作业、学生汇总，并给出「用时—得分」散点与用时明显偏短的作品。
 * 只给本课教师/助教看。没有用时记录的作品（多数是功能上线前的）单独计数，不当成 0 分钟参与统计。自由创作每篇各占一个占位作业，没有可比的同组，不纳入。
 */
@Service
public class TimeStats {
 static final int ENTRY_LIMIT=5000;
 /** 用时明显偏短：低于同作业中位数的这个比例，且不到 SHORT_ABS 秒。 */
 static final double SHORT_RATIO=0.25;static final int SHORT_ABS=600,MIN_PEERS=3;
 final Store s;TimeStats(Store s){this.s=s;}

 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Map<String,Object> stats(long cid,long uid,long aid){
  String role=s.role(cid,uid);if(!Set.of("TEACHER","ASSISTANT").contains(role))throw fail(403,"用时统计仅教师或助教可见");
  if(aid<0)throw fail(400,"作业参数不正确");
  var args=new ArrayList<Object>(List.of(cid));String where="a.course_id=?";
  if(aid>0){where+=" AND a.id=?";args.add(aid);}
  args.add(ENTRY_LIMIT+1);
  var rows=s.db.queryForList("SELECT e.id,e.title,e.author_id,u.display_name AS author_name,a.id AS assignment_id,a.title AS assignment_title,"+
   "(SELECT COALESCE(SUM(t.seconds),0) FROM activity_time t WHERE t.kind='WRITE' AND t.target_id=e.id AND t.user_id=e.author_id) AS write_seconds,"+
   "(SELECT COALESCE(SUM(t.seconds),0) FROM activity_time t JOIN submission v ON v.id=t.target_id WHERE t.kind='GRADE' AND v.entry_id=e.id) AS grade_seconds,"+
   "(SELECT COUNT(DISTINCT t.target_id) FROM activity_time t JOIN submission v ON v.id=t.target_id WHERE t.kind='GRADE' AND v.entry_id=e.id) AS graded_versions_timed,"+
   "(SELECT COUNT(*) FROM submission v WHERE v.entry_id=e.id) AS submission_count,"+
   "(SELECT g.q_final FROM submission v JOIN evaluation g ON g.submission_id=v.id AND g.active=TRUE WHERE v.entry_id=e.id ORDER BY v.version_no DESC LIMIT 1) AS latest_score "+
   "FROM entry e JOIN assignment a ON a.id=e.assignment_id JOIN app_user u ON u.id=e.author_id WHERE "+where+" AND a.free_form=FALSE ORDER BY a.id DESC,e.id LIMIT ?",args.toArray());
  if(rows.size()>ENTRY_LIMIT)throw fail(422,"作品超过 "+ENTRY_LIMIT+" 篇，请按作业筛选后查看");

  long writeTotal=0,gradeTotal=0;int timed=0,untimed=0,gradeVersions=0;
  for(var r:rows){long w=n(r,"write_seconds"),g=n(r,"grade_seconds");writeTotal+=w;gradeTotal+=g;gradeVersions+=(int)n(r,"graded_versions_timed");if(w>0)timed++;else untimed++;}
  var summary=new LinkedHashMap<String,Object>();
  summary.put("entries",rows.size());summary.put("timed_entries",timed);summary.put("untimed_entries",untimed);
  summary.put("write_seconds",writeTotal);summary.put("grade_seconds",gradeTotal);
  summary.put("avg_write_seconds",timed==0?null:writeTotal/timed);
  summary.put("avg_grade_seconds_per_version",gradeVersions==0?null:gradeTotal/gradeVersions);

  // 按作业
  var byAssignment=new LinkedHashMap<Long,List<Map<String,Object>>>();
  for(var r:rows)byAssignment.computeIfAbsent(n(r,"assignment_id"),k->new ArrayList<>()).add(r);
  var assignments=new ArrayList<Map<String,Object>>();var medians=new HashMap<Long,Long>();
  for(var en:byAssignment.entrySet()){
   var list=en.getValue();var times=list.stream().map(r->n(r,"write_seconds")).filter(w->w>0).sorted().toList();
   Long median=median(times);if(median!=null)medians.put(en.getKey(),median);
   var scores=list.stream().map(r->r.get("latest_score")).filter(Objects::nonNull).map(v->((Number)v).doubleValue()).toList();
   long g=list.stream().mapToLong(r->n(r,"grade_seconds")).sum();long gv=list.stream().mapToLong(r->n(r,"graded_versions_timed")).sum();
   var m=new LinkedHashMap<String,Object>();
   m.put("assignment_id",en.getKey());m.put("assignment_title",list.getFirst().get("assignment_title"));
   m.put("entries",list.size());m.put("timed_entries",times.size());
   m.put("median_write_seconds",median);m.put("avg_write_seconds",times.isEmpty()?null:times.stream().mapToLong(Long::longValue).sum()/times.size());
   m.put("avg_grade_seconds_per_version",gv==0?null:g/gv);
   m.put("avg_score",scores.isEmpty()?null:round1(scores.stream().mapToDouble(Double::doubleValue).average().orElse(0)));
   assignments.add(m);
  }

  // 用时—得分散点（只取既有用时又有正式评分的作品）
  var points=new ArrayList<Map<String,Object>>();
  for(var r:rows){if(n(r,"write_seconds")<=0||r.get("latest_score")==null)continue;
   points.add(Map.of("entry_id",r.get("id"),"title",r.get("title"),"author_name",r.get("author_name"),"assignment_title",r.get("assignment_title"),"write_seconds",n(r,"write_seconds"),"score",((Number)r.get("latest_score")).doubleValue()));}
  Double r=points.size()<5?null:pearson(points);

  // 用时明显偏短：已提交、有用时、同作业至少 MIN_PEERS 篇有用时
  var shortList=new ArrayList<Map<String,Object>>();
  for(var row:rows){long w=n(row,"write_seconds");Long med=medians.get(n(row,"assignment_id"));
   int peers=(int)byAssignment.get(n(row,"assignment_id")).stream().filter(x->n(x,"write_seconds")>0).count();
   if(w<=0||med==null||peers<MIN_PEERS||n(row,"submission_count")==0)continue;
   if(w<med*SHORT_RATIO&&w<SHORT_ABS){var m=new LinkedHashMap<String,Object>();
    for(String k:List.of("id","title","author_name","assignment_title","write_seconds","latest_score"))m.put(k,row.get(k));m.put("median_write_seconds",med);shortList.add(m);}}
  shortList.sort(Comparator.comparingLong(m->n(m,"write_seconds")));

  // 按学生
  var students=rows.stream().collect(Collectors.groupingBy(x->n(x,"author_id"),LinkedHashMap::new,Collectors.toList())).values().stream().map(list->{
   var m=new LinkedHashMap<String,Object>();m.put("author_id",list.getFirst().get("author_id"));m.put("author_name",list.getFirst().get("author_name"));
   m.put("entries",list.size());m.put("timed_entries",list.stream().filter(x->n(x,"write_seconds")>0).count());
   m.put("write_seconds",list.stream().mapToLong(x->n(x,"write_seconds")).sum());
   var sc=list.stream().map(x->x.get("latest_score")).filter(Objects::nonNull).mapToDouble(v->((Number)v).doubleValue()).toArray();
   m.put("avg_score",sc.length==0?null:round1(Arrays.stream(sc).average().orElse(0)));return (Map<String,Object>)m;})
   .sorted(Comparator.comparingLong((Map<String,Object> m)->n(m,"write_seconds")).reversed()).toList();

  var out=new LinkedHashMap<String,Object>();
  out.put("summary",summary);out.put("assignments",assignments);out.put("points",points);out.put("correlation",r);
  out.put("short_entries",shortList);out.put("students",students);
  out.put("rules",Map.of("short_ratio",SHORT_RATIO,"short_abs_seconds",SHORT_ABS,"min_peers",MIN_PEERS));
  out.put("assignment_options",s.db.queryForList("SELECT id,title FROM assignment WHERE course_id=? AND free_form=FALSE ORDER BY id DESC",cid));
  return out;
 }
 static long n(Map<String,Object> m,String k){Object v=m.get(k);return v==null?0:((Number)v).longValue();}
 static Long median(List<Long> sorted){if(sorted.isEmpty())return null;int i=sorted.size()/2;return sorted.size()%2==1?sorted.get(i):(sorted.get(i-1)+sorted.get(i))/2;}
 static double round1(double v){return Math.round(v*10)/10.0;}
 static Double pearson(List<Map<String,Object>> pts){
  int k=pts.size();double sx=0,sy=0,sxx=0,syy=0,sxy=0;
  for(var p:pts){double x=Math.log1p(n(p,"write_seconds")/60.0),y=((Number)p.get("score")).doubleValue();sx+=x;sy+=y;sxx+=x*x;syy+=y*y;sxy+=x*y;}
  double cov=sxy-sx*sy/k,vx=sxx-sx*sx/k,vy=syy-sy*sy/k;
  if(vx<=1e-9||vy<=1e-9)return null;return Math.round(cov/Math.sqrt(vx*vy)*100)/100.0;
 }
}

@RestController @RequestMapping("/api")
class TimeStatsController {
 final TimeStats stats;final Store s;TimeStatsController(TimeStats stats,Store s){this.stats=stats;this.s=s;}
 @GetMapping("/courses/{cid}/time-stats") public Object stats(@PathVariable long cid,@RequestParam(defaultValue="0") long assignmentId,Principal p){return stats.stats(cid,s.uid(p.getName()),assignmentId);}
}
