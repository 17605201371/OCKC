package cn.course.community;
import org.springframework.stereotype.Component;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.math.BigDecimal;
@Component
public class DemoSeed implements CommandLineRunner {
 final Store s;final CommunityService service;final PasswordEncoder passwords;final ObjectMapper json;
 @Value("${app.seed}") boolean enabled;@Value("${app.seed-password}") String password;
 DemoSeed(Store s,CommunityService service,PasswordEncoder passwords,ObjectMapper json){this.s=s;this.service=service;this.passwords=passwords;this.json=json;}
 @org.springframework.transaction.annotation.Transactional
 @Override public void run(String... args)throws Exception{
  if(!enabled||s.db.queryForObject("SELECT COUNT(*) FROM app_user",Integer.class)>0)return;
  if(password.length()<12)throw new IllegalStateException("DEMO_SEED=true 时必须设置至少 12 位的 DEMO_PASSWORD；禁止生产环境启用演示数据");
  var emails=List.of("teacher@example.test","student@example.test","peer@example.test","outsider@example.test");var names=List.of("陈老师","林同学","周同学","其他课程学生");
  for(int i=0;i<emails.size();i++)s.db.update("INSERT INTO app_user(email,display_name,password_hash) VALUES(?,?,?)",emails.get(i),names.get(i),passwords.encode(password));
  long teacher=s.uid(emails.get(0)),student=s.uid(emails.get(1)),peer=s.uid(emails.get(2)),outsider=s.uid(emails.get(3));
  long cid=s.id(service.createCourse("教学设计 · 演示课程","以可追溯的知识作品，连接创作、评价与改进。演示数据由系统初始化。",teacher),"id");
  service.addMember(cid,emails.get(1),"STUDENT",teacher);service.addMember(cid,emails.get(2),"STUDENT",teacher);
  long c2=s.id(service.createCourse("独立课程 · 权限测试","用于验证跨课程数据隔离",teacher),"id");service.addMember(c2,emails.get(3),"STUDENT",teacher);
  long aid=s.id(service.createAssignment(cid,"解释光反应与卡尔文循环的物质联系","面向高中课堂撰写一篇知识帖：说明关键场所、物质与能量联系，引用可核验来源，并区分事实与推断。首轮提交后依据反馈至少修订一次。",Instant.now().plus(30,ChronoUnit.DAYS).toString(),teacher),"id");
  var entry=service.start(aid,student);long eid=s.id(entry,"id");
  var doc=json.readTree("""
  {"type":"doc","content":[{"type":"heading","attrs":{"level":2,"blockId":"intro"},"content":[{"type":"text","text":"把两个阶段放回同一条物质链"}]},{"type":"paragraph","attrs":{"blockId":"p-light"},"content":[{"type":"text","text":"光反应发生在类囊体薄膜上，吸收光能并形成 ATP 和 NADPH，水的光解伴随氧气释放。"}]},{"type":"paragraph","attrs":{"blockId":"p-cycle"},"content":[{"type":"text","text":"卡尔文循环在叶绿体基质中进行，利用 ATP 和 NADPH 参与二氧化碳固定及后续还原。两个阶段不是彼此独立的过程。"}]},{"type":"paragraph","attrs":{"blockId":"p-gap"},"content":[{"type":"text","text":"物质能够在两个阶段之间形成联系；这一解释还需补全返回物质及适用条件。"}]}]}
  """);
  service.save(eid,new ApiController.DraftRequest("光反应之后，物质去了哪里？",doc,0,List.of(new ApiController.EvidenceInput("p-light","演示来源：课程教师提供的光合作用讲义","","光反应在类囊体薄膜上进行，并形成 ATP 和 NADPH。","光反应的场所与产物。"))),student);
  long sid=s.id(service.submit(eid,1,"seed-version-one",student),"id");long ev=s.id(service.evidenceTasks(cid,teacher).getFirst(),"id");service.verify(ev,"PASS","演示核验：已对照课程提供的来源摘录；实际使用需换为获授权原件。",teacher);
  var items=new ArrayList<QualityCalculator.Item>();Integer[] scores={80,null,74,92,90,88,70,54,80,70,75};for(int i=0;i<11;i++)items.add(new QualityCalculator.Item(QualityCalculator.CODES.get(i),scores[i]==null?null:BigDecimal.valueOf(scores[i]),scores[i]==null?"NA":"SCORED",scores[i]==null?"本篇不涉及研究方法":""));
  service.evaluate(sid,new ApiController.EvaluationRequest(items,"","主要概念较准确。下一版请补全返回物质，并明确物质循环与能量转移的区别。",0),teacher);
  service.addFeedback(sid,new ApiController.FeedbackRequest("p-gap","C2","请补出 ATP、NADPH 被利用后的物质去向，并解释为什么不能把能量描述为无限循环。"),teacher);
  service.requestPublication(sid,student);var pubs=service.publications(cid,teacher,true);service.publicationAction(s.id(pubs.getFirst(),"id"),"APPROVE",teacher);
  var pe=service.start(aid,peer);long peid=s.id(pe,"id");var pd=doc.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)pd.path("content").get(0).path("content").get(0)).put("text","用因果链解释光合作用");
  service.save(peid,new ApiController.DraftRequest("一束光如何参与有机物合成",pd,0,List.of()),peer);service.submit(peid,1,"seed-peer-version",peer);
 }
}
