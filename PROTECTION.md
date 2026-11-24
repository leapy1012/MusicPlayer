# Release protection

Build the signed release APK with the project's normal `assembleRelease` task.
Debug retains its existing configuration. Root `libs/` contains the normal COUI
2.1.0 release AAR and physics-engine AAR; Gradle consumes them directly.
There is no decryption task, encryption key or runtime loader in the build path.

COUI's private implementation is pre-obfuscated. Application release R8 uses
`protection/release.gradle`, `common.pro`, `app.pro` and generated compiled-code
contracts to retain Android entry points, schemas, reflection and JNI boundaries.
Resource shrinking stays disabled for dynamic theme/framework resource names.

Keep release mapping files private and retain the exact APK/version for retracing.
The APK remains decompilable; public/XML APIs, data schemas and resources remain
readable. Only compiled release libraries are distributed from the private
DreamCOUI source project. Legacy encrypted folders are unused by the build.
