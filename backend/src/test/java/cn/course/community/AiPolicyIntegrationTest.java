package cn.course.community;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:aipolicy;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","app.seed=true","app.seed-password=TestOnly!2026Long"})
@AutoConfigureMockMvc @Transactional
class AiPolicyIntegrationTest {
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired Store s;
 void policy(boolean enabled)throws Exception { mvc.perform(put("/api/assignments/1/ai-policy").with(user("teacher@example.test")).with(csrf()).contentType("application/json").content("{\"enabled\":"+enabled+"}")).andExpect(status().isOk()).andExpect(jsonPath("ai_feedback_enabled").value(enabled)); }
 @Test void existingAssignmentsDefaultDisabled()throws Exception {mvc.perform(get("/api/courses/1/assignments").with(user("student@example.test"))).andExpect(status().isOk()).andExpect(jsonPath("[0].ai_feedback_enabled").value(false));mvc.perform(get("/api/entries/1/ai-feedback").with(user("student@example.test"))).andExpect(jsonPath("enabled").value(false)).andExpect(jsonPath("status").value("DISABLED")).andExpect(jsonPath("suggestions.length()").value(0));}
 @Test void teacherPolicyPersistsAndRevokesImmediately()throws Exception {policy(true);mvc.perform(get("/api/entries/1/ai-feedback").with(user("student@example.test"))).andExpect(status().isOk()).andExpect(jsonPath("enabled").value(true)).andExpect(jsonPath("status").value("NOT_CONFIGURED")).andExpect(jsonPath("formalScore").value(false));policy(false);mvc.perform(get("/api/entries/1/ai-feedback").with(user("student@example.test"))).andExpect(jsonPath("enabled").value(false)).andExpect(jsonPath("suggestions.length()").value(0)); assertEquals(2,s.db.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action_type IN ('ASSIGNMENT_AI_ENABLE','ASSIGNMENT_AI_DISABLE')",Integer.class));}
 @Test void studentsCannotToggle()throws Exception {for(String who:List.of("student","peer","outsider"))mvc.perform(put("/api/assignments/1/ai-policy").with(user(who+"@example.test")).with(csrf()).contentType("application/json").content("{\"enabled\":true}")).andExpect(status().isForbidden());}
 @Test void assistantCannotToggleOrReadPrivateAiFeedback()throws Exception {long uid=s.uid("peer@example.test");s.db.update("UPDATE course_member SET role='ASSISTANT' WHERE course_id=1 AND user_id=?",uid);mvc.perform(put("/api/assignments/1/ai-policy").with(user("peer@example.test")).with(csrf()).contentType("application/json").content("{\"enabled\":true}")).andExpect(status().isForbidden());mvc.perform(get("/api/entries/1/ai-feedback").with(user("peer@example.test"))).andExpect(status().isForbidden());}
 @Test void csrfAndAuthenticationRequired()throws Exception {mvc.perform(put("/api/assignments/1/ai-policy").with(user("teacher@example.test")).contentType("application/json").content("{\"enabled\":true}")).andExpect(status().isForbidden());mvc.perform(get("/api/entries/1/ai-feedback")).andExpect(status().isUnauthorized());}
 @Test void nullPolicyRejected()throws Exception {mvc.perform(put("/api/assignments/1/ai-policy").with(user("teacher@example.test")).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isBadRequest());}
 @Test void peerAndOutsiderCannotReadEvenWithPublishedEntry()throws Exception {policy(true);for(String who:List.of("peer","outsider"))mvc.perform(get("/api/entries/1/ai-feedback").with(user(who+"@example.test"))).andExpect(status().isForbidden());mvc.perform(get("/api/entries/1/ai-feedback").with(user("teacher@example.test"))).andExpect(status().isOk());}
 @Test void openingDoesNotSendOrInventAiFeedback()throws Exception {policy(true);String raw=mvc.perform(get("/api/entries/1/ai-feedback").with(user("student@example.test"))).andReturn().getResponse().getContentAsString();JsonNode d=json.readTree(raw);assertEquals(0,d.path("suggestions").size());assertFalse(d.has("content"));assertFalse(d.has("score"));assertFalse(d.has("draft_json"));}
 @Test void publishingSupportsExplicitOptInAndDefaultsOff()throws Exception {for(boolean enabled:List.of(false,true)){var data=new HashMap<String,Object>();data.put("title","AI 开关功能测试");data.put("requirements","测试发布策略，不代表真实课堂作业");data.put("dueAt",Instant.now().plusSeconds(86400).toString());if(enabled)data.put("aiFeedbackEnabled",true);mvc.perform(post("/api/courses/1/assignments").with(user("teacher@example.test")).with(csrf()).contentType("application/json").content(json.writeValueAsString(data))).andExpect(status().isOk()).andExpect(jsonPath("ai_feedback_enabled").value(enabled));}}
 @Test void policyChangesOnlyTargetAssignmentAndNeverGrade()throws Exception {var before=s.one("SELECT q_final,summary FROM evaluation WHERE submission_id=1 AND active=TRUE");long aid=s.insert("INSERT INTO assignment(course_id,title,requirements,due_at,rubric_version,created_at) VALUES(1,'second','test',?,'four-dim-v1',?)",Instant.now().plusSeconds(86400).toString(),Store.now());policy(true);assertFalse((Boolean)s.one("SELECT ai_feedback_enabled FROM assignment WHERE id=?",aid).get("ai_feedback_enabled"));assertEquals(before,s.one("SELECT q_final,summary FROM evaluation WHERE submission_id=1 AND active=TRUE"));}
}
