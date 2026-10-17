package cn.course.community;
import java.math.*;
import java.util.*;
import static cn.course.community.ApiError.fail;
public class QualityCalculator {
 public static final List<String> CODES=List.of("S1","S2","S3","A1","A2","A3","C1","C2","L1","L2","L3");
 public record Item(String code, BigDecimal score, String status, String reason) {}
 public record Result(Map<String,BigDecimal> dimensions, BigDecimal raw, BigDecimal total) {}
 public static Result calculate(List<Item> items,String gateReason){
  if(items==null||items.size()!=11||!new HashSet<>(items.stream().map(Item::code).toList()).equals(new HashSet<>(CODES)))throw fail(422,"必须包含且仅包含 11 个不重复的二级指标");
  var dims=new LinkedHashMap<String,BigDecimal>();
  for(String d:List.of("S","A","C","L")){
   var valid=new ArrayList<BigDecimal>();
   for(var i:items.stream().filter(x->x.code().startsWith(d)).toList()){
    if("PENDING".equals(i.status()))throw fail(422,"仍有待核验指标，不得发布正式评价");
    if("NA".equals(i.status())){if(i.reason()==null||i.reason().isBlank()||i.reason().length()>500)throw fail(422,"不适用项必须填写 1–500 字理由");if(i.score()!=null)throw fail(422,"不适用项不得携带分数");continue;}
    if(!"SCORED".equals(i.status())||i.score()==null||i.score().compareTo(BigDecimal.ZERO)<0||i.score().compareTo(BigDecimal.valueOf(100))>0||i.score().scale()>2)throw fail(422,"有效指标应填写 0–100 的数值（最多两位小数）");
    valid.add(i.score());
   }
   if(valid.isEmpty())throw fail(422,"每个维度至少有一项有效评分");
   dims.put(d,valid.stream().reduce(BigDecimal.ZERO,BigDecimal::add).divide(BigDecimal.valueOf(valid.size()),8,RoundingMode.HALF_UP));
  }
  BigDecimal raw=dims.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add).divide(BigDecimal.valueOf(4),4,RoundingMode.HALF_UP);
  return new Result(dims,raw,gateReason!=null&&!gateReason.isBlank()?raw.min(BigDecimal.valueOf(89)):raw);
 }
}
