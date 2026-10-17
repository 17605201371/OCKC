package cn.course.community;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import java.security.Principal;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.web.csrf.CsrfToken;
@RestController @RequestMapping("/api")
public class ApiController {
 final Store s;final CommunityService service;
 ApiController(Store s,CommunityService service){this.s=s;this.service=service;}
 long uid(Principal p){return s.uid(p.getName());}
 public record CourseRequest(@NotBlank @Size(max=120) String title,@NotNull @Size(max=1000) String description){}
 public record MemberRequest(@NotBlank @Email @Size(max=160) String email,@Pattern(regexp="STUDENT|ASSISTANT") String role){}
 public record AssignmentRequest(@NotBlank @Size(max=160) String title,@NotBlank @Size(max=4000) String requirements,@NotBlank String dueAt){}
 public record EvidenceInput(@NotBlank @Size(max=80) String paragraphId,@NotBlank @Size(max=300) String sourceTitle,@NotNull @Size(max=2000) String sourceUrl,@NotBlank @Size(max=4000) String excerpt,@NotBlank @Size(max=2000) String claim){}
 public record DraftRequest(@NotBlank @Size(max=160) String title,@NotNull JsonNode content,@Min(0) int revision,@NotNull @Size(max=30) List<@Valid EvidenceInput> evidence){}
 public record SubmitRequest(@Min(0) int revision){}
 public record EvaluationRequest(@NotNull List<QualityCalculator.Item> items,@NotNull @Size(max=1000) String gateReason,@NotBlank @Size(max=2000) String summary,@Min(0) int expectedRevision){}
 public record FeedbackRequest(@NotBlank @Size(max=80) String paragraphId,@NotBlank String indicator,@NotBlank @Size(max=2000) String body){}
 public record ActionRequest(@NotBlank String action,@NotNull @Size(max=2000) String body){}
 public record VerifyRequest(@NotBlank String verdict,@NotBlank @Size(max=2000) String reason){}
 @GetMapping("/auth/csrf") public Map<String,String> csrf(CsrfToken token){return Map.of("token",token.getToken(),"headerName",token.getHeaderName());}
 @GetMapping("/auth/me") public Map<String,Object> me(Principal p){return s.one("SELECT id,email,display_name FROM app_user WHERE email=?",p.getName());}
 @GetMapping("/courses") public Object courses(Principal p){return service.courses(uid(p));}
 @PostMapping("/courses") public Object createCourse(@Valid @RequestBody CourseRequest r,Principal p){long u=uid(p);if(s.db.queryForObject("SELECT COUNT(*) FROM course_member WHERE user_id=? AND role='TEACHER'",Integer.class,u)==0)throw ApiError.fail(403,"创建课程需要已有教师身份");return service.createCourse(r.title(),r.description(),u);}
 @PostMapping("/courses/{cid}/members") public Object member(@PathVariable long cid,@Valid @RequestBody MemberRequest r,Principal p){service.addMember(cid,r.email(),r.role(),uid(p));return Map.of("ok",true);}
 @GetMapping("/courses/{cid}/progress") public Object progress(@PathVariable long cid,Principal p){return service.progress(cid,uid(p));}
 @GetMapping("/courses/{cid}/assignments") public Object assignments(@PathVariable long cid,Principal p){return service.assignments(cid,uid(p));}
 @PostMapping("/courses/{cid}/assignments") public Object createAssignment(@PathVariable long cid,@Valid @RequestBody AssignmentRequest r,Principal p){return service.createAssignment(cid,r.title(),r.requirements(),r.dueAt(),uid(p));}
 @PostMapping("/assignments/{aid}/entries") public Object start(@PathVariable long aid,Principal p){return service.start(aid,uid(p));}
 @GetMapping("/entries/{eid}") public Object getEntry(@PathVariable long eid,Principal p){return service.getEntry(eid,uid(p));}
 @PutMapping("/entries/{eid}/draft") public Object save(@PathVariable long eid,@Valid @RequestBody DraftRequest r,Principal p){return service.save(eid,r,uid(p));}
 @PostMapping("/entries/{eid}/submissions") public Object submit(@PathVariable long eid,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody SubmitRequest r,Principal p){if(!key.matches("[a-zA-Z0-9_-]{8,80}"))throw ApiError.fail(422,"幂等键格式不正确");return service.submit(eid,r.revision(),key,uid(p));}
 @GetMapping("/submissions/{sid}") public Object submission(@PathVariable long sid,Principal p){return service.getSubmission(sid,uid(p));}
 @GetMapping("/courses/{cid}/review-queue") public Object queue(@PathVariable long cid,Principal p){return service.queue(cid,uid(p));}
 @PostMapping("/submissions/{sid}/evaluations") public Object evaluate(@PathVariable long sid,@Valid @RequestBody EvaluationRequest r,Principal p){return service.evaluate(sid,r,uid(p));}
 @PostMapping("/submissions/{sid}/feedback") public Object feedback(@PathVariable long sid,@Valid @RequestBody FeedbackRequest r,Principal p){return service.addFeedback(sid,r,uid(p));}
 @PostMapping("/feedback/{fid}/actions") public Object action(@PathVariable long fid,@Valid @RequestBody ActionRequest r,Principal p){service.action(fid,r.action(),r.body(),uid(p));return Map.of("ok",true);}
 @GetMapping("/courses/{cid}/evidence") public Object evidence(@PathVariable long cid,Principal p){return service.evidenceTasks(cid,uid(p));}
 @GetMapping("/evidence/{id}/verifications") public Object verifications(@PathVariable long id,Principal p){return service.verifications(id,uid(p));}
 @PostMapping("/evidence/{id}/verifications") public Object verify(@PathVariable long id,@Valid @RequestBody VerifyRequest r,Principal p){service.verify(id,r.verdict(),r.reason(),uid(p));return Map.of("ok",true);}
 @PostMapping("/submissions/{sid}/publication") public Object requestPub(@PathVariable long sid,Principal p){service.requestPublication(sid,uid(p));return Map.of("ok",true);}
 @GetMapping("/courses/{cid}/publications") public Object pubs(@PathVariable long cid,@RequestParam(defaultValue="false") boolean pending,Principal p){return service.publications(cid,uid(p),pending);}
 @PostMapping("/publications/{id}/actions") public Object pubAction(@PathVariable long id,@Valid @RequestBody ActionRequest r,Principal p){service.publicationAction(id,r.action(),uid(p));return Map.of("ok",true);}
}
