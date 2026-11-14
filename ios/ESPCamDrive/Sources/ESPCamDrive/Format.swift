import Foundation

/// Dostępne odstępy między zdjęciami w trybie cyklicznym (sekundy).
let intervalOptions: [Double] = [0.5, 1, 2, 3, 5, 10, 15, 30, 60, 120, 300, 600, 1800, 3600]

func formatInterval(_ s: Double) -> String {
    if s >= 60, s.truncatingRemainder(dividingBy: 60) == 0 { return "\(Int(s / 60)) min" }
    if s.truncatingRemainder(dividingBy: 1) == 0 { return "\(Int(s)) s" }
    return String(format: "%.1f s", s).replacingOccurrences(of: ".", with: ",")
}

func formatDuration(_ t: TimeInterval) -> String {
    let s = max(0, Int(t))
    let h = s / 3600, m = (s % 3600) / 60, sec = s % 60
    return h > 0 ? String(format: "%d:%02d:%02d", h, m, sec)
                 : String(format: "%d:%02d", m, sec)
}
