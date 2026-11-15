package com.riskmemory.map;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.speech.tts.TextToSpeech;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private static final int BG=Color.rgb(16,26,35), CARD=Color.rgb(27,41,53), INK=Color.rgb(238,245,244);
    private static final int MUTED=Color.rgb(163,185,193), MINT=Color.rgb(157,242,207), AMBER=Color.rgb(255,188,119), BLUE=Color.rgb(120,181,255);
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final List<Session> history=new ArrayList<>();
    private final Set<String> warned=new HashSet<>();
    private SharedPreferences prefs;
    private Session active, selected;
    private DriveEngine engine; private int lastWarningSecond=-100; private SpeedView speedView;
    private boolean running, review, speechEnabled, speechReady;
    private String filter="all", warning="동일 경로의 이전 시연 기록이 있으면 재방문 알림을 표시합니다.";
    private LinearLayout root, eventList;
    private TextView speedText, phaseText, eventCountText, elapsedText, warningText;
    private RouteView routeView;
    private TextToSpeech speech;
    private final Runnable tick=new Runnable(){public void run(){
        if(!running || active==null) return;
        active.seconds++;
        DriveEngine.Sample sample=DriveEngine.demo(active.seconds);
        active.distance+=sample.speed/3.6;
        List<DriveEngine.Event> fresh=engine.accept(sample);
        active.events.addAll(fresh);
        if(!fresh.isEmpty()) warning="새 변화 후보 기록 · "+kind(fresh.get(0))+" · 원인은 주행 후 확인하세요.";
        checkRevisit(); updateLive(); saveActive();
        if(active.seconds>=DriveEngine.DURATION) finishTrip(); else handler.postDelayed(this,500);
    }};

    @Override public void onCreate(Bundle state){
        super.onCreate(state); prefs=getSharedPreferences("risk_memory_v1",MODE_PRIVATE);
        load();
        speech=new TextToSpeech(this,status->{
            if(status==TextToSpeech.SUCCESS && speech!=null){
                int result=speech.setLanguage(Locale.KOREAN);
                speechReady=result!=TextToSpeech.LANG_MISSING_DATA && result!=TextToSpeech.LANG_NOT_SUPPORTED;
            }
        });
        render();
    }
    @Override protected void onPause(){super.onPause(); running=false; handler.removeCallbacks(tick); saveActive();}
    @Override protected void onResume(){super.onResume(); if(prefs!=null) render();}
    @Override protected void onDestroy(){handler.removeCallbacks(tick); if(speech!=null){speech.stop();speech.shutdown();}super.onDestroy();}

    private void render(){
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        root=column(); root.setPadding(dp(24),dp(18),dp(24),dp(24)); scroll.addView(root); setContentView(scroll);
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(dp(24),dp(18)+insets.getSystemWindowInsetTop(),dp(24),dp(24)+insets.getSystemWindowInsetBottom());return insets;});
        text(root,"RISK MEMORY  /  주행 순간 복기",13,MINT,true);
        text(root,review?"지나온 순간을, 다음 주행의 기억으로.":"주행을 기록하고, 차분하게 돌아보세요.",28,INK,true);
        text(root,"시연 모드 · 차량 센서 미연결 · 예시 경로 · 실제 위험 판정 서비스가 아닙니다",14,AMBER,false);
        LinearLayout tabs=row(); root.addView(tabs);
        addButton(tabs,"주행 시연",()->{review=false;filter="all";render();},!review);
        addButton(tabs,"기록 복기 ("+history.size()+")",()->{pause();review=true;selected=history.isEmpty()?null:history.get(0);render();},review);
        addButton(tabs,"탐지 기준",this::criteria,false);
        if(review) renderReview(); else renderDrive();
        text(root,"모든 기록은 이 기기에만 저장됩니다. 공유 서버·익명 수집·실차 센서·미끄러짐 판정은 구현하지 않았습니다.",12,MUTED,false);
    }
    private void renderDrive(){
        LinearLayout controls=row(); root.addView(controls);
        addButton(controls,active==null?"시험 주행 시작":running?"일시정지":"이어서 시연",()->{
            if(running){pause();render();return;}
            if(active==null){active=new Session();active.started=System.currentTimeMillis();engine=new DriveEngine();engine.accept(DriveEngine.demo(0));warned.clear();lastWarningSecond=-100;}
            running=true; render(); handler.removeCallbacks(tick);handler.postDelayed(tick,500);
        },true);
        addButton(controls,"주행 종료 · 복기",()->{if(active!=null)finishTrip();else toast("시험 주행을 먼저 시작하세요.");},false);
        addButton(controls,speechEnabled?"음성 알림 켜짐":"음성 알림 꺼짐",()->{speechEnabled=!speechEnabled;if(speechEnabled&&!speechReady)toast("한국어 음성 엔진이 준비되지 않아 화면 알림을 사용합니다.");render();},speechEnabled);
        text(root,"64초 분량 시험 데이터를 2배속으로 재생합니다. 백그라운드로 이동하면 일시정지합니다.",13,MUTED,false);
        LinearLayout dashboard=getResources().getConfiguration().screenWidthDp>=850?row():column(); root.addView(dashboard);
        LinearLayout mapCard=card(dashboard); text(mapCard,"01  /  이번 경로",15,MINT,true);
        routeView=new RouteView(); mapCard.addView(routeView,new LinearLayout.LayoutParams(-1,dp(285)));
        text(mapCard,"예시 경로 도식 · 실제 도로지도 아님 · 표시를 누르면 상세 확인",12,MUTED,false);
        LinearLayout stats=card(dashboard); text(stats,"02  /  시연 데이터",15,MINT,true);
        speedText=text(stats,"0 km/h",38,INK,true);
        phaseText=text(stats,"대기 중",16,MUTED,false);
        elapsedText=text(stats,"00:00 / 01:04",17,INK,false);
        eventCountText=text(stats,"변화 후보 0건",23,AMBER,true);
        speedView=new SpeedView();stats.addView(speedView,new LinearLayout.LayoutParams(-1,dp(90)));
        LinearLayout warnCard=card(root); text(warnCard,"기억 알림",14,MINT,true);
        warningText=text(warnCard,warning,18,INK,false);
        text(warnCard,"개인 시연 기록 재방문 시에만 알림 · 도로 위험도나 운전자 과실을 뜻하지 않습니다",12,MUTED,false);
        text(root,"방금 기록된 순간",21,INK,true); eventList=column();root.addView(eventList);updateLive();
    }
    private void updateLive(){
        if(speedText==null || review)return;
        int second=active==null?0:active.seconds;
        speedText.setText(String.format(Locale.KOREA,"%.0f km/h",DriveEngine.demo(second).speed));
        phaseText.setText(running?"시험 데이터 재생 중":active==null?"시작 버튼으로 시연하세요":"일시정지 · 이어서 시연할 수 있습니다");
        elapsedText.setText(clock(second)+" / 01:04  ·  "+String.format(Locale.KOREA,"%.2f km",active==null?0:active.distance/1000));
        eventCountText.setText("변화 후보 "+(active==null?0:active.events.size())+"건");
        warningText.setText(warning);if(routeView!=null)routeView.invalidate();if(speedView!=null)speedView.invalidate();
        eventList.removeAllViews();
        if(active==null || active.events.isEmpty())text(eventList,"아직 기록된 변화 후보가 없습니다. 이는 안전 판정을 의미하지 않습니다.",15,MUTED,false);
        else for(int i=active.events.size()-1;i>=Math.max(0,active.events.size()-3);i--)eventCard(eventList,active,active.events.get(i));
        root.invalidate();
    }
    private void renderReview(){
        if(history.isEmpty()){
            LinearLayout empty=card(root); text(empty,"아직 완료한 주행이 없습니다.",24,INK,true);
            text(empty,"주행 시연에서 시험 데이터를 재생하고 종료하면 경로·타임라인을 확인할 수 있습니다.",17,MUTED,false);
            addButton(empty,"시험 주행 시작하기",()->{review=false;render();},true);return;
        }
        if(selected==null)selected=history.get(0);
        LinearLayout chooser=row();root.addView(chooser);
        addButton(chooser,"주행 선택 · "+date(selected.started),this::chooseTrip,false);
        addButton(chooser,"같은 경로 재주행",()->{if(active!=null){toast("주행 시연에서 진행 중인 주행을 먼저 종료하세요.");return;}review=false;warned.clear();lastWarningSecond=-100;warning="이전 기록이 있는 경로입니다. 재주행 시 기록 구간 가까이에서 알립니다.";render();},true);
        addButton(chooser,"기록 삭제",this::deleteHistory,false);
        int brakes=0;for(DriveEngine.Event e:selected.events)if(e.kind.equals("brake"))brakes++;
        LinearLayout summary=card(root);
        text(summary,"시험 주행 요약",15,MINT,true);
        text(summary,"급감속 후보 "+brakes+"건   ·   급조향 후보 "+(selected.events.size()-brakes)+"건",25,INK,true);
        text(summary,clock(selected.seconds)+" · "+String.format(Locale.KOREA,"%.2f km",selected.distance/1000)+" · 모든 값은 시험 데이터",15,MUTED,false);
        text(summary,"급격한 변화만으로 실수·위험을 확정할 수 없습니다. 표시를 눌러 당시 상황을 직접 남겨 주세요.",16,MUTED,false);
        LinearLayout mapCard=card(root); routeView=new RouteView();mapCard.addView(routeView,new LinearLayout.LayoutParams(-1,dp(275)));
        text(mapCard,"예시 경로 도식 · 실제 도로지도 아님 · 숫자는 기록 순서",12,MUTED,false);
        LinearLayout filters=row();root.addView(filters);
        addButton(filters,"전체",()->{filter="all";render();},filter.equals("all"));
        addButton(filters,"급감속",()->{filter="brake";render();},filter.equals("brake"));
        addButton(filters,"급조향",()->{filter="turn";render();},filter.equals("turn"));
        text(root,"주행 타임라인",21,INK,true);
        int shown=0;
        for(DriveEngine.Event event:selected.events)if(filter.equals("all")||filter.equals(event.kind)){eventCard(root,selected,event);shown++;}
        if(shown==0)text(root,"이 조건에 해당하는 기록이 없습니다.",16,MUTED,false);
    }
    private void eventCard(LinearLayout parent,Session session,DriveEngine.Event event){
        LinearLayout card=card(parent); text(card,clock((int)event.second)+"  ·  "+kind(event),19,event.kind.equals("brake")?AMBER:BLUE,true);
        text(card,String.format(Locale.KOREA,"%.0f → %.0f km/h   |   감속 %.1f m/s²   |   조향 변화 %.0f°/s",event.speedBefore,event.speedAfter,event.acceleration,event.steeringRate),14,MUTED,false);
        text(card,"상황 메모: "+event.context,15,INK,false);
        card.setContentDescription(clock((int)event.second)+" "+kind(event)+" 상세 및 상황 메모");
        card.setOnClickListener(v->details(session,event));
    }
    private void details(Session session,DriveEngine.Event event){ if(running){pause();updateLive();}
        String[] options={"미확인","보행자 대응","끼어들기 대응","신호·정체","도로 형태","기타 / 알 수 없음"};
        new AlertDialog.Builder(this).setTitle(clock((int)event.second)+" · "+kind(event))
            .setMessage(String.format(Locale.KOREA,"시험 데이터 기록\n속도 %.0f → %.0f km/h\n가속도 %.2f m/s²\n조향각 변화율 %.0f°/s\n\n감지 원인이나 운전자 과실을 확정하지 않습니다.\n사용자 메모: %s",event.speedBefore,event.speedAfter,event.acceleration,event.steeringRate,event.context))
            .setPositiveButton("상황 메모",(d,w)->new AlertDialog.Builder(this).setTitle("직접 확인한 상황을 선택하세요")
                .setItems(options,(dialog,index)->{event.context=options[index];persist();saveActive();render();}).setNegativeButton("취소",null).show())
            .setNegativeButton("닫기",null).show();
    }
    private void pause(){running=false;handler.removeCallbacks(tick);saveActive();}
    private void finishTrip(){
        pause(); if(active==null)return;
        history.add(0,active);while(history.size()>12)history.remove(history.size()-1);
        selected=active;active=null;prefs.edit().remove("active").apply();persist();review=true;filter="all";render();
    }
    private void checkRevisit(){ if(active.seconds-lastWarningSecond<8)return;
        for(Session old:history)for(int i=0;i<old.events.size();i++){
            DriveEngine.Event event=old.events.get(i);String key=event.kind+":"+(int)event.second;
            // All demo trips use one fixed route and direction; compare along-route time, not proximity alone.
            if(active.seconds>=event.second-5 && active.seconds<event.second && warned.add(key)){
                lastWarningSecond=active.seconds;warning="이전 시연에서 "+kind(event)+"가 기록된 구간에 접근합니다. 주변 상황을 살펴보세요.";
                if(speechEnabled&&speechReady)speech.speak("이전 기록 구간입니다. 주변 상황을 살펴보세요.",TextToSpeech.QUEUE_FLUSH,null,"memory");
                return;
            }
        }
    }
    private void criteria(){new AlertDialog.Builder(this).setTitle("시험용 탐지 기준")
        .setMessage("급감속 후보: 가속도 -3.0 m/s² 이하, 이전 속도 15 km/h 이상\n급조향 후보: 조향각 변화율 70°/s 이상, 전후 속도 모두 30 km/h 이상\n\n같은 종류는 8초 동안 중복 기록하지 않습니다. 2.5초 초과 데이터 공백, 역순 시간, 비정상 수치는 탐지에서 제외합니다.\n\n이 수치는 시연 기준이며 실차 검증을 거친 안전 기준이 아닙니다. 미끄러짐·사고·운전자 과실은 판단하지 않습니다.")
        .setPositiveButton("확인",null).show();}
    private void chooseTrip(){String[] names=new String[history.size()];for(int i=0;i<names.length;i++)names[i]=date(history.get(i).started)+" · "+history.get(i).events.size()+"건 · 시연";
        new AlertDialog.Builder(this).setTitle("최근 주행 (최대 12개)").setItems(names,(d,i)->{selected=history.get(i);render();}).setNegativeButton("닫기",null).show();}
    private void deleteHistory(){new AlertDialog.Builder(this).setTitle("기록을 모두 삭제할까요?").setMessage("이 기기에 저장한 완료 주행과 상황 메모를 삭제합니다.")
        .setNegativeButton("취소",null).setPositiveButton("삭제",(d,w)->{history.clear();selected=null;persist();render();}).show();}
    private void load(){
        try{JSONArray list=new JSONArray(prefs.getString("history","[]"));for(int i=0;i<Math.min(12,list.length());i++)history.add(Session.from(list.getJSONObject(i)));
            String saved=prefs.getString("active",null);if(saved!=null){active=Session.from(new JSONObject(saved));engine=new DriveEngine();for(int i=0;i<=active.seconds;i++)engine.accept(DriveEngine.demo(i));}
        }catch(JSONException e){toast("저장 기록 일부를 읽지 못했습니다.");}
        if(!history.isEmpty())selected=history.get(0);
    }
    private void persist(){JSONArray list=new JSONArray();for(Session session:history)list.put(session.json());prefs.edit().putString("history",list.toString()).apply();}
    private void saveActive(){if(prefs!=null&&active!=null)prefs.edit().putString("active",active.json().toString()).apply();}
    private static class Session {
        long started;int seconds;double distance;final List<DriveEngine.Event> events=new ArrayList<>();
        JSONObject json(){JSONObject o=new JSONObject();try{o.put("source","demo");o.put("started",started);o.put("seconds",seconds);o.put("distance",distance);JSONArray a=new JSONArray();
            for(DriveEngine.Event e:events){JSONObject j=new JSONObject();j.put("kind",e.kind);j.put("second",e.second);j.put("before",e.speedBefore);j.put("after",e.speedAfter);j.put("acceleration",e.acceleration);j.put("rate",e.steeringRate);j.put("context",e.context);a.put(j);}o.put("events",a);
        }catch(JSONException ignored){}return o;}
        static Session from(JSONObject o)throws JSONException{Session s=new Session();s.started=o.getLong("started");s.seconds=Math.max(0,Math.min(64,o.getInt("seconds")));s.distance=o.getDouble("distance");JSONArray a=o.getJSONArray("events");
            for(int i=0;i<a.length();i++){JSONObject j=a.getJSONObject(i);DriveEngine.Event e=new DriveEngine.Event(j.getString("kind"),j.getDouble("second"),j.getDouble("before"),j.getDouble("after"),j.getDouble("acceleration"),j.getDouble("rate"));e.context=j.optString("context","미확인");s.events.add(e);}return s;}
    }
    private class RouteView extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final float[] route={.08f,.82f,.22f,.82f,.35f,.66f,.35f,.42f,.58f,.42f,.72f,.20f,.92f,.20f};
        RouteView(){super(MainActivity.this);setContentDescription("시험 경로 도식과 변화 후보 위치");setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        PointF point(double progress){int segments=route.length/2-1;double pos=Math.max(0,Math.min(.999999,progress))*segments;int index=(int)pos;float t=(float)(pos-index);return new PointF((route[index*2]+(route[index*2+2]-route[index*2])*t)*getWidth(),(route[index*2+1]+(route[index*2+3]-route[index*2+1])*t)*getHeight());}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float w=getWidth(),h=getHeight();paint.setStrokeWidth(dp(1));paint.setColor(Color.rgb(36,55,68));for(int i=1;i<8;i++){canvas.drawLine(w*i/8,0,w*i/8,h,paint);canvas.drawLine(0,h*i/8,w,h*i/8,paint);}
            Path path=new Path();path.moveTo(route[0]*w,route[1]*h);for(int i=2;i<route.length;i+=2)path.lineTo(route[i]*w,route[i+1]*h);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStrokeWidth(dp(12));paint.setColor(Color.rgb(45,75,87));canvas.drawPath(path,paint);paint.setStrokeWidth(dp(3));paint.setColor(MINT);canvas.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(dp(12));paint.setColor(MUTED);canvas.drawText("출발",dp(12),h-dp(16),paint);canvas.drawText("도착",w-dp(48),dp(28),paint);
            Session s=review?selected:active;if(s!=null)for(int i=0;i<s.events.size();i++){DriveEngine.Event e=s.events.get(i);PointF p=point(e.progress());paint.setColor(e.kind.equals("brake")?AMBER:BLUE);canvas.drawCircle(p.x,p.y,dp(13),paint);paint.setColor(BG);paint.setTextSize(dp(13));paint.setTypeface(Typeface.DEFAULT_BOLD);canvas.drawText(""+(i+1),p.x-dp(4),p.y+dp(5),paint);}
            if(!review){PointF p=point(active==null?0:active.seconds/(double)DriveEngine.DURATION);paint.setColor(INK);canvas.drawCircle(p.x,p.y,dp(8),paint);paint.setColor(BG);canvas.drawCircle(p.x,p.y,dp(3),paint);}
        }
        @Override public boolean onTouchEvent(android.view.MotionEvent event){if(event.getAction()!=android.view.MotionEvent.ACTION_UP)return true;Session s=review?selected:active;if(s!=null)for(DriveEngine.Event e:s.events){PointF p=point(e.progress());if(Math.hypot(event.getX()-p.x,event.getY()-p.y)<dp(30)){performClick();details(s,e);return true;}}return true;}
        @Override public boolean performClick(){super.performClick();return true;}
    }
    private class SpeedView extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);SpeedView(){super(MainActivity.this);setContentDescription("시험 주행 속도 그래프");}
        @Override protected void onDraw(Canvas c){super.onDraw(c);int end=active==null?0:active.seconds;Path line=new Path();for(int i=0;i<=end;i++){float x=getWidth()*i/64f,y=(float)(getHeight()-dp(8)-(DriveEngine.demo(i).speed/80)*(getHeight()-dp(16)));if(i==0)line.moveTo(x,y);else line.lineTo(x,y);}p.setColor(MINT);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));c.drawPath(line,p);}
    }
    private String kind(DriveEngine.Event e){return e.kind.equals("brake")?"급감속 후보":"급조향 후보";}
    private String clock(int seconds){return String.format(Locale.KOREA,"%02d:%02d",seconds/60,seconds%60);}
    private String date(long time){return new SimpleDateFormat("MM.dd HH:mm",Locale.KOREA).format(new Date(time));}
    private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    private TextView text(LinearLayout parent,String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(6),0,dp(6));if(bold)t.setTypeface(null,Typeface.BOLD);parent.addView(t);return t;}
    private LinearLayout card(LinearLayout parent){LinearLayout c=column();c.setPadding(dp(18),dp(12),dp(18),dp(12));GradientDrawable d=new GradientDrawable();d.setColor(CARD);d.setCornerRadius(dp(18));c.setBackground(d);LinearLayout.LayoutParams params=parent.getOrientation()==LinearLayout.HORIZONTAL?new LinearLayout.LayoutParams(0,-2,1):new LinearLayout.LayoutParams(-1,-2);params.setMargins(0,dp(10),dp(8),dp(4));parent.addView(c,params);return c;}
    private void addButton(LinearLayout parent,String value,Runnable action,boolean primary){Button b=new Button(this);b.setText(value);b.setAllCaps(false);b.setTextSize(16);b.setMinHeight(dp(58));b.setTextColor(primary?BG:INK);GradientDrawable d=new GradientDrawable();d.setColor(primary?MINT:CARD);d.setCornerRadius(dp(12));b.setBackground(d);b.setPadding(dp(12),dp(8),dp(12),dp(8));LinearLayout.LayoutParams params=parent.getOrientation()==LinearLayout.HORIZONTAL?new LinearLayout.LayoutParams(0,-2,1):new LinearLayout.LayoutParams(-1,-2);params.setMargins(0,dp(8),dp(8),dp(8));parent.addView(b,params);b.setOnClickListener(v->action.run());}
}