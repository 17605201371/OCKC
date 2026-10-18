package cn.course.community;
import java.security.Principal;
import cn.course.community.QualityCalculator.Item;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api")
public class WorkflowController {
 final Store s;final NotificationService notices;final ReviewDraftService drafts;
 WorkflowController(Store s,NotificationService notices,ReviewDraftService drafts){this.s=s;this.notices=notices;this.drafts=drafts;}
 public record ReviewDraftRequest(@NotNull @Size(min=11,max=11) List<@NotNull Item> items,@NotNull @Size(max=2000) String summary,@NotNull @Size(max=1000) String gateReason,@Min(0) int revision,@Min(0) int baseEvaluationRevision){}
 public record DiscardRequest(@Min(0) int revision){}
 @GetMapping("/notifications") public Object inbox(@RequestParam(required=false) Long before,@RequestParam(defaultValue="false") boolean unread,Principal p){return notices.inbox(s.uid(p.getName()),before,unread);}
 @PutMapping("/notifications/read-all") public Object readAll(Principal p){return Map.of("updated",notices.readAll(s.uid(p.getName())));}
 @PutMapping("/notifications/{id}/read") public Object read(@PathVariable long id,Principal p){notices.read(id,s.uid(p.getName()));return Map.of("ok",true);}
 @GetMapping("/submissions/{sid}/review-draft") public Object get(@PathVariable long sid,Principal p){return drafts.get(sid,s.uid(p.getName()));}
 @PutMapping("/submissions/{sid}/review-draft") public Object save(@PathVariable long sid,@Valid @RequestBody ReviewDraftRequest r,Principal p){return drafts.save(sid,s.uid(p.getName()),r);}
 @DeleteMapping("/submissions/{sid}/review-draft") public Object discard(@PathVariable long sid,@Valid @RequestBody DiscardRequest r,Principal p){drafts.discard(sid,s.uid(p.getName()),r.revision());return Map.of("ok",true);}
}
