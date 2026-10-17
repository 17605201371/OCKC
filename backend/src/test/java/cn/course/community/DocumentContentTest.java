package cn.course.community;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DocumentContentTest {
 final ObjectMapper json=new ObjectMapper();final CommunityService service=new CommunityService(null,json);
 JsonNode doc(String content)throws Exception{return json.readTree("{\"type\":\"doc\",\"content\":["+content+"]}");}
 @Test void markdownBlocksAndMarksAccepted()throws Exception{
  var node=doc("""
   {"type":"heading","attrs":{"level":2,"blockId":"h1"},"content":[{"type":"text","text":"论点"}]},
   {"type":"blockquote","content":[{"type":"paragraph","attrs":{"blockId":"q1"},"content":[{"type":"text","text":"原文","marks":[{"type":"bold"},{"type":"italic"},{"type":"code"},{"type":"strike"}]}]}]},
   {"type":"bulletList","content":[{"type":"listItem","content":[{"type":"paragraph","attrs":{"blockId":"li1"},"content":[{"type":"text","text":"来源","marks":[{"type":"link","attrs":{"href":"https://example.org/source"}}]}]}]}]},
   {"type":"codeBlock","attrs":{"blockId":"code1","language":"js"},"content":[{"type":"text","text":"const x = 1;"}]},{"type":"horizontalRule"}
   """);
  assertDoesNotThrow(()->service.validateContent(node));assertTrue(service.anchors(node).containsKey("code1"));
 }
 @Test void unsafeLinkSchemesRejected()throws Exception{
  for(String url:new String[]{"javascript:alert(1)","data:text/html,test","//evil.example","https://","https://bad host"}){
   var n=doc("{\"type\":\"paragraph\",\"attrs\":{\"blockId\":\"p\"},\"content\":[{\"type\":\"text\",\"text\":\"link\",\"marks\":[{\"type\":\"link\",\"attrs\":{\"href\":\""+url+"\"}}]}]}");
   assertThrows(ApiError.Failure.class,()->service.validateContent(n));
  }
 }
 @Test void duplicateCodeBlockAnchorRejected()throws Exception{
  var n=doc("{\"type\":\"paragraph\",\"attrs\":{\"blockId\":\"same\"}},{\"type\":\"codeBlock\",\"attrs\":{\"blockId\":\"same\"}}");assertThrows(ApiError.Failure.class,()->service.validateContent(n));
 }
 @Test void legacyCodeBlocksRemainReadableAndSavable()throws Exception{var n=doc("{\"type\":\"codeBlock\",\"content\":[{\"type\":\"text\",\"text\":\"old\"}]}");assertDoesNotThrow(()->service.validateContent(n));}
 @Test void unknownMarksRejected()throws Exception{var n=doc("{\"type\":\"paragraph\",\"attrs\":{\"blockId\":\"p\"},\"content\":[{\"type\":\"text\",\"text\":\"bad\",\"marks\":[{\"type\":\"script\"}]}]}");assertThrows(ApiError.Failure.class,()->service.validateContent(n));}
}
