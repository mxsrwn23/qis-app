package dev.maxsauerwein.qis.model;

import java.util.List;

public final class GradeTable {
    public final List<String> header;
    public final List<List<String>> rows;
    /** "(angestrebter) Abschluss" / "Fach" aus der Stammdaten-Tabelle oberhalb der Notentabelle,
     *  z. B. "Bachelor of Science" / "Informatik (dual) (PO-Version 2024)". Kann null sein, falls
     *  nicht vorhanden. */
    public final String abschluss;
    public final String fach;

    public GradeTable(List<String> header, List<List<String>> rows, String abschluss, String fach) {
        this.header = header;
        this.rows = rows;
        this.abschluss = abschluss;
        this.fach = fach;
    }
}
