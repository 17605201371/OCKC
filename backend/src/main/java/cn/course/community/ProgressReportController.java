package cn.course.community;
import java.security.Principal;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api")
class ProgressReportController {
 final ProgressReports reports;final Store s;ProgressReportController(ProgressReports reports,Store s){this.reports=reports;this.s=s;}
 @GetMapping("/courses/{cid}/progress-export.csv") ResponseEntity<byte[]> csv(@PathVariable long cid,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") long assignmentId,@RequestParam(defaultValue="0") long authorId,@RequestParam(defaultValue="ALL") String state,@RequestParam(defaultValue="recent") String sort,Principal p){byte[] bytes=reports.csv(cid,s.uid(p.getName()),q,assignmentId,authorId,state,sort);return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"no-store").header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"course-progress-"+cid+".csv\"").header("X-Content-Type-Options","nosniff").contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(bytes);}
 @GetMapping("/progress-entries/{id}/report") ResponseEntity<Object> report(@PathVariable long id,Principal p){return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"no-store").body(reports.report(id,s.uid(p.getName())));}
}
