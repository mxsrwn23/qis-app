package dev.maxsauerwein.qis.util;

import java.util.ArrayList;
import java.util.List;

import dev.maxsauerwein.qis.model.GradeTable;
import dev.maxsauerwein.qis.storage.GradeSettingsStore;

/**
 * Modul-Gruppierung und gewichtete Durchschnittsberechnung, portiert aus der qis-extension
 * (groupRowsByModule / calcAvgGrade in entrypoints/qis-content.content/index.js).
 */
public final class GradeAnalysis {

    private GradeAnalysis() {
    }

    public static final class ModuleGroup {
        public final int headerRowIndex;
        public final List<Integer> detailRowIndices = new ArrayList<>();

        ModuleGroup(int headerRowIndex) {
            this.headerRowIndex = headerRowIndex;
        }
    }

    /**
     * Gruppiert Zeilen unter die nächstgelegene vorangehende "Modul: …"-Kopfzeile; eine
     * Abschnitts-Trennzeile (Kernmodule/Pflichtmodule/Wahlpflichtmodule) beendet die aktuelle
     * Gruppe.
     */
    public static List<ModuleGroup> moduleGroups(List<List<String>> rows, int prüfungstextIndex) {
        List<ModuleGroup> groups = new ArrayList<>();
        ModuleGroup current = null;
        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (GradeStyling.isModuleRow(row, prüfungstextIndex)) {
                current = new ModuleGroup(i);
                groups.add(current);
            } else if (GradeStyling.isSectionRow(row, prüfungstextIndex)) {
                current = null;
            } else if (current != null) {
                current.detailRowIndices.add(i);
            }
        }
        return groups;
    }

    private static final class Attempt {
        final double grade;
        final double ects;
        final int versuch;

        Attempt(double grade, double ects, int versuch) {
            this.grade = grade;
            this.ects = ects;
            this.versuch = versuch;
        }
    }

    private static Double parseGermanDecimal(String value) {
        try {
            return Double.parseDouble(value.replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<Attempt> attempts(ModuleGroup group, List<List<String>> rows, int noteIndex, int ectsIndex, int versuchIndex) {
        List<Attempt> attempts = new ArrayList<>();
        for (int index : group.detailRowIndices) {
            List<String> row = rows.get(index);
            if (noteIndex >= row.size() || ectsIndex >= row.size()) {
                continue;
            }
            Double grade = parseGermanDecimal(row.get(noteIndex));
            Double ects = parseGermanDecimal(row.get(ectsIndex));
            if (grade == null || grade <= 0 || grade >= 5 || ects == null || ects <= 0) {
                continue;
            }
            int versuch = 0;
            if (versuchIndex >= 0 && versuchIndex < row.size()) {
                try {
                    versuch = Integer.parseInt(row.get(versuchIndex).trim());
                } catch (NumberFormatException ignored) {
                    versuch = 0;
                }
            }
            attempts.add(new Attempt(grade, ects, versuch));
        }
        return attempts;
    }

    /**
     * ECTS-gewichteter Durchschnitt über alle Module: "all" summiert jeden gültigen Versuch,
     * "last" wählt pro Modul den Versuch mit der höchsten Versuchsnummer, "best" wählt die beste
     * (niedrigste) Note.
     */
    public static Double average(GradeTable table, GradeSettingsStore.AverageMode mode) {
        int noteIndex = GradeStyling.columnIndex(table.header, "note");
        int ectsIndex = GradeStyling.columnIndex(table.header, "ects");
        if (noteIndex < 0 || ectsIndex < 0) {
            return null;
        }
        int prüfungstextIndex = GradeStyling.columnIndex(table.header, "prüfungstext");
        int versuchIndex = GradeStyling.columnIndex(table.header, "versuch");
        List<ModuleGroup> groups = moduleGroups(table.rows, prüfungstextIndex);

        double weightedSum = 0;
        double totalEcts = 0;

        for (ModuleGroup group : groups) {
            List<Attempt> groupAttempts = attempts(group, table.rows, noteIndex, ectsIndex, versuchIndex);
            if (groupAttempts.isEmpty()) {
                continue;
            }

            List<Attempt> selected;
            switch (mode) {
                case LAST: {
                    Attempt best = groupAttempts.get(0);
                    for (Attempt a : groupAttempts) {
                        if (a.versuch >= best.versuch) {
                            best = a;
                        }
                    }
                    selected = new ArrayList<>();
                    selected.add(best);
                    break;
                }
                case BEST: {
                    Attempt best = groupAttempts.get(0);
                    for (Attempt a : groupAttempts) {
                        if (a.grade < best.grade) {
                            best = a;
                        }
                    }
                    selected = new ArrayList<>();
                    selected.add(best);
                    break;
                }
                default:
                    selected = groupAttempts;
            }

            for (Attempt attempt : selected) {
                weightedSum += attempt.grade * attempt.ects;
                totalEcts += attempt.ects;
            }
        }

        if (totalEcts <= 0) {
            return null;
        }
        return weightedSum / totalEcts;
    }
}
