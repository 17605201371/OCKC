package cn.course.community;
import java.util.*;
import java.time.Instant;
import java.sql.Statement;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import static cn.course.community.ApiError.fail;
@Component
public class Store {
 final JdbcTemplate db;Store(JdbcTemplate db){this.db=db;}
 public Map<String,Object> one(String sql,Object... args){var rows=db.queryForList(sql,args);if(rows.isEmpty())throw fail(404,"记录不存在");return rows.getFirst();}
 public long id(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
 public long insert(String sql,Object... args){var key=new GeneratedKeyHolder();db.update(c->{var ps=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)ps.setObject(i+1,args[i]);return ps;},key);return Objects.requireNonNull(key.getKey()).longValue();}
 public long uid(String email){return id(one("SELECT id FROM app_user WHERE email=?",email),"id");}
 public String role(long cid,long uid){var rows=db.queryForList("SELECT role FROM course_member WHERE course_id=? AND user_id=?",cid,uid);if(rows.isEmpty())throw fail(403,"你不是该课程成员");return (String)rows.getFirst().get("role");}
 public void teacher(long cid,long uid){if(!"TEACHER".equals(role(cid,uid)))throw fail(403,"该操作仅任课教师可执行");}
 public void student(long cid,long uid){if(!"STUDENT".equals(role(cid,uid)))throw fail(403,"该操作仅课程学生可执行");}
 public void audit(long uid,String action,String type,long id){db.update("INSERT INTO audit_log(actor_id,action_type,resource_type,resource_id,created_at) VALUES(?,?,?,?,?)",uid,action,type,id,now());}
 public static String now(){return Instant.now().toString();}
}
