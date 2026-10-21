package com.kasper.mobsenses;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ServerTextTest {

    @Test
    void languageCodes() {
        assertEquals("ru_ru", ServerText.language("ru"));
        assertEquals("ru_ru", ServerText.language("ru-RU"));
        assertEquals("ru_ru", ServerText.language("RU_RU"));
        assertEquals("pt_br", ServerText.language("pt_pt"));
        assertEquals("es_es", ServerText.language("es_mx"));
        assertEquals("zh_cn", ServerText.language("zh_tw"));
        assertEquals("en_us", ServerText.language("fr_fr"));
        assertEquals("en_us", ServerText.language(""));
        assertEquals("en_us", ServerText.language(null));
    }

    @Test
    void parsesEscapes() {
        Map<String, String> m = ServerText.parse("{\"a\": \"x\\\"y\", \"b\":\"\\u00e9\\n\"}");
        assertEquals("x\"y", m.get("a"));
        assertEquals("é\n", m.get("b"));
    }

    @Test
    void fillsPlaceholdersAndFallsBackToEnglish() {
        assertEquals("Preset: Hardcore", ServerText.get("en_us", "status.preset", "Hardcore"));
        assertEquals("Пресет: x", ServerText.get("ru_ru", "status.preset", "x"));
        assertEquals("Preset: y", ServerText.get("xx_yy", "status.preset", "y"));
    }
}
