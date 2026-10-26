package cn.course.community;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static cn.course.community.ApiError.fail;
import static cn.course.community.Store.now;
@Service
public class CommunityService {
 final Store s;final ObjectMapper json;final NotificationService notices;final MemberManagement members;final RubricService rubrics;
 CommunityService(Store s,ObjectMapper json,NotificationService notices,MemberManagement members,RubricService rubrics){this.s=s;this.json=json;this.notices=notices;this.members=members;this.rubrics=rubrics;}
 String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw fail(400,"内容无法序列化");}}
 // 步骤型作业的问题类型标签，前后端共用同一份枚举；新增标签要同时改前端 STEP_TAGS。
 static final Set<String> STEP_TAGS=Set.of("忽略题设条件","计算或单位错误","概念混淆","论证跳跃","表述不清");
 static void validateStepScores(List<ApiController.StepScore> steps){
  Set<Integer> seen=new HashSet<>();
  for(var x:steps){
   if(x.index()<0||x.index()>39||!seen.add(x.index()))throw fail(422,"步骤序号重复或超出范围");
   if(x.score()==null||x.max()==null)throw fail(422,"每个步骤都要填写得分与满分");
   if(x.max().signum()<=0||x.score().signum()<0||x.score().compareTo(x.max())>0)throw fail(422,"步骤得分须在 0 到该步满分之间");
   if(x.tags()!=null)for(String t:x.tags())if(!STEP_TAGS.contains(t))throw fail(422,"问题类型标签不在允许范围内："+t);
  }
 }
 JsonNode decode(Object value){try{return json.readTree((String)value);}catch(Exception e){throw new IllegalStateException(e);}}
 public Map<String,Object> assignment(long id){return s.one("SELECT * FROM assignment WHERE id=?",id);}
 public Map<String,Object> entry(long id){return s.one("SELECT e.*,a.course_id,a.due_at,a.rubric_version FROM entry e JOIN assignment a ON a.id=e.assignment_id WHERE e.id=?",id);}
 public Map<String,Object> submission(long id){return s.one("SELECT v.*,e.author_id,e.assignment_id,e.state AS entry_state,e.state_reason AS entry_state_reason,e.state_at AS entry_state_at,a.course_id,u.display_name AS author_name,a.title AS assignment_title FROM submission v JOIN entry e ON e.id=v.entry_id JOIN assignment a ON a.id=e.assignment_id JOIN app_user u ON u.id=e.author_id WHERE v.id=?",id);}
 void own(Map<String,Object> e,long uid){s.role(s.id(e,"course_id"),uid);if(s.id(e,"author_id")!=uid)throw fail(403,"不能修改他人的作品");}
 void visible(Map<String,Object> v,long uid){String r=s.role(s.id(v,"course_id"),uid);if(s.id(v,"author_id")==uid||r.equals("TEACHER")||r.equals("ASSISTANT"))return;
  if(s.db.queryForObject("SELECT COUNT(*) FROM publication WHERE submission_id=? AND state='PUBLISHED'",Integer.class,v.get("id"))==0)throw fail(403,"该提交尚未获准在课程社区展示");}
 public List<Map<String,Object>> courses(long uid){return s.db.queryForList("SELECT c.*,m.role FROM course c JOIN course_member m ON m.course_id=c.id WHERE m.user_id=? ORDER BY c.id",uid);}
 public List<Map<String,Object>> assignments(long cid,long uid){s.role(cid,uid);var rows=s.db.queryForList("SELECT a.*,e.id AS entry_id,e.revision AS draft_revision,e.state AS entry_state,(SELECT MAX(v.version_no) FROM submission v WHERE v.entry_id=e.id) AS version_no FROM assignment a LEFT JOIN entry e ON e.assignment_id=a.id AND e.author_id=? WHERE a.course_id=? AND (a.free_form=FALSE OR e.id IS NOT NULL) ORDER BY a.id DESC",uid,cid);Instant clock=Instant.now();for(var a:rows){Instant due=Instant.parse((String)a.get("due_at"));a.put("server_now",clock.toString());a.put("due_status",Boolean.TRUE.equals(a.get("manually_closed"))||!due.isAfter(clock)?"CLOSED":!due.isAfter(clock.plusSeconds(86400))?"DUE_SOON":"OPEN");}return rows;}
 public Map<String,Object> progress(long cid,long uid){
  String role=s.role(cid,uid);boolean staff=Set.of("TEACHER","ASSISTANT").contains(role);
  String filter=staff?"":" AND e.author_id="+uid;
  // 统计口径用相关子查询一次算完，避免每个作品再发三条 COUNT。
  var entries=s.db.queryForList("SELECT e.id,e.title,e.author_id,e.revision,e.updated_at,a.title AS assignment_title,a.due_at,u.display_name AS author_name,"
   +"(SELECT COUNT(*) FROM feedback f JOIN submission sv ON sv.id=f.submission_id WHERE sv.entry_id=e.id) AS feedback_total,"
   +"(SELECT COUNT(*) FROM feedback f JOIN submission sv ON sv.id=f.submission_id WHERE sv.entry_id=e.id AND f.resolved=TRUE) AS feedback_resolved,"
   +"(SELECT COUNT(*) FROM evidence x JOIN submission sv ON sv.id=x.submission_id WHERE sv.entry_id=e.id AND x.state='PASS') AS evidence_passed "
   +"FROM entry e JOIN assignment a ON a.id=e.assignment_id JOIN app_user u ON u.id=e.author_id WHERE a.course_id=?"+filter+" ORDER BY e.updated_at DESC",cid);
  int assignmentCount=s.db.queryForObject("SELECT COUNT(*) FROM assignment WHERE course_id=?",Integer.class,cid);
  if(entries.isEmpty())return Map.of("entries",entries,"staff",staff,"assignmentCount",assignmentCount);
  // 所有版本一次取回后按作品分组，查询数与作品数量无关。
  String ids=entries.stream().map(e->String.valueOf(s.id(e,"id"))).collect(java.util.stream.Collectors.joining(","));
  var versions=s.db.queryForList("SELECT v.entry_id,v.id,v.version_no,v.created_at,g.q_final,g.dimensions_json,g.summary FROM submission v LEFT JOIN evaluation g ON g.submission_id=v.id AND g.active=TRUE WHERE v.entry_id IN ("+ids+") ORDER BY v.entry_id,v.version_no");
  Map<Long,List<Map<String,Object>>> grouped=new HashMap<>();
  for(var v:versions){long eid=((Number)v.remove("entry_id")).longValue();Object dims=v.remove("dimensions_json");v.put("dimensions",dims==null?Map.of():decode(dims));grouped.computeIfAbsent(eid,k->new ArrayList<>()).add(v);}
  for(var e:entries)e.put("versions",grouped.getOrDefault(s.id(e,"id"),List.of()));
  return Map.of("entries",entries,"staff",staff,"assignmentCount",assignmentCount);
 }
 @Transactional public Map<String,Object> createCourse(String title,String description,long uid){long id=s.insert("INSERT INTO course(title,description) VALUES(?,?)",title,description);s.db.update("INSERT INTO course_member(course_id,user_id,role) VALUES(?,?,'TEACHER')",id,uid);s.audit(uid,"COURSE_CREATE","course",id);return s.one("SELECT * FROM course WHERE id=?",id);}
 @Transactional public void addMember(long cid,String email,String role,long uid){members.add(cid,email,role,uid,null,"演示初始化或教师添加已有账号");}
 @Transactional public void addMember(long cid,String email,String role,long uid,Integer revision,String reason){members.add(cid,email,role,uid,revision,reason);}
 @Transactional public Map<String,Object> createAssignment(long cid,String title,String requirements,String due,boolean aiEnabled,long uid){return createAssignment(cid,title,requirements,due,aiEnabled,null,null,null,uid);}
 @Transactional public Map<String,Object> createAssignment(long cid,String title,String requirements,String due,boolean aiEnabled,Long rubricId,long uid){return createAssignment(cid,title,requirements,due,aiEnabled,rubricId,null,null,uid);}
 @Transactional public Map<String,Object> createAssignment(long cid,String title,String requirements,String due,boolean aiEnabled,Long rubricId,String kind,String evidencePolicy,long uid){s.teacher(cid,uid);Instant deadline;try{deadline=Instant.parse(due);}catch(Exception e){throw fail(422,"截止时间格式错误");}if(deadline.isBefore(Instant.now()))throw fail(422,"截止时间须晚于当前时间");var rubric=rubrics.selection(cid,rubricId);long rid=((Number)rubric.get("id")).longValue();
  // 未传类型/核验策略时给默认值，保持既有客户端可用：知识帖 + 仅争议阻断发分。
  String k=kind==null||kind.isBlank()?"POST":kind.trim().toUpperCase();
  if(!Set.of("POST","STEP").contains(k))throw fail(422,"作业类型只能是知识帖或步骤");
  String policy=evidencePolicy==null||evidencePolicy.isBlank()?"DISPUTED":evidencePolicy.trim().toUpperCase();
  if(!Set.of("REQUIRED","DISPUTED","OFF").contains(policy))throw fail(422,"证据核验策略不正确");
  long id=s.insert("INSERT INTO assignment(course_id,title,requirements,due_at,rubric_version,rubric_id,created_at,ai_feedback_enabled,kind,evidence_policy) VALUES(?,?,?,?,?,?,?,?,?,?)",cid,title,requirements,deadline.toString(),rubric.get("code"),rid==0?null:rid,now(),aiEnabled,k,policy);s.audit(uid,"ASSIGNMENT_CREATE","assignment",id);return assignment(id);}
 @Transactional public Map<String,Object> setAiPolicy(long aid,boolean enabled,long uid){
  var a=assignment(aid);s.teacher(s.id(a,"course_id"),uid);
  s.one("SELECT id FROM assignment WHERE id=? FOR UPDATE",aid);
  s.db.update("UPDATE assignment SET ai_feedback_enabled=? WHERE id=?",enabled,aid);
  s.audit(uid,enabled?"ASSIGNMENT_AI_ENABLE":"ASSIGNMENT_AI_DISABLE","assignment",aid);
  return assignment(aid);
 }
 @org.springframework.beans.factory.annotation.Value("${app.ai.key:}") String aiKey;
 @org.springframework.beans.factory.annotation.Value("${app.ai.base:https://api.stepfun.com/step_plan/v1}") String aiBase;
 @org.springframework.beans.factory.annotation.Value("${app.ai.model:step-3.7-flash}") String aiModel;
 public Map<String,Object> aiFeedback(long eid,long uid){return aiFeedback(eid,uid,false);}
 public Map<String,Object> aiFeedback(long eid,long uid,boolean generate){
  var e=entry(eid);String role=s.role(s.id(e,"course_id"),uid);
  if(s.id(e,"author_id")!=uid&&!"TEACHER".equals(role))throw fail(403,"AI 反馈仅作者与任课教师可见");
  var rule=assignment(s.id(e,"assignment_id"));
  boolean enabled=Boolean.TRUE.equals(rule.get("ai_feedback_enabled"));
  boolean stepMode="STEP".equals(rule.get("kind"));
  if(!enabled)return Map.of("enabled",false,"status","DISABLED","suggestions",List.of(),"formalScore",false);
  if(aiKey==null||aiKey.isBlank())return Map.of("enabled",true,"status","NOT_CONFIGURED","suggestions",List.of(),"formalScore",false);
  int rev=((Number)e.get("revision")).intValue();
  var saved=s.db.queryForList("SELECT id,paragraph_id,quote_text,dimension,title,body,state,step_index FROM ai_suggestion WHERE entry_id=? AND revision=? ORDER BY step_index NULLS FIRST,id",eid,rev);
  if(!saved.isEmpty())return Map.of("enabled",true,"status","READY","suggestions",saved,"formalScore",false,"saved",true,"model",aiModel);
  if(!generate)return Map.of("enabled",true,"status","NOT_GENERATED","suggestions",List.of(),"formalScore",false,"model",aiModel);
  var paragraphs=anchors(decode(e.get("draft_json")));
  if(paragraphs.isEmpty()||text(decode(e.get("draft_json"))).isBlank())return Map.of("enabled",true,"status","EMPTY","suggestions",List.of(),"formalScore",false);
  // 只把段落标识与正文交给模型，图片与证据原文不出网。
  StringBuilder listing=new StringBuilder();
  var sections=stepMode?stepSections(decode(e.get("draft_json"))):List.<Map<String,Object>>of();
  if(stepMode){
   // 步骤作业：按步骤分组送审，模型才知道「这是第几步的哪句话」。
   for(int i=0;i<sections.size();i++){
    var sec=sections.get(i);listing.append("第").append(i+1).append("步 ").append(sec.get("title")).append('\n');
    for(Object line:(List<?>)sec.get("lines"))listing.append("  ").append(line).append('\n');
    if(listing.length()>6000)break;
   }
  }
  for(var en:paragraphs.entrySet()){
   if(stepMode)break;
   String value=en.getValue().trim();
   if(value.isEmpty())continue;
   listing.append('[').append(en.getKey()).append("] ").append(value).append('\n');
   if(listing.length()>6000)break;
  }
  String title=(String)e.get("title");
  try{
   int stepCount=stepMode?sections.size():0;
   String content=requestSuggestions(title,listing.substring(0,Math.min(listing.length(),6000)),stepMode,stepCount);
   JsonNode node;
   try{node=decode(stripFence(content));}
   catch(Exception parseError){
    // 模型偶发把 JSON 截断；同一份输入再要一次通常就完整了。
    content=requestSuggestions(title,listing.substring(0,Math.min(listing.length(),6000)),stepMode,stepCount);
    node=decode(stripFence(content));
   }
   if(!node.path("suggestions").isArray()||node.path("suggestions").isEmpty())throw new IllegalStateException("模型未返回可用建议");
   s.db.update("DELETE FROM ai_suggestion WHERE entry_id=? AND revision=?",eid,rev);
   int count=0;
   for(var item:node.path("suggestions")){
    if(count++>=4)break;
    String pid=item.path("paragraphId").asText("").trim();
    // 段落标识必须是本文真实存在的锚点，否则建议无法定位，只保留文字。
    if(!paragraphs.containsKey(pid))pid="";
    String quote=item.path("quote").asText("").trim();
    if(quote.length()>600||(!pid.isEmpty()&&!paragraphs.get(pid).contains(quote)))quote="";
    Integer stepIndex=null;
    if(stepMode&&stepCount>0){int si=item.path("stepIndex").asInt(-1);if(si>=0&&si<stepCount)stepIndex=si;}
    s.db.update("INSERT INTO ai_suggestion(entry_id,revision,paragraph_id,quote_text,dimension,title,body,state,created_at,step_index) VALUES(?,?,?,?,?,?,?,'OPEN',?,?)",
     eid,rev,pid.isEmpty()?null:pid,quote.isEmpty()?null:quote,
     item.path("dimension").asText("C"),trimTo(stripStepPrefix(item.path("title").asText("")),200),trimTo(item.path("body").asText(""),1000),now(),stepIndex);
   }
   s.audit(uid,"AI_FEEDBACK_GENERATE","entry",eid);
   var list=s.db.queryForList("SELECT id,paragraph_id,quote_text,dimension,title,body,state,step_index FROM ai_suggestion WHERE entry_id=? AND revision=? ORDER BY step_index NULLS FIRST,id",eid,rev);
   return Map.of("enabled",true,"status","READY","suggestions",list,"formalScore",false,"saved",true,"model",aiModel);
  }catch(Exception ex){
   org.slf4j.LoggerFactory.getLogger(CommunityService.class).warn("AI feedback failed for entry {}: {}",eid,ex.toString());
   return Map.of("enabled",true,"status","FAILED","message","模型服务暂时不可用，请稍后重试","suggestions",List.of(),"formalScore",false);
  }
 }
 private String stripStepPrefix(String title){String t=title==null?"":title.trim();return t.replaceFirst("^第[0-9一二三四五六七八九十]+步[\\s:：、.。]*","");}
 private String trimTo(String value,int max){String v=value==null?"":value.trim();return v.length()>max?v.substring(0,max):v;}
 @Transactional public void setAiSuggestionState(long id,String state,long uid){
  if(!Set.of("OPEN","ADOPTED","DISMISSED").contains(state))throw fail(422,"未知的建议状态");
  var rows=s.db.queryForList("SELECT entry_id FROM ai_suggestion WHERE id=?",id);
  if(rows.isEmpty())throw fail(404,"建议不存在");
  var e=entry(s.id(rows.getFirst(),"entry_id"));String role=s.role(s.id(e,"course_id"),uid);
  if(s.id(e,"author_id")!=uid&&!"TEACHER".equals(role))throw fail(403,"无权处理该建议");
  s.db.update("UPDATE ai_suggestion SET state=? WHERE id=?",state,id);
  s.audit(uid,"AI_SUGGESTION_"+state,"ai_suggestion",id);
 }

 // 步骤作业送审前按标题分步：优先二级标题，退化到实际用到的最高一级。
 List<Map<String,Object>> stepSections(JsonNode doc){
  List<JsonNode> nodes=new ArrayList<>();if(doc!=null)doc.path("content").forEach(nodes::add);
  boolean has2=false;int min=99;
  for(var n:nodes){if(!"heading".equals(n.path("type").asText()))continue;int lv=n.path("attrs").path("level").asInt(1);if(lv==2)has2=true;min=Math.min(min,lv);}
  int level=has2?2:(min==99?0:min);
  List<Map<String,Object>> out=new ArrayList<>();Map<String,Object> cur=null;
  for(var n:nodes){
   boolean heading="heading".equals(n.path("type").asText());
   if(heading&&level>0&&n.path("attrs").path("level").asInt(1)==level){
    cur=new LinkedHashMap<>();cur.put("title",n.path("content").isEmpty()||n.path("content").get(0).path("text").asText("").isBlank()?"未命名步骤":n.path("content").get(0).path("text").asText(""));cur.put("lines",new ArrayList<String>());out.add(cur);
   }else if(cur!=null){
    String id=n.path("attrs").path("blockId").asText("");String txt=inlineText(n).trim();
    if(!txt.isEmpty())((List<String>)cur.get("lines")).add((id.isEmpty()?"":"["+id+"] ")+txt);
   }
  }
  return out;
 }
 String inlineText(JsonNode node){StringBuilder b=new StringBuilder();collectText(node,b);return b.toString();}
 void collectText(JsonNode node,StringBuilder b){if(node==null)return;if(node.has("text"))b.append(node.path("text").asText());node.path("content").forEach(c->collectText(c,b));}
 private String stripFence(String raw){
  String t=raw==null?"":raw.trim();
  if(t.startsWith("```")){int nl=t.indexOf('\n');if(nl>0)t=t.substring(nl+1);int end=t.lastIndexOf("```");if(end>=0)t=t.substring(0,end);}
  int start=t.indexOf('{'),end=t.lastIndexOf('}');
  if(start>=0&&end>start)return t.substring(start,end+1);
  return t;
 }
 private String requestSuggestions(String title,String body,boolean stepMode,int stepCount){
  var factory=new org.springframework.http.client.SimpleClientHttpRequestFactory();
  factory.setConnectTimeout(8000);factory.setReadTimeout(120000);
  var client=org.springframework.web.client.RestClient.builder().baseUrl(aiBase).requestFactory(factory).build();
  String system=stepMode
   ?("你是课程写作助教，学生在写步骤型作业：用若干步把推理写清楚。"
    +"你的任务是逐条指出某一步里最具体的问题，并给出最小改法（改哪一句、补哪个量或单位、加什么限定条件）。"
    +"禁止给出完整解答、标准答案或重写全文，也不要把学生没写的推理替他补完。"
    +"四个维度：S 科学性、A 准确性、C 完整性、L 逻辑性。"
    +"我会给你按步分组的正文，每步行如「第2步 标题」，下面每行形如 [p_xxx] 段落内容。"
    +"严格只输出 JSON，不要解释或代码块标记。格式：{\"suggestions\":[{\"stepIndex\":0,\"paragraphId\":\"p_xxx\",\"quote\":\"该段落中 10-40 字原文\",\"dimension\":\"S|A|C|L\",\"title\":\"不超过18字的结论\",\"body\":\"40-80字，指出这一步的问题并给最小改法\"}]}。"
    +"stepIndex 从 0 开始，必须对应我给出的步序；paragraphId 必须来自该步；每步最多 2 条，总数不超过 5 条；每条 body 不超过 60 字，务必保证 JSON 完整闭合。")
   :("你是课程写作助教，帮助学生改进知识帖。只能依据四维量规给出可执行的修改建议，不得编造来源。"
    +"四个维度：S 科学性（概念与机制是否正确）、A 准确性（表述与数据是否有据）、C 完整性（关键环节是否交代）、L 逻辑性（论证链条是否连贯）。"
    +"我会给你带段落标识的正文，每行形如 [p_xxx] 段落内容。"
    +"严格只输出 JSON，不要任何解释或代码块标记。格式：{\"suggestions\":[{\"paragraphId\":\"p_xxx\",\"quote\":\"该段落中 10-40 字的原文片段\",\"dimension\":\"S|A|C|L\",\"title\":\"不超过18字的结论\",\"body\":\"40-80字，指出问题并给出改法\"}]}。"
    +"paragraphId 必须来自我给出的列表；quote 必须逐字出现在该段落里。最多 4 条，按重要程度排序。");
  String user=stepMode
   ?"标题："+(title==null?"":title)+"\n分步正文（共 "+stepCount+" 步）：\n"+body+"\n\n请针对每一步指出具体问题，最多 6 条，每条都要能定位到某一步。"
   :"标题："+(title==null?"":title)+"\n正文（带段落标识）：\n"+body+"\n\n请给出 3 到 4 条修改建议。";
  Map<String,Object> payload=new LinkedHashMap<>();
  payload.put("model",aiModel);
  payload.put("temperature",0.3);
  // 步骤作业一次要给多步、每条正文更长，给足额度，否则 JSON 会被截断。
  payload.put("max_tokens",stepMode?4096:3000);
  // 该系列模型默认会先输出较长推理，挤占正文配额；降到 low 档才能稳定拿到 JSON。
  payload.put("reasoning_effort","low");
  payload.put("messages",List.of(Map.of("role","system","content",system),Map.of("role","user","content",user)));
  Map<?,?> resp=client.post().uri("/chat/completions").header("Authorization","Bearer "+aiKey)
   .contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(payload).retrieve().body(Map.class);
  if(resp==null)throw new IllegalStateException("空响应");
  var choices=(List<?>)resp.get("choices");
  var message=(Map<?,?>)((Map<?,?>)choices.getFirst()).get("message");
  Object content=message.get("content");
  if(content==null||content.toString().isBlank())throw new IllegalStateException("模型无输出");
  return content.toString();
 }
 @Transactional // 自由创作：为作者新建一条不挂作业要求与截止时间的知识帖（内部用 free_form 作业占位，沿用既有联表逻辑）。
 public Map<String,Object> createFreeEntry(long cid,String title,long uid){
  s.student(cid,uid);
  String t=title==null?"":title.trim();
  if(t.isEmpty()||t.length()>160)throw fail(422,"标题需为 1-160 字");
  var rubric=rubrics.selection(cid,null);long rid=((Number)rubric.get("id")).longValue();
  Instant due=Instant.now().plusSeconds(3600L*24*365*100);
  long aid=s.insert("INSERT INTO assignment(course_id,title,requirements,due_at,rubric_version,rubric_id,created_at,ai_feedback_enabled,free_form) VALUES(?,?,?,?,?,?,?,FALSE,TRUE)",cid,t,"自由创作：不受作业要求与截止时间约束；提交后由任课教师审核，通过后可在知识社区展示。",due.toString(),rubric.get("code"),rid==0?null:rid,now());
  s.audit(uid,"FREE_ENTRY_CREATE","assignment",aid);
  return start(aid,uid);
 }
