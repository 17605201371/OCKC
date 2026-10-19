package cn.course.community;

import java.io.*;
import java.security.Principal;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import static cn.course.community.ApiError.fail;

/** Immutable, entry-scoped raster media. No remote fetch, SVG, or arbitrary file serving. */
@RestController @RequestMapping("/api")
public class MediaController {
 final Store s; final CommunityService service;
 MediaController(Store s,CommunityService service){this.s=s;this.service=service;}
 @org.springframework.transaction.annotation.Transactional
 @PostMapping("/entries/{eid}/media")
 public Map<String,Object> upload(@PathVariable long eid,@RequestParam("file") MultipartFile file,Principal p)throws IOException {
  long uid=s.uid(p.getName());var e=service.entry(eid);service.own(e,uid);s.student(s.id(e,"course_id"),uid);s.one("SELECT id FROM entry WHERE id=? FOR UPDATE",eid);
  if(file.isEmpty()||file.getSize()>5*1024*1024)throw fail(422,"图片须小于 5 MB");
  // Cap both asset count and storage per entry; old versions retain their original images.
  if(s.db.queryForObject("SELECT COUNT(*) FROM entry_media WHERE entry_id=?",Integer.class,eid)>=60)throw fail(422,"每篇最多保留 60 张图片（含历史版本）");
  try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(file.getBytes()))){
   var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw fail(422,"仅支持有效的 PNG、JPEG 图片");
   var reader=readers.next();
   try {
    String format=reader.getFormatName().toLowerCase(Locale.ROOT);
    if(!Set.of("png","jpeg","jpg").contains(format))throw fail(422,"仅支持 PNG、JPEG 图片，不支持 SVG 或动图");
    reader.setInput(input,true,true);int w=reader.getWidth(0),h=reader.getHeight(0);
    if(w<1||h<1||w>6000||h>6000||(long)w*h>16000000)throw fail(422,"图片尺寸过大，请压缩到 1600 万像素以内，单边不超过 6000 像素");
    var image=reader.read(0);var bytes=new ByteArrayOutputStream();String out=format.equals("png")?"png":"jpeg";
    ImageIO.write(image,out,bytes);if(bytes.size()>8*1024*1024)throw fail(422,"图片解码后过大，请压缩后上传");
    String id=UUID.randomUUID().toString(),mime="image/"+out;
    s.db.update("INSERT INTO entry_media(id,entry_id,mime_type,width,height,body,created_at) VALUES(?,?,?,?,?,?,?)",id,eid,mime,w,h,bytes.toByteArray(),Store.now());
    s.audit(uid,"MEDIA_UPLOAD","entry",eid);
    return Map.of("src","/api/media/"+id,"width",w,"height",h);
   }finally{reader.dispose();}
  }catch(javax.imageio.IIOException ex){throw fail(422,"图片损坏或格式不受支持");}
 }
 @GetMapping("/media/{id}")
 public ResponseEntity<byte[]> read(@PathVariable String id,Principal p){
  if(!id.matches("[a-f0-9-]{36}"))throw fail(404,"图片不存在");
  var m=s.one("SELECT entry_id,mime_type FROM entry_media WHERE id=?",id);long eid=s.id(m,"entry_id"),uid=s.uid(p.getName());var e=service.entry(eid);String role=s.role(s.id(e,"course_id"),uid);
  boolean visible=s.id(e,"author_id")==uid||Set.of("TEACHER","ASSISTANT").contains(role);
  if(!visible){
   // A published old version must never expose images only present in a newer draft.
   var versions=s.db.queryForList("SELECT v.content_json FROM submission v JOIN publication p ON p.submission_id=v.id WHERE v.entry_id=? AND p.state='PUBLISHED'",eid);
   visible=versions.stream().anyMatch(v->service.mediaSources(service.decode(v.get("content_json"))).contains("/api/media/"+id));
  }
  if(!visible)throw fail(403,"图片尚未获准在课程内展示");
  byte[] bytes=s.db.queryForObject("SELECT body FROM entry_media WHERE id=?",byte[].class,id);
  return ResponseEntity.ok().contentType(MediaType.parseMediaType((String)m.get("mime_type"))).header("Cache-Control","private, no-store").header("Content-Disposition","inline").body(bytes);
 }
}
