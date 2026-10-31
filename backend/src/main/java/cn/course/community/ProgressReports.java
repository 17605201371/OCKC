package cn.course.community;
import java.util.*;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import static cn.course.community.ApiError.fail;

@Service
public class ProgressReports {
 static final int ENTRY_LIMIT=2000,VERSION_LIMIT=200,FEEDBACK_LIMIT=500,ACTION_LIMIT=1500;
 final Store s;final ProgressWorkbench progress;final CommunityService domain;final RubricService rubrics;
 ProgressReports(Store s,ProgressWorkbench progress,CommunityService domain,RubricService rubrics){this.s=s;this.progress=progress;this.domain=domain;this.rubrics=rubrics;}
 static final String SCOPE_NOTE="统计按当前作业、作者、关键词筛选；概览不随状态筛选变化，导出行遵循状态筛选且包含全部匹配页。每篇作品取最近已评分提交等权平均，不是所有版本平均，也不是课程总成绩或个人排名。";
 static final String REPORT_NOTE="只包含真实提交与当前生效的正式评价，不包含正文草稿、教师私有评分草稿或未提交输入。未评分不记零分；回应、采纳、忽略均不自动表示问题解决。报告是生成时快照，后续复评或反馈处理不会自动更新已下载文件；不得仅用修订次数或分数变化推断学习成效。";
 // CSV quoting alone does not prevent spreadsheet formula execution. Neutralize every untrusted cell.
 static String csvCell(Object value){String text=value==null?"":String.valueOf(value);text=text.replace("\r\n","\n").replace('\r','\n').replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"," ");String leading=text.replaceFirst("^[\\s\\p{Z}\\uFEFF]*","");if(text.startsWith("\t")||text.startsWith("\n")||leading.matches("(?s)^[=+@-].*"))text="'"+text;return "\""+text.replace("\"","\"\"")+"\"";}
 static void line(StringBuilder out,Object... values){for(int i=0;i<values.length;i++){if(i>0)out.append(',');out.append(csvCell(values[i]));}out.append("\r\n");}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public byte[] csv(long cid,long uid,String query,long aid,long author,String state,String sort){
  var scope=progress.scope(cid,uid,query,aid,author,state,sort);int total=progress.count(scope);if(total>ENTRY_LIMIT)throw fail(422,"匹配作品超过 2000 篇，请缩小作业、作者或关键词范围后导出；不会静默截断");
  var summary=progress.summary(scope);var course=s.one("SELECT id,title FROM course WHERE id=?",cid);
  var args=new ArrayList<Object>(scope.args());args.add(ENTRY_LIMIT+1);var rows=s.db.queryForList("SELECT * FROM ("+scope.base()+") scoped WHERE "+scope.condition()+" ORDER BY "+scope.order()+" LIMIT ?",args.toArray());if(rows.size()!=total)throw fail(409,"报告范围已变化，请重新生成，不导出不完整数据");
  var out=new StringBuilder("\uFEFF");line(out,"报告类型",progress.staff(scope.role())?"课程教学进展":"个人学习轨迹");line(out,"课程",course.get("title"));line(out,"生成时间 UTC",Store.now());line(out,"使用说明",SCOPE_NOTE);line(out,"隐私提示","文件含个人学习记录，仅用于获授权的课程教学；下载后请安全保存，系统不会自动发布或分享。");
  line(out,"关键词",query);line(out,"作业筛选 ID（0=全部）",aid);line(out,"作者筛选 ID（0=当前身份允许范围）",author);line(out,"状态筛选",state);line(out,"排序",sort);line(out,"导出匹配作品",total);line(out,"概览范围作品",summary.get("entry_count"));line(out,"概览范围累计提交",summary.get("submissions"));line(out,"概览已评分作品",summary.get("graded_entries"));line(out,"概览最近质量均分（空白=无正式评分）",summary.get("latest_average"));line(out,"概览未解决反馈",summary.get("open_feedback"));line(out,"未评分说明","无正式评价的分数保持空白，不记作零分。最近已评分版本可能早于最新提交。");line(out);
  line(out,"作品ID","作者ID","作者显示名称","作业ID","作业名称","作品标题","草稿修订号（非提交次数）","累计提交版本","未评版本","最新提交版本","最近已评分提交版本","该提交当前评价修订号","该提交量规版本","最近质量分（空白=未评分）","科学性","准确性","完整性","逻辑性","反馈总数","未解决反馈","已解决反馈","已通过历史证据","作业绑定量规","最近编辑 UTC","做题用时（秒）","批改用时（秒，仅教师导出）");
  boolean staffExport=progress.staff(scope.role());
  for(var e:rows){Object raw=e.get("latest_dimensions_json");JsonNode dims=raw==null?null:domain.decode(raw);line(out,e.get("id"),e.get("author_id"),e.get("author_name"),e.get("assignment_id"),e.get("assignment_title"),e.get("title"),e.get("revision"),e.get("submission_count"),e.get("pending_count"),e.get("latest_submitted_version"),e.get("latest_graded_version"),e.get("latest_evaluation_revision"),e.get("latest_graded_rubric"),e.get("latest_score"),dimension(dims,"S"),dimension(dims,"A"),dimension(dims,"C"),dimension(dims,"L"),e.get("feedback_total"),e.get("feedback_open"),((Number)e.get("feedback_total")).longValue()-((Number)e.get("feedback_open")).longValue(),e.get("evidence_passed"),e.get("assignment_rubric"),e.get("updated_at"),e.get("write_seconds"),staffExport?e.get("grade_seconds"):null);}
  return out.toString().getBytes(StandardCharsets.UTF_8);
 }
 Object dimension(JsonNode dimensions,String key){if(dimensions==null||!dimensions.hasNonNull(key))return null;return dimensions.get(key).asText();}
 void bound(int count,int limit,String what){if(count>limit)throw fail(422,"作品"+what+"超过本版报告上限（"+limit+"），本次不生成或截断报告，请联系课程维护人员处理");}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Map<String,Object> report(long eid,long uid){
  var entry=s.one("SELECT e.id,e.title,e.author_id,e.revision,e.updated_at,a.id AS assignment_id,a.title AS assignment_title,a.course_id,a.rubric_version AS assignment_rubric,u.display_name AS author_name,c.title AS course_title FROM entry e JOIN assignment a ON a.id=e.assignment_id JOIN app_user u ON u.id=e.author_id JOIN course c ON c.id=a.course_id WHERE e.id=?",eid);
  String role=s.role(s.id(entry,"course_id"),uid);if(!progress.staff(role)&&s.id(entry,"author_id")!=uid)throw fail(403,"只能导出本人的作品报告");
  int versionCount=s.db.queryForObject("SELECT COUNT(*) FROM submission WHERE entry_id=?",Integer.class,eid),feedbackCount=s.db.queryForObject("SELECT COUNT(*) FROM feedback f JOIN submission v ON v.id=f.submission_id WHERE v.entry_id=?",Integer.class,eid),actionCount=s.db.queryForObject("SELECT COUNT(*) FROM feedback_action z JOIN feedback f ON f.id=z.feedback_id JOIN submission v ON v.id=f.submission_id WHERE v.entry_id=?",Integer.class,eid);
  bound(versionCount,VERSION_LIMIT,"提交版本数");bound(feedbackCount,FEEDBACK_LIMIT,"反馈条数");bound(actionCount,ACTION_LIMIT,"反馈处理记录数");
  var versions=s.db.queryForList("SELECT v.id,v.version_no,v.title,v.created_at,v.rubric_version,v.rubric_snapshot_json,g.q_final,g.revision_no AS evaluation_revision,g.dimensions_json,g.summary AS evaluation_summary,g.gate_reason,g.created_at AS evaluated_at,u.display_name AS teacher_name FROM submission v LEFT JOIN evaluation g ON g.submission_id=v.id AND g.active=TRUE LEFT JOIN app_user u ON u.id=g.teacher_id WHERE v.entry_id=? ORDER BY v.version_no DESC",eid);
  Map<String,Object> latest=null;int pending=0;var bySid=new HashMap<Long,Map<String,Object>>();
  for(var v:versions){if(v.get("q_final")==null)pending++;else if(latest==null)latest=v;Object dims=v.remove("dimensions_json");v.put("dimensions",dims==null?Map.of():domain.decode(dims));try{var packet=rubrics.forSubmission(v);v.put("rubric_title",packet.path("title").asText());v.put("rubric_provenance",v.get("rubric_snapshot_json")==null?"BUILTIN_LEGACY":"SUBMISSION_SNAPSHOT");}catch(ApiError.Failure e){if(e.status!=422)throw e;v.put("rubric_title",null);v.put("rubric_provenance","UNAVAILABLE");}v.remove("rubric_snapshot_json");v.put("evidence_counts",new LinkedHashMap<String,Object>(Map.of("PENDING",0,"DISPUTED",0,"PASS",0,"FAIL",0)));bySid.put(s.id(v,"id"),v);}
  var states=s.db.queryForList("SELECT x.submission_id,x.state,COUNT(*) AS n FROM evidence x JOIN submission v ON v.id=x.submission_id WHERE v.entry_id=? GROUP BY x.submission_id,x.state",eid);for(var row:states)((Map<String,Object>)bySid.get(s.id(row,"submission_id")).get("evidence_counts")).put((String)row.get("state"),row.get("n"));
  var feedback=s.db.queryForList("SELECT f.id,f.submission_id,f.paragraph_id,f.original_text,f.indicator_code,f.body,f.resolved,f.created_at,v.version_no,u.display_name AS teacher_name FROM feedback f JOIN submission v ON v.id=f.submission_id JOIN app_user u ON u.id=f.teacher_id WHERE v.entry_id=? ORDER BY f.id",eid);
  var byFeedback=new HashMap<Long,Map<String,Object>>();long open=0;for(var f:feedback){f.put("actions",new ArrayList<>());byFeedback.put(s.id(f,"id"),f);if(!Boolean.TRUE.equals(f.get("resolved")))open++;}
  var actions=s.db.queryForList("SELECT z.id,z.feedback_id,z.user_id,z.action_type,z.body,z.created_at,u.display_name AS actor_name FROM feedback_action z JOIN feedback f ON f.id=z.feedback_id JOIN submission v ON v.id=f.submission_id JOIN app_user u ON u.id=z.user_id WHERE v.entry_id=? ORDER BY z.id",eid);
  for(var a:actions){var f=byFeedback.get(s.id(a,"feedback_id"));((List<Map<String,Object>>)f.get("actions")).add(a);if(s.id(a,"user_id")==s.id(entry,"author_id")&&Set.of("REPLY","ADOPT","IGNORE").contains(a.get("action_type")))f.put("author_action",a.get("action_type"));}
  var summary=new LinkedHashMap<String,Object>();summary.put("submissions",versionCount);summary.put("pending_versions",pending);summary.put("latest_submitted_version",versions.isEmpty()?null:versions.getFirst().get("version_no"));summary.put("latest_graded_version",latest==null?null:latest.get("version_no"));summary.put("latest_evaluation_revision",latest==null?null:latest.get("evaluation_revision"));summary.put("latest_score",latest==null?null:latest.get("q_final"));summary.put("open_feedback",open);summary.put("feedback_total",feedbackCount);summary.put("resolved_feedback",feedbackCount-open);
  return Map.of("schemaVersion",1,"generatedAt",Store.now(),"kind","ENTRY_PROGRESS_REPORT","entry",entry,"summary",summary,"versions",versions,"feedback",feedback,"notes",List.of(REPORT_NOTE,"草稿标题/修订号仅作为当前作品索引；版本行中的标题才是该提交的冻结标题。显示名称是生成报告时的当前名称。","量规标记 SUBMISSION_SNAPSHOT 表示该提交存有快照；BUILTIN_LEGACY 仅有已知历史基础规则，不追溯新增锚点；UNAVAILABLE 表示当时标准无法完整核对，不用当前标准代替。","文件包含个人作品和教师反馈，请按课程授权使用并安全保存；系统不会自动发布、发送或分享。"));
 }
}
