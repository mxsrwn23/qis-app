package dev.maxsauerwein.qis.model;

import java.util.Objects;

/** Ein (Abschluss, Fach)-Paar, exakt wie es aus den echten QIS-Stammdaten des eingeloggten
 *  Nutzers gelesen wurde (Rohwerte, inkl. "(PO-Version JJJJ)" bei fach). Diese Optionen bilden die
 *  einzige Quelle für Auswahlmöglichkeiten in der Profil-Verwaltung -- es gibt bewusst keine
 *  hartkodierte Liste von Abschlüssen oder Studiengängen irgendwo in der App. */
public final class DegreeOption {
    public final String abschluss;
    public final String fach;

    public DegreeOption(String abschluss, String fach) {
        this.abschluss = abschluss;
        this.fach = fach;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DegreeOption)) return false;
        DegreeOption that = (DegreeOption) other;
        return Objects.equals(abschluss, that.abschluss) && Objects.equals(fach, that.fach);
    }

    @Override
    public int hashCode() {
        return Objects.hash(abschluss, fach);
    }
}
