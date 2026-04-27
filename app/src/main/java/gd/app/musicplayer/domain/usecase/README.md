# Domain use cases

Use cases hold app-specific business actions.

Rules:

- One public action per class.
- Constructor injection with `@Inject`.
- Depend on repository interfaces from `domain/repository`.
- Keep Android framework APIs outside this layer.

Examples:

- `GetLibraryTracksUseCase`
- `ToggleFavoriteTrackUseCase`
