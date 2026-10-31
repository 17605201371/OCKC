package cn.course.community;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import static cn.course.community.ApiError.fail;
@Service
public class RubricService {
 final Store s;final ObjectMapper json;RubricService(Store s,ObjectMapper json){this.s=s;this.json=json;}
 public record Criterion(@NotBlank String code,@NotBlank @Size(max=1000) String description,@NotBlank @Size(max=1000) String excellent,@NotBlank @Size(max=1000) String good,@NotBlank @Size(max=1000) String pass,@NotBlank @Size(max=1000) String improve){}
 // 维度是可配置对象：代码固定为 S/A/C/L，名称、说明与权重由教师决定。校验放在 validate() 里给出中文提示。
 public record Dimension(String code,String name,String description,Integer weight){}
 public record Edit(@NotBlank @Size(max=120) String title,@NotBlank @Size(max=2000) String instructions,List<@Valid Dimension> dimensions,@NotNull @Size(min=11,max=11) List<@Valid Criterion> criteria,@NotNull @Min(0) Integer revision){}
 public record Create(@Min(0) Long sourceId){}
 public record Enable(@NotNull @Min(0) Integer revision,@NotNull @Min(0) Integer catalogRevision,@NotBlank @Size(max=1000) String reason){}
 public record Default(@NotNull @Min(0) Long rubricId,@NotNull @Min(0) Integer catalogRevision,@NotBlank @Size(max=1000) String reason){}
 static final List<String> NAMES=List.of("依据可靠性","方法合理性","证据与结论匹配性","事实准确性","数据准确性","表达规范性","主题覆盖完整性","必要内容要素完整性","结构组织合理性","论证推理连贯性","内容关系一致性");
 static final List<String> DIM_CODES=List.of("S","A","C","L");
 static final List<String> DIM_NAMES=List.of("科学性","准确性","完整性","逻辑性");
 static final List<String> DIM_DESCS=List.of("论断有依据，方法与结论相称。","事实、数据与表述准确，来源可核验。","关键环节交代完整，必要要素不缺。","结构清晰，论证与内容关系前后一致。");
 public static List<Map<String,Object>> builtinDimensions(){var out=new ArrayList<Map<String,Object>>();for(int i=0;i<4;i++)out.add(Map.of("code",DIM_CODES.get(i),"name",DIM_NAMES.get(i),"description",DIM_DESCS.get(i),"weight",25));return out;}
 public static Map<String,Object> builtin(){
  var criteria=new ArrayList<Map<String,Object>>();for(int i=0;i<11;i++){String name=NAMES.get(i);criteria.add(Map.of("code",QualityCalculator.CODES.get(i),"name",name,"description","依据作品实际内容判断「"+name+"」，评分应与可核验文本相对应。","excellent","90–100：该指标表现充分、清晰，关键论断有适当支持。","good","75–89：基本达到要求，仍有少量需要完善之处。","pass","60–74：达到基本要求，但存在需要明确修订的问题。","improve","0–59：关键要求尚未达到，需要针对性补充或纠正。"));}
  var m=new LinkedHashMap<String,Object>();m.put("schemaVersion",1);m.put("code","four-dim-v1");m.put("title","四维基础量规");m.put("instructions","四维按权重加权，每维取有效二级指标平均；N/A 不计零分，待核验不发布正式评价。严重缺陷由教师确认后综合分封顶 89。");m.put("algorithm","four-dim-weighted-v1");m.put("dimensions",builtinDimensions());m.put("criteria",criteria);return m;
 }
 String encode(Object o){try{return json.writeValueAsString(o);}catch(Exception e){throw new IllegalStateException(e);}}
 JsonNode decode(Object o){try{return json.readTree((String)o);}catch(Exception e){throw new IllegalStateException(e);}}
 String code(long id){return id==0?"four-dim-v1":"cr-"+id;}
 // 默认量规可能因历史数据被删除而悬空：此时回退内置四维，避免整页 500。
 long defaultId(Map<String,Object> c){if(c.get("default_rubric_id")==null)return 0;long id=s.id(c,"default_rubric_id");var rows=s.db.queryForList("SELECT state FROM course_rubric WHERE id=?",id);return rows.isEmpty()||!"ENABLED".equals(rows.getFirst().get("state"))?0:id;}
 Map<String,Object> course(long cid,boolean lock){return s.one("SELECT id,default_rubric_id,rubric_revision FROM course WHERE id=?"+(lock?" FOR UPDATE":""),cid);}
 Map<String,Object> row(long id){return s.one("SELECT * FROM course_rubric WHERE id=?",id);}
 Map<String,Object> owned(long cid,long id){var r=row(id);if(s.id(r,"course_id")!=cid)throw fail(403,"量规不属于当前课程");return r;}
 Map<String,Object> snapshot(Map<String,Object> r){var m=new LinkedHashMap<String,Object>();m.put("schemaVersion",1);m.put("code",code(s.id(r,"id")));m.put("title",r.get("title"));m.put("instructions",r.get("instructions"));
  // 历史量规没有维度列：按四维等权回退，保证旧版本的评分口径与展示都不变。
  var dims=existingDimensions(r);m.put("dimensions",dims==null?builtinDimensions():dims);m.put("algorithm",dims==null?"four-dim-equal-v1":"four-dim-weighted-v1");m.put("criteria",decode(r.get("criteria_json")));return m;}
 Map<String,Object> full(Map<String,Object> r){var m=snapshot(r);for(String k:List.of("id","version_no","state","revision","created_at","updated_at","enabled_at"))m.put(k,r.get(k));return m;}
 Map<String,Object> builtinFull(){var m=builtin();m.put("id",0);m.put("version_no",0);m.put("state","ENABLED");m.put("revision",0);return m;}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ) public Map<String,Object> list(long cid,long uid){
  String role=s.role(cid,uid);var c=course(cid,false);var rows=s.db.queryForList("SELECT id,version_no,state,revision,title,enabled_at,updated_at FROM course_rubric WHERE course_id=?"+(role.equals("TEACHER")?"":" AND state='ENABLED'")+" ORDER BY version_no DESC",cid);
  var items=new ArrayList<Map<String,Object>>();for(var r:rows){r.put("code",code(s.id(r,"id")));items.add(r);}var base=new LinkedHashMap<String,Object>();base.put("id",0);base.put("code","four-dim-v1");base.put("title","四维基础量规");base.put("state","ENABLED");base.put("version_no",0);items.add(base);
  return Map.of("items",items,"catalogRevision",c.get("rubric_revision"),"defaultId",defaultId(c),"defaultCode",code(defaultId(c)),"role",role);
 }
 public Map<String,Object> get(long cid,long uid,String code){String role=s.role(cid,uid);if(code.equals("four-dim-v1"))return builtinFull();if(!code.matches("cr-[1-9][0-9]{0,18}"))throw fail(400,"量规版本格式不正确");long id;try{id=Long.parseLong(code.substring(3));}catch(NumberFormatException e){throw fail(400,"量规版本格式不正确");}var r=owned(cid,id);if(!role.equals("TEACHER")&&!r.get("state").equals("ENABLED"))throw fail(403,"量规草稿仅任课教师可见");return full(r);}
 void validate(Edit e){
  if(e==null||e.title()==null||e.title().isBlank()||e.title().length()>120||e.instructions()==null||e.instructions().isBlank()||e.instructions().length()>2000||e.criteria()==null||e.criteria().size()!=11||e.revision()==null||e.revision()<0)throw fail(422,"请完整填写量规标题、说明和 11 项指标");
  Set<String> codes=new HashSet<>();for(var i:e.criteria()){if(i==null||!QualityCalculator.CODES.contains(i.code())||!codes.add(i.code()))throw fail(422,"必须包含且仅包含 11 个不重复的固定指标代码");for(String text:Arrays.asList(i.description(),i.excellent(),i.good(),i.pass(),i.improve()))if(text==null||text.isBlank()||text.length()>1000)throw fail(422,"每项指标的说明和四个评分锚点均须填写，最多 1000 字");}
  if(e.dimensions()!=null)dimensions(e.dimensions());
 }
 void dimensions(List<Dimension> dims){
  if(dims.size()!=4)throw fail(422,"必须包含且仅包含四个维度（科学性、准确性、完整性、逻辑性）");
  Set<String> codes=new HashSet<>();int sum=0;
  for(var d:dims){
   if(d==null||d.code()==null||!DIM_CODES.contains(d.code())||!codes.add(d.code()))throw fail(422,"四个维度的代码必须为 S、A、C、L 且不重复");
   if(d.name()==null||d.name().isBlank()||d.name().trim().length()>40)throw fail(422,"每个维度需填写 1–40 字的名称");
   if(d.description()==null||d.description().isBlank()||d.description().trim().length()>500)throw fail(422,"每个维度需填写 1–500 字的说明");
   if(d.weight()==null||d.weight()<0||d.weight()>100)throw fail(422,"维度权重需为 0–100 的整数");
   sum+=d.weight();
  }
  if(sum!=100)throw fail(422,"四个维度的权重合计必须为 100%，当前为 "+sum+"%");
 }
 List<Map<String,Object>> canonical(Edit e){var out=new ArrayList<Map<String,Object>>();for(int j=0;j<11;j++){String code=QualityCalculator.CODES.get(j);var i=e.criteria().stream().filter(c->c.code().equals(code)).findFirst().orElseThrow();out.add(Map.of("code",code,"name",NAMES.get(j),"description",i.description().trim(),"excellent",i.excellent().trim(),"good",i.good().trim(),"pass",i.pass().trim(),"improve",i.improve().trim()));}return out;}
 // 请求未带维度时沿用该版本已保存的配置，再退回内置四维等权，避免旧客户端把权重清零。
 List<Map<String,Object>> canonicalDimensions(Edit e,List<Map<String,Object>> fallback){
  if(e.dimensions()==null)return fallback==null?builtinDimensions():fallback;
  var out=new ArrayList<Map<String,Object>>();
  for(String c:DIM_CODES){var d=e.dimensions().stream().filter(x->x!=null&&c.equals(x.code())).findFirst().orElseThrow(()->fail(422,"缺少维度 "+c));out.add(Map.of("code",c,"name",d.name().trim(),"description",d.description().trim(),"weight",d.weight()));}
  return out;
 }
 List<Map<String,Object>> existingDimensions(Map<String,Object> r){
  if(r==null||r.get("dimensions_json")==null)return null;
  try{var node=decode(r.get("dimensions_json"));if(!node.isArray()||node.size()!=4)return null;var out=new ArrayList<Map<String,Object>>();for(var n:node){var m=new LinkedHashMap<String,Object>();m.put("code",n.path("code").asText());m.put("name",n.path("name").asText());m.put("description",n.path("description").asText());m.put("weight",n.path("weight").asInt(25));out.add(m);}return out;}catch(Exception e){return null;}
 }
 @Transactional public Map<String,Object> create(long cid,long uid,Long sourceId){s.teacher(cid,uid);var c=course(cid,true);long source=sourceId==null?defaultId(c):sourceId;if(source<0)throw fail(400,"量规来源不正确");Map<String,Object> original;if(source==0)original=builtin();else{var r=owned(cid,source);if(!r.get("state").equals("ENABLED"))throw fail(422,"只能从已启用量规创建新的修订草稿");original=snapshot(r);}
  int count=s.db.queryForObject("SELECT COUNT(*) FROM course_rubric WHERE course_id=?",Integer.class,cid);if(count>=100)throw fail(422,"本课程量规版本已达 100 个，请联系维护人员整理，不会删除历史版本");String time=Store.now();long id=s.insert("INSERT INTO course_rubric(course_id,version_no,state,revision,title,instructions,dimensions_json,criteria_json,created_by,created_at,updated_at) VALUES(?,?,'DRAFT',0,?,?,?,?,?,?,?)",cid,count+1,original.get("title"),original.get("instructions"),encode(original.get("dimensions")),encode(original.get("criteria")),uid,time,time);s.audit(uid,"RUBRIC_DRAFT_CREATE","rubric",id);return full(row(id));
 }
 @Transactional public Map<String,Object> edit(long cid,long id,long uid,Edit e){s.teacher(cid,uid);validate(e);var r=owned(cid,id);var dims=canonicalDimensions(e,existingDimensions(r));int changed=s.db.update("UPDATE course_rubric SET title=?,instructions=?,dimensions_json=?,criteria_json=?,revision=revision+1,updated_at=? WHERE id=? AND course_id=? AND state='DRAFT' AND revision=?",e.title().trim(),e.instructions().trim(),encode(dims),encode(canonical(e)),Store.now(),id,cid,e.revision());if(changed!=1)throw fail(409,"量规已在其他窗口保存或启用；当前输入仍保留，请刷新后核对");s.audit(uid,"RUBRIC_DRAFT_SAVE","rubric",id);return full(row(id));}
 void expected(Map<String,Object> c,Integer revision){if(revision==null||((Number)c.get("rubric_revision")).intValue()!=revision)throw fail(409,"课程默认量规已变化，请刷新版本列表后核对");}
 void reason(String value){if(value==null||value.isBlank()||value.length()>1000)throw fail(422,"请填写 1–1000 字的启用或切换理由");}
 void event(long cid,long uid,String action,long before,long after,String reason){s.db.update("INSERT INTO rubric_event(course_id,actor_id,action_type,before_code,after_code,reason,created_at) VALUES(?,?,?,?,?,?,?)",cid,uid,action,code(before),code(after),reason.trim(),Store.now());s.audit(uid,"RUBRIC_"+action,"course",cid);}
 @Transactional public Map<String,Object> enable(long cid,long id,long uid,Enable e){s.teacher(cid,uid);reason(e.reason());var c=course(cid,true);expected(c,e.catalogRevision());var r=s.one("SELECT * FROM course_rubric WHERE id=? FOR UPDATE",id);if(s.id(r,"course_id")!=cid)throw fail(403,"量规不属于当前课程");if(!r.get("state").equals("DRAFT")||e.revision()==null||((Number)r.get("revision")).intValue()!=e.revision())throw fail(409,"量规草稿已保存或启用，请刷新后核对");
  s.db.update("UPDATE course_rubric SET state='ENABLED',enabled_at=?,updated_at=? WHERE id=?",Store.now(),Store.now(),id);s.db.update("UPDATE course SET default_rubric_id=?,rubric_revision=rubric_revision+1 WHERE id=?",id,cid);event(cid,uid,"ENABLE",defaultId(c),id,e.reason());return full(row(id));
 }
 @Transactional public Map<String,Object> setDefault(long cid,long uid,Default e){s.teacher(cid,uid);reason(e.reason());var c=course(cid,true);expected(c,e.catalogRevision());long id=e.rubricId();if(id<0)throw fail(400,"量规版本不正确");if(id!=0&&!owned(cid,id).get("state").equals("ENABLED"))throw fail(422,"草稿不能用作默认量规");if(defaultId(c)==id)throw fail(422,"默认量规未变化");s.db.update("UPDATE course SET default_rubric_id=?,rubric_revision=rubric_revision+1 WHERE id=?",id==0?null:id,cid);event(cid,uid,"DEFAULT",defaultId(c),id,e.reason());return list(cid,uid);}
 public List<Map<String,Object>> history(long cid,long uid){s.teacher(cid,uid);return s.db.queryForList("SELECT e.*,u.display_name AS actor_name FROM rubric_event e JOIN app_user u ON u.id=e.actor_id WHERE e.course_id=? ORDER BY e.id DESC LIMIT 100",cid);}
 // Called while assignment creation holds the course row lock. Existing assignments never follow later defaults.
 Map<String,Object> selection(long cid,Long requested){var c=course(cid,true);long id=requested==null?defaultId(c):requested;if(id<0)throw fail(400,"量规版本不正确");if(id==0){var m=builtin();m.put("id",0L);return m;}var r=owned(cid,id);if(!r.get("state").equals("ENABLED"))throw fail(422,"作业只能绑定已启用的量规版本");var m=snapshot(r);m.put("id",id);return m;}
 Map<String,Object> forAssignment(long aid){var a=s.one("SELECT course_id,rubric_id,rubric_version FROM assignment WHERE id=?",aid);if(a.get("rubric_id")==null){if(!a.get("rubric_version").equals("four-dim-v1"))throw fail(422,"历史量规版本不可用，不使用当前默认量规冒充");return builtin();}var r=owned(s.id(a,"course_id"),s.id(a,"rubric_id"));if(!r.get("state").equals("ENABLED")||!code(s.id(r,"id")).equals(a.get("rubric_version")))throw fail(422,"作业量规版本不一致，请联系维护人员");return snapshot(r);}
 public static Map<String,Object> legacyBuiltin(){var m=builtin();var criteria=new ArrayList<Map<String,Object>>();for(int i=0;i<11;i++)criteria.add(Map.of("code",QualityCalculator.CODES.get(i),"name",NAMES.get(i)));m.put("criteria",criteria);m.put("legacy",true);m.put("algorithm","four-dim-equal-v1");m.put("instructions","四维等权，每维取有效二级指标平均；N/A 不计零分，待核验不发布正式评价。严重缺陷由教师确认后综合分封顶 89。此历史提交未记录单项说明和锚点，不追溯新增内容。");return m;}
 JsonNode forSubmission(Map<String,Object> v){
  if(v.get("rubric_snapshot_json")!=null){try{var packet=decode(v.get("rubric_snapshot_json"));String algorithm=packet.path("algorithm").asText();if(!packet.path("code").asText().equals(v.get("rubric_version"))||!(algorithm.equals("four-dim-equal-v1")||algorithm.equals("four-dim-weighted-v1"))||packet.path("schemaVersion").asInt()!=1||!packet.path("criteria").isArray()||packet.path("criteria").size()!=11)throw new IllegalArgumentException();var seen=new HashSet<String>();for(var i:packet.path("criteria"))if(!QualityCalculator.CODES.contains(i.path("code").asText())||!seen.add(i.path("code").asText()))throw new IllegalArgumentException();return packet;}catch(Exception ex){throw fail(422,"提交的量规快照损坏或版本不一致，请联系维护人员，不使用当前量规代替");}}
  if("four-dim-v1".equals(v.get("rubric_version")))return json.valueToTree(legacyBuiltin());throw fail(422,"此历史提交未保存可识别的量规，不使用当前版本冒充");
 }
}
