# Luno

A quick access tool.

## Build

    nix develop -c ./gradlew :app:assembleDebug

`nix develop` provides JDK 17 and the Android SDK (see `flake.nix`).

## License

Apache-2.0 — see [LICENSE](LICENSE).

The bundled PP-OCRv6 model is Apache-2.0; attribution in
[app/src/main/assets/ppocrv6_gpu/NOTICE.md](app/src/main/assets/ppocrv6_gpu/NOTICE.md).
