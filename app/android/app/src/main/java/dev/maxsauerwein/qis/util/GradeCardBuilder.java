package dev.maxsauerwein.qis.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import dev.maxsauerwein.qis.model.AttemptRow;
import dev.maxsauerwein.qis.model.GradeTable;
import dev.maxsauerwein.qis.model.ModuleCardData;
import dev.maxsauerwein.qis.storage.GradeSettingsStore;

/**
 * Wandelt die flachen GradeTable-Zeilen in eine Karte pro Modul um (Detail-/Versuchszeilen
 * werden darunter gruppiert und mit dem umgebenden Kernmodule-/Pflichtmodule-/
 * Wahlpflichtmodule-Abschnitt versehen).
 */
public final class GradeCardBuilder {

    /** NZ = nicht zugelassen: die Prüfungsvorleistung wurde nicht bekommen, daher gab es keine
     *  Zulassung zur Prüfung. Eigener Status "Nicht zugelassen". */
    private static final Set<String> NOT_ADMITTED_REMARKS = new HashSet<>(Collections.singletonList("nz"));
    /** RT = Rücktritt, NE = nicht erschienen, KR = krank: trotz Zulassung (PV vorhanden) wurde die
     *  Prüfung nicht geschrieben. Status "Kein Ergebnis". */
    private static final Set<String> WITHDRAWN_REMARKS = new HashSet<>(Arrays.asList("rt", "ne", "kr"));

    private GradeCardBuilder() {
    }

    public static List<ModuleCardData> buildCards(GradeTable table, GradeSettingsStore.Settings settings) {
        return buildCards(table, settings, Collections.emptySet());
    }

    public static List<ModuleCardData> buildCards(GradeTable table, GradeSettingsStore.Settings settings,
                                                    Set<String> newModuleKeys) {
        int prüfungstextIndex = GradeStyling.columnIndex(table.header, "prüfungstext");
        int statusIndex = GradeStyling.columnIndex(table.header, "status");
        int noteIndex = GradeStyling.columnIndex(table.header, "note");
        int ectsIndex = GradeStyling.columnIndex(table.header, "ects");
        int semesterIndex = GradeStyling.columnIndex(table.header, "semester");
        int versuchIndex = GradeStyling.columnIndex(table.header, "versuch");
        int datumIndex = GradeStyling.columnIndex(table.header, "datum");

        List<List<String>> rows = table.rows;
        List<ModuleCardData> cards = new ArrayList<>();
        String currentSectionTitle = null;
        int index = 0;

        while (index < rows.size()) {
            List<String> row = rows.get(index);

            if (GradeStyling.isSectionRow(row, prüfungstextIndex)) {
                String text = value(prüfungstextIndex, row).trim();
                currentSectionTitle = text.isEmpty() ? null : text;
                index++;
                continue;
            }

            if (!GradeStyling.isModuleRow(row, prüfungstextIndex)) {
                index++;
                continue;
            }

            List<List<String>> detailRows = new ArrayList<>();
            int lookahead = index + 1;
            while (lookahead < rows.size()
                    && !GradeStyling.isModuleRow(rows.get(lookahead), prüfungstextIndex)
                    && !GradeStyling.isSectionRow(rows.get(lookahead), prüfungstextIndex)) {
                detailRows.add(rows.get(lookahead));
                lookahead++;
            }
            index = lookahead;

            List<List<String>> filteredDetails = detailRows;
            if (settings.hideStudienleistungen) {
                List<List<String>> filtered = new ArrayList<>();
                for (List<String> detail : filteredDetails) {
                    if (!value(prüfungstextIndex, detail).toLowerCase(Locale.GERMAN).contains("studienleistung")) {
                        filtered.add(detail);
                    }
                }
                filteredDetails = filtered;
            }

            List<AttemptRow> attempts = new ArrayList<>();
            for (List<String> detail : filteredDetails) {
                attempts.add(new AttemptRow(
                        value(semesterIndex, detail).trim(),
                        value(noteIndex, detail).trim(),
                        value(versuchIndex, detail).trim(),
                        value(datumIndex, detail).trim(),
                        value(statusIndex, detail)
                ));
            }

            String moduleName = collapseWhitespace(value(prüfungstextIndex, row).replace("Modul:", ""));

            // Über alle (ungefilterten) Detailzeilen scannen, da der Vermerk in einer beliebigen
            // Spalte stehen kann und auch für ausgeblendete Studienleistungen relevant bleibt.
            GradeStyling.StatusCategory remarkStatus = containsRemark(detailRows, NOT_ADMITTED_REMARKS)
                    ? GradeStyling.StatusCategory.NOT_ADMITTED
                    : containsRemark(detailRows, WITHDRAWN_REMARKS)
                            ? GradeStyling.StatusCategory.NO_RESULT
                            : null;

            cards.add(new ModuleCardData(
                    currentSectionTitle,
                    moduleName,
                    value(noteIndex, row).trim(),
                    value(statusIndex, row),
                    value(ectsIndex, row).trim(),
                    attempts,
                    newModuleKeys.contains(moduleName),
                    remarkStatus
            ));
        }

        return cards;
    }

