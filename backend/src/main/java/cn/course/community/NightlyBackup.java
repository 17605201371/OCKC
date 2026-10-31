package cn.course.community;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每晚在线备份 H2 文件库：H2 的 BACKUP TO 在运行中生成一致的 zip，不需要停服务。
 * 只在配置了 app.backup.dir 且数据库是 H2 时生效；MySQL 部署应使用数据库自己的备份方案。
 * 备份和数据库在同一台机器上，只防误操作和数据库文件损坏，防不了整机故障，需要另外拷走。
 */
@Component
public class NightlyBackup {
 static final Logger log=LoggerFactory.getLogger(NightlyBackup.class);
 static final DateTimeFormatter STAMP=DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
 final JdbcTemplate db;
 @Value("${app.backup.dir:}") String dir;
 @Value("${app.backup.keep:14}") int keep;
 @Value("${spring.datasource.url:}") String url;
 NightlyBackup(JdbcTemplate db){this.db=db;}

 @Scheduled(cron="${app.backup.cron:0 30 3 * * *}",zone="Asia/Shanghai")
 public void nightly(){if(enabled())run();}

 boolean enabled(){return dir!=null&&!dir.isBlank()&&url!=null&&url.startsWith("jdbc:h2:");}

 public Path run(){
  try{
   Path target=Paths.get(dir).toAbsolutePath().normalize();
   Files.createDirectories(target);
   Path file=target.resolve("community-"+LocalDateTime.now(ZoneId.of("Asia/Shanghai")).format(STAMP)+".zip");
   // 路径由配置和时间戳拼成，不含用户输入；仍按 SQL 字符串规则转义单引号。
   db.execute("BACKUP TO '"+file.toString().replace("'","''")+"'");
   log.info("database backup written: {} ({} bytes)",file,Files.size(file));
   prune(target);
   return file;
  }catch(Exception e){
   log.error("database backup failed: {}",e.toString());
   return null;
  }
 }
 void prune(Path target)throws IOException{
  if(keep<1)return;
  List<Path> old;
  try(Stream<Path> files=Files.list(target)){
   old=files.filter(p->p.getFileName().toString().matches("community-\\d{8}-\\d{6}\\.zip"))
    .sorted(Comparator.comparing((Path p)->p.getFileName().toString()).reversed()).skip(keep).toList();
  }
  for(Path p:old){Files.deleteIfExists(p);log.info("old backup removed: {}",p.getFileName());}
 }
}

@Configuration @EnableScheduling
class SchedulingConfig {}
