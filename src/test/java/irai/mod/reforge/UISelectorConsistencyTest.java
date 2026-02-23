package irai.mod.reforge;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Ensures selector literals used by custom UI pages exist in their .ui files.
 * This prevents runtime crashes from invalid selectors in UI commands.
 */
public class UISelectorConsistencyTest {

    private static final Pattern JAVA_STRING_LITERAL = Pattern.compile("\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"");
    private static final Pattern UI_SELECTOR = Pattern.compile("#[A-Za-z][A-Za-z0-9_]*");
    private static final Pattern UI_ELEMENT_DECL = Pattern.compile("(?m)^\\s*([A-Za-z][A-Za-z0-9_]*)\\s+(#[A-Za-z][A-Za-z0-9_]*)\\s*\\{");
    private static final Pattern EVENT_BINDING = Pattern.compile(
            "addEventBinding\\(\\s*CustomUIEventBindingType\\.([A-Za-z][A-Za-z0-9_]*)\\s*,\\s*\"(#[A-Za-z][A-Za-z0-9_]*)\"");

    @Test
    void weaponSocketAndEssenceSelectorsShouldExist() throws IOException {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put("src/main/java/irai/mod/reforge/UI/WeaponStatsUI.java", "src/main/resources/Common/UI/Custom/WeaponStatHUD.ui");
        mappings.put("src/main/java/irai/mod/reforge/UI/SocketPunchUI.java", "src/main/resources/Common/UI/Custom/SocketPunchUI.ui");
        mappings.put("src/main/java/irai/mod/reforge/UI/EssenceSocketUI.java", "src/main/resources/Common/UI/Custom/EssenceSocketUI.ui");

        Map<String, List<String>> dynamicPrefixes = new HashMap<>();
        dynamicPrefixes.put("src/main/java/irai/mod/reforge/UI/SocketPunchUI.java", List.of("#Socket"));
        dynamicPrefixes.put("src/main/java/irai/mod/reforge/UI/EssenceSocketUI.java", List.of("#Socket", "#EffectLine"));

        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, String> entry : mappings.entrySet()) {
            String javaFile = entry.getKey();
            String uiFile = entry.getValue();
            String expectedDocName = Path.of(uiFile).getFileName().toString();

            Set<String> javaSelectors = extractSelectorsFromJavaLiterals(Path.of(javaFile));
            Set<String> uiSelectors = extractSelectorsFromUi(Path.of(uiFile));
            Map<String, String> uiElementTypes = extractUiElementTypes(Path.of(uiFile));
            List<String> prefixes = dynamicPrefixes.getOrDefault(javaFile, List.of());
            Set<String> appendDocs = extractAppendDocuments(Path.of(javaFile));
            List<EventBinding> eventBindings = extractEventBindings(Path.of(javaFile));

            if (appendDocs.isEmpty()) {
                failures.add(javaFile + " does not call cmd.append(<ui-doc>)");
            } else if (!appendDocs.contains(expectedDocName)) {
                failures.add(javaFile + " appends " + appendDocs + " but expected " + expectedDocName);
            }
            for (String doc : appendDocs) {
                if (doc.contains("/") || doc.contains("\\")) {
                    failures.add(javaFile + " appends a path-like document name '" + doc + "'; use filename-only lookup");
                }
            }

            for (String selector : javaSelectors) {
                if (uiSelectors.contains(selector)) {
                    continue;
                }

                boolean matchesDynamicPrefix = prefixes.stream()
                        .anyMatch(prefix -> selector.equals(prefix) && uiSelectors.stream().anyMatch(id -> id.startsWith(prefix)));
                if (!matchesDynamicPrefix) {
                    failures.add(javaFile + " uses missing selector " + selector + " not found in " + uiFile);
                }
            }

            for (EventBinding eventBinding : eventBindings) {
                String selector = eventBinding.selector;
                String eventType = eventBinding.eventType;
                if (!uiSelectors.contains(selector)) {
                    failures.add(javaFile + " binds " + eventType + " to missing selector " + selector + " not found in " + uiFile);
                    continue;
                }

                String elementType = uiElementTypes.get(selector);
                if ("Activating".equals(eventType) && "Group".equals(elementType)) {
                    failures.add(javaFile + " binds Activating to Group selector " + selector
                            + " in " + uiFile + "; use a clickable control or MouseButtonReleased");
                }
            }
        }

        assertTrue(failures.isEmpty(), String.join(System.lineSeparator(), failures));
    }

    private Set<String> extractSelectorsFromJavaLiterals(Path javaPath) throws IOException {
        String content = Files.readString(javaPath);
        Matcher matcher = JAVA_STRING_LITERAL.matcher(content);
        Set<String> selectors = new HashSet<>();
        while (matcher.find()) {
            String value = matcher.group(1);
            if (value != null && value.matches("^#[A-Za-z][A-Za-z0-9_]*$")) {
                selectors.add(value);
            }
        }
        return selectors;
    }

    private Set<String> extractAppendDocuments(Path javaPath) throws IOException {
        String content = Files.readString(javaPath);
        Pattern appendPattern = Pattern.compile("\\.append\\(\"([^\"]+\\.ui)\"\\)");
        Matcher matcher = appendPattern.matcher(content);
        Set<String> docs = new HashSet<>();
        while (matcher.find()) {
            docs.add(matcher.group(1));
        }
        return docs;
    }

    private Set<String> extractSelectorsFromUi(Path uiPath) throws IOException {
        String content = Files.readString(uiPath);
        Matcher matcher = UI_SELECTOR.matcher(content);
        Set<String> selectors = new HashSet<>();
        while (matcher.find()) {
            selectors.add(matcher.group());
        }
        return selectors;
    }

    private Map<String, String> extractUiElementTypes(Path uiPath) throws IOException {
        String content = Files.readString(uiPath);
        Matcher matcher = UI_ELEMENT_DECL.matcher(content);
        Map<String, String> elementTypes = new HashMap<>();
        while (matcher.find()) {
            elementTypes.put(matcher.group(2), matcher.group(1));
        }
        return elementTypes;
    }

    private List<EventBinding> extractEventBindings(Path javaPath) throws IOException {
        String content = Files.readString(javaPath);
        Matcher matcher = EVENT_BINDING.matcher(content);
        List<EventBinding> bindings = new ArrayList<>();
        while (matcher.find()) {
            bindings.add(new EventBinding(matcher.group(1), matcher.group(2)));
        }
        return bindings;
    }

    private static final class EventBinding {
        private final String eventType;
        private final String selector;

        private EventBinding(String eventType, String selector) {
            this.eventType = eventType;
            this.selector = selector;
        }
    }
}
