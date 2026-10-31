package cn.course.community;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:today;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","app.seed=true","app.seed-password=TestOnly!2026Long"})
@AutoConfigureMockMvc @Transactional
class TodayServiceTest {
 @Autowired MockMvc mvc;@Autowired Store s;@Autowired TodayService t;
 long uid(String n){return s.uid(n+"@example.test");}
 Map<String,Object> group(String who,String key){
  @SuppressWarnings("unchecked") var groups=(List<Map<String,Object>>)t.today(1,uid(who)).get("groups");
  return groups.stream().filter(g->g.get("key").equals(key)).findFirst().orElse(null);
 }
 int count(String who,String key){var g=group(who,key);return g==null?-1:((Number)g.get("count")).intValue();}
 List<String> keys(String who){@SuppressWarnings("unchecked") var groups=(List<Map<String,Object>>)t.today(1,uid(who)).get("groups");return groups.stream().map(g->(String)g.get("key")).toList();}
 long entryOf(long sid){return s.db.queryForObject("SELECT entry_id FROM submission WHERE id=?",Long.class,sid);}
 long authorOf(long sid){return s.db.queryForObject("SELECT e.author_id FROM submission v JOIN entry e ON e.id=v.entry_id WHERE v.id=?",Long.class,sid);}
 long newVersion(long sid){int no=s.db.queryForObject("SELECT MAX(version_no) FROM submission WHERE entry_id=?",Integer.class,entryOf(sid))+1;return s.insert("INSERT INTO submission(entry_id,version_no,title,content_json,content_hash,rubric_version,idempotency_key,created_at) SELECT entry_id,?,title,content_json,content_hash,rubric_version,?,? FROM submission WHERE id=?",no,"today-"+sid+"-"+no,Store.now(),sid);}
 long feedback(long sid){return s.insert("INSERT INTO feedback(submission_id,teacher_id,paragraph_id,original_text,indicator_code,body,resolved,created_at) VALUES(?,?,'p1','原文','S1','今日待办验收批注',FALSE,?)",sid,uid("teacher"),Store.now());}
 void action(long fid,long user,String type){s.insert("INSERT INTO feedback_action(feedback_id,user_id,action_type,body,created_at) VALUES(?,?,?,'今日待办验收',?)",fid,user,type,Store.now());}
 long assignmentDueIn(long hours){return s.insert("INSERT INTO assignment(course_id,title,requirements,due_at,rubric_version,created_at) VALUES(1,'今日待办验收作业','仅用于测试',?,'four-dim-v1',?)",Instant.now().plusSeconds(hours*3600).toString(),Store.now());}

 @Test void groupsDependOnRole(){
  assertEquals(List.of("review","responses","publications","disputes","due"),keys("teacher"));
  assertEquals(List.of("feedback","due","verify"),keys("student"));
  s.db.update("UPDATE course_member SET role='ASSISTANT' WHERE course_id=1 AND user_id=?",uid("peer"));
  assertEquals(List.of("review","due"),keys("peer"));
 }
 @Test void nonMembersAreRejected()throws Exception{
  assertThrows(Exception.class,()->t.today(1,uid("outsider")));
  mvc.perform(get("/api/courses/1/today")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/courses/1/today").with(user("teacher@example.test"))).andExpect(status().isOk());
 }
 @Test void ungradedLatestVersionIsPendingUntilGraded(){
  int before=count("teacher","review");long v=newVersion(1);
  assertEquals(before+1,count("teacher","review"));
  s.db.update("INSERT INTO evaluation(submission_id,teacher_id,revision_no,details_json,dimensions_json,q_raw,q_final,gate_reason,summary,active,created_at) VALUES(?,?,1,'[]','{}',80,80,'','今日待办验收',TRUE,?)",v,uid("teacher"),Store.now());
  assertEquals(before,count("teacher","review"));
 }
 @Test void studentResponseWaitsForTeacherThenClears(){
  long sid=1,fid=feedback(sid);int before=count("teacher","responses");
  assertEquals(before,count("teacher","responses"),"a fresh comment without a reply is not the teacher's to-do");
  action(fid,authorOf(sid),"ADOPT");assertEquals(before+1,count("teacher","responses"));
  action(fid,uid("teacher"),"REPLY");assertEquals(before,count("teacher","responses"),"the teacher already followed up");
  action(fid,authorOf(sid),"REPLY");assertEquals(before+1,count("teacher","responses"));
  s.db.update("UPDATE feedback SET resolved=TRUE WHERE id=?",fid);assertEquals(before,count("teacher","responses"));
 }
 @Test void studentFeedbackClearsOnceTheyRespond(){
  long sid=1;String author=s.db.queryForObject("SELECT email FROM app_user WHERE id=?",String.class,authorOf(sid)).replace("@example.test","");
  int before=count(author,"feedback");long fid=feedback(sid);
  assertEquals(before+1,count(author,"feedback"));
  action(fid,authorOf(sid),"ADOPT");assertEquals(before,count(author,"feedback"));
  action(fid,uid("teacher"),"REPLY");assertEquals(before+1,count(author,"feedback"),"a new teacher reply needs the student again");
 }
 @Test void dueSoonListsOnlyUnsubmittedWorkWithinTwoDays(){
  long soon=assignmentDueIn(10),later=assignmentDueIn(24*5);
  var due=group("student","due");
  assertTrue(((List<?>)due.get("items")).stream().anyMatch(r->((Map<?,?>)r).get("assignment_id").equals(soon)));
  assertTrue(((List<?>)due.get("items")).stream().noneMatch(r->((Map<?,?>)r).get("assignment_id").equals(later)));
  var staff=group("teacher","due");var row=(Map<?,?>)((List<?>)staff.get("items")).stream().filter(r->((Map<?,?>)r).get("assignment_id").equals(soon)).findFirst().orElseThrow();
  int students=((Number)row.get("students")).intValue();assertEquals(students,((Number)row.get("missing")).intValue());
  long eid=s.insert("INSERT INTO entry(assignment_id,author_id,title,draft_json,evidence_json,revision,updated_at) VALUES(?,?,'今日待办验收','{\"type\":\"doc\",\"content\":[]}','[]',0,?)",soon,uid("student"),Store.now());
  s.insert("INSERT INTO submission(entry_id,version_no,title,content_json,content_hash,rubric_version,idempotency_key,created_at) SELECT ?,1,title,content_json,content_hash,rubric_version,'today-due',? FROM submission WHERE id=1",eid,Store.now());
  assertTrue(((List<?>)group("student","due").get("items")).stream().noneMatch(r->((Map<?,?>)r).get("assignment_id").equals(soon)));
  var after=(Map<?,?>)((List<?>)group("teacher","due").get("items")).stream().filter(r->((Map<?,?>)r).get("assignment_id").equals(soon)).findFirst().orElse(null);
  if(students>1)assertEquals(students-1,((Number)after.get("missing")).intValue());else assertNull(after);
 }
}
