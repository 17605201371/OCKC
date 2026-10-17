package cn.course.community;
import org.junit.jupiter.api.Test;
import java.util.*;import java.math.*;
import static org.junit.jupiter.api.Assertions.*;
class QualityCalculatorTest {
 List<QualityCalculator.Item> sample(){Integer[] v={80,null,74,92,90,88,70,54,80,70,75};var r=new ArrayList<QualityCalculator.Item>();for(int i=0;i<11;i++)r.add(new QualityCalculator.Item(QualityCalculator.CODES.get(i),v[i]==null?null:BigDecimal.valueOf(v[i]),v[i]==null?"NA":"SCORED",v[i]==null?"不涉及方法":""));return r;}
 @Test void reference(){var r=QualityCalculator.calculate(sample(),"");assertEquals(0,r.total().compareTo(new BigDecimal("76")));assertEquals(0,r.dimensions().get("S").compareTo(new BigDecimal("77")));}
 @Test void gateCaps(){var r=sample();r.replaceAll(i->new QualityCalculator.Item(i.code(),BigDecimal.valueOf(94),"SCORED",""));assertEquals(0,QualityCalculator.calculate(r,"已证实核心事实错误").total().compareTo(BigDecimal.valueOf(89)));}
 @Test void pendingBlocks(){var r=sample();r.set(0,new QualityCalculator.Item("S1",null,"PENDING","等待"));assertThrows(ApiError.Failure.class,()->QualityCalculator.calculate(r,""));}
 @Test void emptyDimensionBlocks(){var r=sample();r.replaceAll(i->i.code().startsWith("C")?new QualityCalculator.Item(i.code(),null,"NA","不涉及"):i);assertThrows(ApiError.Failure.class,()->QualityCalculator.calculate(r,""));}
 @Test void duplicatesBlock(){var r=sample();r.set(0,r.get(2));assertThrows(ApiError.Failure.class,()->QualityCalculator.calculate(r,""));}
 @Test void rangeBlocks(){var r=sample();r.set(0,new QualityCalculator.Item("S1",BigDecimal.valueOf(101),"SCORED",""));assertThrows(ApiError.Failure.class,()->QualityCalculator.calculate(r,""));}
 @Test void naNeedsReason(){var r=sample();r.set(1,new QualityCalculator.Item("S2",null,"NA",""));assertThrows(ApiError.Failure.class,()->QualityCalculator.calculate(r,""));}
}
