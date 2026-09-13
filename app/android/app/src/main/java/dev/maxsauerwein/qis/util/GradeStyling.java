package dev.maxsauerwein.qis.util;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Gedämpfte, dezente Status-Farbpalette für die Kartenliste. Die Sättigung ist bewusst niedriger
 * als bei lauten Systemfarben, damit die Badges in einer dichten Liste ruhig wirken.
 * Überschreibbar über GradeSettingsStore.Settings.customColors (Farbauswahl pro Status in
 * "Ansicht").
 */
public final class GradeStyling {

    private GradeStyling() {
    }

    /** Ein Eintrag pro Farbauswahl in den "Ansicht"-Einstellungen. */
    public enum ColorKey {
        BE("be", "Bestanden", 0xFF368A50),   // gedämpftes Grün, angenähert an das Markenwaldgrün
        PV("pv", "Offen", 0xFF3A6F85),       // gedämpftes Schieferblau, angenähert an das Marken-Türkis
        FAIL("fail", "Nicht bestanden", 0xFFAA2F3D), // gedämpftes Rot, angenähert an das Markenkarmesin
        AN("an", "Angemeldet", 0xFFA96A2C);  // gedämpftes Amber, angenähert an das Markenbrandorange

        public final String key;
        public final String label;
        public final int defaultColor;

        ColorKey(String key, String label, int defaultColor) {
            this.key = key;
            this.label = label;
            this.defaultColor = defaultColor;
        }
    }

    public enum StatusCategory {
        BE(ColorKey.BE), PV(ColorKey.PV), FAIL(ColorKey.FAIL), AN(ColorKey.AN),
        /** Nicht zur Prüfung zugelassen (NZ) -- die Prüfungsvorleistung wurde nicht bekommen. Hat
         *  keine anpassbare ColorKey, siehe accent(StatusCategory, ...) unten für die feste Farbe. */
        NOT_ADMITTED(null),
        /** Prüfung trotz Zulassung nicht geschrieben (Rücktritt, nicht erschienen, krank), daher
         *  kommt in diesem Semester kein Ergebnis mehr. Ebenfalls keine anpassbare ColorKey. */
        NO_RESULT(null);

        public final ColorKey colorKey;

        StatusCategory(ColorKey colorKey) {
            this.colorKey = colorKey;
        }
    }

    private static final int NOT_ADMITTED_COLOR = 0xFFB2661F;
    private static final int NO_RESULT_COLOR = 0xFF757575;

    private static final String[] SECTION_PREFIXES = {"kernmodule", "pflichtmodule", "wahlpflichtmodule"};

    // MARK: Farbauflösung (Standardwerte, überschreibbar über GradeSettingsStore.Settings.customColors)

    public static int accent(ColorKey key, Map<String, String> customColors) {
        String hex = customColors.get(key.key);
        Integer parsed = parseHex(hex);
        return parsed != null ? parsed : key.defaultColor;
    }

    public static int backgroundTint(ColorKey key, Map<String, String> customColors) {
        return withAlpha(accent(key, customColors), 36); // ~14 % von 255
    }

    /** Farbe pro Status-Kategorie. "Nicht zugelassen" bekommt ein aufmerksamkeitsstarkes gedämpftes
     *  Amber (ein Hinweis, der auffallen soll), "Kein Ergebnis" ein neutrales Grau. Beide heben sich
     *  klar vom Blau des offenen Status ab. Entspricht GradeStyling.swifts
     *  accent(for: GradeStatusCategory, ...) auf iOS. */
    public static int accent(StatusCategory category, Map<String, String> customColors) {
        switch (category) {
            case NOT_ADMITTED: return NOT_ADMITTED_COLOR;
            case NO_RESULT: return NO_RESULT_COLOR;
            default:
                return category.colorKey != null ? accent(category.colorKey, customColors) : NO_RESULT_COLOR;
        }
    }

    public static int backgroundTint(StatusCategory category, Map<String, String> customColors) {
        return withAlpha(accent(category, customColors), 36);
    }

    private static Integer parseHex(String hex) {
        if (hex == null) {
            return null;
        }
        String value = hex.trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        if (value.length() != 6) {
            return null;
        }
        try {
            return 0xFF000000 | Integer.parseInt(value, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String toHexString(int color) {
        return String.format(Locale.ROOT, "#%06X", color & 0x00FFFFFF);
    }

    /** Ersetzt den Alpha-Kanal einer ARGB-Farbe; `alpha` liegt zwischen 0 und 255. */
    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    public static int columnIndex(List<String> header, String keyword) {
        for (int i = 0; i < header.size(); i++) {
            if (header.get(i).toLowerCase(Locale.GERMAN).contains(keyword)) {
                return i;
            }
        }
        return -1;
    }

    public static boolean isModuleRow(List<String> row, int prüfungstextIndex) {
        if (prüfungstextIndex < 0 || prüfungstextIndex >= row.size()) {
            return false;
        }
        return row.get(prüfungstextIndex).startsWith("Modul:");
    }

    public static boolean isSectionRow(List<String> row, int prüfungstextIndex) {
        if (prüfungstextIndex < 0 || prüfungstextIndex >= row.size()) {
            return false;
        }
        String normalized = row.get(prüfungstextIndex).trim().toLowerCase(Locale.GERMAN);
        for (String prefix : SECTION_PREFIXES) {
            if (normalized.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
