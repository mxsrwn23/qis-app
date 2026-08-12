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

    public ModuleCardData(String sectionTitle, String moduleName, String grade, String status,
                           String ects, List<AttemptRow> attempts, boolean isNew) {
        this.sectionTitle = sectionTitle;
        this.moduleName = moduleName;
        this.grade = grade;
        this.status = status;
        this.ects = ects;
        this.attempts = attempts;
        this.isNew = isNew;
    }

    public GradeStyling.StatusCategory statusCategory() {
        switch (status.trim().toLowerCase(Locale.GERMAN)) {
            case "be": return GradeStyling.StatusCategory.BE;
            case "pv": return GradeStyling.StatusCategory.PV;
            case "nb":
            case "en": return GradeStyling.StatusCategory.FAIL;
            case "an": return GradeStyling.StatusCategory.AN;
            default: return null;
        }
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
            default: return status.trim();
        }
    }
}
