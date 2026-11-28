/*
 * Toxicgram REST adapter for the original Telegram Android client.
 * GPL-2.0-or-later, consistent with the enclosing project.
 * Unsupported RPCs fail explicitly; never fall back to Telegram.
 */
package org.telegram.tgnet;

import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.tgnet.tl.TL_update;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.FileLoader;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.net.ssl.HttpsURLConnection;

public final class ToxicgramRpc {
    public static final boolean ENABLED=true;
    private static final String BASE="https://toxicgram-auth-production.up.railway.app";
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private static final ExecutorService PROBES=Executors.newSingleThreadExecutor();
    private static final java.util.Set<Integer> checking=java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<Integer,Boolean>());
    public interface PremiumCallback { void done(JSONObject data, String error); }
    public static void premiumRequest(int account, int months, boolean cancel, PremiumCallback callback){
        IO.execute(()->{
            JSONObject result=null;String error=null;
            try{
                String path=cancel?"/v1/premium/cancel":"/v1/premium";
                JSONObject body=months>0?new JSONObject().put("months",months).put("request_id",java.util.UUID.randomUUID().toString()):null;
                result=http(months>0||cancel?"POST":"GET",path,body,token(account));
                TLRPC.TL_user updated=user(account,http("GET","/v1/me",null,token(account)),true);
                org.telegram.messenger.AndroidUtilities.runOnUIThread(()->{
                    MessagesController.getInstance(account).putUser(updated,false);
                    UserConfig.getInstance(account).setCurrentUser(updated);
                    UserConfig.getInstance(account).saveConfig(false);
                    org.telegram.messenger.NotificationCenter.getInstance(account).postNotificationName(org.telegram.messenger.NotificationCenter.updateInterfaces,0);
                });
            }catch(ApiError e){error=e.code==409?"Недостаточно звёзд или подписка уже активна":"Не удалось выполнить запрос";}
            catch(Exception e){error="Не удалось подключиться к серверу";}
            final JSONObject data=result;final String failure=error;
            org.telegram.messenger.AndroidUtilities.runOnUIThread(()->callback.done(data,failure));
        });
    }
    public static void probe(int account){
        if(!checking.add(account))return;
        PROBES.execute(()->{
            int state;
            try{
                JSONObject health=http("GET","/health",null,null);
                if(!"ok".equals(health.optString("status")))throw new IOException("Health unavailable");
                state=ConnectionsManager.ConnectionStateConnected;
            }catch(Exception e){
                state=ApplicationLoader.isNetworkOnline()?ConnectionsManager.ConnectionStateConnecting:ConnectionsManager.ConnectionStateWaitingForNetwork;
            }finally{checking.remove(account);}
            ConnectionsManager.onToxicgramConnectionStateChanged(state,account);
        });
    }
    // Foreground polling is separate from outgoing RPCs, so a stalled network
    // request does not prevent sending. At most one sync per account.
    private static final java.util.concurrent.ScheduledExecutorService SYNC=Executors.newScheduledThreadPool(2);
    private static final java.util.Map<Integer,Poll> polls=new java.util.HashMap<>();
    private static final class Poll{
        volatile boolean active=true;
        volatile java.util.concurrent.ScheduledFuture<?> future;
        int failures=0;
        boolean first=true;
        final java.util.Map<String,Integer> readOut=new java.util.HashMap<>(),readIn=new java.util.HashMap<>();
    }
    public static synchronized void setForeground(int a,boolean foreground){
        Poll old=polls.get(a);
        if(!foreground){
            if(old!=null){old.active=false;if(old.future!=null)old.future.cancel(false);polls.remove(a);}
            return;
        }
        if(old!=null&&old.active)return;
        Poll p=new Poll();polls.put(a,p);scheduleSync(a,p,0);
    }
    private static void scheduleSync(int a,Poll p,long delay){
        synchronized(ToxicgramRpc.class){if(p.active&&polls.get(a)==p)p.future=SYNC.schedule(()->poll(a,p),delay,java.util.concurrent.TimeUnit.SECONDS);}
    }
    private static TLRPC.UserStatus presence(JSONObject row){
        int last=row.optInt("last_seen",0),until=row.optInt("online_until",0);
        if(until>System.currentTimeMillis()/1000L){TLRPC.TL_userStatusOnline s=new TLRPC.TL_userStatusOnline();s.expires=until;return s;}
        if(last>0){TLRPC.TL_userStatusOffline s=new TLRPC.TL_userStatusOffline();s.expires=last;return s;}
        return new TLRPC.TL_userStatusEmpty();
    }
    private static void poll(int a,Poll p){
        if(!p.active)return;
        if(!UserConfig.getInstance(a).isClientActivated()){scheduleSync(a,p,5);return;}
        try{
            String t=token(a),self=prefs(a).getString("number","");
            if(self.isEmpty()){scheduleSync(a,p,5);return;}
            long cursor=prefs(a).getLong("sync_cursor_v1",0);
            JSONObject response=http("GET","/v1/sync?after="+cursor,null,t);
            if(!p.active||!t.equals(token(a)))return;
            JSONArray incoming=response.getJSONArray("messages"),profiles=response.getJSONArray("users"),statuses=response.getJSONArray("statuses");
            java.util.ArrayList<TLRPC.User> users=new java.util.ArrayList<>();
            java.util.ArrayList<TLRPC.Update> events=new java.util.ArrayList<>();
            for(int i=0;i<profiles.length();i++)users.add(user(a,profiles.getJSONObject(i),false));
            // Read markers precede message delivery to avoid replaying already-read
            // messages as unread after a reconnect.
            for(int i=0;i<statuses.length();i++){
                JSONObject row=statuses.getJSONObject(i);String n=row.getString("number");
                TL_update.TL_updateUserStatus status=new TL_update.TL_updateUserStatus();status.user_id=userId(n);status.status=presence(row);events.add(status);
                int out=row.optInt("read_outbox",0),in=row.optInt("read_inbox",0);
                if(out>0&&out>p.readOut.getOrDefault(n,0)){
                    TL_update.TL_updateReadHistoryOutbox read=new TL_update.TL_updateReadHistoryOutbox();read.peer=peerObject(n);read.max_id=out;events.add(read);p.readOut.put(n,out);
                }
                if(in>0&&in>p.readIn.getOrDefault(n,0)){
                    TL_update.TL_updateReadHistoryInbox read=new TL_update.TL_updateReadHistoryInbox();read.peer=peerObject(n);read.max_id=in;events.add(read);p.readIn.put(n,in);
                }
            }
            for(int i=0;i<incoming.length();i++){
                TLRPC.Message m=message(a,incoming.getJSONObject(i),self);
                // The conversation can already have been loaded from history.
                if(MessagesStorage.getInstance(a).getMessage(m.peer_id.user_id,m.id)!=null)continue;
                TL_update.TL_updateNewMessage event=new TL_update.TL_updateNewMessage();event.message=m;events.add(event);
            }
            java.util.ArrayList<TLRPC.Chat> roomChats=new java.util.ArrayList<>();
            long roomCursor=prefs(a).getLong("room_sync_cursor",0);
            long roomNext=roomCursor;boolean roomMore=false;
            try {
                JSONObject roomSync=http("GET","/v1/rooms/sync?after="+roomCursor,null,t);
                java.util.HashMap<String,JSONObject> roomMap=new java.util.HashMap<>();
                JSONArray rooms=roomSync.getJSONArray("rooms"),members=roomSync.getJSONArray("users"),posts=roomSync.getJSONArray("messages");
                for(int i=0;i<rooms.length();i++){
                    JSONObject row=rooms.getJSONObject(i);roomMap.put(row.getString("id"),row);roomChats.add(roomChat(row,self));
                }
                for(int i=0;i<members.length();i++){
                    JSONObject row=members.getJSONObject(i);users.add(user(a,row,self.equals(row.optString("number"))));
                }
                for(int i=0;i<posts.length();i++){
                    JSONObject row=posts.getJSONObject(i),room=roomMap.get(row.getString("room_id"));
                    if(room==null)continue;
                    TLRPC.TL_message m=roomMessage(a,row,room,self);
                    long dialogId=-(ROOM_BASE+room.getLong("numeric_id"));
                    if(MessagesStorage.getInstance(a).getMessage(dialogId,m.id)!=null)continue;
                    if("channel".equals(room.optString("mode"))){
                        TL_update.TL_updateNewChannelMessage event=new TL_update.TL_updateNewChannelMessage();event.message=m;events.add(event);
                    }else{
                        TL_update.TL_updateNewMessage event=new TL_update.TL_updateNewMessage();event.message=m;events.add(event);
                    }
                }
                roomNext=roomSync.getLong("cursor");roomMore=roomSync.optBoolean("more");
            }catch(ApiError e){if(e.code!=404)org.telegram.messenger.FileLog.e("Toxicgram room sync: "+e.code);}
            catch(Exception e){org.telegram.messenger.FileLog.e("Toxicgram room sync: "+e.getClass().getSimpleName());}
            final long nextRoom=roomNext;
            long next=response.getLong("cursor");boolean more=response.optBoolean("more",false)||roomMore;
            int date=response.getInt("server_time");
            Utilities.stageQueue.postRunnable(()->{
                try{
                    if(!p.active||!t.equals(token(a)))return;
                    if(!MessagesController.getInstance(a).processUpdateArray(events,users,roomChats,true,date)){
                        p.readOut.clear();p.readIn.clear();scheduleSync(a,p,5);return;
                    }
                    // Persist cursor behind the storage queue after incoming messages.
                    MessagesStorage.getInstance(a).getStorageQueue().postRunnable(()->{
                        try{
                            if(p.active&&t.equals(token(a))){
                                prefs(a).edit().putLong("sync_cursor_v1",next).putLong("room_sync_cursor",nextRoom).commit();
                                p.failures=0;
                                ConnectionsManager.onToxicgramConnectionStateChanged(ConnectionsManager.ConnectionStateConnected,a);
                                scheduleSync(a,p,more?0:4);
                            }
                        }catch(Exception ignored){scheduleSync(a,p,5);}
                    });
                }catch(Exception e){p.readOut.clear();p.readIn.clear();scheduleSync(a,p,5);}
            });
        }catch(ApiError e){
            if(e.code==401){scheduleSync(a,p,30);return;}
            scheduleSync(a,p,Math.min(30,4L*(++p.failures)));
        }catch(Exception e){
            if(p.active)ConnectionsManager.onToxicgramConnectionStateChanged(ApplicationLoader.isNetworkOnline()?ConnectionsManager.ConnectionStateConnecting:ConnectionsManager.ConnectionStateWaitingForNetwork,a);
            scheduleSync(a,p,Math.min(30,4L*(++p.failures)));
        }
    }
    private static final String KEY="toxicgram_original_session";
    private ToxicgramRpc(){}
    private static SharedPreferences prefs(int a){
        return ApplicationLoader.applicationContext.getSharedPreferences("toxicgram_rest_"+a,0);
    }
    private static SecretKey key()throws Exception{
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(ks.containsAlias(KEY))return (SecretKey)ks.getKey(KEY,null);
        KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(KEY,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return gen.generateKey();
    }
    private static void store(int a,String token,String number)throws Exception{
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] encrypted=cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
        SharedPreferences.Editor editor=prefs(a).edit();
        if(!number.equals(prefs(a).getString("number","")))editor.remove("sync_cursor_v1").remove("room_sync_cursor");
        if(!editor.putString("token",Base64.encodeToString(encrypted,Base64.NO_WRAP))
            .putString("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)).putString("number",number).commit())
            throw new IOException("Cannot persist session");
    }
    private static String token(int a)throws Exception{
        String data=prefs(a).getString("token",""),iv=prefs(a).getString("iv","");
        if(data.isEmpty()||iv.isEmpty())throw new ApiError(401,"AUTH_KEY_UNREGISTERED");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(iv,Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(data,Base64.NO_WRAP)),StandardCharsets.UTF_8);
    }
    private static class ApiError extends IOException {
        final int code;final String type;
        ApiError(int c,String t){code=c;type=t;}
    }
    private static JSONObject http(String method,String path,JSONObject body,String token)throws Exception{
        HttpsURLConnection conn=(HttpsURLConnection)new URL(BASE+path).openConnection();
        try{
            conn.setConnectTimeout(15000);conn.setReadTimeout(20000);conn.setRequestMethod(method);
            conn.setInstanceFollowRedirects(false);conn.setRequestProperty("Accept","application/json");
            if(token!=null)conn.setRequestProperty("Authorization","Bearer "+token);
            if(body!=null){conn.setDoOutput(true);conn.setRequestProperty("Content-Type","application/json");
                try(OutputStream o=conn.getOutputStream()){o.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
            int status=conn.getResponseCode();
            if(status<200||status>=300)throw new ApiError(status,status==401?"AUTH_KEY_UNREGISTERED":status==429?"FLOOD_WAIT_60":status==404?"PEER_ID_INVALID":"TOXICGRAM_SERVER_ERROR");
            try(InputStream in=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>2*1024*1024)throw new IOException("Response too large");out.write(buffer,0,n);}
                return new JSONObject(out.toString("UTF-8"));
            }
        }finally{conn.disconnect();}
    }
    private static String phone(String raw){
        String v=raw==null?"":raw.replaceAll("[^0-9]","");
        return "+"+v;
    }
    public static long userId(String number){
        if(!number.matches("TG-[0-9]{4}-[0-9]{4}"))throw new IllegalArgumentException("Invalid internal identifier");
        return 100000000L+Long.parseLong(number.replaceAll("[^0-9]",""));
    }
    private static final java.util.Map<Long,StagedFile> staged=new java.util.concurrent.ConcurrentHashMap<>();
    private static class StagedFile{
        final int account;final File file;final long expires;
        StagedFile(int a,File f){account=a;file=f;expires=System.currentTimeMillis()+3600000;}
    }
    public static TLRPC.InputFile stageUpload(int account,File file){
        if(!file.isFile()||file.length()==0||file.length()>24*1024*1024)return null;
        staged.entrySet().removeIf(x->x.getValue().expires<System.currentTimeMillis());
        if(staged.size()>=300)return null;
        long id=new java.security.SecureRandom().nextLong();
        staged.put(id,new StagedFile(account,file));
        TLRPC.TL_inputFile input=new TLRPC.TL_inputFile();input.id=id;input.parts=1;input.name=file.getName();input.md5_checksum="";
        return input;
    }
    private static File stagedFile(int a,TLRPC.InputFile input)throws Exception{
        StagedFile f=input==null?null:staged.get(input.id);
        if(f==null||f.account!=a||f.expires<System.currentTimeMillis()||!f.file.isFile())throw new ApiError(400,"FILE_PARTS_INVALID");
        return f.file;
    }
    private static JSONObject uploadBytes(int a,String method,String path,File file,int limit)throws Exception{
        if(file.length()==0||file.length()>limit)throw new ApiError(400,"FILE_TOO_BIG");
        HttpsURLConnection conn=(HttpsURLConnection)new URL(BASE+path).openConnection();
        try{
            conn.setConnectTimeout(15000);conn.setReadTimeout(30000);conn.setRequestMethod(method);conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("Authorization","Bearer "+token(a));conn.setRequestProperty("Content-Type","application/octet-stream");conn.setDoOutput(true);conn.setFixedLengthStreamingMode((int)file.length());
            try(InputStream in=new FileInputStream(file);OutputStream out=conn.getOutputStream()){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
            int status=conn.getResponseCode();if(status<200||status>=300)throw new ApiError(status,status==413?"FILE_TOO_BIG":status==507?"STORAGE_FULL":"TOXICGRAM_UPLOAD_FAILED");
            try(InputStream in=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>65536)throw new IOException("Invalid response");out.write(b,0,n);}return new JSONObject(out.toString("UTF-8"));}
        }finally{conn.disconnect();}
    }
    private static final long MEDIA_BASE=4000000000000000000L,AVATAR_BASE=2000000000000000000L;
    private static long localId(String id,long base)throws IOException{
        if(!id.matches("[a-f0-9]{32}"))throw new IOException("Invalid asset ID");
        return base+Long.parseLong(id.substring(0,15),16);
    }
    private static File avatarFile(int a,long id){
        return new File(new File(ApplicationLoader.applicationContext.getCacheDir(),"toxicgram_avatars_v2_"+a+"_"+prefs(a).getString("number","").replaceAll("[^A-Za-z0-9-]","")),id+".jpg");
    }
    public static String avatarPath(int a,long id){
        if(id<AVATAR_BASE||id>=AVATAR_BASE+(1L<<60))return null;
        File f=avatarFile(a,id);return f.isFile()?f.getAbsolutePath():null;
    }
    private static void fetchAsset(int a,String id,File target,int max)throws Exception{
        if(!id.matches("[a-f0-9]{32}"))throw new IOException("Invalid asset");
        if(target.isFile()&&target.length()>0)return;
        File parent=target.getParentFile();if(parent!=null&&!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Cache unavailable");
        File tmp=File.createTempFile("tg-asset-",".tmp",parent);
        HttpsURLConnection conn=(HttpsURLConnection)new URL(BASE+"/v1/media/"+id).openConnection();
        try{
            conn.setConnectTimeout(10000);conn.setReadTimeout(20000);conn.setInstanceFollowRedirects(false);conn.setRequestProperty("Authorization","Bearer "+token(a));
            if(conn.getResponseCode()!=200)throw new IOException("Asset unavailable");
            try(InputStream in=conn.getInputStream();OutputStream out=new FileOutputStream(tmp)){byte[] b=new byte[16384];int n,total=0;while((n=in.read(b))!=-1){total+=n;if(total>max)throw new IOException("Asset too large");out.write(b,0,n);}}
            if(!tmp.renameTo(target)&&!target.isFile())throw new IOException("Cache write failed");
        }finally{conn.disconnect();tmp.delete();}
    }
    private static TLRPC.FileLocation avatarLocation(long id,int type){
        TLRPC.TL_fileLocationToBeDeprecated loc=new TLRPC.TL_fileLocationToBeDeprecated();loc.volume_id=-id;loc.local_id=type;loc.dc_id=1;return loc;
    }
    private static TLRPC.TL_photo avatarPhoto(int a,String asset)throws Exception{
        long id=localId(asset,AVATAR_BASE);File file=avatarFile(a,id);fetchAsset(a,asset,file,512*1024);
        TLRPC.TL_photo photo=new TLRPC.TL_photo();photo.id=id;photo.access_hash=id;photo.file_reference=asset.getBytes(StandardCharsets.UTF_8);photo.dc_id=1;photo.date=(int)(System.currentTimeMillis()/1000L);
        for(String type:new String[]{"a","c"}){
            TLRPC.TL_photoSize size=new TLRPC.TL_photoSize();size.type=type;size.w="a".equals(type)?150:800;size.h=size.w;size.size=(int)file.length();size.location=avatarLocation(id,type.charAt(0));photo.sizes.add(size);
        }
        return photo;
    }
    private static TLRPC.TL_user user(int a,JSONObject p,boolean self)throws Exception{
        TLRPC.TL_user u=new TLRPC.TL_user();
        u.id=userId(p.getString("number"));u.first_name=p.optString("first_name",p.optString("name",p.optString("phone","Toxicgram")));u.last_name=p.optString("last_name","");
        u.phone=p.optString("phone","").replace("+","");u.username=p.optString("username","");
        self=p.getString("number").equals(prefs(a).getString("number","")) || self;
        u.premium=p.optBoolean("premium",false);
        u.self=self;u.flags=2|4|16|(u.username.isEmpty()?0:8)|(self?1024:0);
        u.access_hash=u.id;u.flags|=1;
        if(u.premium)u.flags|=268435456;
        JSONObject verification=p.optJSONObject("verification");
        if(verification!=null){
            u.bot_verification_icon=920000000000000000L+verification.optInt("color")*6L+verification.optInt("symbol");
            u.flags2|=16384;
        }
        if(p.has("last_seen")){u.status=presence(p);u.flags|=64;}
        String asset=p.optString("avatar","");
        if(asset.matches("[a-f0-9]{32}")){
            long id=localId(asset,AVATAR_BASE);
            TLRPC.TL_userProfilePhoto photo=new TLRPC.TL_userProfilePhoto();photo.photo_id=id;photo.dc_id=1;photo.photo_small=avatarLocation(id,'a');photo.photo_big=avatarLocation(id,'c');
            u.photo=photo;u.flags|=32;
            try{fetchAsset(a,asset,avatarFile(a,id),512*1024);}catch(Exception ignored){}
        }else{u.photo=new TLRPC.TL_userProfilePhotoEmpty();u.flags|=32;}
        return u;
    }
    private static String canonical(long id) throws ApiError {
        if(id<100000000L||id>=200000000L)throw new ApiError(400,"PEER_ID_INVALID");
        String digits=String.format(Locale.US,"%08d",id-100000000L);
        return "TG-"+digits.substring(0,4)+"-"+digits.substring(4);
    }
    private static String peer(int a,TLRPC.InputPeer input)throws ApiError{
        if(input instanceof TLRPC.TL_inputPeerSelf)return prefs(a).getString("number","");
        if(input instanceof TLRPC.TL_inputPeerUser)return canonical(input.user_id);
        throw new ApiError(501,"TOXICGRAM_PEER_TYPE_NOT_IMPLEMENTED");
    }
    private static TLRPC.TL_peerUser peerObject(String number){
        TLRPC.TL_peerUser p=new TLRPC.TL_peerUser();p.user_id=userId(number);return p;
    }
    private static TLRPC.Message message(int a,JSONObject data,String self)throws Exception{
        JSONObject receipt=data.optJSONObject("gift");
        if(receipt!=null){
            try{
                TLRPC.TL_messageService service=new TLRPC.TL_messageService();
                service.id=Math.toIntExact(data.getLong("id"));service.date=data.getInt("created");
                service.out=self.equals(data.optString("sender"));
                service.peer_id=peerObject(service.out?data.getString("recipient"):data.getString("sender"));
                service.from_id=peerObject(data.getString("sender"));
                service.flags=256|(service.out?2:0);
                TLRPC.TL_messageActionStarGift action=new TLRPC.TL_messageActionStarGift();
                action.gift=gift(receipt);
                action.convert_stars=0;
                action.saved=receipt.optInt("visible",0)!=0;
                action.forceIn=true;
                String note=receipt.optString("message","");
                if(!note.isEmpty()){
                    action.message=new TLRPC.TL_textWithEntities();
                    action.message.text=note;
                }
                service.action=action;
                return service;
            }catch(Exception ignored){/* Preserve readable receipt if artwork is unavailable. */}
        }
        TLRPC.TL_message m=new TLRPC.TL_message();
        m.id=Math.toIntExact(data.getLong("id"));m.date=data.getInt("created");
        m.message=data.optString("text","");m.out=self.equals(data.optString("sender"));
        String other=m.out?data.getString("recipient"):data.getString("sender");
        m.peer_id=peerObject(other);m.from_id=peerObject(data.getString("sender"));
        m.flags=256|(m.out?2:0);m.media=new TLRPC.TL_messageMediaEmpty();
        if(data.optLong("edited")>0){m.edit_date=data.getInt("edited");m.flags|=32768;}
        JSONObject attachment=data.optJSONObject("attachment");
        if(attachment!=null){m.media=attachmentMedia(a,attachment,m.date,null);m.flags|=512;}
        return m;
    }
    public static void send(int account,TLObject request,RequestDelegate completion,RequestDelegateTimestamp timestamp){
        IO.execute(()->{
            TLObject response=null;TLRPC.TL_error error=null;
            try{response=dispatch(account,request);}
            catch(ApiError e){error=new TLRPC.TL_error();error.code=e.code;error.text=e.type;}
            catch(Exception e){
                error=new TLRPC.TL_error();error.code=500;error.text="TOXICGRAM_REQUEST_FAILED";
                // Log only the exception type, never a phone number, code or token.
                boolean auth=request instanceof TLRPC.TL_auth_sendCode || request instanceof TLRPC.TL_auth_resendCode || request instanceof TLRPC.TL_auth_signIn;
                if(auth){
                    if(e instanceof java.net.UnknownHostException) error.text="TOXICGRAM_DNS_ERROR";
                    else if(e instanceof javax.net.ssl.SSLException) error.text="TOXICGRAM_TLS_ERROR";
                    else if(e instanceof java.net.SocketTimeoutException) error.text="TOXICGRAM_TIMEOUT";
                    else if(e instanceof java.net.ConnectException || e instanceof java.net.NoRouteToHostException) error.text="TOXICGRAM_CONNECTION_ERROR";
                    else if(e instanceof java.security.GeneralSecurityException) error.text="TOXICGRAM_SESSION_STORAGE_ERROR";
                    else if(e instanceof ClassCastException) error.text="TOXICGRAM_LOCAL_STATE_ERROR";
                    org.telegram.messenger.FileLog.e("Toxicgram auth: "+e.getClass().getSimpleName());
                }
                if(e instanceof IOException)probe(account);
            }
            final TLObject result=response;final TLRPC.TL_error failure=error;
            Utilities.stageQueue.postRunnable(()->{
                if(completion!=null)completion.run(result,failure);
                else if(timestamp!=null)timestamp.run(result,failure,System.currentTimeMillis());
            });
        });
    }
    private static final String[] GIFT_KINDS={"heart","bear","rose","star","crystal","present","cake"};
    private static final String[] GIFT_ART={"email_setup_heart","gift","gift","star_reaction","wallet_diamond_blue","gift","cake"};
    private static final long GIFT_BASE=830000000000000000L;
    private static long catalogId(JSONObject row)throws Exception{
        if(row.has("gift_id"))return Long.parseLong(row.getString("gift_id"));
        for(int i=0;i<GIFT_KINDS.length;i++)if(GIFT_KINDS[i].equals(row.getString("kind")))return GIFT_BASE+i;
        throw new ApiError(400,"STARGIFT_INVALID");
    }
    private static JSONObject findGiftId(JSONArray catalog,long id)throws Exception{
        for(int i=0;i<catalog.length();i++){JSONObject row=catalog.getJSONObject(i);if(catalogId(row)==id)return row;}
        throw new ApiError(400,"STARGIFT_INVALID");
    }
    private static File artDir(){
        return new File(ApplicationLoader.applicationContext.getCacheDir(),"toxicgram_gift_art");
    }
    private static android.content.SharedPreferences artPrefs(){
        return ApplicationLoader.applicationContext.getSharedPreferences("toxicgram_gift_art",0);
    }
    public static synchronized String giftAnimationPath(long id){
        long index=id-GIFT_BASE;
        if(index<0||index>=1000000)return null;
        try{
            File dir=artDir();
            if(!dir.isDirectory()&&!dir.mkdirs())return null;
            String asset=artPrefs().getString(Long.toString(id),"");
            if(asset.matches("[a-f0-9]{64}")){
                File f=new File(dir,asset+".tgs");
                if(f.isFile())return f.getAbsolutePath();
            }
            if(index>=GIFT_ART.length)return null;
            String art=GIFT_ART[(int)index];
            File target=new File(dir,art+".tgs");
            if(!target.exists()){
                int resource=ApplicationLoader.applicationContext.getResources().getIdentifier(art,"raw",ApplicationLoader.applicationContext.getPackageName());
                if(resource==0)return null;
                File tmp=new File(dir,art+".tmp");
                try(InputStream in=ApplicationLoader.applicationContext.getResources().openRawResource(resource);
                    java.util.zip.GZIPOutputStream out=new java.util.zip.GZIPOutputStream(new FileOutputStream(tmp))){
                    byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
                }
                if(!tmp.renameTo(target)){tmp.delete();return null;}
            }
            return target.getAbsolutePath();
        }catch(Exception e){return null;}
    }
    private static void downloadGiftArt(long id,String asset)throws Exception{
        if(!asset.matches("[a-f0-9]{64}"))throw new IOException("Invalid gift asset");
        File dir=artDir();if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Cannot create artwork cache");
        File target=new File(dir,asset+".tgs");
        if(!target.isFile()){
            HttpsURLConnection conn=(HttpsURLConnection)new URL(BASE+"/v1/gift-art/"+asset).openConnection();
            try{
                conn.setConnectTimeout(10000);conn.setReadTimeout(15000);conn.setInstanceFollowRedirects(false);
                if(conn.getResponseCode()!=200)throw new IOException("Artwork unavailable");
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                try(InputStream in=conn.getInputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(bytes.size()+n>262144)throw new IOException("Artwork too large");bytes.write(b,0,n);}}
                byte[] data=bytes.toByteArray();byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(data);StringBuilder hex=new StringBuilder();
                for(byte b:digest)hex.append(String.format(Locale.US,"%02x",b&255));
                if(!asset.equals(hex.toString()))throw new IOException("Artwork hash mismatch");
                File tmp=new File(dir,asset+".tmp");try(FileOutputStream out=new FileOutputStream(tmp)){out.write(data);}
                if(!tmp.renameTo(target)){tmp.delete();throw new IOException("Artwork cache error");}
            }finally{conn.disconnect();}
        }
        artPrefs().edit().putString(Long.toString(id),asset).apply();
    }
    private static TL_stars.TL_starGift gift(JSONObject row)throws Exception{
        TL_stars.TL_starGift g=new TL_stars.TL_starGift();g.id=catalogId(row);g.stars=row.getLong("price");
        g.convert_stars=0;g.can_upgrade=false; // Upgrade workflow is not yet supported by the original UI adapter.
        int supply=row.optInt("supply");
        if(supply>0){g.limited=true;g.availability_total=supply;g.availability_remains=row.optInt("remaining");g.sold_out=g.availability_remains<=0;}
        String asset=row.optString("asset","");
        if(!asset.isEmpty())downloadGiftArt(g.id,asset);
        TLRPC.TL_document d=new TLRPC.TL_document();
        d.id=g.id;d.access_hash=g.id;d.file_reference=asset.getBytes(StandardCharsets.UTF_8);d.date=1;d.mime_type="application/x-tgsticker";d.dc_id=1;
        String local=giftAnimationPath(d.id);
        d.size=local==null?0:new File(local).length();
        TLRPC.TL_documentAttributeSticker sticker=new TLRPC.TL_documentAttributeSticker();sticker.alt=row.optString("emoji","");sticker.stickerset=new TLRPC.TL_inputStickerSetEmpty();
        d.attributes.add(sticker);
        TLRPC.TL_documentAttributeFilename filename=new TLRPC.TL_documentAttributeFilename();filename.file_name="toxicgram-gift.tgs";d.attributes.add(filename);
        TLRPC.TL_documentAttributeImageSize dims=new TLRPC.TL_documentAttributeImageSize();dims.w=512;dims.h=512;d.attributes.add(dims);
        g.sticker=d;return g;
    }
    private static JSONObject findGift(JSONArray catalog,String kind)throws Exception{
        for(int i=0;i<catalog.length();i++){JSONObject g=catalog.getJSONObject(i);if(kind.equals(g.getString("kind")))return g;}
        throw new ApiError(400,"STARGIFT_INVALID");
    }
    private static final class GiftOrder{
        final int account;final String recipient,kind,message;final boolean anonymous;final long expires,price,giftId;
        GiftOrder(int a,String r,String k,String m,boolean h,long p,long g){account=a;recipient=r;kind=k;message=m;anonymous=h;price=p;giftId=g;expires=System.currentTimeMillis()+600000;}
    }
    private static final java.util.Map<Long,GiftOrder> orders=new java.util.HashMap<>();
    private static TLObject gifts(int a,TLObject req)throws Exception{
        if(req instanceof TL_stars.getStarGifts){
            JSONArray catalog=http("GET","/v1/gifts?dynamic=true",null,token(a)).getJSONArray("catalog");
            TL_stars.TL_starGifts result=new TL_stars.TL_starGifts();result.hash=catalog.toString().hashCode();
            int eligible=0;
            for(int i=0;i<catalog.length();i++){
                JSONObject row=catalog.getJSONObject(i);
                if(row.optInt("active",1)!=0&&(!row.optString("art","").isEmpty()||!row.optString("asset","").isEmpty())){
                    eligible++;
                    try{result.gifts.add(gift(row));}catch(Exception e){org.telegram.messenger.FileLog.e(e);}
                }
            }
            // Preserve the last successful catalog instead of publishing an empty or partial list.
            if(result.gifts.size()!=eligible)throw new IOException("Gift catalog artwork incomplete");
            return result;
        }
        if(req instanceof TL_stars.TL_payments_getStarsStatus){
            TL_stars.TL_payments_getStarsStatus q=(TL_stars.TL_payments_getStarsStatus)req;
            if(q.ton||!peer(a,q.peer).equals(prefs(a).getString("number","")))throw new ApiError(501,"TOXICGRAM_METHOD_NOT_IMPLEMENTED");
            TL_stars.TL_payments_starsStatus result=new TL_stars.TL_payments_starsStatus();
            result.balance=TL_stars.StarsAmount.ofStars(http("GET","/v1/gifts?dynamic=true",null,token(a)).getLong("balance"));return result;
        }
        if(req instanceof TLRPC.TL_payments_getPaymentForm && ((TLRPC.TL_payments_getPaymentForm)req).invoice instanceof TLRPC.TL_inputInvoiceStarGift){
            TLRPC.TL_inputInvoiceStarGift invoice=(TLRPC.TL_inputInvoiceStarGift)((TLRPC.TL_payments_getPaymentForm)req).invoice;
            if(invoice.include_upgrade)throw new ApiError(501,"TOXICGRAM_UPGRADE_NOT_IMPLEMENTED");
            String recipient=peer(a,invoice.peer);
            JSONObject row=findGiftId(http("GET","/v1/gifts?dynamic=true",null,token(a)).getJSONArray("catalog"),invoice.gift_id);
            String kind=row.getString("kind");if(row.optInt("active",1)==0||row.optInt("supply")>0&&row.optInt("remaining")==0)throw new ApiError(400,"STARGIFT_USAGE_LIMITED");
            long id=new java.security.SecureRandom().nextLong()&Long.MAX_VALUE;
            orders.entrySet().removeIf(e->e.getValue().expires<System.currentTimeMillis());
            if(orders.size()>1000)throw new ApiError(429,"FLOOD_WAIT_60");
            orders.put(id,new GiftOrder(a,recipient,kind,invoice.message==null?"":invoice.message.text,invoice.hide_name,row.getLong("price"),invoice.gift_id));
            TLRPC.TL_payments_paymentFormStarGift form=new TLRPC.TL_payments_paymentFormStarGift();
            form.form_id=id;form.invoice=new TLRPC.TL_invoice();form.invoice.currency="XTR";
            TLRPC.TL_labeledPrice price=new TLRPC.TL_labeledPrice();price.label=row.optString("name");price.amount=row.getLong("price");form.invoice.prices.add(price);
            return form;
        }
        if(req instanceof TL_stars.TL_payments_sendStarsForm){
            TL_stars.TL_payments_sendStarsForm send=(TL_stars.TL_payments_sendStarsForm)req;
            if(!(send.invoice instanceof TLRPC.TL_inputInvoiceStarGift))throw new ApiError(501,"TOXICGRAM_METHOD_NOT_IMPLEMENTED");
            TLRPC.TL_inputInvoiceStarGift invoice=(TLRPC.TL_inputInvoiceStarGift)send.invoice;
            GiftOrder order=orders.get(send.form_id);
            if(order==null||order.account!=a||order.expires<System.currentTimeMillis()||!order.recipient.equals(peer(a,invoice.peer))||order.giftId!=invoice.gift_id)throw new ApiError(400,"FORM_EXPIRED");
            try{http("POST","/v1/gifts",new JSONObject().put("kind",order.kind).put("recipient",order.recipient).put("request_id",Long.toString(send.form_id)).put("anonymous",order.anonymous).put("message",order.message).put("expected_price",order.price),token(a));}
            catch(ApiError e){if(e.code==409)throw new ApiError(400,"BALANCE_TOO_LOW");if(e.code==410)throw new ApiError(400,"STARGIFT_USAGE_LIMITED");if(e.code==412)throw new ApiError(400,"FORM_EXPIRED");throw e;}
            TLRPC.TL_payments_paymentResult result=new TLRPC.TL_payments_paymentResult();result.updates=new TLRPC.TL_updates();result.updates.date=(int)(System.currentTimeMillis()/1000L);return result;
        }
        if(req instanceof TL_stars.getSavedStarGifts){
            TL_stars.getSavedStarGifts query=(TL_stars.getSavedStarGifts)req;
            String owner=peer(a,query.peer),t=token(a);
            TL_stars.TL_payments_savedStarGifts result=new TL_stars.TL_payments_savedStarGifts();
            if(query.offset!=null&&!query.offset.isEmpty())return result;
            JSONArray received=http("GET","/v1/users/"+owner+"/gifts",null,t).getJSONArray("received");
            JSONArray catalog=http("GET","/v1/gifts?dynamic=true",null,t).getJSONArray("catalog");
            for(int i=0;i<received.length();i++){
                JSONObject row=received.getJSONObject(i);boolean visible=row.optInt("visible")!=0;
                JSONObject item=findGift(catalog,row.getString("kind"));
                if((query.exclude_unsaved&&!visible)||(query.exclude_saved&&visible)||(query.exclude_unlimited&&item.optInt("supply")==0)||query.exclude_unupgradable)continue;
                TL_stars.TL_savedStarGift g=new TL_stars.TL_savedStarGift();try{g.gift=gift(item);}catch(Exception ignored){continue;}g.date=row.getInt("created");
                g.msg_id=Math.toIntExact(row.getLong("id"));g.flags=8;g.unsaved=!visible;g.name_hidden=row.optInt("anonymous")!=0;
                String sender=row.optString("sender");if(sender.matches("TG-[0-9]{4}-[0-9]{4}")){g.from_id=peerObject(sender);g.flags|=2;try{result.users.add(user(a,http("GET","/v1/users/"+sender,null,t),sender.equals(prefs(a).getString("number",""))));}catch(Exception ignored){}}
                String message=row.optString("message");if(!message.isEmpty()){g.message=new TLRPC.TL_textWithEntities();g.message.text=message;g.flags|=4;}
                result.gifts.add(g);
            }
            result.count=result.gifts.size();return result;
        }
        if(req instanceof TL_stars.saveStarGift){
            TL_stars.saveStarGift save=(TL_stars.saveStarGift)req;
            if(!(save.stargift instanceof TL_stars.TL_inputSavedStarGiftUser))throw new ApiError(501,"TOXICGRAM_METHOD_NOT_IMPLEMENTED");
            int id=((TL_stars.TL_inputSavedStarGiftUser)save.stargift).msg_id;
            http("PUT","/v1/gifts/"+id+"/visibility",new JSONObject().put("visible",!save.unsave),token(a));return new TLRPC.TL_boolTrue();
        }
        return null;
    }
    private static String inputUser(int a,TLRPC.InputUser input)throws ApiError{
        if(input instanceof TLRPC.TL_inputUserSelf)return prefs(a).getString("number","");
        if(input instanceof TLRPC.TL_inputUser)return canonical(input.user_id);
        throw new ApiError(400,"PEER_ID_INVALID");
    }
    private static TLRPC.TL_updates updates(){
        TLRPC.TL_updates u=new TLRPC.TL_updates();u.date=(int)(System.currentTimeMillis()/1000L);return u;
    }
    private static void cacheMediaFile(int a,String asset,File target,File original)throws Exception{
        if(original==null){fetchAsset(a,asset,target,24*1024*1024);return;}
        if(target.equals(original)||target.isFile()&&target.length()==original.length())return;
        File parent=target.getParentFile();if(parent==null)throw new IOException("Invalid cache directory");
        if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Cache unavailable");
        File tmp=File.createTempFile("tg-media-",".tmp",parent);
        try{
            try(InputStream in=new FileInputStream(original);OutputStream out=new FileOutputStream(tmp)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
            if(!tmp.renameTo(target)&&!target.isFile())throw new IOException("Cache write failed");
        }finally{tmp.delete();}
    }
    private static TLRPC.MessageMedia attachmentMedia(int a,JSONObject row,int date,File original)throws Exception{
        if(!"photo".equals(row.optString("kind")))return documentMedia(a,row,date,original);
        String asset=row.getString("id");
        long id=localId(asset,MEDIA_BASE);
        TLRPC.TL_photo photo=new TLRPC.TL_photo();photo.id=id;photo.access_hash=id;photo.dc_id=1;photo.date=date;photo.file_reference=asset.getBytes(StandardCharsets.UTF_8);
        TLRPC.TL_photoSize size=new TLRPC.TL_photoSize();size.type="x";size.w=Math.max(1,row.optInt("width",800));size.h=Math.max(1,row.optInt("height",800));size.size=(int)row.optLong("size");size.location=avatarLocation(id,'x');photo.sizes.add(size);
        try{
            File target=FileLoader.getInstance(a).getPathToAttach(size,false);
            cacheMediaFile(a,asset,target,original);
            android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeFile(target.getAbsolutePath(),options);
            if(options.outWidth>0&&options.outHeight>0){size.w=options.outWidth;size.h=options.outHeight;}
            File cache=FileLoader.getInstance(a).getPathToAttach(size,true);
            if(!cache.equals(target))cacheMediaFile(a,asset,cache,target);
        }catch(Exception e){org.telegram.messenger.FileLog.e("Toxicgram photo cache: "+e.getClass().getSimpleName());}
        TLRPC.TL_messageMediaPhoto result=new TLRPC.TL_messageMediaPhoto();result.photo=photo;result.flags=1;return result;
    }
    private static TLRPC.TL_messageMediaDocument documentMedia(int a,JSONObject row,int date,File original)throws Exception{
        String asset=row.getString("id"),kind=row.optString("kind","file");
        TLRPC.TL_document d=new TLRPC.TL_document();d.id=localId(asset,MEDIA_BASE);d.access_hash=d.id;d.file_reference=asset.getBytes(StandardCharsets.UTF_8);d.date=date;d.dc_id=1;d.size=row.optLong("size");d.mime_type=row.optString("mime","application/octet-stream");
        TLRPC.TL_documentAttributeFilename fn=new TLRPC.TL_documentAttributeFilename();fn.file_name=row.optString("name","file");d.attributes.add(fn);
        if("voice".equals(kind)){TLRPC.TL_documentAttributeAudio audio=new TLRPC.TL_documentAttributeAudio();audio.voice=true;audio.duration=row.optInt("duration");audio.flags=1024;d.attributes.add(audio);}
        if("round".equals(kind)||"video".equals(kind)){TLRPC.TL_documentAttributeVideo video=new TLRPC.TL_documentAttributeVideo();video.round_message="round".equals(kind);video.duration=row.optInt("duration");video.w=Math.max(1,row.optInt("width",360));video.h=Math.max(1,row.optInt("height",360));video.flags=video.round_message?1:0;d.attributes.add(video);}
        TLRPC.TL_messageMediaDocument m=new TLRPC.TL_messageMediaDocument();m.document=d;m.flags=1;m.voice="voice".equals(kind);m.round="round".equals(kind);m.video=m.round||"video".equals(kind);
        if(m.voice)m.flags|=256;if(m.video)m.flags|=64;if(m.round)m.flags|=128;
        File target=FileLoader.getInstance(a).getPathToAttach(d,false);
        try{
            if(original!=null&&!target.isFile()){File parent=target.getParentFile();if(parent!=null)parent.mkdirs();try(InputStream in=new FileInputStream(original);OutputStream out=new FileOutputStream(target)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}}
            else if(original==null)fetchAsset(a,asset,target,24*1024*1024);
        }catch(Exception ignored){}
        return m;
    }
    private static TLObject mediaRpc(int a,TLObject req)throws Exception{
        if(req instanceof TLRPC.TL_photos_uploadProfilePhoto){
            TLRPC.TL_photos_uploadProfilePhoto q=(TLRPC.TL_photos_uploadProfilePhoto)req;
            if(q.video!=null||q.bot!=null||q.fallback)throw new ApiError(400,"TOXICGRAM_PHOTO_ONLY");
            File source=stagedFile(a,q.file);
            android.graphics.BitmapFactory.Options opt=new android.graphics.BitmapFactory.Options();opt.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeFile(source.getAbsolutePath(),opt);
            if(opt.outWidth<=0||opt.outHeight<=0)throw new ApiError(400,"PHOTO_INVALID");
            opt.inJustDecodeBounds=false;opt.inSampleSize=1;while(Math.max(opt.outWidth,opt.outHeight)/opt.inSampleSize>1600)opt.inSampleSize*=2;
            android.graphics.Bitmap bm=android.graphics.BitmapFactory.decodeFile(source.getAbsolutePath(),opt);
            if(bm==null)throw new ApiError(400,"PHOTO_INVALID");
            File tmp=File.createTempFile("tg-avatar-",".jpg",ApplicationLoader.applicationContext.getCacheDir());
            try{
                try(OutputStream out=new FileOutputStream(tmp)){bm.compress(android.graphics.Bitmap.CompressFormat.JPEG,85,out);}
                String id=uploadBytes(a,"PUT","/v1/avatar",tmp,512*1024).getString("avatar");
                JSONObject p=http("GET","/v1/me",null,token(a));
                TLRPC.TL_photos_photo result=new TLRPC.TL_photos_photo();result.photo=avatarPhoto(a,id);result.users.add(user(a,p,true));return result;
            }finally{bm.recycle();tmp.delete();}
        }
        if(req instanceof TLRPC.TL_photos_getUserPhotos){
            TLRPC.TL_photos_getUserPhotos q=(TLRPC.TL_photos_getUserPhotos)req;String n=inputUser(a,q.user_id);boolean self=n.equals(prefs(a).getString("number",""));
            JSONObject p=http("GET",self?"/v1/me":"/v1/users/"+n,null,token(a));TLRPC.TL_photos_photos result=new TLRPC.TL_photos_photos();
            String id=p.optString("avatar","");if(q.offset==0&&q.max_id==0&&id.matches("[a-f0-9]{32}"))result.photos.add(avatarPhoto(a,id));
            result.users.add(user(a,p,self));return result;
        }
        if(req instanceof TLRPC.TL_messages_sendMedia){
            TLRPC.TL_messages_sendMedia q=(TLRPC.TL_messages_sendMedia)req;
            if(!(q.media instanceof TLRPC.TL_inputMediaUploadedDocument || q.media instanceof TLRPC.TL_inputMediaUploadedPhoto)||q.schedule_date!=0)throw new ApiError(501,"TOXICGRAM_MEDIA_NOT_IMPLEMENTED");
            File source=stagedFile(a,q.media.file);
            boolean photo=q.media instanceof TLRPC.TL_inputMediaUploadedPhoto;
            String kind=photo?"photo":"file",mime=photo?"image/jpeg":q.media.mime_type,name=source.getName();
            if(mime==null||mime.isEmpty())mime="application/octet-stream";
            int duration=0,w=0,h=0;
            if(photo){
                android.graphics.BitmapFactory.Options opt=new android.graphics.BitmapFactory.Options();opt.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeFile(source.getAbsolutePath(),opt);
                if(opt.outWidth<=0||opt.outHeight<=0)throw new ApiError(400,"PHOTO_INVALID");
                w=opt.outWidth;h=opt.outHeight;name="photo.jpg";
                if(opt.outMimeType!=null)mime=opt.outMimeType;
            }else if(q.media.attributes!=null)for(TLRPC.DocumentAttribute attr:q.media.attributes){
                if(attr instanceof TLRPC.TL_documentAttributeFilename && attr.file_name!=null)name=attr.file_name;
                if(attr instanceof TLRPC.TL_documentAttributeAudio&&attr.voice){kind="voice";duration=(int)Math.ceil(attr.duration);}
                if(attr instanceof TLRPC.TL_documentAttributeVideo){kind=attr.round_message?"round":"video";duration=(int)Math.ceil(attr.duration);w=attr.w;h=attr.h;}
            }
            if("voice".equals(kind)){mime="audio/ogg";name="voice.ogg";}
            if("round".equals(kind)||"video".equals(kind)){mime="video/mp4";name="video.mp4";}
            boolean room=isRoom(q.peer);
            String path=room?"/v1/rooms/"+roomInfo(a,roomPeerId(q.peer)).getJSONObject("room").getString("id")+"/attachments?":"/v1/attachments?recipient="+android.net.Uri.encode(peer(a,q.peer))+"&";
            path+="kind="+kind+"&mime="+android.net.Uri.encode(mime)+"&name="+android.net.Uri.encode(name)+"&duration="+duration+"&width="+w+"&height="+h+"&request_id="+q.random_id+"&caption="+android.net.Uri.encode(q.message==null?"":q.message);
            JSONObject sent=uploadBytes(a,"POST",path,source,24*1024*1024);
            JSONObject row=new JSONObject().put("id",sent.getString("attachment")).put("kind",kind).put("name",name).put("mime",mime).put("duration",duration).put("width",w).put("height",h).put("size",source.length());
            TLRPC.TL_updateShortSentMessage result=new TLRPC.TL_updateShortSentMessage();result.id=room?Math.addExact(ROOM_MESSAGE_BASE,sent.getInt("id")):sent.getInt("id");result.date=sent.getInt("created");result.out=true;result.flags=2|512;result.media=attachmentMedia(a,row,result.date,source);return result;
        }
        return null;
    }
    private static TLObject profileRpc(int a,TLObject req)throws Exception{
        if(req instanceof TL_account.updateProfile){
            TL_account.updateProfile q=(TL_account.updateProfile)req;JSONObject body=new JSONObject();
            if((q.flags&1)!=0)body.put("first_name",q.first_name);
            if((q.flags&2)!=0)body.put("last_name",q.last_name);
            if((q.flags&4)!=0)body.put("bio",q.about);
            return user(a,http("PUT","/v1/profile/partial",body,token(a)),true);
        }
        if(req instanceof TL_account.checkUsername){
            String username=((TL_account.checkUsername)req).username;
            try{if(!http("GET","/v1/username/check?username="+android.net.Uri.encode(username),null,token(a)).getBoolean("available"))throw new ApiError(400,"USERNAME_OCCUPIED");}
            catch(ApiError e){if(e.code==400&&!"USERNAME_OCCUPIED".equals(e.type))throw new ApiError(400,"USERNAME_INVALID");throw e;}
            return new TLRPC.TL_boolTrue();
        }
        if(req instanceof TL_account.updateUsername){
            try{return user(a,http("PUT","/v1/profile/partial",new JSONObject().put("username",((TL_account.updateUsername)req).username),token(a)),true);}
            catch(ApiError e){if(e.code==409)throw new ApiError(400,"USERNAME_OCCUPIED");if(e.code==400)throw new ApiError(400,"USERNAME_INVALID");throw e;}
        }
        if(req instanceof TLRPC.TL_users_getFullUser){
            String number=inputUser(a,((TLRPC.TL_users_getFullUser)req).id);
            boolean self=number.equals(prefs(a).getString("number",""));
            JSONObject p=http("GET",self?"/v1/me":"/v1/users/"+number,null,token(a));
            TLRPC.TL_users_userFull result=new TLRPC.TL_users_userFull();
            TLRPC.TL_userFull full=new TLRPC.TL_userFull();full.id=userId(number);full.about=p.optString("bio","");full.flags=2;
            full.settings=new TLRPC.TL_peerSettings();full.notify_settings=new TLRPC.TL_peerNotifySettings();
            full.stargifts_count=p.optInt("gift_count",0);full.flags2|=256;
            JSONObject verification=p.optJSONObject("verification");
            if(verification!=null){
                full.bot_verification=new org.telegram.tgnet.tl.TL_bots.BotVerification();
                full.bot_verification.bot_id=0;
                full.bot_verification.icon=920000000000000000L+verification.optInt("color")*6L+verification.optInt("symbol");
                full.bot_verification.description=new TLRPC.TL_textWithEntities();
                full.bot_verification.description.text=verification.optString("title")+" — "+verification.optString("description");
                full.flags2|=131072;
            }
            String avatar=p.optString("avatar","");if(avatar.matches("[a-f0-9]{32}")){try{full.profile_photo=avatarPhoto(a,avatar);full.flags|=4;}catch(Exception ignored){}}
            result.full_user=full;result.users.add(user(a,p,self));return result;
        }
        if(req instanceof TLRPC.TL_contacts_search){
            String q=((TLRPC.TL_contacts_search)req).q;
            JSONArray users=http("GET","/v1/users-search?q="+android.net.Uri.encode(q),null,token(a)).getJSONArray("users");
            TLRPC.TL_contacts_found result=new TLRPC.TL_contacts_found();
            for(int i=0;i<users.length();i++){JSONObject p=users.getJSONObject(i);result.users.add(user(a,p,false));result.results.add(peerObject(p.getString("number")));}
            return result;
        }
        return null;
    }
    private static final long ROOM_BASE=300000000L;
    private static final int ROOM_MESSAGE_BASE=1000000000;
    private static boolean isRoom(TLRPC.InputPeer p){return p instanceof TLRPC.TL_inputPeerChat||p instanceof TLRPC.TL_inputPeerChannel;}
    private static long roomPeerId(TLRPC.InputPeer p)throws ApiError{
        if(p instanceof TLRPC.TL_inputPeerChat)return p.chat_id;
        if(p instanceof TLRPC.TL_inputPeerChannel)return p.channel_id;
        throw new ApiError(400,"PEER_ID_INVALID");
    }
    private static JSONObject roomInfo(int a,long id)throws Exception{
        if(id<=ROOM_BASE)throw new ApiError(400,"CHAT_ID_INVALID");
        return http("GET","/v1/rooms/by-id/"+(id-ROOM_BASE),null,token(a));
    }
    private static TLRPC.Chat roomChat(JSONObject row,String self)throws Exception{
        boolean channel="channel".equals(row.optString("mode"));
        TLRPC.Chat chat=channel?new TLRPC.TL_channel():new TLRPC.TL_chat();
        chat.id=ROOM_BASE+row.getLong("numeric_id");chat.title=row.getString("title");chat.photo=new TLRPC.TL_chatPhotoEmpty();
        chat.creator=self.equals(row.optString("owner"));chat.date=row.optInt("created",1);chat.participants_count=row.optInt("members",1);chat.version=1;
        if(channel){chat.access_hash=chat.id;chat.broadcast="channel".equals(row.optString("kind"));chat.megagroup=!chat.broadcast;chat.flags=8192|131072|(chat.broadcast?32:256)|(chat.creator?1:0);}
        else chat.flags=chat.creator?1:0;
        return chat;
    }
    private static TLRPC.Peer roomPeer(JSONObject row)throws Exception{
        long id=ROOM_BASE+row.getLong("numeric_id");
        if("channel".equals(row.optString("mode"))){TLRPC.TL_peerChannel p=new TLRPC.TL_peerChannel();p.channel_id=id;return p;}
        TLRPC.TL_peerChat p=new TLRPC.TL_peerChat();p.chat_id=id;return p;
    }
    private static TLRPC.TL_message roomMessage(int a,JSONObject row,JSONObject room,String self)throws Exception{
        TLRPC.TL_message m=new TLRPC.TL_message();m.id=Math.addExact(ROOM_MESSAGE_BASE,row.getInt("id"));m.date=row.getInt("created");m.message=row.optString("text","");
        m.peer_id=roomPeer(room);m.out=self.equals(row.optString("sender"));m.from_id=peerObject(row.getString("sender"));m.flags=256|(m.out?2:0);m.media=new TLRPC.TL_messageMediaEmpty();JSONObject attachment=row.optJSONObject("attachment");if(attachment!=null){m.media=attachmentMedia(a,attachment,m.date,null);m.flags|=512;}return m;
    }
    private static TLObject roomsRpc(int a,TLObject req)throws Exception{
        String self=prefs(a).getString("number","");
        if(req instanceof TLRPC.TL_messages_getFullChat || req instanceof TLRPC.TL_channels_getFullChannel){
            long id=req instanceof TLRPC.TL_messages_getFullChat?((TLRPC.TL_messages_getFullChat)req).chat_id:((TLRPC.TL_channels_getFullChannel)req).channel.channel_id;
            JSONObject info=roomInfo(a,id),row=info.getJSONObject("room");JSONArray members=info.getJSONArray("users");
            TLRPC.TL_messages_chatFull result=new TLRPC.TL_messages_chatFull();
            boolean channel="channel".equals(row.optString("mode"));
            TLRPC.ChatFull full=channel?new TLRPC.TL_channelFull():new TLRPC.TL_chatFull();
            full.id=id;full.about=row.optString("about","");full.notify_settings=new TLRPC.TL_peerNotifySettings();full.participants_count=row.optInt("members",1);
            full.chat_photo=new TLRPC.TL_photoEmpty();
            if(channel){full.flags=1;full.can_view_participants=true;}
            else {
                TLRPC.TL_chatParticipants ps=new TLRPC.TL_chatParticipants();ps.chat_id=id;ps.version=1;
                for(int i=0;i<members.length();i++){
                    JSONObject p=members.getJSONObject(i);String n=p.getString("number");
                    TLRPC.ChatParticipant member=n.equals(row.optString("owner"))?new TLRPC.TL_chatParticipantCreator():new TLRPC.TL_chatParticipant();
                    member.user_id=userId(n);member.inviter_id=userId(row.getString("owner"));member.date=row.optInt("created",1);ps.participants.add(member);
                }
                full.participants=ps;
            }
            for(int i=0;i<members.length();i++){JSONObject p=members.getJSONObject(i);result.users.add(user(a,p,self.equals(p.getString("number"))));}
            result.full_chat=full;result.chats.add(roomChat(row,self));return result;
        }
        if(req instanceof TLRPC.TL_messages_createChat){
            TLRPC.TL_messages_createChat q=(TLRPC.TL_messages_createChat)req;JSONArray members=new JSONArray();
            for(TLRPC.InputUser u:q.users)members.put(inputUser(a,u));
            JSONObject room=http("POST","/v1/rooms",new JSONObject().put("kind","group").put("mode","basic").put("title",q.title).put("members",members),token(a));
            TLRPC.TL_messages_invitedUsers result=new TLRPC.TL_messages_invitedUsers();TLRPC.TL_updates u=updates();u.chats.add(roomChat(room,self));result.updates=u;return result;
        }
        if(req instanceof TLRPC.TL_channels_createChannel){
            TLRPC.TL_channels_createChannel q=(TLRPC.TL_channels_createChannel)req;
            JSONObject room=http("POST","/v1/rooms",new JSONObject().put("kind",q.megagroup?"group":"channel").put("mode","channel").put("title",q.title).put("about",q.about==null?"":q.about),token(a));
            TLRPC.TL_updates u=updates();u.chats.add(roomChat(room,self));return u;
        }
        if(req instanceof TLRPC.TL_channels_inviteToChannel){
            TLRPC.TL_channels_inviteToChannel q=(TLRPC.TL_channels_inviteToChannel)req;
            JSONObject room=roomInfo(a,q.channel.channel_id).getJSONObject("room");
            for(TLRPC.InputUser u:q.users)http("POST","/v1/rooms/"+room.getString("id")+"/invite",new JSONObject().put("number",inputUser(a,u)),token(a));
            TLRPC.TL_messages_invitedUsers result=new TLRPC.TL_messages_invitedUsers();TLRPC.TL_updates u=updates();u.chats.add(roomChat(roomInfo(a,q.channel.channel_id).getJSONObject("room"),self));result.updates=u;return result;
        }
        if(req instanceof TLRPC.TL_messages_getHistory && isRoom(((TLRPC.TL_messages_getHistory)req).peer)){
            TLRPC.TL_messages_getHistory q=(TLRPC.TL_messages_getHistory)req;String t=token(a);
            JSONObject info=roomInfo(a,roomPeerId(q.peer)),room=info.getJSONObject("room");
            String path="/v1/rooms/"+room.getString("id")+"/messages"+(q.offset_id>ROOM_MESSAGE_BASE?"?before="+(q.offset_id-ROOM_MESSAGE_BASE):"");
            JSONArray messages=http("GET",path,null,t).getJSONArray("messages"),members=info.getJSONArray("users");
            TLRPC.TL_messages_messages result=new TLRPC.TL_messages_messages();
            for(int i=messages.length()-1;i>=0;i--)result.messages.add(roomMessage(a,messages.getJSONObject(i),room,self));
            for(int i=0;i<members.length();i++){JSONObject p=members.getJSONObject(i);result.users.add(user(a,p,self.equals(p.getString("number"))));}
            result.chats.add(roomChat(room,self));return result;
        }
        if(req instanceof TLRPC.TL_messages_sendMessage && isRoom(((TLRPC.TL_messages_sendMessage)req).peer)){
            TLRPC.TL_messages_sendMessage q=(TLRPC.TL_messages_sendMessage)req;
            JSONObject room=roomInfo(a,roomPeerId(q.peer)).getJSONObject("room");
            JSONObject sent=http("POST","/v1/rooms/"+room.getString("id")+"/messages",new JSONObject().put("text",q.message),token(a));
            TLRPC.TL_updateShortSentMessage result=new TLRPC.TL_updateShortSentMessage();result.id=Math.addExact(ROOM_MESSAGE_BASE,sent.getInt("id"));result.date=sent.getInt("created");result.out=true;result.flags=2;return result;
        }
        return null;
    }
    private static TLObject dispatch(int a,TLObject req)throws Exception{
        // Authentication must not depend on cached room, gift or profile state.
        if(req instanceof TLRPC.TL_auth_sendCode || req instanceof TLRPC.TL_auth_resendCode){
            String raw=req instanceof TLRPC.TL_auth_sendCode?((TLRPC.TL_auth_sendCode)req).phone_number:((TLRPC.TL_auth_resendCode)req).phone_number;
            if(!phone(raw).matches("\\+(7|1|388)000[0-9]{7}"))throw new ApiError(400,"PHONE_NUMBER_INVALID");
            // The bot issues the code. This response only opens the original code-entry screen.
            TLRPC.TL_auth_sentCode result=new TLRPC.TL_auth_sentCode();
            TLRPC.TL_auth_sentCodeTypeApp type=new TLRPC.TL_auth_sentCodeTypeApp();
            type.length=6;result.type=type;result.phone_code_hash="toxicgram-bot";result.timeout=300;
            return result;
        }
        if(req instanceof TLRPC.TL_auth_signIn){
            TLRPC.TL_auth_signIn sign=(TLRPC.TL_auth_signIn)req;
            if(sign.phone_code==null||!sign.phone_code.matches("[0-9]{6}"))throw new ApiError(400,"PHONE_CODE_INVALID");
            JSONObject login;
            try{login=http("POST","/v1/auth/verify",new JSONObject().put("number",phone(sign.phone_number)).put("code",sign.phone_code),null);}
            catch(ApiError e){if(e.code==401)throw new ApiError(400,"PHONE_CODE_INVALID");throw e;}
            String t=login.getString("access_token");
            JSONObject profile=http("GET","/v1/me",null,t);
            TLRPC.TL_auth_authorization auth=new TLRPC.TL_auth_authorization();
            auth.user=user(a,profile,true);
            setForeground(a,false);store(a,t,profile.getString("number"));setForeground(a,true);ConnectionsManager.onToxicgramConnectionStateChanged(ConnectionsManager.ConnectionStateConnected,a);return auth;
        }
        TLObject mediaResponse=mediaRpc(a,req);
        if(mediaResponse!=null)return mediaResponse;
        TLObject profileResponse=profileRpc(a,req);
        if(profileResponse!=null)return profileResponse;
        TLObject roomResponse=roomsRpc(a,req);
        if(roomResponse!=null)return roomResponse;
        TLObject giftResponse=gifts(a,req);
        if(giftResponse!=null)return giftResponse;
        if(req instanceof TLRPC.TL_auth_logOut){
            http("POST","/v1/auth/logout",null,token(a));setForeground(a,false);prefs(a).edit().clear().commit();
            return new TLRPC.TL_auth_loggedOut();
        }
        if(req instanceof TLRPC.TL_messages_getDialogs){
            TLRPC.TL_messages_getDialogs query=(TLRPC.TL_messages_getDialogs)req;
            TLRPC.TL_messages_dialogs result=new TLRPC.TL_messages_dialogs();
            if(query.offset_id!=0)return result;
            String t=token(a),self=prefs(a).getString("number","");
            JSONArray chats=http("GET","/v1/chats",null,t).getJSONArray("chats");
            for(int i=0;i<chats.length();i++){
                JSONObject row=chats.getJSONObject(i);String number=row.getString("number");
                boolean isSelf=self.equals(number);
                TLRPC.TL_user u=user(a,row,isSelf);result.users.add(u);
                TLRPC.TL_dialog dialog=new TLRPC.TL_dialog();
                dialog.peer=peerObject(number);dialog.top_message=Math.toIntExact(row.getLong("id"));
                dialog.unread_count=row.optInt("unread");dialog.notify_settings=new TLRPC.TL_peerNotifySettings();
                dialog.pinned=row.optInt("pinned")!=0;if(dialog.pinned)dialog.flags|=4;
                result.dialogs.add(dialog);
                // Fetch actual latest message rather than infer its sender from the dialog.
                JSONArray history=http("GET","/v1/chats/"+number+"/messages",null,t).getJSONArray("messages");
                if(history.length()>0)result.messages.add(message(a,history.getJSONObject(history.length()-1),self));
            }
            JSONArray rooms=http("GET","/v1/rooms",null,t).getJSONArray("rooms");
            for(int i=0;i<rooms.length();i++){
                JSONObject row=rooms.getJSONObject(i);result.chats.add(roomChat(row,self));
                TLRPC.TL_dialog dialog=new TLRPC.TL_dialog();dialog.peer=roomPeer(row);dialog.notify_settings=new TLRPC.TL_peerNotifySettings();
                JSONArray history=http("GET","/v1/rooms/"+row.getString("id")+"/messages",null,t).getJSONArray("messages");
                if(history.length()>0){
                    JSONObject latest=history.getJSONObject(history.length()-1);TLRPC.TL_message m=roomMessage(a,latest,row,self);dialog.top_message=m.id;result.messages.add(m);
                    String sender=latest.getString("sender");
                    if(!self.equals(sender))result.users.add(user(a,http("GET","/v1/users/"+sender,null,t),false));
                }
                result.dialogs.add(dialog);
            }
            return result;
        }
        if(req instanceof TLRPC.TL_messages_getHistory){
            TLRPC.TL_messages_getHistory query=(TLRPC.TL_messages_getHistory)req;
            String other=peer(a,query.peer),t=token(a),self=prefs(a).getString("number","");
            String path="/v1/chats/"+other+"/messages"+(query.offset_id>0?"?before="+query.offset_id:"");
            JSONArray messages=http("GET",path,null,t).getJSONArray("messages");
            TLRPC.TL_messages_messages result=new TLRPC.TL_messages_messages();
            for(int i=messages.length()-1;i>=0;i--)result.messages.add(message(a,messages.getJSONObject(i),self));
            if(!other.equals(self))result.users.add(user(a,http("GET","/v1/users/"+other,null,t),false));
            return result;
        }
        if(req instanceof TLRPC.TL_messages_sendMessage){
            TLRPC.TL_messages_sendMessage send=(TLRPC.TL_messages_sendMessage)req;
            JSONObject sent=http("POST","/v1/messages",new JSONObject().put("recipient",peer(a,send.peer)).put("text",send.message),token(a));
            TLRPC.TL_updateShortSentMessage result=new TLRPC.TL_updateShortSentMessage();
            result.id=Math.toIntExact(sent.getLong("id"));result.date=sent.getInt("created");result.out=true;result.flags=2;
            // REST has no global MTProto pts stream. This response acknowledges the persisted message.
            result.pts=0;result.pts_count=0;return result;
        }
        if(req instanceof TLRPC.TL_messages_readHistory){
            TLRPC.TL_messages_readHistory read=(TLRPC.TL_messages_readHistory)req;
            if(isRoom(read.peer))throw new ApiError(501,"TOXICGRAM_ROOM_READ_NOT_IMPLEMENTED");
            http("PUT","/v1/chats/"+peer(a,read.peer)+"/read",new JSONObject().put("last_id",Math.max(0,read.max_id)),token(a));
            TLRPC.TL_messages_affectedMessages result=new TLRPC.TL_messages_affectedMessages();result.pts=0;result.pts_count=0;return result;
        }
        if(req instanceof TLRPC.TL_contacts_resolveUsername || req instanceof TLRPC.TL_contacts_resolvePhone){
            String value=req instanceof TLRPC.TL_contacts_resolveUsername?"@"+((TLRPC.TL_contacts_resolveUsername)req).username:phone(((TLRPC.TL_contacts_resolvePhone)req).phone);
            JSONObject profile=http("GET","/v1/users/"+android.net.Uri.encode(value),null,token(a));
            TLRPC.TL_contacts_resolvedPeer result=new TLRPC.TL_contacts_resolvedPeer();
            result.peer=peerObject(profile.getString("number"));result.users.add(user(a,profile,prefs(a).getString("number","").equals(profile.getString("number"))));return result;
        }
        // All unported methods fail closed. No native MTProto or official Telegram RPC fallback.
        throw new ApiError(501,"TOXICGRAM_METHOD_NOT_IMPLEMENTED");
    }
}
