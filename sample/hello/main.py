from buttergram import BasePlugin


class HelloPlugin(BasePlugin):
    def on_plugin_load(self):
        self.register_command("hello", self.hello)
        self.register_command("ping", self.ping)
        self.log("loaded")

    def hello(self, args, dialog_id):
        name = args or self.get_setting("name", "world")
        return "Hello, %s!" % name

    def ping(self, args, dialog_id):
        self.toast("pong")
        return None  # nothing is sent

    def on_send_message(self, text, dialog_id):
        if self.get_setting("shout", False):
            return text.upper()
        return text

    def create_settings(self):
        return [
            {"type": "header", "title": "Hello"},
            {"type": "switch", "key": "shout", "title": "Send in CAPITALS", "default": False},
            {"type": "text", "key": "name", "title": "Default name for .hello", "default": "world"},
            {"type": "button", "key": "test", "title": "Show a test dialog"},
        ]

    def on_setting_changed(self, key, value):
        if key == "test":
            self.dialog("Hello", "The add-on is running.")
