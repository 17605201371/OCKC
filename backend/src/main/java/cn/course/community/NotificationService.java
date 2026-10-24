package cn.course.community;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
 static final Set<String> KINDS=Set.of("MEMBERSHIP_CHANGE","ASSIGNMENT_CHANGE","SUBMISSION","EVALUATION","FEEDBACK","FEEDBACK_REPLY","EVIDENCE","EVIDENCE_DISPUTED","PUBLICATION_REQUEST","PUBLICATION_RESULT");
 String kind(String value){if(value==null||value.isBlank())return null;if(!KINDS.contains(value))throw fail(400,"消息类型不正确");return value;}
 String accessStamp(long uid){
  var memberships=s.db.queryForList("SELECT course_id,role FROM course_member WHERE user_id=? ORDER BY course_id",uid);
  String canonical=memberships.stream().map(m->m.get("course_id")+":"+m.get("role")).collect(java.util.stream.Collectors.joining("|"));
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Map<String,Object> summary(long uid,String filter){
  String selected=kind(filter);var args=new ArrayList<Object>();args.add(uid);String where=eligible;
  if(selected!=null){where+=" AND n.kind=?";args.add(selected);}
  var stats=s.one("SELECT COALESCE(MAX(n.id),0) AS latest_id, COUNT(CASE WHEN n.read_at IS NULL THEN 1 END) AS scope_unread FROM notification n WHERE "+where,args.toArray());
  int unread=s.db.queryForObject("SELECT COUNT(*) FROM notification n WHERE "+eligible+" AND n.read_at IS NULL",Integer.class,uid);
  return Map.of("unreadCount",unread,"scopeUnreadCount",((Number)stats.get("scope_unread")).intValue(),"latestId",s.id(stats,"latest_id"),"accessStamp",accessStamp(uid));
 }
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Map<String,Object> inbox(long uid,Long before,boolean unread){return inbox(uid,before,unread,null);}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Map<String,Object> inbox(long uid,Long before,boolean unread,String filter){
  if(before!=null&&before<1)throw fail(400,"消息游标不正确");String selected=kind(filter);
  var args=new ArrayList<Object>();args.add(uid);String where=eligible;
  if(selected!=null){where+=" AND n.kind=?";args.add(selected);}
  if(before!=null){where+=" AND n.id<?";args.add(before);}if(unread)where+=" AND n.read_at IS NULL";
  var rows=s.db.queryForList("SELECT n.*,c.title AS course_title FROM notification n JOIN course c ON c.id=n.course_id WHERE "+where+" ORDER BY n.id DESC LIMIT 21",args.toArray());boolean more=rows.size()>20;
  if(more)rows=new ArrayList<>(rows.subList(0,20));var out=new LinkedHashMap<String,Object>(summary(uid,selected));out.put("items",rows);out.put("hasMore",more);out.put("nextCursor",more?s.id(rows.getLast(),"id"):null);return out;
 }
 @Transactional public void read(long id,long uid){var n=s.one("SELECT n.id,n.read_at FROM notification n WHERE n.id=? AND "+eligible,id,uid);if(n.get("read_at")==null)s.db.update("UPDATE notification SET read_at=? WHERE id=? AND recipient_id=? AND read_at IS NULL",Store.now(),id,uid);}
 @Transactional public int readAll(long uid){return readAll(uid,null);}
 @Transactional public int readAll(long uid,String filter){String selected=kind(filter);var args=new ArrayList<Object>();args.add(Store.now());args.add(uid);String where=eligible+" AND n.read_at IS NULL";if(selected!=null){where+=" AND n.kind=?";args.add(selected);}return s.db.update("UPDATE notification SET read_at=? WHERE id IN (SELECT n.id FROM (SELECT * FROM notification) n WHERE "+where+")",args.toArray());}
}
