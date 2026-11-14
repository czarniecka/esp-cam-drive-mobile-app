import SwiftUI

/// Odświeża widok, gdy zmieni się stan logowania do Google.
private struct WithAuth<Content: View>: View {
    @ObservedObject var auth: GoogleAuth
    @ViewBuilder let content: (Bool) -> Content
    var body: some View { content(auth.isSignedIn) }
}

struct ContentView: View {
    @StateObject private var model = AppModel()
    @AppStorage("host") private var host = ""
    @AppStorage("folder") private var folder = "ESP32-CAM"
    @AppStorage("autoMode") private var autoMode = false
    @AppStorage("intervalSec") private var interval = 1.0
    @AppStorage("deleteAfter") private var deleteAfter = true
    @State private var showSettings = false

    private var hostEmpty: Bool { host.trimmingCharacters(in: .whitespaces).isEmpty }

    var body: some View {
        NavigationStack {
            WithAuth(auth: model.auth) { signedIn in
                List {
                    if hostEmpty || !signedIn { setupBanner(signedIn: signedIn) }
                    previewSection
                    controlsSection(signedIn: signedIn)
                    if autoMode && (model.autoRunning || model.autoStart != nil) { statsSection }
                    if !model.items.isEmpty { filesSection }
                }
            }
            .navigationTitle("ESP Cam Drive")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button { showSettings = true } label: { Image(systemName: "gearshape") }
                }
            }
            .sheet(isPresented: $showSettings) {
                SettingsView(model: model, auth: model.auth)
            }
            .onAppear { if hostEmpty { showSettings = true } }
        }
    }

    // MARK: Sekcje

    private func setupBanner(signedIn: Bool) -> some View {
        Section {
            VStack(alignment: .leading, spacing: 8) {
                Label("Najpierw skonfiguruj aplikację", systemImage: "exclamationmark.triangle.fill")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.orange)
                Text([hostEmpty ? "podaj adres IP kamery" : nil,
                      signedIn ? nil : "zaloguj się do Google"]
                        .compactMap { $0 }.joined(separator: ", ").capitalizedFirst)
                    .font(.footnote).foregroundStyle(.secondary)
                Button("Otwórz ustawienia") { showSettings = true }
                    .buttonStyle(.borderless)
            }
        }
    }

    private var statusColor: Color {
        if model.statusIsError { return .red }
        if model.autoRunning || model.recording { return .green }
        return .gray
    }

    private var previewSection: some View {
        Section {
            ZStack {
                RoundedRectangle(cornerRadius: 12).fill(Color.secondary.opacity(0.12))
                if let img = model.lastImage {
                    Image(uiImage: img)
                        .resizable().scaledToFit()
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                } else {
                    VStack(spacing: 8) {
                        Image(systemName: "camera.viewfinder").font(.largeTitle)
                        Text("Brak podglądu").font(.footnote)
                    }
                    .foregroundStyle(.secondary)
                }
            }
            .frame(height: 220)
            .overlay(alignment: .topLeading) {
                if model.recording {
                    Label("REC \(formatDuration(Double(model.elapsed)))", systemImage: "circle.fill")
                        .font(.caption.weight(.bold)).monospacedDigit()
                        .foregroundStyle(.white)
                        .padding(.horizontal, 8).padding(.vertical, 4)
                        .background(Color.red, in: Capsule())
                        .padding(8)
                }
            }
            .listRowInsets(EdgeInsets(top: 8, leading: 8, bottom: 8, trailing: 8))

            HStack(alignment: .top, spacing: 8) {
                Circle().fill(statusColor).frame(width: 10, height: 10).padding(.top, 4)
                Text(model.status).font(.footnote)
            }
        }
    }

    private func controlsSection(signedIn: Bool) -> some View {
        Section("Sterowanie") {
            Picker("Tryb", selection: $autoMode) {
                Text("Ręczny").tag(false)
                Text("Cykliczny").tag(true)
            }
            .pickerStyle(.segmented)
            .disabled(model.autoRunning || model.recording)

            Text(autoMode
                 ? "Zdjęcia robią się same w wybranym odstępie i od razu trafiają na Dysk."
                 : "Zdjęcie lub film pojawia się na liście, na Dysk wysyłasz je przyciskiem przy pliku.")
                .font(.footnote).foregroundStyle(.secondary)

            if autoMode {
                Picker("Odstęp", selection: $interval) {
                    ForEach(intervalOptions, id: \.self) { Text(formatInterval($0)).tag($0) }
                }
                .pickerStyle(.menu)
                .disabled(model.autoRunning)

                if interval < 1 {
                    Text("Przy takim odstępie kamera i internet muszą wyrabiać ok. \(Int((1 / interval).rounded())) zdjęcia na sekundę. Jeśli nie zdążą, spóźnione klatki zostaną pominięte.")
                        .font(.footnote).foregroundStyle(.orange)
                }

                Toggle("Usuń z telefonu po wysłaniu", isOn: $deleteAfter)
                    .disabled(model.autoRunning)

                autoButton(signedIn: signedIn)
                    .listRowBackground(Color.clear)
            }

            manualButtons
                .listRowBackground(Color.clear)
        }
    }

    @ViewBuilder
    private func autoButton(signedIn: Bool) -> some View {
        if model.autoRunning {
            Button(role: .destructive) { model.stopAuto() } label: {
                Label("Zatrzymaj", systemImage: "stop.fill").frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent).tint(.red).controlSize(.large)
        } else {
            Button {
                model.startAuto(host: host, interval: interval, folder: folder, deleteAfter: deleteAfter)
            } label: {
                Label("Start: zdjęcie co \(formatInterval(interval))", systemImage: "play.fill")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent).tint(.green).controlSize(.large)
            .disabled(hostEmpty || !signedIn || model.busy)
        }
    }

    private var manualButtons: some View {
        HStack(spacing: 12) {
            Button {
                Task { await model.photo(host: host, autoUpload: autoMode, folder: folder) }
            } label: {
                Label("Zdjęcie", systemImage: "camera.fill").frame(maxWidth: .infinity)
            }
            .disabled(model.busy || model.autoRunning || hostEmpty)

            if model.recording {
                Button(role: .destructive) { model.stopRecording() } label: {
                    Label("Zakończ", systemImage: "stop.circle.fill").frame(maxWidth: .infinity)
                }
            } else {
                Button {
                    Task { await model.startRecording(host: host, autoUpload: autoMode, folder: folder) }
                } label: {
                    Label("Nagrywaj", systemImage: "record.circle").frame(maxWidth: .infinity)
                }
                .disabled(model.busy || model.autoRunning || hostEmpty)
            }
        }
        .buttonStyle(.bordered)
        .controlSize(.large)
    }

    private var statsSection: some View {
        Section("Cykl") {
            HStack {
                StatTile(value: "\(model.captured)", label: "Zrobione")
                StatTile(value: "\(model.uploaded)", label: "Wysłane")
                StatTile(value: "\(model.pending)", label: "W kolejce")
                StatTile(value: "\(model.failed)", label: "Błędy",
                         tint: model.failed > 0 ? .red : .primary)
            }
            if let start = model.autoStart {
                LabeledContent("Czas działania") {
                    TimelineView(.periodic(from: .now, by: 1)) { ctx in
                        Text(formatDuration((model.autoEnd ?? ctx.date).timeIntervalSince(start)))
                            .monospacedDigit()
                    }
                }
            }
        }
    }

    private var filesSection: some View {
        Section("Ostatnie pliki") {
            ForEach(model.items) { item in
                ItemRow(item: item) {
                    Task { await model.uploadItem(item.id, folder: folder) }
                }
            }
        }
    }
}

