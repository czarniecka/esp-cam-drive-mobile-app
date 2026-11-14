// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "ESPCamDrive",
    platforms: [.iOS(.v16)],
    products: [
        .library(name: "ESPCamDrive", targets: ["ESPCamDrive"]),
    ],
    targets: [
        .target(name: "ESPCamDrive"),
    ]
)
