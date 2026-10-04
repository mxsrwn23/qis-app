import SwiftUI

/// Einmaliger Einrichtungsschritt, wenn QIS beim Login mehrere Abschluss-Typen und/oder
/// Studiengänge für den Nutzer meldet (Szenario 2 und/oder 3 der Profil-Logik): zeigt
/// ausschließlich die tatsächlich in `tables` gefundenen Optionen zur Auswahl an -- nie eine
/// hartkodierte Liste. RootView zeigt diese View, solange `GradeSettings.needsDegreeSetup`
/// zutrifft, und ruft `onConfirmed` auf, sobald der Nutzer eine vollständige Wahl getroffen hat.
/// Bewusst als eigenständiger, immersiver Onboarding-Screen gestaltet (kein Settings-Formular):
/// das hier ist ein einmaliger Moment, kein wiederkehrender Einstellungs-Dialog.
struct DegreeSetupView: View {
    let tables: [GradeTable]
    let onConfirmed: () -> Void

    @State private var settings: GradeSettings

    init(tables: [GradeTable], onConfirmed: @escaping () -> Void) {
        self.tables = tables
        self.onConfirmed = onConfirmed
        // Wendet dieselbe Auto-Erkennung wie RootView.present() erneut an, statt sich darauf zu
        // verlassen, dass sie bereits gelaufen ist -- macht die View robust gegen einen
        // zukünftigen Aufrufer, der sie ohne diesen Vorab-Schritt präsentiert.
        _settings = State(initialValue: GradeSettings.loadApplyingAutoDetection(from: tables))
    }
    @State private var hasAppeared = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var abschlussOptions: [String] { settings.distinctAbschlussOptions }
    private var fachOptions: [String] { settings.fachOptions(forAbschluss: settings.selectedAbschluss) }
    private var canConfirm: Bool {
        !settings.selectedAbschluss.isEmpty && !settings.selectedFach.isEmpty
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 32) {
                header

                VStack(spacing: 22) {
                    if abschlussOptions.count > 1 {
                        optionGroup(
                            title: "Abschluss-Typ",
                            options: abschlussOptions,
                            label: QISLabels.degreeFullName,
                            selection: $settings.selectedAbschluss
                        )
                    } else if !settings.selectedAbschluss.isEmpty {
                        confirmedRow(title: "Abschluss-Typ", value: QISLabels.degreeFullName(for: settings.selectedAbschluss))
                    }

                    if !settings.selectedAbschluss.isEmpty {
                        if fachOptions.count > 1 {
                            optionGroup(
                                title: "Studiengang",
                                options: fachOptions,
                                label: QISLabels.fachName,
                                selection: $settings.selectedFach
                            )
                        } else if !settings.selectedFach.isEmpty {
                            confirmedRow(title: "Studiengang", value: QISLabels.fachName(for: settings.selectedFach))
                        }
                    }
                }
                .opacity(hasAppeared ? 1 : 0)
                .offset(y: hasAppeared ? 0 : 12)
            }
            .padding(.horizontal, 20)
            .padding(.top, 8)
            .padding(.bottom, 24)
        }
        .background(backgroundGlow)
        .safeAreaInset(edge: .bottom) { confirmButton }
        #if os(iOS)
        .toolbar(.hidden, for: .navigationBar)
        #endif
        .onAppear(perform: animateIn)
        .onChange(of: settings.selectedAbschluss) { _, newAbschluss in
            let options = settings.fachOptions(forAbschluss: newAbschluss)
            settings.selectedFach = options.count == 1 ? options[0] : ""
        }
    }

    private func animateIn() {
        guard !hasAppeared else { return }
        guard !reduceMotion else {
            hasAppeared = true
            return
        }
        withAnimation(.spring(response: 0.6, dampingFraction: 0.75).delay(0.05)) {
            hasAppeared = true
        }
    }

    private var backgroundGlow: some View {
        LinearGradient(
            colors: [Color.accentColor.opacity(0.16), Color.accentColor.opacity(0.03), .clear],
            startPoint: .top,
            endPoint: .bottom
        )
        .ignoresSafeArea()
    }

    private var header: some View {
        VStack(spacing: 16) {
            ZStack {
                Circle()
                    .fill(
                        LinearGradient(
                            colors: [Color.accentColor, Color.accentColor.opacity(0.55)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .frame(width: 88, height: 88)
                    .shadow(color: .accentColor.opacity(0.35), radius: 18, y: 10)
                Image(systemName: "graduationcap.fill")
                    .font(.system(size: 36, weight: .semibold))
                    .foregroundStyle(.white)
            }
            .scaleEffect(hasAppeared ? 1 : 0.55)
            .opacity(hasAppeared ? 1 : 0)

            VStack(spacing: 8) {
                Text("Fast geschafft")
                    .font(.system(.largeTitle, design: .rounded).weight(.bold))
                Text("QIS hat für deinen Account mehrere Möglichkeiten gemeldet. Wähle aus, welche Noten angezeigt werden sollen, das lässt sich später jederzeit in den Einstellungen ändern.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal, 8)
            }
            .opacity(hasAppeared ? 1 : 0)
        }
        .padding(.top, 24)
    }

    private func optionGroup(
        title: String,
        options: [String],
        label: @escaping (String) -> String,
        selection: Binding<String>
    ) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title.uppercased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
                .padding(.leading, 4)

            VStack(spacing: 10) {
                ForEach(options, id: \.self) { option in
                    OptionCard(label: label(option), isSelected: selection.wrappedValue == option) {
                        if reduceMotion {
                            selection.wrappedValue = option
                        } else {
                            withAnimation(.snappy(duration: 0.22)) { selection.wrappedValue = option }
                        }
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func confirmedRow(title: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title.uppercased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
                .padding(.leading, 4)
            HStack(spacing: 10) {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundStyle(.tint)
                Text(value)
                    .font(.body.weight(.medium))
                Spacer(minLength: 0)
            }
            .padding(16)
            .background(Color.primary.opacity(0.05), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var confirmButton: some View {
        Button(action: confirm) {
            Text("Übernehmen")
                .font(.headline)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 6)
        }
        .buttonStyle(.borderedProminent)
        .controlSize(.large)
        .disabled(!canConfirm)
        .padding(.horizontal, 20)
        .padding(.vertical, 14)
        .background(.ultraThinMaterial)
    }

    private func confirm() {
        settings.save()
        onConfirmed()
    }
}

/// Große, antippbare Auswahlkarte für eine einzelne Abschluss-/Fach-Option -- statt eines
/// nüchternen System-Pickers, passend zum einmaligen Onboarding-Charakter dieses Screens.
private struct OptionCard: View {
    let label: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Text(label)
                    .font(.body.weight(.medium))
                    .foregroundStyle(.primary)
                    .multilineTextAlignment(.leading)
                Spacer(minLength: 8)
                ZStack {
                    Circle()
                        .fill(isSelected ? Color.accentColor : Color.clear)
                    Circle()
                        .strokeBorder(isSelected ? Color.clear : Color.primary.opacity(0.22), lineWidth: 1.5)
                    if isSelected {
                        Image(systemName: "checkmark")
                            .font(.caption.weight(.bold))
                            .foregroundStyle(.white)
                    }
                }
                .frame(width: 24, height: 24)
            }
            .padding(16)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(isSelected ? Color.accentColor.opacity(0.14) : Color.primary.opacity(0.045))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .strokeBorder(isSelected ? Color.accentColor : Color.clear, lineWidth: 1.5)
            )
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? [.isSelected] : [])
    }
}
