# PP-OCRv6 Small (LiteRT) — model source and license

The model files in this directory (PP-OCRv6 text detection / recognition, fp32 TFLite)
and the character dictionary `characters.json` come from:

- Repository: https://huggingface.co/litert-community/PP-OCRv6-Small-LiteRT
- License: Apache License, Version 2.0
  (`LICENSE` in the repository,
  https://huggingface.co/litert-community/PP-OCRv6-Small-LiteRT/blob/main/LICENSE )

Bundled set: `ppocrv6_small_det_640_fp32.tflite` +
`ppocrv6_small_rec_640_fp32.tflite` + `characters.json`, synced into the APK
assets by the `syncOcrModels` Gradle task. The model binaries are not committed to
git; run `./gradlew syncOcrModels` after checkout (see `capability/ocr/README.md`).

The full Apache-2.0 text is in the repository LICENSE; this NOTICE is only an
attribution and provenance record.
