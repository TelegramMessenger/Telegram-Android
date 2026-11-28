"""Loads .baddon add-ons and dispatches app events to them. Called from ButterGramPyEngine."""
import importlib.util
import json
import os
import sys
import traceback

from java import jclass

import buttergram

_Api = jclass("org.telegram.messenger.ButterGramPluginApi")

_plugins = {}  # id -> plugin instance
_paths = {}  # id -> directory on sys.path


def _log(pid, text):
    _Api.log(pid, str(text))


def _fail(pid, where):
    _log(pid, "error in %s:\n%s" % (where, traceback.format_exc()))


def load_plugin(pid, directory, main):
    """Returns "" on success, otherwise an error text."""
    try:
        unload_plugin(pid)
        path = os.path.join(directory, main)
        name = "bg_plugin_" + "".join(c if c.isalnum() else "_" for c in pid)
        spec = importlib.util.spec_from_file_location(name, path)
        module = importlib.util.module_from_spec(spec)
        if directory not in sys.path:
            sys.path.insert(0, directory)
        _paths[pid] = directory
        spec.loader.exec_module(module)
        cls = None
        for value in vars(module).values():
            if isinstance(value, type) and issubclass(value, buttergram.BasePlugin) and value is not buttergram.BasePlugin:
                cls = value
                break
        if cls is None:
            return "No class that extends BasePlugin was found in " + main
        plugin = cls()
        plugin.id = pid
        if not hasattr(plugin, "_commands"):
            plugin._commands = {}
        plugin.on_plugin_load()
        _plugins[pid] = plugin
        return ""
    except Exception:
        return traceback.format_exc()


def unload_plugin(pid):
    plugin = _plugins.pop(pid, None)
    if plugin is not None:
        try:
            plugin.on_plugin_unload()
        except Exception:
            _fail(pid, "on_plugin_unload")
    directory = _paths.pop(pid, None)
    if directory and directory in sys.path:
        sys.path.remove(directory)


def outgoing(text, dialog_id):
    """Returns "T:" + text to send, or "C" to cancel."""
    current = str(text)
    for pid, plugin in list(_plugins.items()):
        # commands: ".name args"
        if current.startswith("."):
            head, _, rest = current[1:].partition(" ")
            handler = plugin._commands.get(head.lower())
            if handler is not None:
                try:
                    result = handler(rest.strip(), int(dialog_id))
                except Exception:
                    _fail(pid, "command ." + head)
                    return "C"
                if result is None:
                    return "C"
                return "T:" + str(result)
        try:
            result = plugin.on_send_message(current, int(dialog_id))
        except Exception:
            _fail(pid, "on_send_message")
            continue
        if result is None:
            return "C"
        current = str(result)
    return "T:" + current


def incoming(text, dialog_id, from_id, message_id, is_out):
    for pid, plugin in list(_plugins.items()):
        try:
            plugin.on_new_message(str(text), int(dialog_id), int(from_id), int(message_id), bool(is_out))
        except Exception:
            _fail(pid, "on_new_message")


def settings(pid):
    plugin = _plugins.get(pid)
    if plugin is None:
        return "[]"
    try:
        return json.dumps(list(plugin.create_settings() or []))
    except Exception:
        _fail(pid, "create_settings")
        return "[]"


def action(pid, key):
    plugin = _plugins.get(pid)
    if plugin is None:
        return
    try:
        plugin.on_setting_changed(str(key), plugin.get_setting(str(key)))
    except Exception:
        _fail(pid, "on_setting_changed")
