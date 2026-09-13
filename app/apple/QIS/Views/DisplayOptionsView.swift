import SwiftUI

struct DisplayOptionsView: View {
    @Binding var gradeSettings: GradeSettings

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section("Anzeige") {
                    Toggle("Zeilen mit Studienleistung ausblenden", isOn: $gradeSettings.hideStudienleistungen)
                }

                Section("Notenschnitt berechnen aus") {
                    Picker("Notenschnitt", selection: $gradeSettings.averageMode) {
                        ForEach(GradeSettings.AverageMode.allCases, id: \.self) { mode in
                            Text(mode.label).tag(mode)
                        }
                    }
                    .pickerStyle(.menu)
                }

                Section("Angezeigte Felder") {
                    ForEach(GradeSettings.attemptFields, id: \.self) { field in
                        Toggle(GradeSettings.attemptFieldLabels[field] ?? field, isOn: fieldBinding(for: field))
                    }
                }

                Section("Farben") {
                    ForEach(GradeColorKey.allCases, id: \.self) { key in
                        ColorPicker(key.label, selection: colorBinding(for: key))
                    }
                    Button("Standardfarben wiederherstellen") {
                        gradeSettings.customColors = [:]
                    }
                }
            }
            .navigationTitle("Ansicht")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Fertig") { dismiss() }
                }
            }
        }
    }

    private func fieldBinding(for field: String) -> Binding<Bool> {
        Binding(
            get: { gradeSettings.visibleAttemptFields.contains(field) },
            set: { isVisible in
                if isVisible {
                    gradeSettings.visibleAttemptFields.insert(field)
                } else {
                    gradeSettings.visibleAttemptFields.remove(field)
                }
            }
        )
    }

    private func colorBinding(for key: GradeColorKey) -> Binding<Color> {
        Binding(
            get: { GradeStyling.accent(for: key, customColors: gradeSettings.customColors) },
            set: { gradeSettings.customColors[key.rawValue] = $0.hexString }
        )
    }
}
