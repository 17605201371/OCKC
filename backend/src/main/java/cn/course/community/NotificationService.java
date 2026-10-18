package cn.course.community;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static cn.course.community.ApiError.fail;
@Service
public class NotificationService {
 final Store s;NotificationService(Store s){this.s=s;}
 // Called inside the domain transaction: a rolled-back operation creates no alert.
 void send(long recipient,long cid,String kind,String audience,String title,String type,long target,String anchor,long actor){
  if(recipient==actor)return;
  s.db.update("INSERT INTO notification(recipient_id,course_id,kind,audience,title,target_type,target_id,anchor_id,created_at) VALUES(?,?,?,?,?,?,?,?,?)",recipient,cid,kind,audience,title,type,target,anchor,Store.now());
 }
 void teachers(long cid,String kind,String title,String type,long target,long actor){
  for(var m:s.db.queryForList("SELECT user_id FROM course_member WHERE course_id=? AND role='TEACHER'",cid))send(s.id(m,"user_id"),cid,kind,"TEACHER",title,type,target,"",actor);
 }
 // Notifications contain routing metadata, not copies of grading drafts or feedback bodies.
 final String eligible=" n.recipient_id=? AND EXISTS (SELECT 1 FROM course_member m WHERE m.course_id=n.course_id AND m.user_id=n.recipient_id AND (n.audience<>'TEACHER' OR m.role='TEACHER'))";
 public Map<String,Object> inbox(long uid,Long before,boolean unread){
  if(before!=null&&before<1)throw fail(400,"消息游标不正确");
  var args=new ArrayList<Object>();args.add(uid);String where=eligible;
  if(before!=null){where+=" AND n.id<?";args.add(before);}if(unread)where+=" AND n.read_at IS NULL";
  var rows=s.db.queryForList("SELECT n.*,c.title AS course_title FROM notification n JOIN course c ON c.id=n.course_id WHERE "+where+" ORDER BY n.id DESC LIMIT 21",args.toArray());boolean more=rows.size()>20;
  if(more)rows=new ArrayList<>(rows.subList(0,20));var out=new LinkedHashMap<String,Object>();out.put("items",rows);out.put("unreadCount",s.db.queryForObject("SELECT COUNT(*) FROM notification n WHERE "+eligible+" AND n.read_at IS NULL",Integer.class,uid));out.put("hasMore",more);out.put("nextCursor",more?s.id(rows.getLast(),"id"):null);return out;
 }
 @Transactional public void read(long id,long uid){var n=s.one("SELECT n.id,n.read_at FROM notification n WHERE n.id=? AND "+eligible,id,uid);if(n.get("read_at")==null)s.db.update("UPDATE notification SET read_at=? WHERE id=? AND recipient_id=? AND read_at IS NULL",Store.now(),id,uid);}
 @Transactional public int readAll(long uid){return s.db.update("UPDATE notification SET read_at=? WHERE id IN (SELECT n.id FROM (SELECT * FROM notification) n WHERE "+eligible+" AND n.read_at IS NULL)",Store.now(),uid);}
}
