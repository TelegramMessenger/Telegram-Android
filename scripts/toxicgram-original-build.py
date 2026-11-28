#!/usr/bin/env python3
"""Prepare original Telegram sources for an isolated Toxicgram development build."""
import json
from pathlib import Path
import re

root=Path("TMessagesProj")
english={
    "AppName":"Toxicgram","AppNameBeta":"Toxicgram Dev",
    "YourPhone":"Your Toxicgram number","StartText":"Enter the virtual number issued by your Toxicgram bot.",
    "StartTextPasskey":"Enter the virtual number issued by your Toxicgram bot.",
    "SentAppCode":"Enter the six-digit code from your Toxicgram bot. Use /code in the bot to request it.",
    "SentAppCodeWithPhone":"Enter the six-digit Toxicgram bot code for %1$s.",
    "SentAppCodeTitle":"Check your Toxicgram bot",
}
russian={
    "AppName":"Toxicgram","AppNameBeta":"Toxicgram Dev",
    "YourPhone":"Ваш номер Toxicgram","StartText":"Введите виртуальный номер, полученный в боте Toxicgram.",
    "StartTextPasskey":"Введите виртуальный номер, полученный в боте Toxicgram.",
    "SentAppCode":"Введите шестизначный код бота Toxicgram. Для нового кода отправьте боту /code.",
    "SentAppCodeWithPhone":"Введите шестизначный код бота Toxicgram для %1$s.",
    "SentAppCodeTitle":"Проверьте сообщения бота Toxicgram",
}
for f in root.glob("src/main/res/values*/strings.xml"):
    s=f.read_text(encoding="utf-8")
    mapping=russian if f.parent.name=="values-ru" else english if f.parent.name=="values" else {"AppName":"Toxicgram","AppNameBeta":"Toxicgram Dev"}
    for k,v in mapping.items():
        s=re.sub(r'(<string name="'+k+r'"[^>]*>).*?(</string>)',lambda m:m.group(1)+v+m.group(2),s,flags=re.S)
    f.write_text(s,encoding="utf-8")
for f in [root/"config/release/AndroidManifest_standalone.xml",root/"src/main/AndroidManifest.xml"]:
    if not f.exists():continue
    s=f.read_text(encoding="utf-8")
    s=re.sub(r'android:(icon|roundIcon)="@mipmap/[^"]+"',r'android:\1="@drawable/toxicgram_launcher"',s)
    s=re.sub(r'(<meta-data\s+android:name="com.google.android.maps.v2.API_KEY"\s+android:value=")[^"]*(")',r'\1\2',s)
    if "firebase_messaging_auto_init_enabled" not in s:
        s=re.sub(r'(<application\b[^>]*>)',r'\1\n        <meta-data android:name="firebase_messaging_auto_init_enabled" android:value="false" />\n        <meta-data android:name="firebase_analytics_collection_enabled" android:value="false" />',s,count=1,flags=re.S)
    f.write_text(s,encoding="utf-8")
# The plugin expects a google-services file; use deliberately nonfunctional placeholders.
# Never use Telegram's Firebase project or credentials.
packages=["com.toxicgram.original","com.toxicgram.original.web","com.toxicgram.original.beta","org.telegram.messenger","org.telegram.messenger.web","org.telegram.messenger.beta"]
config={"project_info":{"project_number":"000000000000","project_id":"toxicgram-offline-build","storage_bucket":"toxicgram-offline-build.invalid"},"client":[{"client_info":{"mobilesdk_app_id":"1:000000000000:android:0000000000000000","android_client_info":{"package_name":p}},"api_key":[{"current_key":"disabled-local-build-placeholder"}]} for p in packages],"configuration_version":"1"}
for f in Path(".").glob("TMessagesProj*/google-services.json"):
    f.write_text(json.dumps(config),encoding="utf-8")

# Virtual Stars are not Google Play/Telegram billing. Keep the original settings row,
# and open a read-only real balance screen instead of the external top-up flow.
profile=root/"src/main/java/org/telegram/ui/ProfileActivity.java"
src=profile.read_text(encoding="utf-8")
old="if (getMessagesController().starsPurchaseAvailable()) {\n                    starsRow = rowCount++;"
new="if (org.telegram.tgnet.ToxicgramRpc.ENABLED || getMessagesController().starsPurchaseAvailable()) {\n                    starsRow = rowCount++;"
if old in src:
    src=src.replace(old,new,1)
elif new not in src:
    raise RuntimeError('Stars row patch target not found in ProfileActivity.java')
old="                presentFragment(new StarsIntroActivity());"
new="""                if (org.telegram.tgnet.ToxicgramRpc.ENABLED) {
                    org.telegram.ui.Stars.StarsController.getInstance(currentAccount).getBalance(true, () -> {
                        if (getParentActivity() == null) return;
                        new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity())
                            .setTitle("Toxic Stars")
                            .setMessage("⭐ " + org.telegram.ui.Stars.StarsController.getInstance(currentAccount).getBalance().amount + "\\nВиртуальный баланс Toxicgram. /ref в боте — награды за приглашения.")
                            .setPositiveButton("OK", null).show();
                    }, true);
                } else {
                    presentFragment(new StarsIntroActivity());
                }"""
if new not in src:
    if old not in src:
        raise RuntimeError('Stars click patch target not found in ProfileActivity.java')
    src=src.replace(old,new,1)
src=src.replace("LocaleController.getString(R.string.MenuTelegramStars), c.balanceAvailable()", '(org.telegram.tgnet.ToxicgramRpc.ENABLED ? "Toxic Stars" : LocaleController.getString(R.string.MenuTelegramStars)), c.balanceAvailable()',1)
profile.write_text(src,encoding="utf-8")