// MARK: Ustawienia

private struct SettingsView: View {
    @ObservedObject var model: AppModel
    @ObservedObject var auth: GoogleAuth
    @AppStorage("host") private var host = ""
    @AppStorage("folder") private var folder = "ESP32-CAM"
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Adres IP, np. 192.168.1.50", text: $host)
                        .keyboardType(.numbersAndPunctuation)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                } header: {
                    Text("Kamera ESP32-CAM")
                } footer: {
                    Text("Telefon musi być w tej samej sieci WiFi co kamera. Kamera musi działać ze szkicem Arduino CameraWebServer.")
                }

                Section {
                    GoogleRow(auth: auth) { Task { await model.signIn() } }
                    TextField("Folder na Dysku", text: $folder)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                } header: {
                    Text("Dysk Google")
                } footer: {
                    Text("Aplikacja widzi tylko foldery i pliki, które sama utworzyła. Jeśli folder o tej nazwie nie istnieje, zostanie założony.")
                }

                if !model.status.isEmpty {
                    Section("Status") {
                        Text(model.status).font(.footnote)
                            .foregroundStyle(model.statusIsError ? .red : .secondary)
                    }
                }
            }
            .navigationTitle("Ustawienia")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Gotowe") { dismiss() }
                }
            }
        }
    }
}

// MARK: Elementy pomocnicze

private struct StatTile: View {
    let value: String
    let label: String
    var tint: Color = .primary

    var body: some View {
        VStack(spacing: 2) {
            Text(value).font(.title3.weight(.semibold)).monospacedDigit().foregroundStyle(tint)
            Text(label).font(.caption2).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
    }
}

private struct GoogleRow: View {
    @ObservedObject var auth: GoogleAuth
    let signIn: () -> Void

    var body: some View {
        if auth.isSignedIn {
            HStack {
                Label("Zalogowano", systemImage: "checkmark.circle.fill").foregroundStyle(.green)
                Spacer()
                Button("Wyloguj") { auth.signOut() }.buttonStyle(.borderless)
            }
        } else {
            Button(action: signIn) { Label("Zaloguj się do Google", systemImage: "person.crop.circle") }
        }
    }
}

private struct ItemRow: View {
    let item: Item
    let onUpload: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Group {
                if let t = item.thumb {
                    Image(uiImage: t).resizable().scaledToFill()
                } else {
                    Image(systemName: item.isVideo ? "video.fill" : "photo").foregroundStyle(.secondary)
                }
            }
            .frame(width: 56, height: 56)
            .background(Color.secondary.opacity(0.15))
            .clipShape(RoundedRectangle(cornerRadius: 8))

            Text(item.url.lastPathComponent).font(.footnote)
            Spacer()
            if item.uploaded {
                Image(systemName: "checkmark.icloud.fill").foregroundStyle(.green)
            } else {
                Button(action: onUpload) { Image(systemName: "icloud.and.arrow.up") }
                    .buttonStyle(.borderless)
            }
        }
    }
}

private extension String {
    var capitalizedFirst: String { prefix(1).uppercased() + dropFirst() }
}
