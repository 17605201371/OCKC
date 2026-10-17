package cn.course.community;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:media;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","app.seed=true","app.seed-password=TestOnly!2026Long"})
@AutoConfigureMockMvc @Transactional
class MediaIntegrationTest {
 @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired Store s;
 MockMultipartFile image()throws Exception{var bytes=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(64,32,BufferedImage.TYPE_INT_RGB),"png",bytes);return new MockMultipartFile("file","diagram.png","image/png",bytes.toByteArray());}
 String upload()throws Exception{return json.readTree(mvc.perform(multipart("/api/entries/1/media").file(image()).with(user("student@example.test")).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("width").value(64)).andReturn().getResponse().getContentAsString()).path("src").asText();}
 @Test void uploadAndAuthorTeacherRead()throws Exception{String src=upload();for(String who:List.of("student@example.test","teacher@example.test"))mvc.perform(get(src).with(user(who))).andExpect(status().isOk()).andExpect(content().contentType("image/png")).andExpect(header().string("Cache-Control","private, no-store"));}
 @Test void mediaRequiresSessionAndCoursePermission()throws Exception{String src=upload();mvc.perform(get(src)).andExpect(status().isUnauthorized());mvc.perform(get(src).with(user("outsider@example.test"))).andExpect(status().isForbidden());mvc.perform(get(src).with(user("peer@example.test"))).andExpect(status().isForbidden());}
 @Test void uploadRequiresOwnerAndCsrf()throws Exception{mvc.perform(multipart("/api/entries/1/media").file(image()).with(user("student@example.test"))).andExpect(status().isForbidden());mvc.perform(multipart("/api/entries/1/media").file(image()).with(user("peer@example.test")).with(csrf())).andExpect(status().isForbidden());}
 @Test void svgAndSpoofedImagesRejected()throws Exception{for(String data:List.of("<svg><script>alert(1)</script></svg>","not an image"))mvc.perform(multipart("/api/entries/1/media").file(new MockMultipartFile("file","bad.png","image/png",data.getBytes())).with(user("student@example.test")).with(csrf())).andExpect(status().isUnprocessableEntity());}
 @Test void largeFileRejected()throws Exception{mvc.perform(multipart("/api/entries/1/media").file(new MockMultipartFile("file","big.png","image/png",new byte[5*1024*1024+1])).with(user("student@example.test")).with(csrf())).andExpect(status().isUnprocessableEntity());}
 @Test void publishedVersionDoesNotExposeNewDraftImages()throws Exception{String src=upload();s.db.update("UPDATE submission SET content_json=? WHERE id=1","{\"type\":\"doc\",\"content\":[{\"type\":\"figure\",\"attrs\":{\"src\":\""+src+"\",\"blockId\":\"fig\"}}]}");mvc.perform(get(src).with(user("peer@example.test"))).andExpect(status().isOk());String newSrc=upload();mvc.perform(get(newSrc).with(user("peer@example.test"))).andExpect(status().isForbidden());s.db.update("UPDATE publication SET state='WITHDRAWN' WHERE submission_id=1");mvc.perform(get(src).with(user("peer@example.test"))).andExpect(status().isForbidden());}
 @Test void inventedMediaCannotBeSaved()throws Exception{var e=json.readTree(mvc.perform(get("/api/entries/1").with(user("student@example.test"))).andReturn().getResponse().getContentAsString());var doc=json.readTree("{\"type\":\"doc\",\"content\":[{\"type\":\"figure\",\"attrs\":{\"src\":\"/api/media/00000000-0000-0000-0000-000000000000\",\"blockId\":\"fig1\"}}]}");mvc.perform(put("/api/entries/1/draft").with(user("student@example.test")).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("title","test","revision",e.get("revision"),"content",doc,"evidence",List.of())))).andExpect(status().isUnprocessableEntity());}
 @Test void studentProgressOnlyOwnEntries()throws Exception{mvc.perform(get("/api/courses/1/progress").with(user("student@example.test"))).andExpect(status().isOk()).andExpect(jsonPath("staff").value(false)).andExpect(jsonPath("entries.length()").value(1)).andExpect(jsonPath("entries[0].author_name").value("林同学")).andExpect(jsonPath("entries[0].evidence_passed").value(1));}
 @Test void teacherProgressIncludesCourseEntries()throws Exception{mvc.perform(get("/api/courses/1/progress").with(user("teacher@example.test"))).andExpect(status().isOk()).andExpect(jsonPath("staff").value(true)).andExpect(jsonPath("entries.length()").value(2));}
 @Test void outsiderProgressDenied()throws Exception{mvc.perform(get("/api/courses/1/progress").with(user("outsider@example.test"))).andExpect(status().isForbidden());}
}
