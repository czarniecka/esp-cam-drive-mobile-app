import Foundation
import UIKit

struct Item: Identifiable {
    let id = UUID()
    let url: URL
    let isVideo: Bool
    var uploaded = false
    var thumb: UIImage?
}

@MainActor
final class AppModel: ObservableObject {
    // Ogólne
    @Published var items: [Item] = []
    @Published var status = "Skonfiguruj kamerę i konto Google (ikona ⚙︎)"
    @Published var statusIsError = false
    @Published var busy = false
    @Published var recording = false
    @Published var elapsed = 0
    @Published var lastImage: UIImage?

    // Tryb cykliczny
    @Published var autoRunning = false
    @Published var autoStart: Date?
    @Published var autoEnd: Date?
    @Published var captured = 0
    @Published var uploaded = 0
    @Published var failed = 0
    @Published var pending = 0

    let auth = GoogleAuth()
    private lazy var drive = DriveUploader(auth: auth)
    private var autoTask: Task<Void, Never>?
    private let keeper = BackgroundKeeper()
    private var stopFlag: StopFlag?
    private var cameraErrors = 0
    private var uploadErrorShown = false
    private var runningText = ""

    private func setStatus(_ text: String, error: Bool = false) {
        status = text
        statusIsError = error
    }

    // MARK: Google

    func signIn() async {
        do { try await auth.signIn(); setStatus("Zalogowano do Google") }
        catch { setStatus("Logowanie: \(error.localizedDescription)", error: true) }
    }

    // MARK: Zdjęcie / film (ręcznie)

    func photo(host: String, autoUpload: Bool, folder: String) async {
        guard !busy else { return }
        busy = true
        defer { busy = false }
        setStatus("Pobieram zdjęcie…")
        do {
            let data = try await CameraClient(host: host).captureJPEG()
            let url = Self.newFileURL(prefix: "IMG", ext: "jpg")
            try data.write(to: url)
            let image = UIImage(data: data)
            lastImage = image?.preparingThumbnail(of: CGSize(width: 800, height: 800))
            let item = Item(url: url, isVideo: false,
                            thumb: image?.preparingThumbnail(of: CGSize(width: 120, height: 120)))
            insert(item)
            setStatus("Zdjęcie zapisane")
            if autoUpload { await uploadItem(item.id, folder: folder) }
        } catch {
            setStatus("Błąd: \(error.localizedDescription)", error: true)
        }
    }

