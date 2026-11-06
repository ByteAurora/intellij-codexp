import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPProgressEngine;
import com.github.ilovegamecoding.intellijcodexp.enums.Event;
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPState;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Element;
import java.nio.file.*;
import java.util.*;

/** Run with the frozen pre-RC 2.2.0 JAR, never the candidate's classes. Synthetic data only. */
public class GenerateLegacyFixtures {
  public static void main(String[] args) throws Exception {
    Path out = Path.of(args[0]); Files.createDirectories(out);
    for (String variant : List.of("legacy175", "nearcap", "large-reward", "default", "fresh")) {
      var state = new CodeXPState();
      var engine = new CodeXPProgressEngine();
      engine.initialize(state);
      int ordinal = switch (variant) {
        case "legacy175" -> 311;
        case "large-reward" -> 1234;
        case "default" -> 21;
        default -> 1;
      };
      for (int i = 1; i < ordinal; i++) {
        var challenge = state.getChallenges().get(Event.SAVE);
        challenge.setProgress(challenge.getGoal() - 1);
        engine.recordEvent(state, Event.SAVE);
      }
      // Retain a small set of actual old-engine completions, including the highest completed ordinal.
      var keptGoals = Set.of(10L, 100L, 300L, 1500L, (ordinal - 1L) * 10);
      state.getCompletedChallenges().removeIf(c -> !keptGoals.contains(c.getGoal()));
      for (var challenge : state.getCompletedChallenges()) challenge.setId("v220-done-" + challenge.getGoal());
      for (var challenge : state.getChallenges().values()) {
        challenge.setId("v220-active-" + variant + "-" + challenge.getEvent().name());
        challenge.setProgress(challenge.getEvent() == Event.SAVE && variant.equals("large-reward") ? challenge.getGoal() - 1 : 0);
      }
      state.setNickname("CodeXP 2.3.0 RC · " + variant);
      state.setXp(switch (variant) {
        case "legacy175" -> 29174269L;
        case "nearcap", "large-reward" -> 550645L;
        case "default" -> 31429L;
        default -> 0L;
      });
      state.getCodeXPConfiguration().setEffectStyle("subtle");
      Element component = XmlSerializer.serialize(state, new SkipDefaultValuesSerializationFilters());
      component.setName("component");
      component.setAttribute("name", "com.github.ilovegamecoding.intellijcodexp.services.CodeXP");
      String xml = JDOMUtil.writeElement(new Element("application").addContent(component));
      if (xml.contains("collectionGeneration") || xml.contains("collectionState")) throw new AssertionError("Not the original 2.2.0 profile format");
      Files.writeString(out.resolve("codexp-2.2.0-" + variant + ".xml"), xml + "\n");
      System.out.println(variant + ": xp=" + state.getXp() + ", history=" + state.getCompletedChallenges().size());
    }
    System.out.println("Class source: " + CodeXPState.class.getProtectionDomain().getCodeSource().getLocation());
  }
}
