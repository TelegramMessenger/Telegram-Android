# ButterGram plugins (.baddon)

A `.baddon` file is a zip archive:

```
manifest.json   required
main.py         the file named in "main"
...             any other files your plugin imports
```

`manifest.json`:

```json
{
  "id": "hello",              // a-z, 0-9, "_", "." or "-", up to 48 chars
  "name": "Hello",
  "version": "1.0",
  "author": "you",
  "description": "What it does",
  "icon": "emoji/0",           // optional: "packShortName/index" of a sticker or emoji pack, or one emoji character
  "main": "main.py",
  "permissions": ["send_messages", "read_messages"]   // shown to the user, informational only
}
```

The main file defines one class that extends `buttergram.BasePlugin` (see `sample/hello`).

Hooks: `on_plugin_load`, `on_plugin_unload`, `on_send_message(text, dialog_id)` (return text, changed text or None to cancel),
`on_new_message(text, dialog_id, from_id, message_id, is_out)`, `create_settings()`, `on_setting_changed(key, value)`.
Helpers: `register_command(name, handler)` (`.name args` in a chat), `toast`, `dialog`, `send_message`, `my_id`, `get_setting`, `set_setting`, `log`.

Settings rows: `header`, `switch`, `text`, `button` (`type`, `key`, `title`, `default`).

## Building the app with plugins

The Python engine (Chaquopy) is optional. A normal build has none; plugins can be installed but will not run.
Run the **Build ButterGram APK with plugins** workflow or build with `BUTTERGRAM_PLUGINS=1 ./gradlew :TMessagesProj_AppStandalone:assembleAfatStandalone`.
The plugin build needs Android 7.0+ (API 24) and the APK is larger.

## Security

A plugin runs with the same rights as the app. The permission list in the manifest is informational, it is not enforced.
Install only plugins from people you trust.
