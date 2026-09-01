## 2026-08-31T12:32:45Z
Worker M4: ViewModels & UI/Screens Migration.
Migrate all usages of app.gamenative.PrefManager to domain preference interfaces (AuthPreferences, ContainerPreferences, InputPreferences, HudPreferences, LibraryPreferences, DownloadPreferences, GeneralPreferences).
- In ViewModels (LibraryViewModel, MainViewModel, UserLoginViewModel, etc.), inject the needed domain preferences via @Inject constructor(...).
- In Activities (MainActivity), inject or resolve via PreferencesEntryPoint.get(this).
- In Composables / Settings Screens / Dialogs: pass preferences from ViewModels, or resolve via LocalContext.current.preferencesEntryPoint(). Remove unnecessary Compose Preview PrefManager.init(context) calls.
- Remove import app.gamenative.PrefManager across all files in ownership.
