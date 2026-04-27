# Data repository layer

Repository implementations live here.

Responsibilities:

- Talk to Room DAOs, MediaStore, files, shared preferences, and metadata readers.
- Map database/media/file models into domain models.
- Implement interfaces declared in `domain/repository`.

Naming convention:

- `LibraryRepositoryImpl : LibraryRepository`
