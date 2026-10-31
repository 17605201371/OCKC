package cn.course.community;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/courses/{cid}/rubrics")
class RubricController {
 final Store s;final RubricService r;RubricController(Store s,RubricService r){this.s=s;this.r=r;}
 @GetMapping public Object list(@PathVariable long cid,Principal p){return r.list(cid,s.uid(p.getName()));}
 @GetMapping("/history") public Object history(@PathVariable long cid,Principal p){return r.history(cid,s.uid(p.getName()));}
 @GetMapping("/{code}") public Object get(@PathVariable long cid,@PathVariable String code,Principal p){return r.get(cid,s.uid(p.getName()),code);}
 @PostMapping public Object create(@PathVariable long cid,@Valid @RequestBody RubricService.Create body,Principal p){return r.create(cid,s.uid(p.getName()),body.sourceId());}
 @PutMapping("/{id}") public Object edit(@PathVariable long cid,@PathVariable long id,@Valid @RequestBody RubricService.Edit body,Principal p){return r.edit(cid,id,s.uid(p.getName()),body);}
 @PostMapping("/{id}/enable") public Object enable(@PathVariable long cid,@PathVariable long id,@Valid @RequestBody RubricService.Enable body,Principal p){return r.enable(cid,id,s.uid(p.getName()),body);}
 @PutMapping("/default") public Object defaultRubric(@PathVariable long cid,@Valid @RequestBody RubricService.Default body,Principal p){return r.setDefault(cid,s.uid(p.getName()),body);}
}
