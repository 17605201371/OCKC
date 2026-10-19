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
 final Store s;final ObjectMapper json;final NotificationService notices;final MemberManagement members;
 CommunityService(Store s,ObjectMapper json,NotificationService notices,MemberManagement members){this.s=s;this.json=json;this.notices=notices;this.members=members;}
 String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw fail(400,"内容无法序列化");}}
 JsonNode decode(Object value){try{return json.readTree((String)value);}catch(Exception e){throw new IllegalStateException(e);}}
 public Map<String,Object> assignment(long id){return s.one("SELECT * FROM assignment WHERE id=?",id);}
 public Map<String,Object> entry(long id){return s.one("SELECT e.*,a.course_id,a.due_at,a.rubric_version FROM entry e JOIN assignment a ON a.id=e.assignment_id WHERE e.id=?",id);}
 public Map<String,Object> submission(long id){return s.one("SELECT v.*,e.author_id,e.assignment_id,a.course_id,u.display_name AS author_name,a.title AS assignment_title FROM submission v JOIN entry e ON e.id=v.entry_id JOIN assignment a ON a.id=e.assignment_id JOIN app_user u ON u.id=e.author_id WHERE v.id=?",id);}
 void own(Map<String,Object> e,long uid){s.role(s.id(e,"course_id"),uid);if(s.id(e,"author_id")!=uid)throw fail(403,"不能修改他人的作品");}
 void visible(Map<String,Object> v,long uid){String r=s.role(s.id(v,"course_id"),uid);if(s.id(v,"author_id")==uid||r.equals("TEACHER")||r.equals("ASSISTANT"))return;
  if(s.db.queryForObject("SELECT COUNT(*) FROM publication WHERE submission_id=? AND state='PUBLISHED'",Integer.class,v.get("id"))==0)throw fail(403,"该提交尚未获准在课程社区展示");}
 public List<Map<String,Object>> courses(long uid){return s.db.queryForList("SELECT c.*,m.role FROM course c JOIN course_member m ON m.course_id=c.id WHERE m.user_id=? ORDER BY c.id",uid);}
 public List<Map<String,Object>> assignments(long cid,long uid){s.role(cid,uid);var rows=s.db.queryForList("SELECT a.*,e.id AS entry_id,e.revision AS draft_revision,(SELECT MAX(v.version_no) FROM submission v WHERE v.entry_id=e.id) AS version_no FROM assignment a LEFT JOIN entry e ON e.assignment_id=a.id AND e.author_id=? WHERE a.course_id=? ORDER BY a.id DESC",uid,cid);Instant clock=Instant.now();for(var a:rows){Instant due=Instant.parse((String)a.get("due_at"));a.put("server_now",clock.toString());a.put("due_status",Boolean.TRUE.equals(a.get("manually_closed"))||!due.isAfter(clock)?"CLOSED":!due.isAfter(clock.plusSeconds(86400))?"DUE_SOON":"OPEN");}return rows;}
 public Map<String,Object> progress(long cid,long uid){
  String role=s.role(cid,uid);boolean staff=Set.of("TEACHER","ASSISTANT").contains(role);
  String filter=staff?"":" AND e.author_id="+uid;
  var entries=s.db.queryForList("SELECT e.id,e.title,e.author_id,e.revision,e.updated_at,a.title AS assignment_title,a.due_at,u.display_name AS author_name FROM entry e JOIN assignment a ON a.id=e.assignment_id JOIN app_user u ON u.id=e.author_id WHERE a.course_id=?"+filter+" ORDER BY e.updated_at DESC",cid);
  for(var e:entries){
   var versions=s.db.queryForList("SELECT v.id,v.version_no,v.created_at,g.q_final,g.dimensions_json,g.summary FROM submission v LEFT JOIN evaluation g ON g.submission_id=v.id AND g.active=TRUE WHERE v.entry_id=? ORDER BY v.version_no",e.get("id"));
   for(var v:versions){Object dims=v.remove("dimensions_json");v.put("dimensions",dims==null?Map.of():decode(dims));}
   e.put("versions",versions);
   e.put("feedback_total",s.db.queryForObject("SELECT COUNT(*) FROM feedback f JOIN submission v ON v.id=f.submission_id WHERE v.entry_id=?",Integer.class,e.get("id")));
   e.put("feedback_resolved",s.db.queryForObject("SELECT COUNT(*) FROM feedback f JOIN submission v ON v.id=f.submission_id WHERE v.entry_id=? AND f.resolved=TRUE",Integer.class,e.get("id")));
   e.put("evidence_passed",s.db.queryForObject("SELECT COUNT(*) FROM evidence x JOIN submission v ON v.id=x.submission_id WHERE v.entry_id=? AND x.state='PASS'",Integer.class,e.get("id")));
  }
  return Map.of("entries",entries,"staff",staff,"assignmentCount",s.db.queryForObject("SELECT COUNT(*) FROM assignment WHERE course_id=?",Integer.class,cid));
 }
 @Transactional public Map<String,Object> createCourse(String title,String description,long uid){long id=s.insert("INSERT INTO course(title,description) VALUES(?,?)",title,description);s.db.update("INSERT INTO course_member(course_id,user_id,role) VALUES(?,?,'TEACHER')",id,uid);s.audit(uid,"COURSE_CREATE","course",id);return s.one("SELECT * FROM course WHERE id=?",id);}
 @Transactional public void addMember(long cid,String email,String role,long uid){members.add(cid,email,role,uid,null,"演示初始化或教师添加已有账号");}
 @Transactional public void addMember(long cid,String email,String role,long uid,Integer revision,String reason){members.add(cid,email,role,uid,revision,reason);}
 @Transactional public Map<String,Object> createAssignment(long cid,String title,String requirements,String due,boolean aiEnabled,long uid){s.teacher(cid,uid);Instant deadline;try{deadline=Instant.parse(due);}catch(Exception e){throw fail(422,"截止时间格式错误");}if(deadline.isBefore(Instant.now()))throw fail(422,"截止时间须晚于当前时间");long id=s.insert("INSERT INTO assignment(course_id,title,requirements,due_at,rubric_version,created_at,ai_feedback_enabled) VALUES(?,?,?,?,'four-dim-v1',?,?)",cid,title,requirements,deadline.toString(),now(),aiEnabled);s.audit(uid,"ASSIGNMENT_CREATE","assignment",id);return assignment(id);}
 @Transactional public Map<String,Object> setAiPolicy(long aid,boolean enabled,long uid){
  var a=assignment(aid);s.teacher(s.id(a,"course_id"),uid);
  s.one("SELECT id FROM assignment WHERE id=? FOR UPDATE",aid);
  s.db.update("UPDATE assignment SET ai_feedback_enabled=? WHERE id=?",enabled,aid);
  s.audit(uid,enabled?"ASSIGNMENT_AI_ENABLE":"ASSIGNMENT_AI_DISABLE","assignment",aid);
  return assignment(aid);
 }
 public Map<String,Object> aiFeedback(long eid,long uid){
  var e=entry(eid);String role=s.role(s.id(e,"course_id"),uid);
  if(s.id(e,"author_id")!=uid&&!"TEACHER".equals(role))throw fail(403,"AI 反馈仅作者与任课教师可见");
  boolean enabled=Boolean.TRUE.equals(assignment(s.id(e,"assignment_id")).get("ai_feedback_enabled"));
  return Map.of("enabled",enabled,"status",enabled?"NOT_CONFIGURED":"DISABLED","suggestions",List.of(),"formalScore",false);
 }
 @Transactional public Map<String,Object> start(long aid,long uid){var a=s.one("SELECT * FROM assignment WHERE id=? FOR UPDATE",aid);s.student(s.id(a,"course_id"),uid);var existing=s.db.queryForList("SELECT id FROM entry WHERE assignment_id=? AND author_id=?",aid,uid);if(!existing.isEmpty())return getEntry(s.id(existing.getFirst(),"id"),uid);if(Boolean.TRUE.equals(a.get("manually_closed"))||!Instant.parse((String)a.get("due_at")).isAfter(Instant.now()))throw fail(422,"作业已结束或截止，不能新建作答；已有草稿仍可整理保存");
  String block=UUID.randomUUID().toString();String doc="{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"attrs\":{\"blockId\":\""+block+"\"}}]}";
  long id=s.insert("INSERT INTO entry(assignment_id,author_id,title,draft_json,evidence_json,revision,updated_at) VALUES(?,?,?,?,?,0,?)",aid,uid,a.get("title"),doc,"[]",now());s.audit(uid,"ENTRY_CREATE","entry",id);return getEntry(id,uid);}
 public Map<String,Object> getEntry(long id,long uid){var e=entry(id);String role=s.role(s.id(e,"course_id"),uid);if(s.id(e,"author_id")!=uid&&!role.equals("TEACHER"))throw fail(403,"草稿仅作者与任课教师可见");e.put("content",decode(e.remove("draft_json")));e.put("evidence",decode(e.remove("evidence_json")));
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
  if(!Set.of("doc","paragraph","heading","text","bulletList","orderedList","listItem","bold","italic","codeBlock","blockquote","hardBreak","horizontalRule","figure","mathBlock","callout","table","tableRow","tableCell","tableHeader").contains(type))throw fail(422,"正文含不支持的节点类型");
  boolean required=Set.of("paragraph","heading","figure","mathBlock").contains(type);
  var attrs=node.path("attrs");
  if(type.equals("figure")){
   if(!attrs.path("src").asText().matches("/api/media/[a-f0-9-]{36}")||attrs.path("alt").asText().length()>300||attrs.path("caption").asText().length()>1000||attrs.path("source").asText().length()>1000)throw fail(422,"图片引用或图注格式无效");
  }
  if(type.equals("mathBlock")&&(attrs.path("latex").asText().isBlank()||attrs.path("latex").asText().length()>2000))throw fail(422,"公式须为 1–2000 字符的 LaTeX");
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
  if(n.path("type").asText().equals("mathBlock"))b.append(n.path("attrs").path("latex").asText());for(var c:n.path("content"))b.append(text(c));return b.toString();}
 @Transactional public Map<String,Object> save(long eid,ApiController.DraftRequest req,long uid){var e=entry(eid);own(e,uid);s.student(s.id(e,"course_id"),uid);validateContent(req.content());for(String src:mediaSources(req.content())){if(s.db.queryForObject("SELECT COUNT(*) FROM entry_media WHERE id=? AND entry_id=?",Integer.class,src.substring("/api/media/".length()),eid)!=1)throw fail(422,"正文图片不属于当前作品，请重新上传");}if(req.evidence()==null||req.evidence().size()>30)throw fail(422,"每篇最多挂载 30 条证据");var ids=anchors(req.content());for(var v:req.evidence()){if(!ids.containsKey(v.paragraphId()))throw fail(422,"证据引用的段落不存在");if(!v.sourceUrl().isBlank()&&!v.sourceUrl().matches("https?://[^\\s]+"))throw fail(422,"证据链接必须使用 http 或 https");}
  int changed=s.db.update("UPDATE entry SET title=?,draft_json=?,evidence_json=?,revision=revision+1,updated_at=? WHERE id=? AND revision=?",req.title(),encode(req.content()),encode(req.evidence()),now(),eid,req.revision());if(changed!=1)throw fail(409,"草稿已被另一窗口修改。当前输入仍在本页，请先复制，再重新加载后合并");return getEntry(eid,uid);}
 @Transactional public Map<String,Object> submit(long eid,int revision,String key,long uid){var e=entry(eid);own(e,uid);s.student(s.id(e,"course_id"),uid);s.one("SELECT id FROM entry WHERE id=? FOR UPDATE",eid);e=entry(eid);
  var repeated=s.db.queryForList("SELECT id FROM submission WHERE entry_id=? AND idempotency_key=?",eid,key);if(!repeated.isEmpty())return submission(s.id(repeated.getFirst(),"id"));
  var rule=s.one("SELECT * FROM assignment WHERE id=? FOR UPDATE",e.get("assignment_id"));
  if(Boolean.TRUE.equals(rule.get("manually_closed")))throw fail(422,"教师已结束作业；已有草稿仍可保存，但不能提交新版本");
  if(((Number)e.get("revision")).intValue()!=revision)throw fail(409,"草稿版本已变化，请保存并刷新后再提交");if(!Instant.parse((String)rule.get("due_at")).isAfter(Instant.now()))throw fail(422,"作业已截止，本版尚不支持迟交");
  JsonNode doc=decode(e.get("draft_json"));validateContent(doc);if(text(doc).isBlank())throw fail(422,"请先填写正文");
  var latest=s.db.queryForList("SELECT id,content_hash FROM submission WHERE entry_id=? ORDER BY version_no DESC LIMIT 1",eid);
  if(!latest.isEmpty()&&s.db.queryForObject("SELECT COUNT(*) FROM evaluation WHERE submission_id=? AND active=TRUE",Integer.class,latest.getFirst().get("id"))==0)throw fail(422,"上一版本尚未完成教师评审，请等待反馈后修订");
  String hash;try{hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((e.get("title")+"\n"+e.get("draft_json")+"\n"+e.get("evidence_json")).getBytes(StandardCharsets.UTF_8)));}catch(Exception x){throw new IllegalStateException(x);}
  if(!latest.isEmpty()&&hash.equals(latest.getFirst().get("content_hash")))throw fail(422,"内容与证据均未变化，无需重复提交");
  int version=s.db.queryForObject("SELECT COUNT(*) FROM submission WHERE entry_id=?",Integer.class,eid)+1;
  long sid=s.insert("INSERT INTO submission(entry_id,version_no,title,content_json,content_hash,rubric_version,idempotency_key,created_at) VALUES(?,?,?,?,?,?,?,?)",eid,version,e.get("title"),e.get("draft_json"),hash,e.get("rubric_version"),key,now());
  s.db.update("UPDATE submission SET assignment_snapshot_json=? WHERE id=?",encode(AssignmentManagement.snapshot(rule)),sid);
  for(JsonNode v:decode(e.get("evidence_json")))s.db.update("INSERT INTO evidence(submission_id,paragraph_id,source_title,source_url,excerpt,claim_text,state) VALUES(?,?,?,?,?,?,'PENDING')",sid,v.path("paragraphId").asText(),v.path("sourceTitle").asText(),v.path("sourceUrl").asText(),v.path("excerpt").asText(),v.path("claim").asText());s.audit(uid,"SUBMISSION_CREATE","submission",sid);notices.teachers(s.id(e,"course_id"),"SUBMISSION","有新版本等待评审","submission",sid,uid);return submission(sid);}
 public Map<String,Object> getSubmission(long sid,long uid){var v=submission(sid);visible(v,uid);v.put("changes",changes(v));v.put("content",decode(v.remove("content_json")));Object task=v.remove("assignment_snapshot_json");v.put("assignmentSnapshot",task==null?null:decode(task));v.put("evaluation",getEvaluation(sid));v.put("feedback",feedback(sid));v.put("evidence",s.db.queryForList("SELECT * FROM evidence WHERE submission_id=?",sid));v.put("publication",s.db.queryForList("SELECT * FROM publication WHERE submission_id=?",sid));return v;}
 public Object getEvaluation(long sid){var rows=s.db.queryForList("SELECT g.*,u.display_name AS teacher_name FROM evaluation g JOIN app_user u ON u.id=g.teacher_id WHERE g.submission_id=? AND g.active=TRUE",sid);if(rows.isEmpty())return null;var g=rows.getFirst();g.put("details",decode(g.remove("details_json")));g.put("dimensions",decode(g.remove("dimensions_json")));return g;}
 public List<Map<String,Object>> feedback(long sid){var rows=s.db.queryForList("SELECT f.*,u.display_name AS teacher_name FROM feedback f JOIN app_user u ON u.id=f.teacher_id WHERE f.submission_id=? ORDER BY f.id",sid);for(var f:rows)f.put("actions",s.db.queryForList("SELECT a.*,u.display_name AS actor_name FROM feedback_action a JOIN app_user u ON u.id=a.user_id WHERE a.feedback_id=? ORDER BY a.id",f.get("id")));return rows;}
 public List<Map<String,Object>> queue(long cid,long uid){String role=s.role(cid,uid);if(!List.of("TEACHER","ASSISTANT").contains(role))throw fail(403,"批改队列仅教师或助教可见");return s.db.queryForList("SELECT v.id,v.title,v.version_no,v.created_at,e.author_id,u.display_name AS author_name,a.title AS assignment_title,g.q_final,d.updated_at AS review_draft_updated_at FROM submission v JOIN entry e ON e.id=v.entry_id JOIN app_user u ON u.id=e.author_id JOIN assignment a ON a.id=e.assignment_id LEFT JOIN evaluation g ON g.submission_id=v.id AND g.active=TRUE LEFT JOIN review_draft d ON d.submission_id=v.id AND d.teacher_id=? WHERE a.course_id=? ORDER BY v.created_at DESC",uid,cid);}
 @Transactional public Map<String,Object> evaluate(long sid,ApiController.EvaluationRequest req,long uid){var v=submission(sid);s.teacher(s.id(v,"course_id"),uid);s.one("SELECT id FROM submission WHERE id=? FOR UPDATE",sid);int current=s.db.queryForObject("SELECT COUNT(*) FROM evaluation WHERE submission_id=?",Integer.class,sid);if(req.expectedRevision()!=current)throw fail(409,"评价已被其他窗口更新，请重新加载后再评");
  var result=QualityCalculator.calculate(req.items(),req.gateReason());
  if(s.db.queryForObject("SELECT COUNT(*) FROM evidence WHERE submission_id=? AND state IN ('PENDING','DISPUTED')",Integer.class,sid)>0)throw fail(422,"有待核验或有争议的证据，请先完成核验再发布正式评分");
  s.db.update("UPDATE evaluation SET active=FALSE WHERE submission_id=? AND active=TRUE",sid);
  long gid=s.insert("INSERT INTO evaluation(submission_id,teacher_id,revision_no,details_json,dimensions_json,q_raw,q_final,gate_reason,summary,active,created_at) VALUES(?,?,?,?,?,?,?,?,?,TRUE,?)",sid,uid,current+1,encode(req.items()),encode(result.dimensions()),result.raw(),result.total(),req.gateReason(),req.summary(),now());
  // A regrade revokes public visibility until the author requests review again.
  s.db.update("UPDATE publication SET state='WITHDRAWN' WHERE submission_id=?",sid);s.audit(uid,"EVALUATION_PUBLISH","evaluation",gid);s.db.update("DELETE FROM review_draft WHERE submission_id=? AND teacher_id=?",sid,uid);notices.send(s.id(v,"author_id"),s.id(v,"course_id"),"EVALUATION","MEMBER","教师已发布 v"+v.get("version_no")+" 的正式评价","submission",sid,"",uid);return (Map<String,Object>)getEvaluation(sid);}
 @Transactional public Map<String,Object> addFeedback(long sid,ApiController.FeedbackRequest req,long uid){var v=submission(sid);s.teacher(s.id(v,"course_id"),uid);var a=anchors(decode(v.get("content_json")));if(!a.containsKey(req.paragraphId()))throw fail(422,"段落不存在");if(!QualityCalculator.CODES.contains(req.indicator()))throw fail(422,"指标编码不存在");long id=s.insert("INSERT INTO feedback(submission_id,teacher_id,paragraph_id,original_text,indicator_code,body,resolved,created_at) VALUES(?,?,?,?,?,?,FALSE,?)",sid,uid,req.paragraphId(),a.get(req.paragraphId()).substring(0,Math.min(4000,a.get(req.paragraphId()).length())),req.indicator(),req.body(),now());s.audit(uid,"FEEDBACK_CREATE","feedback",id);notices.send(s.id(v,"author_id"),s.id(v,"course_id"),"FEEDBACK","MEMBER","收到新的教师批注","submission",sid,req.paragraphId(),uid);return s.one("SELECT * FROM feedback WHERE id=?",id);}
 @Transactional public void action(long fid,String action,String body,long uid){var f=s.one("SELECT * FROM feedback WHERE id=?",fid);var v=submission(s.id(f,"submission_id"));String role=s.role(s.id(v,"course_id"),uid);boolean teacher=role.equals("TEACHER");if(!teacher&&s.id(v,"author_id")!=uid)throw fail(403,"无权处理该反馈");if(!Set.of("REPLY","ADOPT","IGNORE","RESOLVE","REOPEN").contains(action))throw fail(422,"未知反馈操作");if(!teacher&&Set.of("RESOLVE","REOPEN").contains(action))throw fail(403,"只有教师可以确认问题解决");if((action.equals("IGNORE")||action.equals("REPLY"))&&body.isBlank())throw fail(422,"请填写回应或忽略理由");s.db.update("INSERT INTO feedback_action(feedback_id,user_id,action_type,body,created_at) VALUES(?,?,?,?,?)",fid,uid,action,body,now());if(teacher&&Set.of("RESOLVE","REOPEN").contains(action))s.db.update("UPDATE feedback SET resolved=? WHERE id=?",action.equals("RESOLVE"),fid);s.audit(uid,"FEEDBACK_"+action,"feedback",fid);if(teacher)notices.send(s.id(v,"author_id"),s.id(v,"course_id"),"FEEDBACK","MEMBER",action.equals("RESOLVE")?"教师已确认反馈问题解决":"教师更新了批注回应","submission",s.id(v,"id"),(String)f.get("paragraph_id"),uid);else notices.teachers(s.id(v,"course_id"),"FEEDBACK_REPLY","学生回应了教师批注","submission",s.id(v,"id"),uid);}
 public List<Map<String,Object>> evidenceTasks(long cid,long uid){s.role(cid,uid);return s.db.queryForList("SELECT x.*,e.author_id,u.display_name AS author_name,v.title,v.version_no FROM evidence x JOIN submission v ON v.id=x.submission_id JOIN entry e ON e.id=v.entry_id JOIN assignment a ON a.id=e.assignment_id JOIN app_user u ON u.id=e.author_id WHERE a.course_id=? ORDER BY x.id DESC",cid);}
 @Transactional public void verify(long id,String verdict,String reason,long uid){var ev=s.one("SELECT * FROM evidence WHERE id=? FOR UPDATE",id);var v=submission(s.id(ev,"submission_id"));String role=s.role(s.id(v,"course_id"),uid);boolean teacher=role.equals("TEACHER");if(s.id(v,"author_id")==uid)throw fail(403,"作者不能核验自己的证据");if(!Set.of("PASS","FAIL").contains(verdict))throw fail(422,"核验结论不正确");var prior=s.db.queryForList("SELECT * FROM verification WHERE evidence_id=? ORDER BY id",id);if(!teacher&&prior.stream().anyMatch(x->s.id(x,"verifier_id")==uid))throw fail(409,"你已核验此证据；需要改判请联系教师");if(!teacher&&prior.stream().anyMatch(x->x.get("verifier_id").equals(v.get("author_id"))))throw fail(403,"核验权限错误");
  if(!teacher&&prior.stream().anyMatch(x->{var m=s.db.queryForList("SELECT role FROM course_member WHERE course_id=? AND user_id=?",v.get("course_id"),x.get("verifier_id"));return !m.isEmpty()&&m.getFirst().get("role").equals("TEACHER");}))throw fail(422,"教师已裁定此证据");
  if(s.db.queryForObject("SELECT COUNT(*) FROM evaluation WHERE submission_id=? AND active=TRUE",Integer.class,v.get("id"))>0)throw fail(422,"该版已发布评分；核验改判需下一版复核，本版暂不开放改判");
  String state=teacher?verdict:prior.stream().anyMatch(x->!verdict.equals(x.get("verdict")))?"DISPUTED":verdict;
  s.db.update("INSERT INTO verification(evidence_id,verifier_id,verdict,reason,created_at) VALUES(?,?,?,?,?)",id,uid,verdict,reason,now());s.db.update("UPDATE evidence SET state=? WHERE id=?",state,id);s.audit(uid,"EVIDENCE_"+verdict,"evidence",id);notices.send(s.id(v,"author_id"),s.id(v,"course_id"),"EVIDENCE","MEMBER","证据核验结果已更新","evidence",id,"",uid);if(state.equals("DISPUTED"))notices.teachers(s.id(v,"course_id"),"EVIDENCE_DISPUTED","证据存在争议，等待教师裁定","evidence",id,uid);}
 public List<Map<String,Object>> verifications(long eid,long uid){var ev=s.one("SELECT * FROM evidence WHERE id=?",eid);var v=submission(s.id(ev,"submission_id"));s.role(s.id(v,"course_id"),uid);return s.db.queryForList("SELECT x.*,u.display_name AS verifier_name FROM verification x JOIN app_user u ON u.id=x.verifier_id WHERE x.evidence_id=? ORDER BY x.id",eid);}
 @Transactional public void requestPublication(long sid,long uid){var v=submission(sid);own(v,uid);s.one("SELECT id FROM submission WHERE id=? FOR UPDATE",sid);if(getEvaluation(sid)==null)throw fail(422,"请先等待教师发布评分");var old=s.db.queryForList("SELECT id,state FROM publication WHERE submission_id=?",sid);if(old.isEmpty())s.db.update("INSERT INTO publication(submission_id,author_id,state,requested_at) VALUES(?,?,'PENDING',?)",sid,uid,now());else {if(!old.getFirst().get("state").equals("WITHDRAWN"))throw fail(409,"该版已申请或已发布");s.db.update("UPDATE publication SET state='PENDING',requested_at=?,reviewed_at=NULL,reviewer_id=NULL WHERE submission_id=?",now(),sid);}s.audit(uid,"PUBLICATION_REQUEST","submission",sid);notices.teachers(s.id(v,"course_id"),"PUBLICATION_REQUEST","有新的课程内发布申请","publication",sid,uid);}
 @Transactional public void publicationAction(long id,String action,long uid){var pub=s.one("SELECT * FROM publication WHERE id=?",id);s.one("SELECT id FROM submission WHERE id=? FOR UPDATE",pub.get("submission_id"));pub=s.one("SELECT * FROM publication WHERE id=? FOR UPDATE",id);var v=submission(s.id(pub,"submission_id"));String role=s.role(s.id(v,"course_id"),uid);if(!Set.of("APPROVE","WITHDRAW").contains(action))throw fail(422,"未知操作");if(action.equals("APPROVE")){s.teacher(s.id(v,"course_id"),uid);if(!"PENDING".equals(pub.get("state")))throw fail(409,"该申请已处理");if(getEvaluation(s.id(v,"id"))==null)throw fail(422,"该版无有效教师评价");}else if(!role.equals("TEACHER")&&s.id(v,"author_id")!=uid)throw fail(403,"只有作者或教师可撤回");s.db.update("UPDATE publication SET state=?,reviewed_at=?,reviewer_id=? WHERE id=?",action.equals("APPROVE")?"PUBLISHED":"WITHDRAWN",now(),uid,id);s.audit(uid,"PUBLICATION_"+action,"publication",id);notices.send(s.id(v,"author_id"),s.id(v,"course_id"),"PUBLICATION_RESULT","MEMBER",action.equals("APPROVE")?"指定版本已获准在课程内展示":"指定版本的社区展示已撤回","submission",s.id(v,"id"),"",uid);}
 public List<Map<String,Object>> publications(long cid,long uid,boolean pending){if(pending)s.teacher(cid,uid);else s.role(cid,uid);var rows=s.db.queryForList("SELECT p.*,v.title,v.version_no,v.content_json,u.display_name AS author_name,g.q_final,g.summary FROM publication p JOIN submission v ON v.id=p.submission_id JOIN entry e ON e.id=v.entry_id JOIN app_user u ON u.id=e.author_id JOIN assignment a ON a.id=e.assignment_id JOIN evaluation g ON g.submission_id=v.id AND g.active=TRUE WHERE a.course_id=? AND p.state=? ORDER BY p.id DESC",cid,pending?"PENDING":"PUBLISHED");for(var row:rows){var content=decode(row.remove("content_json"));row.put("cover",mediaSources(content).stream().findFirst().orElse(""));}return rows;}
}
