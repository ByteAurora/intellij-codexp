import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPProgressEngine;
import com.github.ilovegamecoding.intellijcodexp.enums.Event;
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPState;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import java.nio.file.*;
import org.jdom.Element;

/** Executes a downgrade write using only the frozen 2.2.0 classes. Inputs must be synthetic. */
public class RoundTrip220 {
  public static void main(String[] args) throws Exception {
    var xml = JDOMUtil.load(Files.readString(Path.of(args[0])));
    var component = xml.getChild("component");
    var state = XmlSerializer.deserialize(component, CodeXPState.class);
    long before = state.getXp();
    new CodeXPProgressEngine().recordEvent(state, Event.SAVE);
    var written = XmlSerializer.serialize(state, new SkipDefaultValuesSerializationFilters());
    written.setName("component");
    written.setAttribute("name", "com.github.ilovegamecoding.intellijcodexp.services.CodeXP");
    var output = JDOMUtil.writeElement(new Element("application").addContent(written));
    if (output.contains("collectionGeneration")) throw new AssertionError("Current classes leaked onto the 2.2.0 classpath");
    Files.writeString(Path.of(args[1]), output + "\n");
    System.out.println("2.2.0 SAVE: " + before + " -> " + state.getXp());
    System.out.println("Class source: " + CodeXPState.class.getProtectionDomain().getCodeSource().getLocation());
  }
}
