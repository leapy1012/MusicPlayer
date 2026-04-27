# playback

Android/media framework integration: foreground playback service, media button receiver, player controller, notification, queue/session integration.

Feature code should communicate with playback through domain use cases or a playback controller abstraction, not by directly owning service internals.
