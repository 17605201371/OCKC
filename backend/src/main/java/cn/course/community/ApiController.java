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
 public record MemberRequest(@NotBlank @Email @Size(max=160) String email,@NotNull @Pattern(regexp="STUDENT|ASSISTANT") String role,@Min(0) Integer revision,@Size(max=1000) String reason){}
 public record AssignmentRequest(@NotBlank @Size(max=160) String title,@NotBlank @Size(max=4000) String requirements,@NotBlank String dueAt,Boolean aiFeedbackEnabled,@Min(0) Long rubricId,@Pattern(regexp="POST|STEP") String kind,@Pattern(regexp="REQUIRED|DISPUTED|OFF") String evidencePolicy){}
 public record AiPolicyRequest(@NotNull Boolean enabled){}
 public record EvidenceInput(@NotBlank @Size(max=80) String paragraphId,@NotBlank @Size(max=300) String sourceTitle,@NotNull @Size(max=2000) String sourceUrl,@NotBlank @Size(max=4000) String excerpt,@NotBlank @Size(max=2000) String claim){}
 public record DraftRequest(@NotBlank @Size(max=160) String title,@NotNull JsonNode content,@Min(0) int revision,@NotNull @Size(max=30) List<@Valid EvidenceInput> evidence){}
 public record SubmitRequest(@Min(0) int revision){}
 public record EvaluationRequest(@NotNull List<QualityCalculator.Item> items,@NotNull @Size(max=1000) String gateReason,@NotBlank @Size(max=2000) String summary,@Min(0) int expectedRevision,@Size(max=40) List<StepScore> stepScores){
  // 兼容不带步骤分的旧调用（知识帖作业、演示数据与既有测试）。
  public EvaluationRequest(List<QualityCalculator.Item> items,String gateReason,String summary,int expectedRevision){this(items,gateReason,summary,expectedRevision,null);}
 }
 // 步骤分：一步一条，score/max 由教师填写，tags 为问题类型标签（见 CommunityService.STEP_TAGS）。
 public record StepScore(@Min(0) int index,@Size(max=200) String title,@DecimalMin("0") java.math.BigDecimal score,@DecimalMin("0.01") java.math.BigDecimal max,@Size(max=4) List<@Size(max=20) String> tags,@Size(max=300) String comment){}
 public record FeedbackRequest(@NotBlank @Size(max=80) String paragraphId,@NotBlank String indicator,@NotBlank @Size(max=2000) String body,@Size(max=600) String quote){}
 public record ActionRequest(@NotBlank String action,@NotNull @Size(max=2000) String body){}
 public record VerifyRequest(@NotBlank String verdict,@NotBlank @Size(max=2000) String reason){}
 @GetMapping("/auth/csrf") public Map<String,String> csrf(CsrfToken token){return Map.of("token",token.getToken(),"headerName",token.getHeaderName());}
 @GetMapping("/auth/me") public Map<String,Object> me(Principal p){return s.one("SELECT id,email,display_name,is_teacher FROM app_user WHERE email=?",p.getName());}
 @GetMapping("/courses") public Object courses(Principal p){return service.courses(uid(p));}
 // 开课只认账号级的教师身份（app_user.is_teacher），与「在这门课里是不是教师」无关。
 @PostMapping("/courses") public Object createCourse(@Valid @RequestBody CourseRequest r,Principal p){long u=uid(p);var row=s.one("SELECT is_teacher FROM app_user WHERE id=?",u);if(!Boolean.TRUE.equals(row.get("is_teacher")))throw ApiError.fail(403,"只有教师账号可以创建课程，请联系管理员开通教师权限");return service.createCourse(r.title(),r.description(),u);}
 @PostMapping("/courses/{cid}/members") public Object member(@PathVariable long cid,@Valid @RequestBody MemberRequest r,Principal p){service.addMember(cid,r.email(),r.role(),uid(p),r.revision(),r.reason());return Map.of("ok",true);}
 @GetMapping("/courses/{cid}/progress") public Object progress(@PathVariable long cid,Principal p){return service.progress(cid,uid(p));}
 @GetMapping("/courses/{cid}/assignments") public Object assignments(@PathVariable long cid,Principal p){return service.assignments(cid,uid(p));}
 @PostMapping("/courses/{cid}/assignments") public Object createAssignment(@PathVariable long cid,@Valid @RequestBody AssignmentRequest r,Principal p){return service.createAssignment(cid,r.title(),r.requirements(),r.dueAt(),Boolean.TRUE.equals(r.aiFeedbackEnabled()),r.rubricId(),r.kind(),r.evidencePolicy(),uid(p));}
 @PutMapping("/assignments/{aid}/ai-policy") public Object aiPolicy(@PathVariable long aid,@Valid @RequestBody AiPolicyRequest r,Principal p){return service.setAiPolicy(aid,r.enabled(),uid(p));}
 @GetMapping("/entries/{eid}/ai-feedback") public Object aiFeedback(@PathVariable long eid,@RequestParam(defaultValue="false") boolean generate,Principal p){return service.aiFeedback(eid,uid(p),generate);}
 public record AiSuggestionStateRequest(@NotBlank @Pattern(regexp="OPEN|ADOPTED|DISMISSED") String state){}
 @PostMapping("/ai-suggestions/{id}/state") public Object aiSuggestionState(@PathVariable long id,@Valid @RequestBody AiSuggestionStateRequest r,Principal p){service.setAiSuggestionState(id,r.state(),uid(p));return Map.of("ok",true);}
 @PostMapping("/assignments/{aid}/entries") public Object start(@PathVariable long aid,Principal p){return service.start(aid,uid(p));}
 public record FreeEntryRequest(@NotBlank @Size(max=160) String title){}
 @PostMapping("/courses/{cid}/free-entries") public Object freeEntry(@PathVariable long cid,@Valid @RequestBody FreeEntryRequest r,Principal p){return service.createFreeEntry(cid,r.title(),uid(p));}
 @GetMapping("/entries/{eid}") public Object getEntry(@PathVariable long eid,Principal p){return service.getEntry(eid,uid(p));}
 @PutMapping("/entries/{eid}/draft") public Object save(@PathVariable long eid,@Valid @RequestBody DraftRequest r,Principal p){return service.save(eid,r,uid(p));}
 @PostMapping("/entries/{eid}/submissions") public Object submit(@PathVariable long eid,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody SubmitRequest r,Principal p){if(!key.matches("[a-zA-Z0-9_-]{8,80}"))throw ApiError.fail(422,"幂等键格式不正确");return service.submit(eid,r.revision(),key,uid(p));}
 @GetMapping("/submissions/{sid}") public Object submission(@PathVariable long sid,Principal p){return service.getSubmission(sid,uid(p));}
 public record EntryVerdictRequest(@NotBlank @Pattern(regexp="PASS|RETURN|REOPEN") String action,@Size(max=2000) String reason){}
 @PostMapping("/entries/{eid}/verdict") public Object entryVerdict(@PathVariable long eid,@Valid @RequestBody EntryVerdictRequest r,Principal p){return service.entryVerdict(eid,r.action(),r.reason(),uid(p));}
 @GetMapping("/courses/{cid}/review-queue") public Object queue(@PathVariable long cid,Principal p){return service.queue(cid,uid(p));}
 @PostMapping("/submissions/{sid}/evaluations") public Object evaluate(@PathVariable long sid,@Valid @RequestBody EvaluationRequest r,Principal p){return service.evaluate(sid,r,uid(p));}
 @PostMapping("/submissions/{sid}/feedback") public Object feedback(@PathVariable long sid,@Valid @RequestBody FeedbackRequest r,Principal p){return service.addFeedback(sid,r,uid(p));}
 @PostMapping("/feedback/{fid}/actions") public Object action(@PathVariable long fid,@Valid @RequestBody ActionRequest r,Principal p){service.action(fid,r.action(),r.body(),uid(p));return Map.of("ok",true);}
 @GetMapping("/courses/{cid}/evidence") public Object evidence(@PathVariable long cid,Principal p){return service.evidenceTasks(cid,uid(p));}
 @GetMapping("/evidence/{id}/verifications") public Object verifications(@PathVariable long id,Principal p){return service.verifications(id,uid(p));}
 @PostMapping("/evidence/{id}/verifications") public Object verify(@PathVariable long id,@Valid @RequestBody VerifyRequest r,Principal p){service.verify(id,r.verdict(),r.reason(),uid(p));return Map.of("ok",true);}
 @PostMapping("/submissions/{sid}/publication") public Object requestPub(@PathVariable long sid,Principal p){service.requestPublication(sid,uid(p));return Map.of("ok",true);}
 @GetMapping("/courses/{cid}/publications") public Object pubs(@PathVariable long cid,@RequestParam(defaultValue="false") boolean pending,@RequestParam(defaultValue="COURSE") String scope,Principal p){return service.publications(cid,uid(p),pending,scope);}
 @PostMapping("/publications/{id}/actions") public Object pubAction(@PathVariable long id,@Valid @RequestBody ActionRequest r,Principal p){service.publicationAction(id,r.action(),uid(p));return Map.of("ok",true);}
 public record PublicationDecision(@NotBlank @Pattern(regexp="APPROVE|RETURN|WITHDRAW") String action,@NotBlank @Size(max=2000) String reason,@NotNull @Min(0) Integer revision){}
 public record PublicationRequest(@NotNull @Min(0) Integer revision,@NotNull @Size(max=2000) String reason,@Pattern(regexp="COURSE|PUBLIC") String visibility,Boolean shareFeedback){}
 @GetMapping("/courses/{cid}/publication-records") public Object publicationRecords(@PathVariable long cid,Principal p){return service.publicationRecords(cid,uid(p));}
 @GetMapping("/publications/{id}/review") public Object publicationReview(@PathVariable long id,Principal p){return service.publicationReview(id,uid(p));}
 @PostMapping("/publications/{id}/decisions") public Object publicationDecision(@PathVariable long id,@Valid @RequestBody PublicationDecision r,Principal p){service.publicationAction(id,r.action(),uid(p),r.revision(),r.reason());return Map.of("ok",true);}
 @PostMapping("/submissions/{sid}/publication-request") public Object publicationRequest(@PathVariable long sid,@Valid @RequestBody PublicationRequest r,Principal p){service.requestPublication(sid,uid(p),r.revision(),r.reason(),r.visibility(),r.shareFeedback());return Map.of("ok",true);}
 @GetMapping("/entries/{eid}/versions") public Object entryVersions(@PathVariable long eid,Principal p){return service.entryVersions(eid,uid(p));}
}
