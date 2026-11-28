package com.toxicgram.app;

import android.app.Activity;
import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.media.MediaPlayer;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.util.LruCache;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.os.Build;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.Editable;
import android.text.format.DateFormat;
import java.util.Date;
import com.toxicgram.app.telegram.CombinedDrawable;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.text.InputType;
import android.text.InputFilter;
import org.json.JSONObject;
import org.json.JSONArray;
import android.os.Handler;
import android.os.Looper;
import android.content.ClipboardManager;
import android.content.ClipData;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String BASE = "https://toxicgram-auth-production.up.railway.app";
    private int BG=0xff17212b, CARD=0xff202b36, INK=0xfff5f5f5, MUTED=0xff8295a6, PURPLE=0xff8c69de;
    private int CHAT=0xff0e1621, OUT=0xff473a70, LINE=0xff283542;
    private boolean light=false;
    private String ownName="", ownPhone="";
    private final java.util.Map<String,String> phones=new java.util.HashMap<>();
    private boolean foreground=true;
    private String pendingPeer="",pendingName="",pendingMedia="",pendingFilename="",pendingToken="";
    private MediaRecorder recorder;private File recording;private MediaPlayer player;private File playback;
    private int pendingPage=-1;
    private Runnable pendingRefresh;
    private final java.util.Map<String,String> avatarIds=new java.util.HashMap<>();
    private final LruCache<String,Bitmap> imageCache=new LruCache<String,Bitmap>(8*1024*1024){protected int sizeOf(String k,Bitmap b){return b.getByteCount();}};
    private final ExecutorService mediaNetwork=Executors.newFixedThreadPool(2);
    private final ExecutorService network=Executors.newSingleThreadExecutor();
    private String session, account;
    private LinearLayout root;
    private TextView status;
    private EditText number, code;
    private Button submit;
    private int page=0;
    private final Handler timer=new Handler(Looper.getMainLooper());
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        light=getPreferences(MODE_PRIVATE).getBoolean("light",false);
        palette();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        showLogin();
    }
    int dp(float v){return (int)(getResources().getDisplayMetrics().density*v+0.5f);}
    GradientDrawable shape(int color,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;
    }
    void palette(){
        BG=light?0xffffffff:0xff17212b;CARD=light?0xfff1f3f5:0xff202b36;
        INK=light?0xff17212b:0xfff5f5f5;MUTED=light?0xff788692:0xff8295a6;
        PURPLE=light?0xff7250c8:0xff8c69de;CHAT=light?0xffe4e7f1:0xff0e1621;
        OUT=light?0xffe3d8fa:0xff473a70;LINE=light?0xffe7e9ed:0xff283542;
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(light?View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0);
    }
    void install(View v){
        if(Build.VERSION.SDK_INT>=30){
            FrameLayout safe=new FrameLayout(this);safe.setBackgroundColor(BG);
            safe.addView(v,new FrameLayout.LayoutParams(-1,-1));
            safe.setOnApplyWindowInsetsListener((view,insets)->{
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());
                view.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;
            });
            setContentView(safe);safe.requestApplyInsets();
        }else setContentView(v);
    }
    void screen(){
        page++;timer.removeCallbacksAndMessages(null);
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setBackgroundColor(BG);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(24),dp(48),dp(24),dp(28));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        sc.addView(root,new ScrollView.LayoutParams(-1,-2));install(sc);
    }
    TextView text(String s,int size,int color,boolean bold,int margin){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(margin);root.addView(t,p);return t;
    }
    EditText field(String hint,int type,int length){
        EditText e=new EditText(this);e.setTextColor(INK);e.setHintTextColor(MUTED);e.setHint(hint);e.setTextSize(17);
        e.setSingleLine();e.setInputType(type);e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(length)});
        e.setPadding(dp(16),0,dp(16),0);e.setBackground(shape(CARD,16));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(60));p.bottomMargin=dp(12);root.addView(e,p);return e;
    }
    Button button(String label){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setTextSize(16);
        b.setBackground(shape(PURPLE,16));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));p.topMargin=dp(12);p.bottomMargin=dp(12);root.addView(b,p);return b;
    }
    void showLogin(){
        screen();
        TextView logo=text("T",38,Color.WHITE,true,24);logo.setBackground(shape(PURPLE,24));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(84),dp(84));lp.bottomMargin=dp(24);logo.setLayoutParams(lp);
        text("Toxicgram",32,INK,true,12);
        text("Вход в свой аккаунт",20,INK,true,8);
        text("Откройте бота Toxicgram в Telegram, отправьте /code и введите выданный виртуальный номер и код.",15,MUTED,false,28);
        number=field("+388 000 1234567",InputType.TYPE_CLASS_TEXT,32);
        code=field("Код из 6 цифр",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD,6);
        submit=button("Войти");
        status=text("",14,MUTED,false,16);
        text("Используйте только код Toxicgram, не код входа в Telegram.",13,MUTED,false,16);
        submit.setOnClickListener(v->login());
    }
    void login(){
        String n=identity(number.getText().toString()), c=code.getText().toString().trim();
        if(!validIdentity(n)||!c.matches("[0-9]{6}")){status.setText("Проверьте формат номера и кода.");return;}
        submit.setEnabled(false);status.setText("Подключение...");
        network.execute(()->{
            try{
                JSONObject body=new JSONObject().put("number",n).put("code",c);
                JSONObject response=request("POST","/v1/auth/verify",body,null);
                String token=response.getString("access_token"), id=response.getString("number");
                JSONObject profile=request("GET","/v1/me",null,token);
                if(!id.equals(profile.getString("number")))throw new IOException("Account mismatch");
                runOnUiThread(()->{if(isFinishing()||isDestroyed())return;session=token;account=id;ownPhone=profile.optString("phone",id);phones.put(id,ownPhone);ownName=profile.optString("name",id);showAccount();});
            }catch(Exception e){
                runOnUiThread(()->{if(isFinishing()||isDestroyed())return;submit.setEnabled(true);status.setText(e instanceof ApiError?(((ApiError)e).status==429?"Слишком много попыток. Повторите позже.":((ApiError)e).status==401?"Неверный или просроченный код. Получите новый.":"Сервер временно недоступен."):"Не удалось подключиться к серверу.");});
            }
        });
    }
    interface Result { void accept(JSONObject data) throws Exception; }
    void call(String method,String path,JSONObject body,Result done,TextView error,Button busy){
        final int current=page;final String token=session;
        if(busy!=null)busy.setEnabled(false);
        network.execute(()->{
            try {
                JSONObject data=request(method,path,body,token);
                runOnUiThread(()->{
                    if(isDestroyed()||current!=page)return;
                    if(busy!=null)busy.setEnabled(true);
                    try{done.accept(data);}catch(Exception e){if(error!=null)error.setText("Не удалось обработать ответ.");}
                });
            } catch(Exception e){
                runOnUiThread(()->{
                    if(isDestroyed()||current!=page)return;
                    if(busy!=null)busy.setEnabled(true);
                    if(e instanceof ApiError&&((ApiError)e).status==401){session=null;account=null;showLogin();status.setText("Сеанс завершён. Войдите снова.");return;}
                    if(error!=null)error.setText(e instanceof ApiError&&((ApiError)e).status==409?"Username уже занят.":e instanceof ApiError&&((ApiError)e).status==403?"Действие недоступно.":e instanceof ApiError&&((ApiError)e).status==404?"Аккаунт не найден.":e instanceof ApiError&&((ApiError)e).status==429?"Слишком много запросов.":"Ошибка соединения. Повторите позже.");
                });
            }
        });
    }
    JSONObject data(String key,String value){
        JSONObject o=new JSONObject();try{o.put(key,value);}catch(Exception ignored){}return o;
    }
    LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    LinearLayout horizontal(){LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    TextView label(String s,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return t;
    }
    void line(LinearLayout parent,int left){
        View v=new View(this);v.setBackgroundColor(LINE);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(1));p.leftMargin=dp(left);parent.addView(v,p);
    }
    Drawable ripple(int color,int radius){return new RippleDrawable(ColorStateList.valueOf(0x228e95ae),shape(color,radius),shape(Color.WHITE,radius));}
    class Glyph extends Drawable {
        final String kind; final int color; final Paint p=new Paint(3);
        Glyph(String k,int c){kind=k;color=c;}
        public int getIntrinsicWidth(){return dp(24);}public int getIntrinsicHeight(){return dp(24);}
        @Override public void draw(Canvas canvas){
            Rect b=getBounds();canvas.save();canvas.translate(b.left,b.top);canvas.scale(b.width()/24f,b.height()/24f);
            p.setColor(color);p.setStrokeWidth(2);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setStyle(Paint.Style.STROKE);
            switch(kind){
                case "menu":for(int y=6;y<=18;y+=6)canvas.drawLine(3,y,21,y,p);break;
                case "back":canvas.drawLine(4,12,20,12,p);canvas.drawLine(4,12,11,5,p);canvas.drawLine(4,12,11,19,p);break;
                case "search":canvas.drawCircle(10,10,6,p);canvas.drawLine(15,15,21,21,p);break;
                case "edit":canvas.drawLine(4,20,8,19,p);canvas.drawLine(8,19,20,7,p);canvas.drawLine(20,7,17,4,p);canvas.drawLine(17,4,5,16,p);canvas.drawLine(5,16,4,20,p);canvas.drawLine(15,6,18,9,p);break;
                case "attach":canvas.drawRoundRect(new RectF(6,2,18,22),6,6,p);canvas.drawLine(10,7,10,17,p);break;
                case "mic":canvas.drawRoundRect(new RectF(9,2,15,14),3,3,p);canvas.drawArc(new RectF(6,5,18,18),0,180,false,p);canvas.drawLine(12,18,12,22,p);canvas.drawLine(8,22,16,22,p);break;
                case "send":p.setStyle(Paint.Style.FILL);Path a=new Path();a.moveTo(2,3);a.lineTo(23,12);a.lineTo(2,21);a.lineTo(6,13);a.lineTo(16,12);a.lineTo(6,11);a.close();canvas.drawPath(a,p);break;
                case "more":p.setStyle(Paint.Style.FILL);for(int y=5;y<=19;y+=7)canvas.drawCircle(12,y,1.7f,p);break;
                case "check":canvas.drawLine(4,12,9,17,p);canvas.drawLine(9,17,20,6,p);break;
            }
            canvas.restore();
        }
        public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    ImageButton icon(String kind,String desc,Runnable click){
        ImageButton b=new ImageButton(this);b.setImageDrawable(new Glyph(kind,MUTED));b.setScaleType(ImageView.ScaleType.CENTER);
        b.setBackground(ripple(Color.TRANSPARENT,24));b.setContentDescription(desc);b.setOnClickListener(v->click.run());return b;
    }
    ImageButton circleAction(String kind,String desc,Runnable click){
        ImageButton b=new ImageButton(this);
        CombinedDrawable d=new CombinedDrawable(shape(PURPLE,100),new Glyph(kind,Color.WHITE));
        d.setCustomSize(dp(56),dp(56));d.setIconSize(dp(24),dp(24));b.setImageDrawable(d);
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);b.setPadding(0,0,0,0);b.setBackgroundColor(Color.TRANSPARENT);
        b.setContentDescription(desc);b.setOnClickListener(v->click.run());return b;
    }
    TextView avatar(String name,int size){
        int[] colors={0xff658ed1,0xffa277ce,0xffd18a70,0xff4da99b,0xff779b58,0xffb96e98};
        String clean=name==null?"":name.trim();String initial=clean.isEmpty()?"?":new String(Character.toChars(clean.codePointAt(0))).toUpperCase(Locale.ROOT);
        TextView a=label(initial,size,Color.WHITE,true);a.setGravity(Gravity.CENTER);a.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{colors[Math.floorMod(clean.hashCode(),colors.length)],0xff62528d}));((GradientDrawable)a.getBackground()).setCornerRadius(dp(100));return a;
    }
    LinearLayout pageLayout(){
        page++;timer.removeCallbacksAndMessages(null);
        LinearLayout layout=column();layout.setBackgroundColor(BG);root=layout;install(layout);return layout;
    }
    LinearLayout toolbar(LinearLayout layout,String title,String sub,Runnable back){
        LinearLayout bar=horizontal();bar.setBackgroundColor(BG);bar.setPadding(dp(4),0,dp(4),0);
        if(back!=null)bar.addView(icon("back","Назад",back),new LinearLayout.LayoutParams(dp(48),dp(56)));
        LinearLayout titles=column();titles.setPadding(dp(12),0,0,0);titles.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading=label(title,20,INK,true);heading.setSingleLine();heading.setEllipsize(TextUtils.TruncateAt.END);titles.addView(heading);
        if(sub!=null){TextView small=label(sub,13,MUTED,false);small.setSingleLine();titles.addView(small);}
        bar.addView(titles,new LinearLayout.LayoutParams(0,dp(56),1));layout.addView(bar,new LinearLayout.LayoutParams(-1,dp(56)));return bar;
    }
    String identity(String input){
        String s=input.trim();if(s.startsWith("+"))return s.replaceAll("[\\s()-]","");
        if(s.startsWith("@"))return s.toLowerCase(Locale.ROOT);
        return s.toUpperCase(Locale.ROOT);
    }
    boolean validIdentity(String s){return s.matches("TG-[0-9]{4}-[0-9]{4}")||s.matches("\\+(7|1|388)000[0-9]{7}")||s.matches("@[A-Za-z0-9_]{5,32}");}
    String displayNumber(String id){return phones.getOrDefault(id,id);}
    void copyId(){
        ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Toxicgram ID",displayNumber(account)));
        Toast.makeText(this,"Номер скопирован",Toast.LENGTH_SHORT).show();
    }
    void notice(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    void hideKeyboard(){View focused=getCurrentFocus();if(focused!=null)((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focused.getWindowToken(),0);}
    void askPeer(){
        EditText e=new EditText(this);e.setSingleLine();e.setHint("+7 000 1234567 или @username");e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32)});
        FrameLayout wrapper=new FrameLayout(this);wrapper.setPadding(dp(24),dp(12),dp(24),0);wrapper.addView(e,new FrameLayout.LayoutParams(-1,-2));
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("Новый чат").setMessage("Введите виртуальный номер пользователя").setView(wrapper).setNegativeButton("Отмена",null).setPositiveButton("Открыть",null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{
            String id=identity(e.getText().toString());
            if(!validIdentity(id)){e.setError("Неверный формат");return;}
            TextView error=new TextView(this);
            final int current=page;final String token=session;dialog.getButton(-1).setEnabled(false);
            network.execute(()->{try{JSONObject d=request("GET","/v1/users/"+id,null,token);runOnUiThread(()->{if(page!=current||isDestroyed()||!dialog.isShowing())return;dialog.dismiss();hideKeyboard();String canonical=d.optString("number",id);phones.put(canonical,d.optString("phone",canonical));avatarIds.put(canonical,d.optString("avatar",""));showChat(canonical,d.optString("name",canonical));});}catch(Exception ex){runOnUiThread(()->{if(page!=current||isDestroyed()||!dialog.isShowing())return;dialog.getButton(-1).setEnabled(true);e.setError(ex instanceof ApiError&&((ApiError)ex).status==404?"Пользователь не найден":"Не удалось подключиться");});}});
        }));dialog.show();
    }
    void drawer(){
        final android.app.Dialog dialog=new android.app.Dialog(this);
        LinearLayout menu=column();menu.setBackgroundColor(BG);
        LinearLayout head=column();head.setBackgroundColor(CARD);head.setPadding(dp(20),dp(24),dp(20),dp(18));
        head.addView(photoAvatar(ownName.isEmpty()?account:ownName,account,28),new LinearLayout.LayoutParams(dp(64),dp(64)));
        TextView nm=label(ownName.isEmpty()?"Toxicgram":ownName,18,INK,true);nm.setPadding(0,dp(12),0,dp(4));head.addView(nm);head.addView(label(displayNumber(account),14,MUTED,false));head.setOnClickListener(v->{dialog.dismiss();copyId();});menu.addView(head);
        String[] names={"Мой профиль","Новый чат","Избранное","Архив","Скопировать номер",light?"Тёмная тема":"Светлая тема","Выйти","Группы и каналы","Истории","Подарки и звёзды"};
        String[] glyphs={"◉","✎","☆","▣","⧉",light?"☾":"☀","↪","♟","◌","★"};
        for(int i=0;i<names.length;i++){
            final int action=i;LinearLayout row=horizontal();row.setPadding(dp(20),0,dp(20),0);row.setBackground(ripple(BG,0));
            TextView g=label(glyphs[i],24,MUTED,false);row.addView(g,new LinearLayout.LayoutParams(dp(46),-2));row.addView(label(names[i],16,INK,true));menu.addView(row,new LinearLayout.LayoutParams(-1,dp(54)));
            row.setOnClickListener(v->{dialog.dismiss();switch(action){case 0:showProfile();break;case 1:askPeer();break;case 2:showChat(account,"Избранное");break;case 3:showChats(true);break;case 4:copyId();break;case 5:light=!light;getPreferences(MODE_PRIVATE).edit().putBoolean("light",light).apply();palette();showAccount();break;case 6:confirmLogout();break;case 7:showRooms();break;case 8:showStories();break;case 9:showGifts();break;}});
        }
        ScrollView menuScroll=new ScrollView(this);menuScroll.addView(menu);dialog.setContentView(menuScroll);if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);dialog.getWindow().setGravity(Gravity.LEFT|Gravity.TOP);dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);dialog.getWindow().setDimAmount(.45f);}
        dialog.show();if(dialog.getWindow()!=null)dialog.getWindow().setLayout(Math.min(dp(320),(int)(getResources().getDisplayMetrics().widthPixels*.85)), -1);
    }
    void confirmLogout(){
        new android.app.AlertDialog.Builder(this).setTitle("Выйти из аккаунта?").setMessage("Для следующего входа потребуется новый код от бота.").setNegativeButton("Отмена",null).setPositiveButton("Выйти",(d,w)->{
            TextView err=new TextView(this);call("POST","/v1/auth/logout",null,x->{session=null;account=null;ownName="";showLogin();},err,null);
        }).show();
    }
    String timeLabel(long seconds){
        if(seconds<=0)return "";Date d=new Date(seconds*1000L);
        java.util.Calendar now=java.util.Calendar.getInstance(),day=java.util.Calendar.getInstance();day.setTime(d);
        return DateFormat.format(now.get(java.util.Calendar.YEAR)==day.get(java.util.Calendar.YEAR)&&now.get(java.util.Calendar.DAY_OF_YEAR)==day.get(java.util.Calendar.DAY_OF_YEAR)?"HH:mm":"dd.MM",d).toString();
    }
    void showAccount(){showChats(false);}
    void showChats(boolean archived){
        LinearLayout layout=pageLayout();final int current=page;
        LinearLayout bar=horizontal();bar.setPadding(dp(4),0,dp(4),0);bar.addView(icon(archived?"back":"menu",archived?"Назад":"Меню",()->{if(archived)showAccount();else drawer();}),new LinearLayout.LayoutParams(dp(48),dp(56)));
        TextView title=label(archived?"Архив":"Toxicgram",21,INK,true);bar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        EditText search=new EditText(this);search.setTextColor(INK);search.setHintTextColor(MUTED);search.setHint("Поиск");search.setTextSize(17);search.setSingleLine();search.setBackgroundColor(Color.TRANSPARENT);search.setVisibility(View.GONE);bar.addView(search,new LinearLayout.LayoutParams(0,dp(48),1));
        bar.addView(icon("search","Поиск",()->{boolean on=search.getVisibility()!=View.VISIBLE;search.setVisibility(on?View.VISIBLE:View.GONE);title.setVisibility(on?View.GONE:View.VISIBLE);if(on){search.requestFocus();((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(search,InputMethodManager.SHOW_IMPLICIT);}else{search.setText("");hideKeyboard();}}),new LinearLayout.LayoutParams(dp(48),dp(56)));
        layout.addView(bar,new LinearLayout.LayoutParams(-1,dp(56)));
        TextView tab=label(archived?"Архивные чаты":"Все чаты",15,PURPLE,true);tab.setPadding(dp(20),dp(12),0,dp(10));layout.addView(tab);
        View indicator=new View(this);indicator.setBackground(shape(PURPLE,3));LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(92),dp(3));ip.leftMargin=dp(16);layout.addView(indicator,ip);line(layout,0);
        TextView info=label("",13,MUTED,false);info.setGravity(Gravity.CENTER);layout.addView(info);
        FrameLayout area=new FrameLayout(this);layout.addView(area,new LinearLayout.LayoutParams(-1,0,1));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout list=column();list.setPadding(0,0,0,dp(84));scroll.addView(list,new ScrollView.LayoutParams(-1,-2));area.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        ImageButton fab=circleAction("edit","Новый чат",()->askPeer());FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(dp(56),dp(56),Gravity.BOTTOM|Gravity.RIGHT);fp.setMargins(0,0,dp(16),dp(18));area.addView(fab,fp);fab.setElevation(dp(5));
        final JSONArray[] cache={new JSONArray()};final String[] snapshot={""};final boolean[] loading={false};final int[] folder={0};
        Runnable render=()->{
            list.removeAllViews();String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);int visible=0;
            for(int i=0;i<cache[0].length();i++){
                JSONObject row=cache[0].optJSONObject(i);if(row==null)continue;
                String id=row.optString("number"),name=id.equals(account)?"Избранное":row.optString("name",id),msg=row.optString("text");phones.put(id,row.optString("phone",id));avatarIds.put(id,row.optString("avatar",""));
                if((row.optInt("archived")!=0)!=archived)continue;if(folder[0]==1&&row.optInt("unread")==0)continue;if(folder[0]==2&&row.optInt("pinned")==0)continue;
                if(!q.isEmpty()&&!(name+" "+displayNumber(id)+" "+id+" "+msg).toLowerCase(Locale.ROOT).contains(q))continue;visible++;
                LinearLayout cell=horizontal();cell.setBackground(ripple(BG,0));cell.setPadding(dp(12),0,dp(16),0);
                cell.addView(photoAvatar(name,id,23),new LinearLayout.LayoutParams(dp(54),dp(54)));
                LinearLayout content=column();content.setPadding(dp(12),0,0,0);
                LinearLayout top=horizontal();TextView nm=label(name,16,INK,true);nm.setSingleLine();nm.setEllipsize(TextUtils.TruncateAt.END);top.addView(nm,new LinearLayout.LayoutParams(0,-2,1));TextView timestamp=label((row.optInt("pinned")!=0?"◆  ":"")+timeLabel(row.optLong("created")),12,MUTED,false);top.addView(timestamp);
                content.addView(top);LinearLayout lower=horizontal();TextView preview=label(msg,14,MUTED,false);preview.setSingleLine();preview.setEllipsize(TextUtils.TruncateAt.END);preview.setPadding(0,dp(4),0,0);lower.addView(preview,new LinearLayout.LayoutParams(0,-2,1));int unread=row.optInt("unread");if(unread>0){TextView badge=label(unread>99?"99+":String.valueOf(unread),12,Color.WHITE,true);badge.setGravity(Gravity.CENTER);badge.setPadding(dp(7),dp(3),dp(7),dp(3));badge.setBackground(shape(PURPLE,20));lower.addView(badge);}content.addView(lower);
                cell.addView(content,new LinearLayout.LayoutParams(0,-2,1));list.addView(cell,new LinearLayout.LayoutParams(-1,dp(76)));line(list,78);cell.setOnClickListener(v->{hideKeyboard();showChat(id,name);});cell.setOnLongClickListener(v->{boolean pin=row.optInt("pinned")!=0;String[] options={pin?"Открепить":"Закрепить",archived?"Вернуть из архива":"В архив"};new android.app.AlertDialog.Builder(this).setTitle(name).setItems(options,(d,i2)->{JSONObject body=new JSONObject();try{body.put(i2==0?"pinned":"archived",i2==0?!pin:!archived);}catch(Exception ignored){}call("PUT","/v1/chats/"+id+"/settings",body,x->showChats(archived),info,null);}).show();return true;});
            }
            if(visible==0){LinearLayout empty=column();empty.setGravity(Gravity.CENTER);empty.setPadding(dp(28),dp(70),dp(28),dp(20));TextView t=label(q.isEmpty()?"Пока нет чатов":"Ничего не найдено",20,INK,true);t.setGravity(Gravity.CENTER);empty.addView(t);TextView hint=label(q.isEmpty()?"Начните переписку по виртуальному номеру друга.":"Введите точный номер в новом чате.",15,MUTED,false);hint.setPadding(0,dp(10),0,0);hint.setGravity(Gravity.CENTER);empty.addView(hint);list.addView(empty,new LinearLayout.LayoutParams(-1,-2));}
        };
        if(!archived){tab.setOnClickListener(v->{String[] folders={"Все чаты","Непрочитанные","Закреплённые"};new android.app.AlertDialog.Builder(this).setTitle("Папки чатов").setSingleChoiceItems(folders,folder[0],(dlg,index)->{folder[0]=index;tab.setText(folders[index]+" ▾");render.run();dlg.dismiss();}).show();});tab.setText("Все чаты ▾");}
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int n,int f){}public void onTextChanged(CharSequence s,int a,int b,int n){render.run();}public void afterTextChanged(Editable e){}});
        Runnable load=()->{if(loading[0])return;loading[0]=true;String token=session;network.execute(()->{try{JSONObject d=request("GET","/v1/chats",null,token);runOnUiThread(()->{loading[0]=false;if(isDestroyed()||page!=current)return;info.setText("");JSONArray arr=d.optJSONArray("chats");if(arr!=null){String value=arr.toString();if(!value.equals(snapshot[0])){snapshot[0]=value;cache[0]=arr;render.run();}}});}catch(Exception e){runOnUiThread(()->{loading[0]=false;if(isDestroyed()||page!=current)return;if(e instanceof ApiError&&((ApiError)e).status==401){session=null;account=null;showLogin();return;}info.setText("Подключение…");});}});};
        call("GET","/v1/profile",null,d->{ownName=d.optString("name",account);ownPhone=d.optString("phone",account);phones.put(account,ownPhone);avatarIds.put(account,d.optString("avatar",""));},null,null);
        render.run();load.run();timer.postDelayed(new Runnable(){public void run(){if(isDestroyed()||page!=current)return;if(foreground)load.run();timer.postDelayed(this,5000);}},5000);
    }
    void showProfile(){
        LinearLayout layout=pageLayout();LinearLayout bar=toolbar(layout,"Профиль",null,()->showAccount());line(layout,0);
        LinearLayout main=column();main.setPadding(dp(22),dp(32),dp(22),dp(20));main.setGravity(Gravity.CENTER_HORIZONTAL);ScrollView profileScroll=new ScrollView(this);profileScroll.addView(main,new ScrollView.LayoutParams(-1,-2));layout.addView(profileScroll,new LinearLayout.LayoutParams(-1,0,1));
        FrameLayout photo=photoAvatar(ownName.isEmpty()?account:ownName,account,42);main.addView(photo,new LinearLayout.LayoutParams(dp(100),dp(100)));photo.setOnClickListener(v->pickAvatar());
        TextView title=label(ownName.isEmpty()?account:ownName,23,INK,true);title.setPadding(0,dp(16),0,dp(8));main.addView(title);
        TextView id=label(displayNumber(account),16,PURPLE,false);id.setOnClickListener(v->copyId());main.addView(id);TextView changePhoto=label("Изменить фото",15,PURPLE,true);changePhoto.setPadding(0,dp(12),0,0);main.addView(changePhoto);changePhoto.setOnClickListener(v->pickAvatar());
        root=main;TextView hint=text("Имя пользователя",13,MUTED,false,8);hint.setPadding(0,dp(30),0,0);
        EditText name=field("Имя",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,64);name.setText(ownName);EditText username=field("@username",InputType.TYPE_CLASS_TEXT,32);EditText bio=field("О себе",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,160);TextView info=text("",14,MUTED,false,8);
        Button save=button("Сохранить");call("GET","/v1/profile",null,d->{name.setText(d.getString("name"));username.setText(d.optString("username"));bio.setText(d.optString("bio"));phones.put(account,d.optString("phone",account));id.setText(displayNumber(account));avatarIds.put(account,d.optString("avatar",""));},info,save);
        save.setOnClickListener(v->{String value=name.getText().toString().trim();if(value.isEmpty()){name.setError("Введите имя");return;}String u=username.getText().toString().trim().replaceFirst("^@","");if(!u.isEmpty()&&!u.matches("[A-Za-z0-9_]{5,32}")){username.setError("От 5 до 32 латинских букв, цифр или _");return;}JSONObject payload=data("name",value);try{payload.put("username",u);payload.put("bio",bio.getText().toString().trim());}catch(Exception ignored){}call("PUT","/v1/profile",payload,d->{ownName=d.optString("name",value);hideKeyboard();showAccount();},info,save);});
    }
    void showChat(String peer,String name){
        LinearLayout layout=pageLayout();final int current=page;
        LinearLayout bar=horizontal();bar.addView(icon("back","Назад",()->{hideKeyboard();showAccount();}),new LinearLayout.LayoutParams(dp(48),dp(56)));
        bar.addView(photoAvatar(name,peer,19),new LinearLayout.LayoutParams(dp(40),dp(40)));
        LinearLayout titles=column();titles.setPadding(dp(12),0,0,0);TextView heading=label(name,18,INK,true);heading.setSingleLine();heading.setEllipsize(TextUtils.TruncateAt.END);titles.addView(heading);titles.addView(label(displayNumber(peer),12,MUTED,false));bar.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        ImageButton more=icon("more","Меню чата",()->{});more.setOnClickListener(v->{PopupMenu popup=new PopupMenu(this,more);popup.getMenu().add("Скопировать номер");if(!peer.equals(account)){popup.getMenu().add("Заблокировать");popup.getMenu().add("Разблокировать");}popup.setOnMenuItemClickListener(item->{if(!item.getTitle().equals("Скопировать номер")){boolean block=item.getTitle().equals("Заблокировать");JSONObject data=new JSONObject();try{data.put("blocked",block);}catch(Exception ignored){}call("PUT","/v1/users/"+peer+"/block",data,x->notice(block?"Пользователь заблокирован":"Пользователь разблокирован"),null,null);return true;}((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Toxicgram ID",displayNumber(peer)));notice("Номер скопирован");return true;});popup.show();});bar.addView(more,new LinearLayout.LayoutParams(dp(48),dp(56)));layout.addView(bar,new LinearLayout.LayoutParams(-1,dp(56)));line(layout,0);
        FrameLayout backdrop=new FrameLayout(this);backdrop.setBackgroundColor(CHAT);layout.addView(backdrop,new LinearLayout.LayoutParams(-1,0,1));
        View pattern=new View(this){final Paint p=new Paint(3);@Override protected void onDraw(Canvas canvas){super.onDraw(canvas);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));p.setColor(light?0x177250c8:0x163c6c95);for(int y=dp(25);y<getHeight();y+=dp(72))for(int x=dp(22);x<getWidth();x+=dp(76)){int shift=(y/dp(72)%2)*dp(20);canvas.drawCircle(x+shift,y,dp(10),p);canvas.drawLine(x+shift-dp(5),y,x+shift+dp(5),y,p);}}};backdrop.addView(pattern,new FrameLayout.LayoutParams(-1,-1));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout history=column();history.setPadding(dp(10),dp(12),dp(10),dp(12));history.setGravity(Gravity.BOTTOM);scroll.addView(history,new ScrollView.LayoutParams(-1,-2));backdrop.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        TextView info=label("",12,MUTED,false);info.setGravity(Gravity.CENTER);layout.addView(info);
        LinearLayout composerRow=horizontal();composerRow.setPadding(dp(8),dp(6),dp(8),dp(6));composerRow.setBackgroundColor(BG);
        ImageButton attach=icon("attach","Прикрепить файл",()->pickAttachment(peer,name));composerRow.addView(attach,new LinearLayout.LayoutParams(dp(40),dp(46)));
        EditText composer=new EditText(this);composer.setTextColor(INK);composer.setHintTextColor(MUTED);composer.setHint("Сообщение");composer.setTextSize(16);composer.setMinLines(1);composer.setMaxLines(4);composer.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);composer.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4000)});composer.setPadding(dp(15),dp(12),dp(15),dp(12));composer.setBackground(shape(CARD,24));composerRow.addView(composer,new LinearLayout.LayoutParams(0,-2,1));ImageButton mic=icon("mic","Голосовое сообщение",()->recordVoice(peer,name));composerRow.addView(mic,new LinearLayout.LayoutParams(dp(36),dp(46)));
        ImageButton send=circleAction("send","Отправить сообщение",()->{});LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(46),dp(46));sp.leftMargin=dp(6);composerRow.addView(send,sp);layout.addView(composerRow,new LinearLayout.LayoutParams(-1,-2));
        final long[] last={-1};final String[] signature={""};final boolean[] loading={false};
        Runnable load=()->{if(loading[0])return;loading[0]=true;String token=session;network.execute(()->{try{JSONObject d=request("GET","/v1/chats/"+peer+"/messages",null,token);runOnUiThread(()->{
            loading[0]=false;if(isDestroyed()||page!=current)return;info.setText("");JSONArray arr=d.optJSONArray("messages");if(arr==null)return;long newest=arr.length()==0?0:arr.optJSONObject(arr.length()-1).optLong("id");String sig=arr.toString();if(sig.equals(signature[0]))return;signature[0]=sig;
            boolean bottom=last[0]<0||scroll.getScrollY()+scroll.getHeight()>=history.getHeight()-dp(100);last[0]=newest;history.removeAllViews();if(newest>0&&foreground){JSONObject read=new JSONObject();try{read.put("last_id",newest);}catch(Exception ignored){}call("PUT","/v1/chats/"+peer+"/read",read,x->{},null,null);}String previousDay="";
            for(int i=0;i<arr.length();i++){
                JSONObject msg=arr.optJSONObject(i);if(msg==null)continue;boolean mine=account.equals(msg.optString("sender"));long created=msg.optLong("created");String day=DateFormat.format("dd MMMM yyyy",new Date(created*1000L)).toString();
                if(!day.equals(previousDay)){TextView date=label(day,12,Color.WHITE,true);date.setGravity(Gravity.CENTER);date.setPadding(dp(12),dp(5),dp(12),dp(5));date.setBackground(shape(0xff64758a,16));LinearLayout.LayoutParams datep=new LinearLayout.LayoutParams(-2,-2);datep.gravity=Gravity.CENTER;datep.setMargins(0,dp(8),0,dp(12));history.addView(date,datep);previousDay=day;}
                LinearLayout bubble=column();bubble.setPadding(dp(12),dp(8),dp(10),dp(6));GradientDrawable bg=new GradientDrawable();bg.setColor(mine?OUT:CARD);float rad=dp(16),tail=dp(4);bg.setCornerRadii(mine?new float[]{rad,rad,rad,rad,tail,tail,rad,rad}:new float[]{rad,rad,rad,rad,rad,rad,tail,tail});bubble.setBackground(bg);
                JSONObject attachment=msg.optJSONObject("attachment");TextView body=label(msg.optString("text"),16,INK,false);body.setMaxWidth(Math.min(dp(310),(int)(getResources().getDisplayMetrics().widthPixels*.76)));body.setTextIsSelectable(false);bubble.addView(body,new LinearLayout.LayoutParams(-2,-2));if(attachment!=null){String aid=attachment.optString("id"),filename=attachment.optString("name","file"),mime=attachment.optString("mime");if(mime.startsWith("image/")){ImageView preview=new ImageView(this);preview.setScaleType(ImageView.ScaleType.CENTER_CROP);preview.setBackground(shape(CHAT,10));bubble.addView(preview,new LinearLayout.LayoutParams(dp(220),dp(150)));loadImage(aid,preview,512);preview.setOnClickListener(v->saveAttachment(aid,filename,mime));}TextView download=label("↓  Сохранить файл · "+(attachment.optLong("size")/1024)+" КБ",13,PURPLE,true);download.setPadding(0,dp(8),0,dp(5));bubble.addView(download);download.setOnClickListener(v->saveAttachment(aid,filename,mime));if(mime.startsWith("audio/")){TextView play=label("▶  Воспроизвести",16,PURPLE,true);play.setPadding(0,dp(8),0,dp(5));bubble.addView(play);play.setOnClickListener(v->playAudio(aid));}}JSONArray reactions=msg.optJSONArray("reactions");if(reactions!=null&&reactions.length()>0){StringBuilder emojis=new StringBuilder();for(int ri=0;ri<reactions.length();ri++){JSONObject rx=reactions.optJSONObject(ri);if(rx!=null)emojis.append(rx.optString("emoji")).append(" ").append(rx.optInt("count")).append("  ");}TextView reaction=label(emojis.toString().trim(),14,PURPLE,true);bubble.addView(reaction);}
                TextView stamp=label((msg.optLong("edited")>0?"изм. ":"")+DateFormat.format("HH:mm",new Date(created*1000L)).toString()+(mine?(msg.optBoolean("read")?"  ✓✓":"  ✓"):""),11,mine?PURPLE:MUTED,false);stamp.setGravity(Gravity.RIGHT);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);tp.topMargin=dp(3);bubble.addView(stamp,tp);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,-2);bp.gravity=mine?Gravity.RIGHT:Gravity.LEFT;bp.bottomMargin=dp(5);history.addView(bubble,bp);View.OnLongClickListener actions=v->{messageActions(msg,mine,peer,name,info);return true;};bubble.setOnLongClickListener(actions);body.setOnLongClickListener(actions);
            }
            if(bottom)scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));
        });}catch(Exception e){runOnUiThread(()->{loading[0]=false;if(isDestroyed()||page!=current)return;if(e instanceof ApiError&&((ApiError)e).status==401){session=null;account=null;showLogin();return;}info.setText("Подключение…");});}});};
        send.setOnClickListener(v->{String value=composer.getText().toString().trim();if(value.isEmpty())return;send.setEnabled(false);JSONObject body=data("recipient",peer);try{body.put("text",value);}catch(Exception ignored){}String token=session;network.execute(()->{try{request("POST","/v1/messages",body,token);runOnUiThread(()->{if(isDestroyed()||page!=current)return;send.setEnabled(true);if(composer.getText().toString().trim().equals(value))composer.setText("");load.run();});}catch(Exception e){runOnUiThread(()->{if(isDestroyed()||page!=current)return;send.setEnabled(true);info.setText("Сообщение не отправлено. Повторите попытку.");});}});});
        load.run();timer.postDelayed(new Runnable(){public void run(){if(isDestroyed()||page!=current)return;if(foreground)load.run();timer.postDelayed(this,5000);}},5000);
    }
    FrameLayout photoAvatar(String name,String id,int size){
        FrameLayout frame=new FrameLayout(this);
        frame.addView(avatar(name,size),new FrameLayout.LayoutParams(-1,-1));
        String aid=avatarIds.get(id);if(aid!=null&&!aid.isEmpty()){
            ImageView photo=new ImageView(this);photo.setBackground(shape(CARD,100));photo.setClipToOutline(true);photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            frame.addView(photo,new FrameLayout.LayoutParams(-1,-1));loadImage(aid,photo,256);
        }return frame;
    }
    byte[] readBytes(InputStream in,int max)throws IOException{
        try(InputStream stream=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){
            if(stream==null)throw new IOException("No file");byte[] b=new byte[8192];int n;while((n=stream.read(b))!=-1){if(out.size()+n>max)throw new IOException("File too large");out.write(b,0,n);}return out.toByteArray();
        }
    }
    byte[] binary(String method,String path,byte[] body,String token)throws Exception{
        HttpsURLConnection conn=(HttpsURLConnection)new URL(BASE+path).openConnection();
        try{conn.setConnectTimeout(15000);conn.setReadTimeout(30000);conn.setRequestMethod(method);conn.setInstanceFollowRedirects(false);conn.setRequestProperty("Authorization","Bearer "+token);
            if(body!=null){conn.setDoOutput(true);conn.setFixedLengthStreamingMode(body.length);conn.setRequestProperty("Content-Type","application/octet-stream");try(OutputStream o=conn.getOutputStream()){o.write(body);}}
            int code=conn.getResponseCode();if(code<200||code>=300)throw new ApiError(code);return readBytes(conn.getInputStream(),8*1024*1024);
        }finally{conn.disconnect();}
    }
    Bitmap decode(byte[] raw,int max){
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(raw,0,raw.length,options);if(options.outWidth<=0||options.outHeight<=0)return null;
        options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>max*2)options.inSampleSize*=2;
        options.inJustDecodeBounds=false;return BitmapFactory.decodeByteArray(raw,0,raw.length,options);
    }
    void loadImage(String aid,ImageView view,int max){
        if(aid.isEmpty()||session==null)return;String key=aid+":"+max;Bitmap cached=imageCache.get(key);if(cached!=null){view.setImageBitmap(cached);return;}
        String token=session;mediaNetwork.execute(()->{try{Bitmap b=decode(binary("GET","/v1/media/"+aid,null,token),max);if(b!=null){imageCache.put(key,b);runOnUiThread(()->{if(!isDestroyed()&&token.equals(session))view.setImageBitmap(b);});}}catch(Exception ignored){}});
    }
    void pickAvatar(){
        pendingPage=page;pendingToken=session;
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.setType("image/*");intent.addCategory(Intent.CATEGORY_OPENABLE);
        try{startActivityForResult(intent,601);}catch(Exception e){notice("Нет приложения для выбора файла");}
    }
    void pickAttachment(String peer,String name){
        pendingPeer=peer;pendingName=name;pendingPage=page;pendingToken=session;
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.setType("*/*");intent.addCategory(Intent.CATEGORY_OPENABLE);
        try{startActivityForResult(intent,602);}catch(Exception e){notice("Нет приложения для выбора файла");}
    }
    void saveAttachment(String aid,String filename,String mime){
        pendingMedia=aid;pendingFilename=filename;pendingPage=page;pendingToken=session;
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.setType(mime.isEmpty()?"application/octet-stream":mime);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.putExtra(Intent.EXTRA_TITLE,filename);
        try{startActivityForResult(intent,603);}catch(Exception e){notice("Нет приложения для сохранения файла");}
    }
    String filename(Uri uri){
        try(Cursor cursor=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(cursor!=null&&cursor.moveToFirst()){String value=cursor.getString(0);if(value!=null)return value;}}catch(Exception ignored){}
        return "file";
    }
    @Override protected void onActivityResult(int code,int result,Intent intent){
        super.onActivityResult(code,result,intent);
        if(result!=RESULT_OK||intent==null||intent.getData()==null||code<601||code>603)return;
        final Uri uri=intent.getData();final int current=pendingPage;final String token=pendingToken,peer=pendingPeer,name=pendingName,aid=pendingMedia;
        if(token==null||!token.equals(session)||page!=current)return;
        notice(code==603?"Загрузка…":"Отправка…");
        mediaNetwork.execute(()->{try{
            if(code==603){byte[] raw=binary("GET","/v1/media/"+aid,null,token);try(OutputStream out=getContentResolver().openOutputStream(uri,"w")){if(out==null)throw new IOException("No output");out.write(raw);}}
            else if(code==601){Bitmap b=decode(readBytes(getContentResolver().openInputStream(uri),8*1024*1024),512);if(b==null)throw new IOException("Invalid photo");int w=b.getWidth(),h=b.getHeight(),size=Math.min(w,h);Bitmap crop=Bitmap.createBitmap(b,(w-size)/2,(h-size)/2,size,size);Bitmap resized=Bitmap.createScaledBitmap(crop,384,384,true);ByteArrayOutputStream bytes=new ByteArrayOutputStream();resized.compress(Bitmap.CompressFormat.JPEG,80,bytes);JSONObject response=new JSONObject(new String(binary("PUT","/v1/avatar",bytes.toByteArray(),token),StandardCharsets.UTF_8));String newId=response.getString("avatar");runOnUiThread(()->{if(!isDestroyed()&&page==current&&token.equals(session)){avatarIds.put(account,newId);showProfile();}});}
            else{byte[] raw=readBytes(getContentResolver().openInputStream(uri),8*1024*1024);String mime=getContentResolver().getType(uri);String path="/v1/attachments?recipient="+Uri.encode(peer)+"&name="+Uri.encode(filename(uri))+"&mime="+Uri.encode(mime==null?"application/octet-stream":mime);binary("POST",path,raw,token);runOnUiThread(()->{if(!isDestroyed()&&page==current&&token.equals(session))showChat(peer,name);});}
            runOnUiThread(()->{if(!isDestroyed()&&token.equals(session))notice(code==603?"Файл сохранён":"Готово");});
        }catch(Exception e){runOnUiThread(()->{if(!isDestroyed()&&token.equals(session))notice("Не удалось выполнить операцию. Лимит файла — 8 МБ.");});}});
    }
    void discardRecording(){if(recorder!=null){try{recorder.stop();}catch(Exception ignored){}try{recorder.release();}catch(Exception ignored){}recorder=null;}if(recording!=null){recording.delete();recording=null;}}
    void recordVoice(String peer,String name){
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},704);return;}
        discardRecording();
        try{recording=File.createTempFile("voice-",".m4a",getCacheDir());recorder=new MediaRecorder();recorder.setAudioSource(MediaRecorder.AudioSource.MIC);recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);recorder.setAudioEncodingBitRate(64000);recorder.setAudioSamplingRate(44100);recorder.setMaxDuration(60000);recorder.setOutputFile(recording.getAbsolutePath());recorder.prepare();recorder.start();
            android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("Запись голосового").setMessage("Запись до 60 секунд. Нажмите «Отправить», когда закончите.").setNegativeButton("Отмена",(d,w)->discardRecording()).setPositiveButton("Отправить",(d,w)->sendVoice(peer,name)).create();dialog.setOnCancelListener(d->discardRecording());recorder.setOnInfoListener((mr,what,extra)->{if(what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED){if(dialog.isShowing())dialog.dismiss();sendVoice(peer,name);}});dialog.show();
        }catch(Exception e){discardRecording();notice("Не удалось начать запись");}
    }
    void sendVoice(String peer,String name){
        if(recorder==null||recording==null)return;File file=recording;recording=null;
        try{recorder.stop();}catch(Exception e){file.delete();notice("Слишком короткая запись");return;}finally{recorder.release();recorder=null;}
        String token=session;int current=page;mediaNetwork.execute(()->{try{byte[] bytes=readBytes(new FileInputStream(file),8*1024*1024);binary("POST","/v1/attachments?recipient="+Uri.encode(peer)+"&name="+Uri.encode("Голосовое.m4a")+"&mime=audio%2Fmp4",bytes,token);runOnUiThread(()->{if(!isDestroyed()&&page==current&&token.equals(session))showChat(peer,name);});}catch(Exception e){runOnUiThread(()->{if(!isDestroyed())notice("Не удалось отправить голосовое");});}finally{file.delete();}});
    }
    void stopAudio(){if(player!=null){try{player.release();}catch(Exception ignored){}player=null;}if(playback!=null){playback.delete();playback=null;}}
    void playAudio(String id){
        stopAudio();String token=session;int current=page;mediaNetwork.execute(()->{File file=null;try{byte[] raw=binary("GET","/v1/media/"+id,null,token);file=File.createTempFile("audio-",".tmp",getCacheDir());try(FileOutputStream out=new FileOutputStream(file)){out.write(raw);}File ready=file;runOnUiThread(()->{if(isDestroyed()||page!=current||!token.equals(session)||!foreground){ready.delete();return;}try{stopAudio();playback=ready;player=new MediaPlayer();player.setDataSource(ready.getAbsolutePath());player.setOnCompletionListener(p->stopAudio());player.setOnErrorListener((p,w,e)->{stopAudio();notice("Не удалось воспроизвести");return true;});player.setOnPreparedListener(p->{if(player==p)p.start();});player.prepareAsync();}catch(Exception e){stopAudio();notice("Не удалось воспроизвести");}});}catch(Exception e){if(file!=null)file.delete();runOnUiThread(()->{if(!isDestroyed())notice("Не удалось загрузить запись");});}});
    }
    void showStories(){
        LinearLayout layout=pageLayout();LinearLayout bar=toolbar(layout,"Истории",null,()->showAccount());line(layout,0);TextView info=label("",13,MUTED,false);layout.addView(info);
        bar.addView(icon("edit","Новая история",()->{EditText input=new EditText(this);input.setHint("Что нового?");input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(280)});new android.app.AlertDialog.Builder(this).setTitle("История на 24 часа").setMessage("Видна пользователям, с которыми вы переписывались.").setView(input).setNegativeButton("Отмена",null).setPositiveButton("Опубликовать",(d,w)->{String v=input.getText().toString().trim();if(!v.isEmpty())call("POST","/v1/stories",data("text",v),x->showStories(),info,null);}).show();}),new LinearLayout.LayoutParams(dp(48),dp(56)));
        ScrollView scroll=new ScrollView(this);LinearLayout list=column();scroll.addView(list);layout.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        call("GET","/v1/stories",null,d->{JSONArray arr=d.getJSONArray("stories");if(arr.length()==0){TextView empty=label("Нет новых историй",17,MUTED,false);empty.setGravity(Gravity.CENTER);empty.setPadding(0,dp(40),0,0);list.addView(empty);}
            for(int j=0;j<arr.length();j++){JSONObject story=arr.getJSONObject(j);LinearLayout card=column();card.setPadding(dp(18),dp(14),dp(18),dp(14));card.setBackground(shape(CARD,18));TextView author=label(story.optString("name"),15,PURPLE,true);card.addView(author);TextView text=label(story.optString("text"),22,INK,false);text.setPadding(0,dp(12),0,dp(12));card.addView(text);card.addView(label(timeLabel(story.optLong("created")),12,MUTED,false));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(12),dp(8),dp(12),dp(4));list.addView(card,p);if(account.equals(story.optString("owner")))card.setOnLongClickListener(v->{new android.app.AlertDialog.Builder(this).setTitle("Удалить историю?").setNegativeButton("Отмена",null).setPositiveButton("Удалить",(a,b)->call("DELETE","/v1/stories/"+story.optLong("id"),null,x->showStories(),info,null)).show();return true;});}
        },info,null);
    }
    void showGifts(){
        LinearLayout layout=pageLayout();toolbar(layout,"Подарки и звёзды",null,()->showAccount());line(layout,0);
        ScrollView sc=new ScrollView(this);LinearLayout list=column();list.setPadding(dp(14),dp(14),dp(14),dp(14));sc.addView(list);layout.addView(sc,new LinearLayout.LayoutParams(-1,0,1));TextView info=label("",13,MUTED,false);list.addView(info);
        call("GET","/v1/gifts",null,d->{TextView balance=label("★ "+d.optInt("balance"),34,PURPLE,true);balance.setGravity(Gravity.CENTER);list.addView(balance);TextView note=label("Внутренние звёзды · без денежной стоимости",13,MUTED,false);note.setGravity(Gravity.CENTER);list.addView(note);JSONArray catalog=d.getJSONArray("catalog"),received=d.getJSONArray("received");
            TextView head=label("Отправить подарок",19,INK,true);head.setPadding(0,dp(24),0,dp(8));list.addView(head);
            java.util.Map<String,String> names=new java.util.HashMap<>();for(int j=0;j<catalog.length();j++){JSONObject item=catalog.getJSONObject(j);String kind=item.getString("kind"),title=item.getString("emoji")+"  "+item.getString("name");int price=item.getInt("price");names.put(kind,title);TextView card=label(title+"   ·   ★ "+price,19,INK,true);card.setPadding(dp(16),dp(18),dp(16),dp(18));card.setBackground(ripple(CARD,14));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(8);list.addView(card,p);card.setOnClickListener(v->{EditText target=new EditText(this);target.setHint("Номер или @username");target.setSingleLine();new android.app.AlertDialog.Builder(this).setTitle(title).setMessage("Отправить за "+price+" внутренних звёзд?").setView(target).setNegativeButton("Отмена",null).setPositiveButton("Отправить",(a,b)->{String id=identity(target.getText().toString());if(!validIdentity(id)){notice("Неверный номер");return;}JSONObject body=data("recipient",id);try{body.put("kind",kind);}catch(Exception ignored){}call("POST","/v1/gifts",body,x->showGifts(),info,null);}).show();});}
            TextView own=label("Моя коллекция",19,INK,true);own.setPadding(0,dp(20),0,dp(12));list.addView(own);if(received.length()==0)list.addView(label("Пока нет подарков",14,MUTED,false));for(int j=0;j<received.length();j++){JSONObject item=received.getJSONObject(j);TextView card=label(names.getOrDefault(item.optString("kind"),item.optString("kind"))+"\n#"+item.optString("serial"),16,INK,false);card.setBackground(shape(CARD,14));card.setPadding(dp(14),dp(12),dp(14),dp(12));list.addView(card);}
        },info,null);
    }
    void createRoom(TextView info){
        LinearLayout panel=column();panel.setPadding(dp(20),dp(8),dp(20),dp(8));
        EditText title=new EditText(this);title.setHint("Название");title.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});panel.addView(title);
        RadioGroup kinds=new RadioGroup(this);kinds.setOrientation(RadioGroup.VERTICAL);
        RadioButton group=new RadioButton(this);group.setText("Группа");group.setId(View.generateViewId());kinds.addView(group);
        RadioButton channel=new RadioButton(this);channel.setText("Канал");channel.setId(View.generateViewId());kinds.addView(channel);kinds.check(group.getId());panel.addView(kinds);
        android.app.AlertDialog dlg=new android.app.AlertDialog.Builder(this).setTitle("Создать").setView(panel).setNegativeButton("Отмена",null).setPositiveButton("Создать",null).create();
        dlg.setOnShowListener(v->dlg.getButton(-1).setOnClickListener(w->{String name=title.getText().toString().trim();if(name.isEmpty()){title.setError("Введите название");return;}JSONObject b=data("title",name);try{b.put("kind",kinds.getCheckedRadioButtonId()==channel.getId()?"channel":"group");}catch(Exception ignored){}dlg.dismiss();call("POST","/v1/rooms",b,this::showRoom,info,null);}));dlg.show();
    }
    void showRooms(){
        LinearLayout layout=pageLayout();LinearLayout bar=toolbar(layout,"Группы и каналы",null,()->showAccount());line(layout,0);
        TextView info=label("",13,MUTED,false);info.setGravity(Gravity.CENTER);layout.addView(info);
        bar.addView(icon("edit","Создать группу или канал",()->createRoom(info)),new LinearLayout.LayoutParams(dp(48),dp(56)));
        ScrollView sc=new ScrollView(this);LinearLayout list=column();sc.addView(list);layout.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        call("GET","/v1/rooms",null,d->{JSONArray arr=d.getJSONArray("rooms");list.removeAllViews();
            if(arr.length()==0){TextView empty=label("Создайте группу или канал",16,MUTED,false);empty.setGravity(Gravity.CENTER);empty.setPadding(0,dp(44),0,0);list.addView(empty);}
            for(int j=0;j<arr.length();j++){JSONObject room=arr.getJSONObject(j);LinearLayout cell=horizontal();cell.setPadding(dp(12),0,dp(12),0);cell.setBackground(ripple(BG,0));String title=room.optString("title");cell.addView(avatar(title,22),new LinearLayout.LayoutParams(dp(52),dp(52)));LinearLayout stack=column();stack.setPadding(dp(12),0,0,0);stack.addView(label(title,16,INK,true));stack.addView(label((room.optString("kind").equals("channel")?"Канал":"Группа")+" · "+room.optInt("members")+" участн.",13,MUTED,false));cell.addView(stack,new LinearLayout.LayoutParams(0,-2,1));list.addView(cell,new LinearLayout.LayoutParams(-1,dp(72)));line(list,76);cell.setOnClickListener(v->showRoom(room));}
        },info,null);
    }
    void showRoom(JSONObject room){
        String rid=room.optString("id"),title=room.optString("title"),kind=room.optString("kind");boolean owner=account.equals(room.optString("owner")),canPost=owner||!kind.equals("channel");
        LinearLayout layout=pageLayout();final int current=page;LinearLayout bar=toolbar(layout,title,kind.equals("channel")?"Канал":"Группа",()->showRooms());line(layout,0);
        TextView info=label("",12,MUTED,false);info.setGravity(Gravity.CENTER);
        ImageButton more=icon("more","Управление",()->{});bar.addView(more,new LinearLayout.LayoutParams(dp(48),dp(56)));
        more.setOnClickListener(v->{PopupMenu m=new PopupMenu(this,more);m.getMenu().add(owner?"Добавить участника":"Покинуть");m.setOnMenuItemClickListener(item->{if(owner){EditText target=new EditText(this);target.setHint("Виртуальный номер или @username");target.setSingleLine();android.app.AlertDialog inv=new android.app.AlertDialog.Builder(this).setTitle("Добавить участника").setView(target).setNegativeButton("Отмена",null).setPositiveButton("Добавить",null).create();inv.setOnShowListener(w->inv.getButton(-1).setOnClickListener(x->{String id=identity(target.getText().toString());if(!validIdentity(id)){target.setError("Неверный номер");return;}inv.dismiss();call("POST","/v1/rooms/"+rid+"/invite",data("number",id),res->notice("Участник добавлен"),info,null);}));inv.show();}else new android.app.AlertDialog.Builder(this).setTitle("Покинуть?").setNegativeButton("Отмена",null).setPositiveButton("Покинуть",(d,w)->call("DELETE","/v1/rooms/"+rid+"/membership",null,res->showRooms(),info,null)).show();return true;});m.show();});
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setBackgroundColor(CHAT);LinearLayout list=column();list.setPadding(dp(12),dp(12),dp(12),dp(12));list.setGravity(Gravity.BOTTOM);sc.addView(list);layout.addView(sc,new LinearLayout.LayoutParams(-1,0,1));layout.addView(info);
        final boolean[] loading={false};final String[] signature={""};
        Runnable load=()->{if(loading[0])return;loading[0]=true;String token=session;network.execute(()->{try{JSONObject d=request("GET","/v1/rooms/"+rid+"/messages",null,token);runOnUiThread(()->{loading[0]=false;if(isDestroyed()||page!=current)return;JSONArray arr=d.optJSONArray("messages");if(arr==null)return;String sig=arr.toString();if(sig.equals(signature[0]))return;signature[0]=sig;list.removeAllViews();info.setText("");
            for(int j=0;j<arr.length();j++){JSONObject msg=arr.optJSONObject(j);if(msg==null)continue;boolean mine=account.equals(msg.optString("sender"));LinearLayout bubble=column();bubble.setPadding(dp(12),dp(8),dp(12),dp(8));bubble.setBackground(shape(mine?OUT:CARD,16));if(!mine)bubble.addView(label(msg.optString("name"),13,PURPLE,true));TextView text=label(msg.optString("text"),16,INK,false);text.setMaxWidth((int)(getResources().getDisplayMetrics().widthPixels*.76));bubble.addView(text);TextView time=label(timeLabel(msg.optLong("created")),11,MUTED,false);time.setGravity(Gravity.RIGHT);bubble.addView(time);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.gravity=mine?Gravity.RIGHT:Gravity.LEFT;p.bottomMargin=dp(6);list.addView(bubble,p);}sc.post(()->sc.fullScroll(View.FOCUS_DOWN));});}catch(Exception e){runOnUiThread(()->{loading[0]=false;if(!isDestroyed()&&page==current)info.setText("Не удалось обновить");});}});};
        if(canPost){LinearLayout compose=horizontal();compose.setPadding(dp(8),dp(6),dp(8),dp(6));EditText text=new EditText(this);text.setHint("Сообщение");text.setTextColor(INK);text.setHintTextColor(MUTED);text.setMaxLines(4);text.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4000)});text.setBackground(shape(CARD,24));text.setPadding(dp(14),dp(10),dp(14),dp(10));compose.addView(text,new LinearLayout.LayoutParams(0,-2,1));ImageButton send=circleAction("send","Отправить",()->{});compose.addView(send,new LinearLayout.LayoutParams(dp(46),dp(46)));send.setOnClickListener(v->{String msg=text.getText().toString().trim();if(msg.isEmpty())return;send.setEnabled(false);String token=session;network.execute(()->{try{request("POST","/v1/rooms/"+rid+"/messages",data("text",msg),token);runOnUiThread(()->{if(!isDestroyed()&&page==current){send.setEnabled(true);if(text.getText().toString().trim().equals(msg))text.setText("");load.run();}});}catch(Exception e){runOnUiThread(()->{if(!isDestroyed()&&page==current){send.setEnabled(true);info.setText("Не удалось отправить");}});}});});layout.addView(compose);}else{TextView readOnly=label("Публикации доступны владельцу канала",13,MUTED,false);readOnly.setGravity(Gravity.CENTER);readOnly.setPadding(0,dp(12),0,dp(12));layout.addView(readOnly);}
        load.run();timer.postDelayed(new Runnable(){public void run(){if(isDestroyed()||page!=current)return;if(foreground)load.run();timer.postDelayed(this,5000);}},5000);
    }
    void messageActions(JSONObject msg,boolean mine,String peer,String name,TextView info){
        long id=msg.optLong("id");String value=msg.optString("text");
        String[] options=msg.optJSONObject("attachment")!=null?(mine?new String[]{"Реакция","Удалить у себя","Удалить у всех"}:new String[]{"Реакция","Удалить у себя"}):mine?new String[]{"Реакция","Копировать","Переслать","В избранное","Изменить","Удалить у себя","Удалить у всех"}:new String[]{"Реакция","Копировать","Переслать","В избранное","Удалить у себя"};
        new android.app.AlertDialog.Builder(this).setTitle("Сообщение").setItems(options,(d,i)->{
            String choice=options[i];
            switch(choice){
                case "Реакция":String[] emoji={"👍","❤️","🔥","😂","😢","👎","Убрать реакцию"};new android.app.AlertDialog.Builder(this).setTitle("Реакция").setItems(emoji,(dlg,index)->call("PUT","/v1/messages/"+id+"/reaction",data("emoji",index==6?"":emoji[index]),x->showChat(peer,name),info,null)).show();break;
                case "Копировать":((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Toxicgram",value));notice("Скопировано");break;
                case "В избранное":JSONObject saved=data("recipient",account);try{saved.put("text",value);}catch(Exception ignored){}call("POST","/v1/messages",saved,x->notice("Сохранено"),info,null);break;
                case "Переслать":EditText target=new EditText(this);target.setSingleLine();target.setHint("+388 000 1234567 или @username");target.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);android.app.AlertDialog forward=new android.app.AlertDialog.Builder(this).setTitle("Переслать текст").setView(target).setNegativeButton("Отмена",null).setPositiveButton("Отправить",null).create();forward.setOnShowListener(v->forward.getButton(-1).setOnClickListener(w->{String recipient=identity(target.getText().toString());if(!validIdentity(recipient)){target.setError("Неверный номер");return;}JSONObject payload=data("recipient",recipient);try{payload.put("text",value);}catch(Exception ignored){}forward.dismiss();call("POST","/v1/messages",payload,x->notice("Переслано"),info,null);}));forward.show();break;
                case "Изменить":EditText edit=new EditText(this);edit.setText(value);edit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4000)});edit.setMinLines(2);android.app.AlertDialog change=new android.app.AlertDialog.Builder(this).setTitle("Редактировать сообщение").setView(edit).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).create();change.setOnShowListener(v->change.getButton(-1).setOnClickListener(w->{String text=edit.getText().toString().trim();if(text.isEmpty()){edit.setError("Пустое сообщение");return;}change.dismiss();call("PUT","/v1/messages/"+id,data("text",text),x->showChat(peer,name),info,null);}));change.show();break;
                default:boolean everyone=choice.equals("Удалить у всех");new android.app.AlertDialog.Builder(this).setTitle(choice+"?").setMessage(everyone?"Сообщение исчезнет у обоих участников.":"Сообщение исчезнет только у вас.").setNegativeButton("Отмена",null).setPositiveButton("Удалить",(a,b)->call("DELETE","/v1/messages/"+id+"?everyone="+everyone,null,x->showChat(peer,name),info,null)).show();
            }
        }).show();
    }
    @Override protected void onResume(){super.onResume();foreground=true;}
    @Override protected void onPause(){foreground=false;if(recorder!=null)discardRecording();stopAudio();super.onPause();}
    @Override public void onBackPressed(){if(session!=null){showAccount();}else super.onBackPressed();}
    static class ApiError extends IOException{int status;ApiError(int s){status=s;}}
    JSONObject request(String method,String path,JSONObject body,String token)throws Exception{
        HttpsURLConnection conn=(HttpsURLConnection)new URL(BASE+path).openConnection();
        try{
            conn.setConnectTimeout(15000);conn.setReadTimeout(15000);conn.setRequestMethod(method);
            conn.setInstanceFollowRedirects(false);conn.setRequestProperty("Accept","application/json");
            if(token!=null)conn.setRequestProperty("Authorization","Bearer "+token);
            if(body!=null){conn.setDoOutput(true);conn.setRequestProperty("Content-Type","application/json");try(OutputStream out=conn.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
            int response=conn.getResponseCode();if(response<200||response>=300)throw new ApiError(response);
            try(InputStream in=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] b=new byte[4096];int count;while((count=in.read(b))!=-1){if(out.size()+count>2097152)throw new IOException("Too large");out.write(b,0,count);}
                return new JSONObject(out.toString("UTF-8"));
            }
        }finally{conn.disconnect();}
    }
    @Override protected void onDestroy(){timer.removeCallbacksAndMessages(null);network.shutdownNow();mediaNetwork.shutdownNow();discardRecording();stopAudio();super.onDestroy();}
}
