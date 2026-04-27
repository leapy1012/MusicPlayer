# feature

User-facing screens grouped by product feature.

Each migrated feature should follow:

```text
feature/<name>/
  <Name>Activity.kt or <Name>Fragment.kt
  <Name>ViewModel.kt
  <Name>UiState.kt
  <Name>Action.kt
  <Name>Event.kt
```

Activities and Fragments should render state, collect events, handle navigation, and request permissions. Business logic belongs in ViewModels/use cases.
