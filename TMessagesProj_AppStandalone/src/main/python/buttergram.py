"""ButterGram add-on SDK (API version 1).

A .baddon is a zip with manifest.json and Python files. The main file defines one class that
extends BasePlugin. Everything here runs inside the app, so keep hooks fast.
"""
import json

from java import jclass

_Api = jclass("org.telegram.messenger.ButterGramPluginApi")

API_VERSION = int(_Api.apiVersion())


def app_version():
    return str(_Api.appVersion())


class BasePlugin:
    """Base class of every add-on. Override the hooks you need."""

    id = ""  # filled in by the loader from manifest.json

    def __init__(self):
        self._commands = {}

    # ------------------------------------------------------------------ hooks

    def on_plugin_load(self):
        """Called once when the add-on is started."""

    def on_plugin_unload(self):
        """Called when the add-on is disabled or removed."""

    def on_send_message(self, text, dialog_id):
        """Outgoing text. Return a string to send it (possibly changed) or None to cancel it."""
        return text

    def on_new_message(self, text, dialog_id, from_id, message_id, is_out):
        """Every new text message that arrives in a chat (also your own)."""

    def create_settings(self):
        """Return a list of dicts for the settings page, for example
        [{"type": "header", "title": "Main"},
         {"type": "switch", "key": "shout", "title": "Shout", "default": False},
         {"type": "text", "key": "name", "title": "Name", "default": "world"},
         {"type": "button", "key": "ping", "title": "Ping"}]"""
        return []

    def on_setting_changed(self, key, value):
        """A switch or text value changed, or a button was pressed (value is None for buttons)."""

    # --------------------------------------------------------------- commands

    def register_command(self, name, handler):
        """Typing `.name args` in a chat calls handler(args, dialog_id).
        Return a string to send it as the message, or None to send nothing."""
        self._commands[name.lower().lstrip(".")] = handler

    # ---------------------------------------------------------------- helpers

    def log(self, message):
        _Api.log(self.id, str(message))

    def toast(self, text):
        _Api.toast(str(text))

    def dialog(self, title, text):
        _Api.dialog(str(title), str(text))

    def send_message(self, dialog_id, text):
        """Sends a message without running it through on_send_message again."""
        _Api.sendMessage(int(dialog_id), str(text))

    def my_id(self):
        return int(_Api.currentUserId())

    def get_setting(self, key, default=None):
        raw = _Api.getSetting(self.id, key)
        if raw is None:
            return default
        try:
            return json.loads(str(raw))
        except ValueError:
            return default

    def set_setting(self, key, value):
        _Api.setSetting(self.id, key, json.dumps(value))
