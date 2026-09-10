package it.touchinformatica.xmleditor.view;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Un tracciato malformato in {@code -fx-shape} non solleva errori: JavaFX lo
 * ignora e il pulsante resta semplicemente senza icona. Questi controlli non
 * sostituiscono l'occhio, ma intercettano refusi e tracciati troncati.
 */
class IconsTest {

    /** Tutte le costanti di tracciato dichiarate in Icons. */
    private List<Field> iconFields() {
        List<Field> fields = new ArrayList<>();
        for (Field f : Icons.class.getDeclaredFields()) {
            if (f.getType() == String.class && Modifier.isStatic(f.getModifiers())) {
                f.setAccessible(true);
                fields.add(f);
            }
        }
        return fields;
    }

    @Test
    @DisplayName("ogni pulsante della toolbar ha il suo tracciato")
    void allIconsDeclared() {
        assertEquals(10, iconFields().size(),
            "il numero di icone non corrisponde ai pulsanti della toolbar");
    }

    @Test
    @DisplayName("i tracciati iniziano con un comando di spostamento")
    void pathsStartWithMoveTo() throws Exception {
        for (Field f : iconFields()) {
            String path = (String) f.get(null);
            assertFalse(path.isBlank(), f.getName() + " è vuoto");
            assertTrue(path.startsWith("M"),
                f.getName() + " non inizia con M: " + path);
        }
    }

    @Test
    @DisplayName("i tracciati contengono solo comandi SVG validi")
    void pathsUseValidCommands() throws Exception {
        for (Field f : iconFields()) {
            String path = (String) f.get(null);
            assertTrue(path.matches("[MmLlHhVvCcSsQqTtAaZz0-9eE.,\\-\\s]+"),
                f.getName() + " contiene caratteri non ammessi: " + path);
            assertTrue(path.endsWith("z") || path.endsWith("Z"),
                f.getName() + " non è chiuso: una forma aperta non si riempie");
        }
    }

    @Test
    @DisplayName("nessun tracciato è rimasto un doppione di un altro")
    void iconsAreDistinct() throws Exception {
        List<String> paths = new ArrayList<>();
        for (Field f : iconFields()) paths.add((String) f.get(null));

        assertEquals(paths.size(), paths.stream().distinct().count(),
            "due pulsanti mostrerebbero la stessa icona");
    }
}
