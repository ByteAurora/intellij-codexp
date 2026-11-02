import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPProgressEngine;
import com.github.ilovegamecoding.intellijcodexp.enums.Event;
import com.github.ilovegamecoding.intellijcodexp.enums.PositionToDisplayGainedXP;
import com.github.ilovegamecoding.intellijcodexp.models.*;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Element;
import java.nio.file.*;
import java.util.*;

public class GenerateLegacyFixtures {
 public static void main(String[] args) throws Exception {
  Path out=Path.of(args[0]); Files.createDirectories(out);
  for(String variant:List.of("default","intellij","silent")) {
   var state=new CodeXPState(); var engine=new CodeXPProgressEngine(); engine.initialize(state);
   Map<Event,Integer> stages=Map.of(Event.SAVE,1234,Event.TYPING,3,Event.COPY,12,Event.RUN,2);
   for(var entry:stages.entrySet()) for(int i=1;i<entry.getValue();i++) {
    var c=state.getChallenges().get(entry.getKey()); c.setProgress(c.getGoal()-1); engine.recordEvent(state,entry.getKey());
   }
   // Bounded fixture history, retaining genuine records created by the old engine at low and high stages.
   state.getCompletedChallenges().removeIf(c -> c.getEvent()==Event.SAVE && c.getGoal()!=10 && c.getGoal()!=1230 && c.getGoal()!=12330 || c.getEvent()==Event.COPY && c.getGoal()!=10 && c.getGoal()!=110);
   state.getCompletedChallenges().sort(Comparator.comparing(c -> c.getEvent().name()+String.format("%09d",c.getGoal())));
   for(var c:state.getCompletedChallenges()) c.setId("legacy-done-"+c.getEvent().name()+"-"+c.getGoal());
   for(var c:state.getChallenges().values()) {
    c.setId("legacy-active-"+c.getEvent().name());
    c.setProgress(c.getEvent()==Event.SAVE?c.getGoal()-1:Math.min(3,c.getGoal()-1));
   }
   for(Event event:Event.values()) state.getEventCounts().put(event,9876543210L+event.ordinal());
   state.setNickname("Upgrade QA 한글 & <legacy>"); state.setXp(1234567890L); state.setShowCompletedChallenges(false);
   if(variant.equals("intellij")) {
    state.getCodeXPConfiguration().setNotificationType("IntelliJ Notification");
    state.getCodeXPConfiguration().setShowGainedXP(false);
    state.getCodeXPConfiguration().setPositionToDisplayGainedXP(PositionToDisplayGainedXP.BOTTOM_LEFT);
   } else if(variant.equals("silent")) {
    state.getCodeXPConfiguration().setShowLevelUpNotification(false);
    state.getCodeXPConfiguration().setShowCompleteChallengeNotification(false);
    state.getCodeXPConfiguration().setShowGainedXP(false);
    state.getCodeXPConfiguration().setPositionToDisplayGainedXP(PositionToDisplayGainedXP.LEFT);
   }
   Element component=XmlSerializer.serialize(state,new SkipDefaultValuesSerializationFilters());
   component.setName("component"); component.setAttribute("name","com.github.ilovegamecoding.intellijcodexp.services.CodeXP");
   String xml=JDOMUtil.writeElement(new Element("application").addContent(component));
   if(xml.contains("lastUpdatedOrder")||xml.contains("effectStyle")||xml.contains("reducedMotion")) throw new AssertionError("Not legacy classes");
   Files.writeString(out.resolve("codexp-2.1.1-"+variant+".xml"),xml+"\n");
   System.out.println(variant+": "+state.getChallenges().size()+" active, "+state.getCompletedChallenges().size()+" completed, xp="+state.getXp());
  }
  System.out.println("Class source: "+CodeXPState.class.getProtectionDomain().getCodeSource().getLocation());
 }
}
