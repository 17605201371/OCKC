package cn.course.community;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","app.seed=true","app.seed-password=TestOnly!2026Long"})
@AutoConfigureMockMvc @Transactional
class ApiIntegrationTest {
 @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired Store s;
 @Test void anonymousDenied()throws Exception{mvc.perform(get("/api/courses")).andExpect(status().isUnauthorized());}
 @Test void csrfRequired()throws Exception{mvc.perform(post("/api/assignments/1/entries").with(user("student@example.test"))).andExpect(status().isForbidden());}
 @Test void realLogin()throws Exception{mvc.perform(post("/api/auth/login").with(csrf()).param("username","student@example.test").param("password","TestOnly!2026Long")).andExpect(status().isOk());}
 @Test void wrongPassword()throws Exception{mvc.perform(post("/api/auth/login").with(csrf()).param("username","student@example.test").param("password","incorrect")).andExpect(status().isUnauthorized());}
 @Test void crossCourseDenied()throws Exception{mvc.perform(get("/api/courses/1/assignments").with(user("outsider@example.test"))).andExpect(status().isForbidden());}
 @Test void peerDraftDenied()throws Exception{mvc.perform(get("/api/entries/1").with(user("peer@example.test"))).andExpect(status().isForbidden());}
 @Test void studentCannotGrade()throws Exception{mvc.perform(post("/api/submissions/1/evaluations").with(user("student@example.test")).with(csrf()).contentType("application/json").content("{\"items\":[],\"gateReason\":\"\",\"summary\":\"test\",\"expectedRevision\":1}")).andExpect(status().isForbidden());}
 @Test void saveConflict()throws Exception{var e=json.readTree(mvc.perform(get("/api/entries/1").with(user("student@example.test"))).andReturn().getResponse().getContentAsString());var body=new HashMap<String,Object>();body.put("title","修订标题");body.put("content",e.get("content"));body.put("evidence",e.get("evidence"));body.put("revision",0);mvc.perform(put("/api/entries/1/draft").with(user("student@example.test")).with(csrf()).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isConflict());}
 @Test void authorCannotVerify()throws Exception{mvc.perform(post("/api/evidence/1/verifications").with(user("student@example.test")).with(csrf()).contentType("application/json").content("{\"verdict\":\"PASS\",\"reason\":\"我核验自己\"}")).andExpect(status().isForbidden());}
 @Test void submitIdempotent()throws Exception{long eid=((Number)s.one("SELECT entry_id FROM submission WHERE id=1").get("entry_id")).longValue();mvc.perform(post("/api/entries/"+eid+"/submissions").with(user("student@example.test")).with(csrf()).header("Idempotency-Key","seed-version-one").contentType("application/json").content("{\"revision\":0}")).andExpect(status().isOk()).andExpect(jsonPath("version_no").value(1));}
 @Test void publicationIsVersionPinned()throws Exception{mvc.perform(get("/api/courses/1/publications").with(user("peer@example.test"))).andExpect(status().isOk()).andExpect(jsonPath("$[0].version_no").value(1));}
 @Test void unapprovedSubmissionDeniedToPeer()throws Exception{mvc.perform(get("/api/submissions/2").with(user("student@example.test"))).andExpect(status().isForbidden());}
 @Test void draftSaveAndImmutableVersion()throws Exception{var e=json.readTree(mvc.perform(get("/api/entries/1").with(user("student@example.test"))).andReturn().getResponse().getContentAsString());var body=Map.of("title","已经修订","content",e.get("content"),"evidence",e.get("evidence"),"revision",e.get("revision"));mvc.perform(put("/api/entries/1/draft").with(user("student@example.test")).with(csrf()).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isOk());mvc.perform(get("/api/submissions/1").with(user("student@example.test"))).andExpect(jsonPath("title").value("光反应之后，物质去了哪里？"));}
}