    /// Nagrywa, dopóki nie wywołasz stopRecording().
    func startRecording(host: String, autoUpload: Bool, folder: String) async {
        guard !busy else { return }
        busy = true
        recording = true
        elapsed = 0
        let flag = StopFlag()
        stopFlag = flag
        let started = Date()
        let ticker = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                self?.elapsed = Int(Date().timeIntervalSince(started))
            }
        }
        setStatus("Nagrywam… (naciśnij Zakończ)")
        defer { busy = false }
        do {
            let url = Self.newFileURL(prefix: "VID", ext: "mp4")
            try await MJPEGRecorder.record(camera: CameraClient(host: host), stop: flag, to: url)
            ticker.cancel()
            recording = false
            stopFlag = nil
            let item = Item(url: url, isVideo: true)
            insert(item)
            setStatus("Film zapisany")
            if autoUpload { await uploadItem(item.id, folder: folder) }
        } catch {
            ticker.cancel()
            recording = false
            stopFlag = nil
            setStatus("Błąd: \(error.localizedDescription)", error: true)
        }
    }

    func stopRecording() {
        stopFlag?.set()
        setStatus("Kończę nagrywanie…")
    }

    private func insert(_ item: Item) {
        items.insert(item, at: 0)
        if items.count > 50 { items.removeLast(items.count - 50) }
    }

    // MARK: Wysyłka (ręczna)

    func uploadItem(_ id: UUID, folder: String) async {
        guard let i = items.firstIndex(where: { $0.id == id }) else { return }
        let item = items[i]
        setStatus("Wysyłam \(item.url.lastPathComponent)…")
        do {
            try await drive.upload(fileURL: item.url,
                                   mime: item.isVideo ? "video/mp4" : "image/jpeg",
                                   folderName: folder)
            if let j = items.firstIndex(where: { $0.id == id }) { items[j].uploaded = true }
            setStatus("Wysłano na Dysk: \(item.url.lastPathComponent)")
        } catch {
            setStatus("Błąd wysyłki: \(error.localizedDescription)", error: true)
        }
    }

    // MARK: Tryb cykliczny

    /// Zdjęcia robione ze stałą częstotliwością. Wysyłka działa równolegle w tle,
    /// więc wolne łącze nie opóźnia kolejnych zdjęć.
    func startAuto(host: String, interval: Double, folder: String, deleteAfter: Bool) {
        guard autoTask == nil else { return }
        captured = 0; uploaded = 0; failed = 0; pending = 0
        cameraErrors = 0
        uploadErrorShown = false
        autoStart = Date()
        autoEnd = nil
        autoRunning = true
        runningText = "Cykl działa: zdjęcie co \(formatInterval(interval))"

        keeper.start()   // lokalizacja w tle utrzymuje aplikację przy życiu po zgaszeniu ekranu
        if keeper.isDenied {
            setStatus("Włącz lokalizację dla aplikacji w Ustawieniach, inaczej cykl zatrzyma się po zgaszeniu ekranu", error: true)
        } else {
            setStatus(runningText)
        }

        let camera = CameraClient(host: host)
        autoTask = Task { [weak self] in
            let clock = ContinuousClock()
            let step = Duration.milliseconds(Int64((interval * 1000).rounded()))
            var next = clock.now
            while !Task.isCancelled {
                guard let self else { break }
                next = next.advanced(by: step)
                await self.autoShot(camera: camera, folder: folder, deleteAfter: deleteAfter)
                if clock.now > next { next = clock.now }   // spóźnione klatki pomijamy, nie nadganiamy
                try? await clock.sleep(until: next)
            }
        }
    }

    func stopAuto() {
        autoTask?.cancel()
        autoTask = nil
        autoRunning = false
        autoEnd = Date()
        keeper.stop()
        setStatus("Zatrzymano")
    }

    private func autoShot(camera: CameraClient, folder: String, deleteAfter: Bool) async {
        do {
            let data = try await camera.captureJPEG(timeout: 5)
            if Task.isCancelled { return }
            let url = Self.newFileURL(prefix: "IMG", ext: "jpg")
            try data.write(to: url)
            captured += 1
            lastImage = UIImage(data: data)?.preparingThumbnail(of: CGSize(width: 800, height: 800))
            if cameraErrors > 0 {
                cameraErrors = 0
                setStatus(runningText)
            }
            pending += 1
            Task { [weak self] in
                await self?.autoUpload(url: url, folder: folder, deleteAfter: deleteAfter)
            }
        } catch {
            if Task.isCancelled { return }
            cameraErrors += 1
            failed += 1
            setStatus("Kamera nie odpowiada (\(cameraErrors) z rzędu): \(error.localizedDescription)", error: true)
        }
    }

    private func autoUpload(url: URL, folder: String, deleteAfter: Bool) async {
        do {
            try await drive.upload(fileURL: url, mime: "image/jpeg", folderName: folder)
            uploaded += 1
            if deleteAfter { try? FileManager.default.removeItem(at: url) }
            if uploadErrorShown && cameraErrors == 0 {
                uploadErrorShown = false
                if autoRunning { setStatus(runningText) }
            }
        } catch {
            // Plik zostaje na telefonie (Pliki → ESP Cam Drive → ESPCam).
            failed += 1
            uploadErrorShown = true
            setStatus("Błąd wysyłki: \(error.localizedDescription)", error: true)
        }
        pending -= 1
    }

    // MARK: Pliki

    private static let stampFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyyMMdd_HHmmss_SSS"   // z milisekundami: przy 0,5 s nazwy muszą być unikalne
        return f
    }()

    private static func newFileURL(prefix: String, ext: String) -> URL {
        let dir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("ESPCam", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.appendingPathComponent("\(prefix)_\(stampFormatter.string(from: Date())).\(ext)")
    }
}
