import Foundation

/// Ein (Abschluss, Fach)-Paar, exakt wie es aus den echten QIS-Stammdaten des eingeloggten
/// Nutzers gelesen wurde (Rohwerte, inkl. "(PO-Version JJJJ)" bei `fach`). Diese Optionen bilden
/// die einzige Quelle für Picker in der Profil-Verwaltung -- es gibt bewusst keine hartkodierte
/// Liste von Abschlüssen oder Studiengängen irgendwo in der App.
struct DegreeOption: Equatable, Codable, Hashable {
    let abschluss: String
    let fach: String
}
