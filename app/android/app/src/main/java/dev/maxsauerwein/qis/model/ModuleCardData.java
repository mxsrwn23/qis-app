package dev.maxsauerwein.qis.model;

import java.util.List;
import java.util.Locale;

import dev.maxsauerwein.qis.util.GradeStyling;

public final class ModuleCardData {
    public final String sectionTitle;
    public final String moduleName;
    public final String grade;
    public final String status;
    public final String ects;
    public final List<AttemptRow> attempts;
    /** True, wenn dieses Modul seit dem letzten bekannten Stand von "offen" auf "benotet"
     *  gewechselt ist, siehe SeenGradesStore. */
    public final boolean isNew;
    /** Aus den Versuchsvermerken ermittelter Status (NOT_ADMITTED bei NZ, NO_RESULT bei RT/NE/KR),
     *  der den regulären Modulstatus ersetzt, solange noch kein finales Ergebnis vorliegt. Wird von
     *  GradeCardBuilder gesetzt. Entspricht ModuleCardData.swifts remarkStatus. */
    public final GradeStyling.StatusCategory remarkStatus;

    public ModuleCardData(String sectionTitle, String moduleName, String grade, String status,
                           String ects, List<AttemptRow> attempts, boolean isNew) {
        this(sectionTitle, moduleName, grade, status, ects, attempts, isNew, null);
    }

    public ModuleCardData(String sectionTitle, String moduleName, String grade, String status,
                           String ects, List<AttemptRow> attempts, boolean isNew,
                           GradeStyling.StatusCategory remarkStatus) {
        this.sectionTitle = sectionTitle;
        this.moduleName = moduleName;
        this.grade = grade;
        this.status = status;
        this.ects = ects;
        this.attempts = attempts;
        this.isNew = isNew;
        this.remarkStatus = remarkStatus;
    }

    private GradeStyling.StatusCategory baseStatusCategory() {
        switch (status.trim().toLowerCase(Locale.GERMAN)) {
            case "be": return GradeStyling.StatusCategory.BE;
            case "pv": return GradeStyling.StatusCategory.PV;
            case "nb":
            case "en": return GradeStyling.StatusCategory.FAIL;
            case "an": return GradeStyling.StatusCategory.AN;
            default: return null;
        }
    }

    /** Ein Modul, das noch kein finales Ergebnis hat, aber einen Vermerk trägt, aus dem in diesem
     *  Semester keine Note mehr folgt, wird als eigene Kategorie geführt -- sonst sähe es wie ein
     *  normal offenes Modul aus. */
    public GradeStyling.StatusCategory statusCategory() {
        GradeStyling.StatusCategory base = baseStatusCategory();
        if (remarkStatus != null && base != GradeStyling.StatusCategory.BE
                && base != GradeStyling.StatusCategory.FAIL) {
            return remarkStatus;
        }
        return base;
    }

    public String statusLabel() {
        GradeStyling.StatusCategory category = statusCategory();
        if (category == null) {
            return status.trim();
        }
        switch (category) {
            case BE: return "Bestanden";
            case PV: return "Offen";
            case FAIL: return "Nicht bestanden";
            case AN: return "Angemeldet";
            case NOT_ADMITTED: return "Nicht zugelassen";
            case NO_RESULT: return "Kein Ergebnis";
            default: return status.trim();
        }
    }
}
