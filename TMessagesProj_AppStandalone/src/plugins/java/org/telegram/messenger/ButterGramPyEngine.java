package org.telegram.messenger;

import android.content.Context;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

/** Python engine for .baddon add-ons. Compiled only into builds made with -PbuttergramPlugins. */
public class ButterGramPyEngine implements ButterGramPlugins.Engine {
    private PyObject loader;

    @Override
    public boolean start(Context context) {
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(context));
        }
        loader = Python.getInstance().getModule("buttergram_loader");
        return loader != null;
    }

    private static String text(PyObject o) {
        if (o == null) {
            return "";
        }
        String s = o.toString();
        return s == null ? "" : s;
    }

    @Override
    public String load(String id, String dir, String main) {
        String r = text(loader.callAttr("load_plugin", id, dir, main));
        return r.isEmpty() ? null : r;
    }

    @Override
    public void unload(String id) {
        loader.callAttr("unload_plugin", id);
    }

    @Override
    public String outgoing(String textToSend, long dialogId) {
        String r = text(loader.callAttr("outgoing", textToSend, dialogId));
        if (r.startsWith("T:")) {
            return r.substring(2);
        }
        return null; // "C": cancelled by a plugin
    }

    @Override
    public void incoming(String text, long dialogId, long fromId, int messageId, boolean out) {
        loader.callAttr("incoming", text, dialogId, fromId, messageId, out);
    }

    @Override
    public String settings(String id) {
        String r = text(loader.callAttr("settings", id));
        return r.isEmpty() ? "[]" : r;
    }

    @Override
    public void action(String id, String key) {
        loader.callAttr("action", id, key);
    }
}