    public static double totalEarnedEcts(List<ModuleCardData> cards) {
        double total = 0;
        for (ModuleCardData card : cards) {
            if (card.statusCategory() != GradeStyling.StatusCategory.BE) {
                continue;
            }
            try {
                total += Double.parseDouble(card.ects.replace(",", "."));
            } catch (NumberFormatException ignored) {
                // kein auswertbarer ECTS-Wert, wird übersprungen
            }
        }
        return total;
    }

    public enum Filter { ALL, PASSED, OPEN }

    public static List<ModuleCardData> filtered(List<ModuleCardData> cards, Filter filter) {
        if (filter == Filter.ALL) {
            return cards;
        }
        List<ModuleCardData> result = new ArrayList<>();
        for (ModuleCardData card : cards) {
            boolean isPassed = card.statusCategory() == GradeStyling.StatusCategory.BE;
            if ((filter == Filter.PASSED) == isPassed) {
                result.add(card);
            }
        }
        return result;
    }

    public enum Sort { NONE, GRADE, SEMESTER }

    public static List<ModuleCardData> sorted(List<ModuleCardData> cards, Sort sort) {
        List<ModuleCardData> result = new ArrayList<>(cards);
        switch (sort) {
            case GRADE:
                result.sort((lhs, rhs) -> {
                    Double l = parseGrade(lhs.grade);
                    Double r = parseGrade(rhs.grade);
                    if (l == null && r == null) return 0;
                    if (l == null) return 1;
                    if (r == null) return -1;
                    return Double.compare(l, r);
                });
                break;
            case SEMESTER:
                result.sort((lhs, rhs) -> Integer.compare(
                        semesterKey(lastSemester(rhs)), semesterKey(lastSemester(lhs))));
                break;
            case NONE:
            default:
                break;
        }
        return result;
    }

    private static String lastSemester(ModuleCardData card) {
        if (card.attempts.isEmpty()) {
            return null;
        }
        return card.attempts.get(card.attempts.size() - 1).semester;
    }

    private static Double parseGrade(String grade) {
        try {
            return Double.parseDouble(grade.replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Sortierbarer chronologischer Schlüssel für deutsche Semesterbezeichnungen ("WiSe 23/24",
     *  "SoSe 24"); fehlende Semester rutschen unabhängig von der Sortierrichtung ans Ende. */
    private static int semesterKey(String text) {
        if (text == null || text.isEmpty()) {
            return Integer.MIN_VALUE;
        }
        String normalized = text.toLowerCase(Locale.GERMAN);
        String digits = text.replaceAll("[^0-9]", "");
        if (digits.length() < 2) {
            return Integer.MIN_VALUE;
        }
        int firstTwoDigits;
        try {
            firstTwoDigits = Integer.parseInt(digits.substring(0, 2));
        } catch (NumberFormatException e) {
            return Integer.MIN_VALUE;
        }
        if (normalized.startsWith("wise")) {
            return firstTwoDigits * 2;
        } else if (normalized.startsWith("sose")) {
            return (firstTwoDigits - 1) * 2 + 1;
        }
        return Integer.MIN_VALUE;
    }

    private static boolean containsRemark(List<List<String>> detailRows, Set<String> remarks) {
        for (List<String> detailRow : detailRows) {
            for (String cell : detailRow) {
                if (remarks.contains(cell.trim().toLowerCase(Locale.GERMAN))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String value(int index, List<String> row) {
        if (index < 0 || index >= row.size()) {
            return "";
        }
        return row.get(index);
    }

    private static String collapseWhitespace(String text) {
        String[] parts = text.trim().split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(part);
        }
        return builder.toString();
    }
}
