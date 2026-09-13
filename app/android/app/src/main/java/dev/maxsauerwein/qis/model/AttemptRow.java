package dev.maxsauerwein.qis.model;

import java.util.Locale;

public final class AttemptRow {
    public final String semester;
    public final String note;
    public final String versuch;
    public final String datum;
    public final String status;

    public AttemptRow(String semester, String note, String versuch, String datum, String status) {
        this.semester = semester;
        this.note = note;
        this.versuch = versuch;
        this.datum = datum;
        this.status = status;
    }

    public boolean isFailed() {
        String normalized = status.trim().toLowerCase(Locale.GERMAN);
        return normalized.equals("nb") || normalized.equals("en");
    }
}