public Map<String,Object> start(long aid,long uid){var a=s.one("SELECT * FROM assignment WHERE id=? FOR UPDATE",aid);s.student(s.id(a,"course_id"),uid);var existing=s.db.queryForList("SELECT id FROM entry WHERE assignment_id=? AND author_id=?",aid,uid);if(!existing.isEmpty())return getEntry(s.id(existing.getFirst(),"id"),uid);if(Boolean.TRUE.equals(a.get("manually_closed"))||!Instant.parse((String)a.get("due_at")).isAfter(Instant.now()))throw fail(422,"作业已结束或截止，不能新建作答；已有草稿仍可整理保存");
  String block=UUID.randomUUID().toString();String doc="{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"attrs\":{\"blockId\":\""+block+"\"}}]}";
  long id=s.insert("INSERT INTO entry(assignment_id,author_id,title,draft_json,evidence_json,revision,updated_at) VALUES(?,?,?,?,?,0,?)",aid,uid,a.get("title"),doc,"[]",now());s.audit(uid,"ENTRY_CREATE","entry",id);return getEntry(id,uid);}
 public Map<String,Object> getEntry(long id,long uid){var e=entry(id);String role=s.role(s.id(e,"course_id"),uid);if(s.id(e,"author_id")!=uid&&!role.equals("TEACHER"))throw fail(403,"草稿仅作者与任课教师可见");e.put("rubric",rubrics.forAssignment(s.id(e,"assignment_id")));e.put("content",decode(e.remove("draft_json")));e.put("evidence",decode(e.remove("evidence_json")));
  e.put("versions",s.db.queryForList("SELECT v.id,v.version_no,v.created_at,v.content_hash,g.q_final,g.summary FROM submission v LEFT JOIN evaluation g ON g.submission_id=v.id AND g.active=TRUE WHERE v.entry_id=? ORDER BY v.version_no DESC",id));return e;}
 public List<Map<String,String>> changes(Map<String,Object> v){
  var prior=s.db.queryForList("SELECT content_json FROM submission WHERE entry_id=? AND version_no<? ORDER BY version_no DESC LIMIT 1",v.get("entry_id"),v.get("version_no"));
  Map<String,String> before=prior.isEmpty()?Map.of():anchors(decode(prior.getFirst().get("content_json")));
  Map<String,String> after=anchors(decode(v.get("content_json")));Set<String> ids=new LinkedHashSet<>(before.keySet());ids.addAll(after.keySet());var result=new ArrayList<Map<String,String>>();
  for(String id:ids){String old=before.get(id),next=after.get(id);if(!Objects.equals(old,next))result.add(Map.of("paragraphId",id,"before",old==null?"":old,"after",next==null?"":next,"type",old==null?"ADDED":next==null?"DELETED":"CHANGED"));}
  if(result.isEmpty()&&!prior.isEmpty()&&!Objects.equals(prior.getFirst().get("content_json"),v.get("content_json")))result.add(Map.of("paragraphId","document","before","","after","正文格式或结构发生变化；当前比较器仅展示段落文本差异。","type","STRUCTURE"));
  return result;
 }
 public void validateContent(JsonNode doc){if(doc==null||!"doc".equals(doc.path("type").asText())||!doc.path("content").isArray()||encode(doc).length()>150000)throw fail(422,"正文结构无效或超过 15 万字符");Set<String> ids=new HashSet<>();walk(doc,ids,0,new int[]{0});}
 private void walk(JsonNode node,Set<String> ids,int depth,int[] count){
  if(depth>12||++count[0]>5000)throw fail(422,"正文结构过深或节点过多");
  String type=node.path("type").asText();
  if(!Set.of("doc","paragraph","heading","text","bulletList","orderedList","listItem","bold","italic","codeBlock","blockquote","hardBreak","horizontalRule","figure","mathBlock","inlineMath","callout","table","tableRow","tableCell","tableHeader").contains(type))throw fail(422,"正文含不支持的节点类型");
  boolean required=Set.of("paragraph","heading","figure","mathBlock").contains(type);
  var attrs=node.path("attrs");
  if(type.equals("figure")){
   if(!attrs.path("src").asText().matches("/api/media/[a-f0-9-]{36}")||attrs.path("alt").asText().length()>300||attrs.path("caption").asText().length()>1000||attrs.path("source").asText().length()>1000)throw fail(422,"图片引用或图注格式无效");
  }
  if(type.equals("mathBlock")&&(attrs.path("latex").asText().isBlank()||attrs.path("latex").asText().length()>2000))throw fail(422,"公式须为 1–2000 字符的 LaTeX");
  if(type.equals("inlineMath")&&(attrs.path("latex").asText().isBlank()||attrs.path("latex").asText().length()>300))throw fail(422,"行内公式须为 1–300 字符的 LaTeX");
  if(type.equals("table")&&(node.path("content").size()<1||node.path("content").size()>50))throw fail(422,"表格最多 50 行");
  if(type.equals("tableRow")&&(node.path("content").size()<1||node.path("content").size()>12))throw fail(422,"表格最多 12 列");
  if(Set.of("tableCell","tableHeader").contains(type)&&(attrs.path("colspan").asInt(1)<1||attrs.path("colspan").asInt(1)>12||attrs.path("rowspan").asInt(1)<1||attrs.path("rowspan").asInt(1)>50))throw fail(422,"表格单元格跨度无效");
  String id=node.path("attrs").path("blockId").asText();
  // Legacy code blocks may lack an id. New code blocks carry stable ids for evidence and diffs.
  if(required||(type.equals("codeBlock")&&!id.isBlank())){
   if(!id.matches("[a-zA-Z0-9_-]{1,80}")||!ids.add(id))throw fail(422,"段落标识缺失或重复，请重新载入编辑器");
  }
  for(JsonNode mark:node.path("marks")){
   String m=mark.path("type").asText();
   if(!Set.of("bold","italic","strike","underline","code","link").contains(m))throw fail(422,"正文含不支持的文字格式");
   if(m.equals("link")){
    String href=mark.path("attrs").path("href").asText();
    try{
     var url=java.net.URI.create(href);
     if(href.length()>4096||href.chars().anyMatch(Character::isWhitespace)||href.chars().anyMatch(Character::isISOControl)||!Set.of("https","http").contains(String.valueOf(url.getScheme()).toLowerCase(Locale.ROOT))||url.getHost()==null)throw new IllegalArgumentException();
    }catch(IllegalArgumentException ex){throw fail(422,"正文链接必须使用完整的 http 或 https 地址");}
   }
  }
  for(JsonNode c:node.path("content"))walk(c,ids,depth+1,count);
 }
 public Map<String,String> anchors(JsonNode doc){Map<String,String> map=new LinkedHashMap<>();collect(doc,map);return map;}
 private void collect(JsonNode n,Map<String,String> out){String id=n.path("attrs").path("blockId").asText();if(!id.isBlank())out.put(id,text(n));for(var c:n.path("content"))collect(c,out);}
 public Set<String> mediaSources(JsonNode doc){Set<String> out=new LinkedHashSet<>();collectMedia(doc,out);return out;}
 private void collectMedia(JsonNode n,Set<String> out){if(n.path("type").asText().equals("figure"))out.add(n.path("attrs").path("src").asText());for(var c:n.path("content"))collectMedia(c,out);}
 private String text(JsonNode n){StringBuilder b=new StringBuilder(n.path("text").asText());
  if(n.path("type").asText().equals("figure"))b.append("[图片] ").append(n.path("attrs").path("alt").asText()).append(" ").append(n.path("attrs").path("caption").asText()).append(" ").append(n.path("attrs").path("source").asText());
  if(n.path("type").asText().equals("mathBlock")||n.path("type").asText().equals("inlineMath"))b.append(n.path("attrs").path("latex").asText());for(var c:n.path("content"))b.append(text(c));return b.toString();}
 @Transactional public Map<String,Object> save(long eid,ApiController.DraftRequest req,long uid){var e=entry(eid);own(e,uid);s.student(s.id(e,"course_id"),uid);validateContent(req.content());for(String src:mediaSources(req.content())){if(s.db.queryForObject("SELECT COUNT(*) FROM entry_media WHERE id=? AND entry_id=?",Integer.class,src.substring("/api/media/".length()),eid)!=1)throw fail(422,"正文图片不属于当前作品，请重新上传");}if(req.evidence()==null||req.evidence().size()>30)throw fail(422,"每篇最多挂载 30 条证据");var ids=anchors(req.content());for(var v:req.evidence()){if(!ids.containsKey(v.paragraphId()))throw fail(422,"证据引用的段落不存在");if(!v.sourceUrl().isBlank()&&!v.sourceUrl().matches("https?://[^\\s]+"))throw fail(422,"证据链接必须使用 http 或 https");}
  int changed=s.db.update("UPDATE entry SET title=?,draft_json=?,evidence_json=?,revision=revision+1,updated_at=? WHERE id=? AND revision=?",req.title(),encode(req.content()),encode(req.evidence()),now(),eid,req.revision());if(changed!=1)throw fail(409,"草稿已被另一窗口修改。当前输入仍在本页，请先复制，再重新加载后合并");
  // 自由创作没有作业要求，列表里显示的就是这条占位作业的标题：跟着正文标题走，别让它一直停在「未命名知识帖」。
  s.db.update("UPDATE assignment SET title=? WHERE id=? AND free_form=TRUE",req.title(),s.id(e,"assignment_id"));return getEntry(eid,uid);}
 @Transactional public Map<String,Object> submit(long eid,int revision,String key,long uid){var e=entry(eid);own(e,uid);s.student(s.id(e,"course_id"),uid);s.one("SELECT id FROM entry WHERE id=? FOR UPDATE",eid);e=entry(eid);
  var repeated=s.db.queryForList("SELECT id FROM submission WHERE entry_id=? AND idempotency_key=?",eid,key);if(!repeated.isEmpty())return submission(s.id(repeated.getFirst(),"id"));
  var rule=s.one("SELECT * FROM assignment WHERE id=? FOR UPDATE",e.get("assignment_id"));
  if(Boolean.TRUE.equals(rule.get("manually_closed")))throw fail(422,"教师已结束作业；已有草稿仍可保存，但不能提交新版本");
  if("PASSED".equals(e.get("state")))throw fail(422,"老师已通过定稿；如需继续修改，请联系老师撤销定稿");
  if(((Number)e.get("revision")).intValue()!=revision)throw fail(409,"草稿版本已变化，请保存并刷新后再提交");if(!Instant.parse((String)rule.get("due_at")).isAfter(Instant.now()))throw fail(422,"作业已截止，本版尚不支持迟交");
  // 提交次数上限：0 表示不限。达到上限后仍可保存草稿，但不能冻结新版本。
  int submitLimit=rule.get("max_submissions")==null?0:((Number)rule.get("max_submissions")).intValue();
  int submitted=s.db.queryForObject("SELECT COUNT(*) FROM submission WHERE entry_id=?",Integer.class,eid);
  if(submitLimit>0&&submitted>=submitLimit)throw fail(422,"本作业最多提交 "+submitLimit+" 个版本，你已用完；可以继续修改草稿，但不能再提交新版本");
  JsonNode doc=decode(e.get("draft_json"));validateContent(doc);if(text(doc).isBlank())throw fail(422,"请先填写正文");
  var latest=s.db.queryForList("SELECT id,content_hash FROM submission WHERE entry_id=? ORDER BY version_no DESC LIMIT 1",eid);
  // 上一版必须有教师反馈才允许交新版，否则老师还没看，学生就反复刷版本。
  // 「反馈」有三种：正式评分、段落批注、老师明确打回——任一存在即视为已反馈。
  // 打回必须放行，否则「打回→修订→再提交」会死锁：被打回的那版往往还没评分。
  if(!latest.isEmpty()){
   long last=s.id(latest.getFirst(),"id");
   boolean graded=s.db.queryForObject("SELECT COUNT(*) FROM evaluation WHERE submission_id=? AND active=TRUE",Integer.class,last)>0;
   boolean annotated=s.db.queryForObject("SELECT COUNT(*) FROM feedback WHERE submission_id=?",Integer.class,last)>0;
   if(!graded&&!annotated&&!"REVISION".equals(e.get("state")))throw fail(422,"上一版本还没有教师反馈，请等待老师评阅或批注后再修订");
  }
  String hash;try{hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((e.get("title")+"\n"+e.get("draft_json")+"\n"+e.get("evidence_json")).getBytes(StandardCharsets.UTF_8)));}catch(Exception x){throw new IllegalStateException(x);}
  if(!latest.isEmpty()&&hash.equals(latest.getFirst().get("content_hash")))throw fail(422,"内容与证据均未变化，无需重复提交");
  int version=s.db.queryForObject("SELECT COUNT(*) FROM submission WHERE entry_id=?",Integer.class,eid)+1;
  long sid=s.insert("INSERT INTO submission(entry_id,version_no,title,content_json,content_hash,rubric_version,idempotency_key,created_at) VALUES(?,?,?,?,?,?,?,?)",eid,version,e.get("title"),e.get("draft_json"),hash,e.get("rubric_version"),key,now());
  s.db.update("UPDATE submission SET assignment_snapshot_json=?,rubric_snapshot_json=? WHERE id=?",encode(AssignmentManagement.snapshot(rule)),encode(rubrics.forAssignment(s.id(e,"assignment_id"))),sid);
  // 学生交出新版本，说明打回的问题已经处理，状态回到进行中，等教师重新评阅。
  if("REVISION".equals(e.get("state")))s.db.update("UPDATE entry SET state='OPEN',state_reason=NULL,state_at=NULL,state_by=NULL WHERE id=?",eid);
  for(JsonNode v:decode(e.get("evidence_json")))s.db.update("INSERT INTO evidence(submission_id,paragraph_id,source_title,source_url,excerpt,claim_text,state) VALUES(?,?,?,?,?,?,'PENDING')",sid,v.path("paragraphId").asText(),v.path("sourceTitle").asText(),v.path("sourceUrl").asText(),v.path("excerpt").asText(),v.path("claim").asText());s.audit(uid,"SUBMISSION_CREATE","submission",sid);notices.teachers(s.id(e,"course_id"),"SUBMISSION","有新版本等待评审","submission",sid,uid);return submission(sid);}
 // 单篇帖子的终审：通过定稿或打回修订。
 // 作业级 manually_closed 是整班结束；这里解决的是「每一篇什么时候算完成」——
 // 不裁定的话，学生可以一直「提交→评分→再提交」，永远没有终点。
 @Transactional public Map<String,Object> entryVerdict(long eid,String action,String reason,long uid){
  var e=entry(eid);s.teacher(s.id(e,"course_id"),uid);
  if(!Set.of("PASS","RETURN","REOPEN").contains(action))throw fail(422,"未知的终审操作");
  String note=reason==null?"":reason.trim();
  if("RETURN".equals(action)&&note.isBlank())throw fail(422,"打回修订要写清原因，学生才知道改什么");
  var latest=s.db.queryForList("SELECT id FROM submission WHERE entry_id=? ORDER BY version_no DESC LIMIT 1",eid);
  long latestId=latest.isEmpty()?0:s.id(latest.getFirst(),"id");
  if("PASS".equals(action)){
   if(latestId==0)throw fail(422,"该帖子还没有提交版本，不能通过定稿");
   if(s.db.queryForObject("SELECT COUNT(*) FROM evaluation WHERE submission_id=? AND active=TRUE",Integer.class,latestId)==0)throw fail(422,"请先发布正式评分，再通过定稿");
  }
  String state="PASS".equals(action)?"PASSED":"RETURN".equals(action)?"REVISION":"OPEN";
  s.db.update("UPDATE entry SET state=?,state_reason=?,state_at=?,state_by=? WHERE id=?",state,note.isBlank()?null:note,now(),uid,eid);
  s.audit(uid,"ENTRY_"+action,"entry",eid);
  String msg="PASS".equals(action)?"老师已通过你的帖子，定稿完成":action.equals("RETURN")?"老师打回了你的帖子，需要继续修订":"老师撤销了定稿，可以继续修改";
  if(latestId>0)notices.send(s.id(e,"author_id"),s.id(e,"course_id"),"ENTRY_VERDICT","MEMBER",msg,"submission",latestId,"",uid);
  return entry(eid);
 }
 public Map<String,Object> getSubmission(long sid,long uid){var v=submission(sid);visible(v,uid);v.put("rubricSnapshot",rubrics.forSubmission(v));v.put("rubricSnapshotSource",v.get("rubric_snapshot_json")==null?"BUILTIN_LEGACY":"SUBMISSION_SNAPSHOT");v.remove("rubric_snapshot_json");v.put("changes",changes(v));v.put("content",decode(v.remove("content_json")));Object task=v.remove("assignment_snapshot_json");v.put("assignmentSnapshot",task==null?null:decode(task));v.put("evaluation",getEvaluation(sid));
  // 批注是对「某一版」说的过程性意见，不是成品的一部分：读者（同学、公开访客）默认拿不到，
  // 除非作者发布时勾了「公开批注作为范例」。裁在服务端，前端隐藏不算数。
  boolean feedbackVisible=canReadFeedback(v,uid);
  boolean privileged=isAuthorOrStaff(v,uid);
  var fbList=feedbackVisible?feedback(sid):List.<Map<String,Object>>of();
  // 公开的只是「老师说了什么」，学生对批注的回应仍属于师生之间的往来，不跟着外露。
  if(feedbackVisible&&!privileged)for(var f:fbList)f.remove("actions");
  v.put("feedback",fbList);v.put("priorFeedback",feedbackVisible?priorFeedback(v):List.of());v.put("feedbackVisible",feedbackVisible);
  v.put("evidence",s.db.queryForList("SELECT * FROM evidence WHERE submission_id=?",sid));var publications=s.db.queryForList("SELECT * FROM publication WHERE submission_id=?",sid);if(s.id(v,"author_id")!=uid&&!s.role(s.id(v,"course_id"),uid).equals("TEACHER"))for(var pub:publications){pub.remove("last_reason");pub.remove("requested_evaluation_revision");}v.put("publication",publications);return v;}
 // 作者与教师始终看得到批注；其他人只有当这一版已发布、且作者勾了「公开批注作为范例」时才看得到。
 boolean canReadFeedback(Map<String,Object> v,long uid){
  if(isAuthorOrStaff(v,uid))return true;
  return s.db.queryForObject("SELECT COUNT(*) FROM publication WHERE submission_id=? AND state='PUBLISHED' AND share_feedback=TRUE",Integer.class,s.id(v,"id"))>0;
 }
 boolean isAuthorOrStaff(Map<String,Object> v,long uid){
  if(s.id(v,"author_id")==uid)return true;
  String role=s.role(s.id(v,"course_id"),uid);
  return role.equals("TEACHER")||role.equals("ASSISTANT");
 }
 // 修订历史：读者只看发布过的版本，草稿版本属于作者和教师。
 public List<Map<String,Object>> entryVersions(long eid,long uid){
  var e=entry(eid);long cid=s.id(e,"course_id");String role=s.role(cid,uid);
  boolean staff=role.equals("TEACHER")||role.equals("ASSISTANT"),author=s.id(e,"author_id")==uid;
  var rows=s.db.queryForList("SELECT v.id,v.version_no,v.title,v.created_at,g.q_final,g.summary,p.state AS publication_state,p.share_feedback FROM submission v LEFT JOIN evaluation g ON g.submission_id=v.id AND g.active=TRUE LEFT JOIN publication p ON p.submission_id=v.id WHERE v.entry_id=? ORDER BY v.version_no DESC",eid);
  List<Map<String,Object>> out=new ArrayList<>();
  for(var r:rows){
   boolean published="PUBLISHED".equals(r.get("publication_state"));
   if(!staff&&!author&&!published)continue;
   boolean fb=staff||author||(published&&Boolean.TRUE.equals(r.get("share_feedback")));
   var list=fb?feedback(s.id(r,"id")):List.<Map<String,Object>>of();
   if(fb&&!staff&&!author)for(var f:list)f.remove("actions");
   r.put("published",published);r.put("feedbackVisible",fb);r.put("feedback",list);r.put("feedbackCount",list.size());r.remove("share_feedback");
   out.add(r);
  }
  return out;
 }
 public Object getEvaluation(long sid){var rows=s.db.queryForList("SELECT g.*,u.display_name AS teacher_name FROM evaluation g JOIN app_user u ON u.id=g.teacher_id WHERE g.submission_id=? AND g.active=TRUE",sid);if(rows.isEmpty())return null;var g=rows.getFirst();g.put("details",decode(g.remove("details_json")));g.put("dimensions",decode(g.remove("dimensions_json")));Object steps=g.remove("step_scores_json");g.put("stepScores",steps==null?List.of():decode(String.valueOf(steps)));return g;}
 public List<Map<String,Object>> feedback(long sid){var rows=s.db.queryForList("SELECT f.*,u.display_name AS teacher_name FROM feedback f JOIN app_user u ON u.id=f.teacher_id WHERE f.submission_id=? ORDER BY f.id",sid);
  if(rows.isEmpty())return rows;
  String ids=rows.stream().map(f->String.valueOf(s.id(f,"id"))).collect(java.util.stream.Collectors.joining(","));
  var actions=s.db.queryForList("SELECT a.*,u.display_name AS actor_name FROM feedback_action a JOIN app_user u ON u.id=a.user_id WHERE a.feedback_id IN ("+ids+") ORDER BY a.feedback_id,a.id");
  Map<Long,List<Map<String,Object>>> grouped=new HashMap<>();
  for(var a:actions)grouped.computeIfAbsent(((Number)a.get("feedback_id")).longValue(),k->new ArrayList<>()).add(a);
  for(var f:rows)f.put("actions",grouped.getOrDefault(s.id(f,"id"),List.of()));
  return rows;}
 // 学生提交新版本后，上一版没解决的批注仍然锚在同一段落上（blockId 跨版本稳定）。
 // 批改时要把它们带出来，否则教师每版都从零开始，学生也不知道哪些问题没改。
 // 同时带上作者最近一次回应（ADOPT=声明已改），教师据此决定先看哪条；已解决的也返回，
 // 前端折叠展示，误点「确认解决」还能重新打开。
 public List<Map<String,Object>> priorFeedback(Map<String,Object> v){
  var rows=s.db.queryForList("SELECT f.*,u.display_name AS teacher_name,sv.version_no AS source_version_no,"
   +"(SELECT z.action_type FROM feedback_action z WHERE z.feedback_id=f.id AND z.user_id=e.author_id AND z.action_type IN ('REPLY','ADOPT','IGNORE') ORDER BY z.id DESC LIMIT 1) AS student_action "
   +"FROM feedback f JOIN app_user u ON u.id=f.teacher_id JOIN submission sv ON sv.id=f.submission_id JOIN entry e ON e.id=sv.entry_id "
   +"WHERE sv.entry_id=? AND sv.id<>? ORDER BY f.id DESC LIMIT 60",v.get("entry_id"),v.get("id"));
  if(rows.isEmpty())return rows;
  Set<String> live=anchors((JsonNode)v.get("content")).keySet();
  for(var f:rows){String pid=(String)f.get("paragraph_id");f.put("stale",pid==null||!live.contains(pid));}
  return rows;}
 public List<Map<String,Object>> queue(long cid,long uid){String role=s.role(cid,uid);if(!List.of("TEACHER","ASSISTANT").contains(role))throw fail(403,"批改队列仅教师或助教可见");return s.db.queryForList("SELECT v.id,v.title,v.version_no,v.created_at,e.author_id,u.display_name AS author_name,a.title AS assignment_title,g.q_final,d.updated_at AS review_draft_updated_at FROM submission v JOIN entry e ON e.id=v.entry_id JOIN app_user u ON u.id=e.author_id JOIN assignment a ON a.id=e.assignment_id LEFT JOIN evaluation g ON g.submission_id=v.id AND g.active=TRUE LEFT JOIN review_draft d ON d.submission_id=v.id AND d.teacher_id=? AND ?=TRUE WHERE a.course_id=? ORDER BY v.created_at DESC",uid,role.equals("TEACHER"),cid);}
 @Transactional public Map<String,Object> evaluate(long sid,ApiController.EvaluationRequest req,long uid){var v=submission(sid);s.teacher(s.id(v,"course_id"),uid);s.one("SELECT id FROM submission WHERE id=? FOR UPDATE",sid);var rubric=rubrics.forSubmission(v);int current=s.db.queryForObject("SELECT COUNT(*) FROM evaluation WHERE submission_id=?",Integer.class,sid);if(req.expectedRevision()!=current)throw fail(409,"评价已被其他窗口更新，请重新加载后再评");
  // 同一版本批阅次数上限：0 表示不限。首次评分与复评都计入。
  var evalRule=s.one("SELECT max_evaluations,evidence_policy,kind FROM assignment WHERE id=?",s.id(v,"assignment_id"));
  int evalLimit=evalRule.get("max_evaluations")==null?0:((Number)evalRule.get("max_evaluations")).intValue();
  if(evalLimit>0&&current>=evalLimit)throw fail(422,"该版本已批阅 "+current+" 次，达到本作业上限（"+evalLimit+" 次），不能再复评");
  // 证据核验按作业策略放行：REQUIRED 逐条核验后才发分；DISPUTED（默认）只挡有争议的；OFF 不挡。
  // 旧行为是「任一条待核验即不能发分」，等于要求教师核完全班每条证据，实际不可持续。
  String policy=String.valueOf(evalRule.get("evidence_policy"));
  int disputed=s.db.queryForObject("SELECT COUNT(*) FROM evidence WHERE submission_id=? AND state='DISPUTED'",Integer.class,sid);
  if(!"OFF".equals(policy)&&disputed>0)throw fail(422,"有 "+disputed+" 条证据存在争议，请先裁定再发布正式评分");
  if("REQUIRED".equals(policy)){int pending=s.db.queryForObject("SELECT COUNT(*) FROM evidence WHERE submission_id=? AND state='PENDING'",Integer.class,sid);if(pending>0)throw fail(422,"本作业要求逐条核验证据，仍有 "+pending+" 条待核验");}
  boolean step= "STEP".equals(evalRule.get("kind"));
  List<ApiController.StepScore> steps=req.stepScores()==null?List.of():req.stepScores();
  if(step)validateStepScores(steps);else if(!steps.isEmpty())throw fail(422,"知识帖作业不接受步骤分");
  // 步骤型作业默认只用步骤分发分，不强制 11 项四维：换算成百分制写入 q_final，
  // details/dimensions 存空数组，前端据此不显示四维面板。
  List<QualityCalculator.Item> items=req.items()==null?List.of():req.items();
  String detailsJson,dimensionsJson;java.math.BigDecimal q;
  if(step&&items.isEmpty()){
    if(steps.isEmpty())throw fail(422,"步骤型作业至少要给一步打分");
    java.math.BigDecimal sum=java.math.BigDecimal.ZERO,max=java.math.BigDecimal.ZERO;
    for(var x:steps){sum=sum.add(x.score());max=max.add(x.max());}
    if(max.signum()<=0)throw fail(422,"步骤分满分必须大于 0");
    q=sum.multiply(java.math.BigDecimal.valueOf(100)).divide(max,2,java.math.RoundingMode.HALF_UP);
    detailsJson="[]";dimensionsJson="[]";
  }else{
    if(items.size()!=QualityCalculator.CODES.size())throw fail(422,"必须包含且仅包含 11 个不重复的二级指标");
    var result=QualityCalculator.calculate(items,req.gateReason(),rubric.path("dimensions"));
    q=result.total();detailsJson=encode(items);dimensionsJson=encode(result.dimensions());
  }
  String stepJson=steps.isEmpty()?null:encode(steps);
  s.db.update("UPDATE evaluation SET active=FALSE WHERE submission_id=? AND active=TRUE",sid);
  long gid=s.insert("INSERT INTO evaluation(submission_id,teacher_id,revision_no,details_json,dimensions_json,q_raw,q_final,gate_reason,summary,active,created_at,step_scores_json) VALUES(?,?,?,?,?,?,?,?,?,TRUE,?,?)",sid,uid,current+1,detailsJson,dimensionsJson,q,q,req.gateReason(),req.summary(),now(),stepJson);
  // A regrade revokes public visibility until the author requests review again.
  publicationAfterEvaluation(sid,uid);s.audit(uid,"EVALUATION_PUBLISH","evaluation",gid);s.db.update("DELETE FROM review_draft WHERE submission_id=? AND teacher_id=?",sid,uid);notices.send(s.id(v,"author_id"),s.id(v,"course_id"),"EVALUATION","MEMBER","教师已发布 v"+v.get("version_no")+" 的正式评价","submission",sid,"",uid);return (Map<String,Object>)getEvaluation(sid);}
 @Transactional public Map<String,Object> addFeedback(long sid,ApiController.FeedbackRequest req,long uid){var v=submission(sid);s.teacher(s.id(v,"course_id"),uid);var a=anchors(decode(v.get("content_json")));if(!a.containsKey(req.paragraphId()))throw fail(422,"段落不存在");
  // 教师批注支持四维（S/A/C/L）轻量标注，同时兼容历史的 11 项二级指标编码。
  boolean dimension=Set.of("S","A","C","L").contains(req.indicator());
  if(!dimension&&!QualityCalculator.CODES.contains(req.indicator()))throw fail(422,"批注维度不存在");
  String quote=req.quote()==null?"":req.quote().trim();
  if(quote.length()>600)throw fail(422,"批注引文不得超过 600 字");
  String anchor=a.get(req.paragraphId());
  if(!quote.isEmpty()&&!anchor.contains(quote))throw fail(422,"引文与所选段落不一致，请重新选中文字");
  long id=s.insert("INSERT INTO feedback(submission_id,teacher_id,paragraph_id,original_text,indicator_code,body,resolved,created_at,quote_text) VALUES(?,?,?,?,?,?,FALSE,?,?)",sid,uid,req.paragraphId(),anchor.substring(0,Math.min(4000,anchor.length())),req.indicator(),req.body(),now(),quote.isEmpty()?null:quote);s.audit(uid,"FEEDBACK_CREATE","feedback",id);notices.send(s.id(v,"author_id"),s.id(v,"course_id"),"FEEDBACK","MEMBER","收到新的教师批注","submission",sid,req.paragraphId(),uid);return s.one("SELECT * FROM feedback WHERE id=?",id);}
 @Transactional public void action(long fid,String action,String body,long uid){var f=s.one("SELECT * FROM feedback WHERE id=?",fid);var v=submission(s.id(f,"submission_id"));String role=s.role(s.id(v,"course_id"),uid);boolean teacher=role.equals("TEACHER");if(!teacher&&s.id(v,"author_id")!=uid)throw fail(403,"无权处理该反馈");if(!Set.of("REPLY","ADOPT","IGNORE","RESOLVE","REOPEN").contains(action))throw fail(422,"未知反馈操作");if(!teacher&&Set.of("RESOLVE","REOPEN").contains(action))throw fail(403,"只有教师可以确认问题解决");if((action.equals("IGNORE")||action.equals("REPLY"))&&body.isBlank())throw fail(422,"请填写回应或忽略理由");s.db.update("INSERT INTO feedback_action(feedback_id,user_id,action_type,body,created_at) VALUES(?,?,?,?,?)",fid,uid,action,body,now());if(teacher&&Set.of("RESOLVE","REOPEN").contains(action))s.db.update("UPDATE feedback SET resolved=? WHERE id=?",action.equals("RESOLVE"),fid);s.audit(uid,"FEEDBACK_"+action,"feedback",fid);if(teacher)notices.send(s.id(v,"author_id"),s.id(v,"course_id"),"FEEDBACK","MEMBER",action.equals("RESOLVE")?"教师已确认反馈问题解决":"教师更新了批注回应","submission",s.id(v,"id"),(String)f.get("paragraph_id"),uid);else notices.teachers(s.id(v,"course_id"),"FEEDBACK_REPLY","学生回应了教师批注","submission",s.id(v,"id"),uid);}
 public List<Map<String,Object>> evidenceTasks(long cid,long uid){s.role(cid,uid);return s.db.queryForList("SELECT x.*,e.author_id,u.display_name AS author_name,v.title,v.version_no FROM evidence x JOIN submission v ON v.id=x.submission_id JOIN entry e ON e.id=v.entry_id JOIN assignment a ON a.id=e.assignment_id JOIN app_user u ON u.id=e.author_id WHERE a.course_id=? ORDER BY x.id DESC",cid);}
 @Transactional public void verify(long id,String verdict,String reason,long uid){verify(id,verdict,reason,uid,null);}
 @Transactional public void verify(long id,String verdict,String reason,long uid,Integer expectedCount){var ref=s.one("SELECT submission_id FROM evidence WHERE id=?",id);s.one("SELECT id FROM submission WHERE id=? FOR UPDATE",ref.get("submission_id"));var ev=s.one("SELECT * FROM evidence WHERE id=? FOR UPDATE",id);var v=submission(s.id(ev,"submission_id"));String role=s.role(s.id(v,"course_id"),uid);boolean teacher=role.equals("TEACHER");if(reason==null||reason.isBlank()||reason.length()>2000)throw fail(422,"请填写不超过2000字的对照材料与判断依据");if(s.id(v,"author_id")==uid)throw fail(403,"作者不能核验自己的证据");if(!Set.of("PASS","FAIL").contains(verdict))throw fail(422,"核验结论不正确");var prior=s.db.queryForList("SELECT * FROM verification WHERE evidence_id=? ORDER BY id",id);if(expectedCount!=null&&expectedCount!=prior.size())throw fail(409,"核验记录已变化，当前判断依据仍保留。请刷新证据后核对最新记录");if(!teacher&&prior.stream().anyMatch(x->s.id(x,"verifier_id")==uid))throw fail(409,"你已核验此证据；需要改判请联系教师");if(!teacher&&prior.stream().anyMatch(x->x.get("verifier_id").equals(v.get("author_id"))))throw fail(403,"核验权限错误");
  if(!teacher&&prior.stream().anyMatch(x->{var m=s.db.queryForList("SELECT role FROM course_member WHERE course_id=? AND user_id=?",v.get("course_id"),x.get("verifier_id"));return "TEACHER".equals(x.get("verifier_role"))||(x.get("verifier_role")==null&&!m.isEmpty()&&m.getFirst().get("role").equals("TEACHER"));}))throw fail(422,"教师已裁定此证据");
  if(s.db.queryForObject("SELECT COUNT(*) FROM evaluation WHERE submission_id=? AND active=TRUE",Integer.class,v.get("id"))>0)throw fail(422,"该版已发布评分；核验改判需下一版复核，本版暂不开放改判");
  String state=teacher?verdict:prior.stream().anyMatch(x->!verdict.equals(x.get("verdict")))?"DISPUTED":verdict;
  s.db.update("INSERT INTO verification(evidence_id,verifier_id,verdict,reason,created_at,verifier_role) VALUES(?,?,?,?,?,?)",id,uid,verdict,reason.trim(),now(),role);s.db.update("UPDATE evidence SET state=? WHERE id=?",state,id);s.audit(uid,"EVIDENCE_"+verdict,"evidence",id);notices.send(s.id(v,"author_id"),s.id(v,"course_id"),"EVIDENCE","MEMBER","证据核验结果已更新","evidence",id,"",uid);if(state.equals("DISPUTED"))notices.teachers(s.id(v,"course_id"),"EVIDENCE_DISPUTED","证据存在争议，等待教师裁定","evidence",id,uid);}
 public List<Map<String,Object>> verifications(long eid,long uid){var ev=s.one("SELECT * FROM evidence WHERE id=?",eid);var v=submission(s.id(ev,"submission_id"));s.role(s.id(v,"course_id"),uid);return s.db.queryForList("SELECT x.*,u.display_name AS verifier_name FROM verification x JOIN app_user u ON u.id=x.verifier_id WHERE x.evidence_id=? ORDER BY x.id",eid);}
 @Transactional public void requestPublication(long sid,long uid){requestPublication(sid,uid,null,"","COURSE",null);}
 @Transactional public void requestPublication(long sid,long uid,Integer expected,String reason){requestPublication(sid,uid,expected,reason,"COURSE",null);}
 @Transactional public void requestPublication(long sid,long uid,Integer expected,String reason,String visibility){requestPublication(sid,uid,expected,reason,visibility,null);}
 @Transactional public void requestPublication(long sid,long uid,Integer expected,String reason,String visibility,Boolean shareFeedback){
  // 可见范围由作者显式选择；缺省沿用该版已有设置，避免旧客户端把公开版本悄悄收回课程内。
  String scope=visibility==null||visibility.isBlank()?null:visibility.trim();
  if(scope!=null&&!Set.of("COURSE","PUBLIC").contains(scope))throw fail(422,"未知的可见范围");
  var v=submission(sid);own(v,uid);s.one("SELECT id FROM submission WHERE id=? FOR UPDATE",sid);
  var grade=(Map<String,Object>)getEvaluation(sid);if(grade==null)throw fail(422,"请先等待教师发布评分");
  var rows=s.db.queryForList("SELECT * FROM publication WHERE submission_id=?",sid);Map<String,Object> pub;
  if(rows.isEmpty()){if(expected!=null&&expected!=0)throw fail(409,"发布状态已变化，请刷新后核对");long id=s.insert("INSERT INTO publication(submission_id,author_id,state,requested_at) VALUES(?,?,'NEW',?)",sid,uid,now());pub=s.one("SELECT * FROM publication WHERE id=?",id);}
  else pub=rows.getFirst();
  checkPublicationRevision(pub,expected);if(!Set.of("NEW","WITHDRAWN","RETURNED").contains(pub.get("state")))throw fail(409,"该版已申请或已发布");
  // 公开批注是作者对「把自己的批改过程给别人看」的授权，缺省不授权；没传就保持原设置。
  s.db.update("UPDATE publication SET requested_at=?,reviewed_at=NULL,reviewer_id=NULL,requested_evaluation_revision=?,visibility=COALESCE(?,visibility),share_feedback=COALESCE(?,share_feedback) WHERE id=?",now(),grade.get("revision_no"),scope,shareFeedback,pub.get("id"));
  publicationEvent(pub,uid,s.role(s.id(v,"course_id"),uid),"REQUEST","PENDING",reason.isBlank()?"作者申请展示此提交版本":reason,(Number)grade.get("revision_no"));
  s.audit(uid,"PUBLICATION_REQUEST","submission",sid);notices.teachers(s.id(v,"course_id"),"PUBLICATION_REQUEST","有新的课程内发布申请","publication",sid,uid);
 }
 void checkPublicationRevision(Map<String,Object> p,Integer expected){if(expected!=null&&expected.intValue()!=((Number)p.get("revision")).intValue())throw fail(409,"发布状态已变化；请刷新记录后重新核对，输入的理由不会自动提交");}
 void publicationEvent(Map<String,Object> p,long uid,String role,String action,String state,String reason,Number evaluation){
  int revision=((Number)p.get("revision")).intValue()+1;
  s.db.update("UPDATE publication SET state=?,revision=?,last_reason=? WHERE id=?",state,revision,reason,p.get("id"));
  s.db.update("INSERT INTO publication_event(publication_id,revision_no,actor_id,actor_role,action_type,before_state,after_state,reason,evaluation_revision,created_at) VALUES(?,?,?,?,?,?,?,?,?,?)",p.get("id"),revision,uid,role,action,p.get("state"),state,reason,evaluation,now());
 }
 @Transactional public void publicationAction(long id,String action,long uid){if(!Set.of("APPROVE","WITHDRAW").contains(action))throw fail(422,"旧接口仅支持通过或撤回，请使用带理由的审核接口");publicationAction(id,action,uid,null,action.equals("APPROVE")?"教师审核通过指定版本":"作者或教师撤回此版本");}
 @Transactional public void publicationAction(long id,String action,long uid,Integer expected,String reason){
  var pub=s.one("SELECT * FROM publication WHERE id=?",id);s.one("SELECT id FROM submission WHERE id=? FOR UPDATE",pub.get("submission_id"));pub=s.one("SELECT * FROM publication WHERE id=? FOR UPDATE",id);
  var v=submission(s.id(pub,"submission_id"));String role=s.role(s.id(v,"course_id"),uid);boolean teacher=role.equals("TEACHER"),author=s.id(v,"author_id")==uid;
  if(!teacher&&!author)throw fail(403,"只有作者或任课教师可处理发布记录");
  if(!Set.of("APPROVE","RETURN","WITHDRAW").contains(action))throw fail(422,"未知操作");
  if(!action.equals("WITHDRAW")&&!teacher)throw fail(403,"发布审核仅限任课教师");
  checkPublicationRevision(pub,expected);String before=(String)pub.get("state");
  if(action.equals("WITHDRAW")){if(!Set.of("PENDING","PUBLISHED").contains(before))throw fail(409,"此版本当前不可撤回");}
  else if(!before.equals("PENDING"))throw fail(409,"该申请已处理");
  if(reason==null||reason.isBlank()||reason.length()>2000)throw fail(422,"请填写处理理由（最多2000字）");
  var grade=(Map<String,Object>)getEvaluation(s.id(v,"id"));
  if(action.equals("APPROVE")){if(grade==null)throw fail(422,"该版无有效教师评价");Object requested=pub.get("requested_evaluation_revision");if(requested!=null&&((Number)requested).intValue()!=((Number)grade.get("revision_no")).intValue())throw fail(409,"教师评价已变化，请重新申请发布");}
  String state=action.equals("APPROVE")?"PUBLISHED":action.equals("RETURN")?"RETURNED":"WITHDRAWN";
  String event=action.equals("WITHDRAW")?(before.equals("PENDING")?"CANCEL":teacher?"TEACHER_WITHDRAW":"AUTHOR_WITHDRAW"):action;
  s.db.update("UPDATE publication SET reviewed_at=?,reviewer_id=? WHERE id=?",now(),uid,id);
  if(action.equals("APPROVE"))s.db.update("UPDATE publication SET published_at=? WHERE id=?",now(),id);
  publicationEvent(pub,uid,role,event,state,reason,grade==null?null:(Number)grade.get("revision_no"));
  s.audit(uid,"PUBLICATION_"+event,"publication",id);notices.send(s.id(v,"author_id"),s.id(v,"course_id"),"PUBLICATION_RESULT","MEMBER",action.equals("APPROVE")?"指定版本已获准在课程内展示":action.equals("RETURN")?"发布申请已退回，请查看审核理由":"指定版本的社区展示已撤回","submission",s.id(v,"id"),"",uid);
 }
 void publicationAfterEvaluation(long sid,long uid){
  var rows=s.db.queryForList("SELECT * FROM publication WHERE submission_id=? AND state IN ('PENDING','PUBLISHED') FOR UPDATE",sid);
  if(rows.isEmpty())return;var pub=rows.getFirst();var grade=(Map<String,Object>)getEvaluation(sid);
  publicationEvent(pub,uid,"TEACHER","REGRADE","WITHDRAWN","该提交已复评，原发布申请或展示已撤回；请按最新评价重新申请。",grade==null?null:(Number)grade.get("revision_no"));
 }
 public Map<String,Object> publicationReview(long id,long uid){
  var p=s.one("SELECT * FROM publication WHERE id=?",id);var v=submission(s.id(p,"submission_id"));String role=s.role(s.id(v,"course_id"),uid);
  if(s.id(v,"author_id")!=uid&&!role.equals("TEACHER"))throw fail(403,"审核理由与记录仅作者和任课教师可见");
  p.put("title",v.get("title"));p.put("version_no",v.get("version_no"));p.put("author_name",v.get("author_name"));p.put("events",s.db.queryForList("SELECT h.*,u.display_name AS actor_name FROM publication_event h JOIN app_user u ON u.id=h.actor_id WHERE h.publication_id=? ORDER BY h.revision_no DESC",id));return p;
 }
 public List<Map<String,Object>> publicationRecords(long cid,long uid){String role=s.role(cid,uid);return s.db.queryForList("SELECT p.*,v.title,v.version_no,u.display_name AS author_name,a.title AS assignment_title FROM publication p JOIN submission v ON v.id=p.submission_id JOIN entry e ON e.id=v.entry_id JOIN assignment a ON a.id=e.assignment_id JOIN app_user u ON u.id=e.author_id WHERE a.course_id=?"+(role.equals("TEACHER")?"":" AND p.author_id="+uid)+" ORDER BY p.requested_at DESC,p.id DESC",cid);}
 public List<Map<String,Object>> publications(long cid,long uid,boolean pending){return publications(cid,uid,pending,"COURSE");}
 public List<Map<String,Object>> publications(long cid,long uid,boolean pending,String scope){String mode=scope==null||scope.isBlank()?"COURSE":scope.trim().toUpperCase();if(!Set.of("COURSE","MINE","PUBLIC").contains(mode))throw fail(422,"未知的社区范围");
  // 待审核列表只对任课教师开放，且始终按本课程过滤。
  if(pending){s.teacher(cid,uid);mode="COURSE";}else s.role(cid,uid);
  String where;Object[] args;
  if("MINE".equals(mode)){where="a.course_id IN (SELECT course_id FROM course_member WHERE user_id=?) AND p.state='PUBLISHED'";args=new Object[]{uid};}
  else if("PUBLIC".equals(mode)){where="p.visibility='PUBLIC' AND p.state='PUBLISHED'";args=new Object[]{};}
  else {where="a.course_id=? AND p.state=?";args=new Object[]{cid,pending?"PENDING":"PUBLISHED"};}
  var rows=s.db.queryForList("SELECT p.*,v.title,v.version_no,v.content_json,u.display_name AS author_name,a.course_id,c.title AS course_title,g.q_final,g.summary,g.dimensions_json,(SELECT COUNT(*) FROM feedback f WHERE f.submission_id=v.id) AS discussion_count FROM publication p JOIN submission v ON v.id=p.submission_id JOIN entry e ON e.id=v.entry_id JOIN app_user u ON u.id=e.author_id JOIN assignment a ON a.id=e.assignment_id JOIN course c ON c.id=a.course_id JOIN evaluation g ON g.submission_id=v.id AND g.active=TRUE WHERE "+where+" ORDER BY COALESCE(p.published_at,p.reviewed_at,p.requested_at) DESC,p.id DESC",args);
  for(var row:rows){var content=decode(row.remove("content_json"));row.put("cover",mediaSources(content).stream().findFirst().orElse(""));Object dims=row.remove("dimensions_json");row.put("dims",dims==null?Map.of():decode(dims));if(!pending){row.remove("last_reason");row.remove("requested_evaluation_revision");}}return rows;}
}
