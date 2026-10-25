package cn.course.community;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static cn.course.community.ApiError.fail;
@Service
public class ReviewDraftService {
 final Store s;final ObjectMapper json;ReviewDraftService(Store s,ObjectMapper json){this.s=s;this.json=json;}
 void access(long sid,long uid){var v=s.one("SELECT a.course_id FROM submission v JOIN entry e ON e.id=v.entry_id JOIN assignment a ON a.id=e.assignment_id WHERE v.id=?",sid);s.teacher(s.id(v,"course_id"),uid);}
 int evaluationRevision(long sid){return s.db.queryForObject("SELECT COUNT(*) FROM evaluation WHERE submission_id=?",Integer.class,sid);}
 public Map<String,Object> get(long sid,long uid){access(sid,uid);int base=evaluationRevision(sid);var rows=s.db.queryForList("SELECT * FROM review_draft WHERE submission_id=? AND teacher_id=?",sid,uid);var out=new LinkedHashMap<String,Object>();out.put("evaluationRevision",base);out.put("draft",null);if(!rows.isEmpty()){var d=rows.getFirst();try{d.put("items",json.readTree((String)d.remove("details_json")));Object steps=d.remove("step_scores_json");d.put("stepScores",steps==null?List.of():json.readTree(String.valueOf(steps)));}catch(Exception e){throw new IllegalStateException(e);}d.put("stale",((Number)d.get("base_evaluation_revision")).intValue()!=base);out.put("draft",d);}return out;}
 @Transactional public Map<String,Object> save(long sid,long uid,WorkflowController.ReviewDraftRequest r){
  access(sid,uid);s.one("SELECT id FROM submission WHERE id=? FOR UPDATE",sid);
  if(r.baseEvaluationRevision()!=evaluationRevision(sid))throw fail(409,"正式评价已更新，请重新加载后复核评分草稿");
  if(r.items().size()!=11||!new HashSet<>(r.items().stream().map(QualityCalculator.Item::code).toList()).equals(new HashSet<>(QualityCalculator.CODES)))throw fail(422,"评分草稿须包含 11 个不重复的二级指标");
  for(var i:r.items())if(!Set.of("SCORED","NA","PENDING").contains(String.valueOf(i.status()))||i.reason()==null||i.reason().length()>500||(i.score()!=null&&(i.score().signum()<0||i.score().compareTo(java.math.BigDecimal.valueOf(100))>0||i.score().scale()>2)))throw fail(422,"评分草稿的分数、状态或理由不正确");
  // 步骤分同样进草稿：教师填了几步就先存几步，不要求一次填完。
  List<ApiController.StepScore> steps=r.stepScores()==null?List.of():r.stepScores();
  if(!steps.isEmpty())CommunityService.validateStepScores(steps);
  var rows=s.db.queryForList("SELECT revision FROM review_draft WHERE submission_id=? AND teacher_id=?",sid,uid);int revision=rows.isEmpty()?0:((Number)rows.getFirst().get("revision")).intValue();if(revision!=r.revision())throw fail(409,"评分草稿已被另一窗口修改，当前输入已保留，请重新加载后合并");
  String details;try{details=json.writeValueAsString(r.items());}catch(Exception e){throw fail(400,"评分草稿格式不正确");}
  String stepJson=steps.isEmpty()?null:detailsFor(steps);
  if(rows.isEmpty())s.db.update("INSERT INTO review_draft(submission_id,teacher_id,details_json,summary,gate_reason,base_evaluation_revision,revision,updated_at,step_scores_json) VALUES(?,?,?,?,?,?,1,?,?)",sid,uid,details,r.summary(),r.gateReason(),r.baseEvaluationRevision(),Store.now(),stepJson);
  else s.db.update("UPDATE review_draft SET details_json=?,summary=?,gate_reason=?,base_evaluation_revision=?,revision=revision+1,updated_at=?,step_scores_json=? WHERE submission_id=? AND teacher_id=?",details,r.summary(),r.gateReason(),r.baseEvaluationRevision(),Store.now(),stepJson,sid,uid);
  return get(sid,uid);
 }
 String detailsFor(List<ApiController.StepScore> steps){try{return json.writeValueAsString(steps);}catch(Exception e){throw fail(400,"步骤分无法序列化");}}
 @Transactional public void discard(long sid,long uid,int revision){access(sid,uid);s.one("SELECT id FROM submission WHERE id=? FOR UPDATE",sid);var rows=s.db.queryForList("SELECT revision FROM review_draft WHERE submission_id=? AND teacher_id=?",sid,uid);if(!rows.isEmpty()&&((Number)rows.getFirst().get("revision")).intValue()!=revision)throw fail(409,"评分草稿已更新，请重新加载后操作");s.db.update("DELETE FROM review_draft WHERE submission_id=? AND teacher_id=?",sid,uid);}
}
