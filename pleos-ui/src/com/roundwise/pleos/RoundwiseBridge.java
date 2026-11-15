package com.roundwise.pleos;
import android.app.Application;
import java.lang.reflect.*;
import java.util.*;
final class RoundwiseBridge {
    final Object vm,pleos,store;
    Object navigation,routeListener;
    volatile long requestedAt;
    volatile boolean guidanceStarted;
    volatile String routeEvent="아직 경로 요청이 없습니다.";
    RoundwiseBridge(Application app)throws Exception{vm=Class.forName("com.roundwise.app.ui.RoundwiseViewModel").getConstructor(Application.class).newInstance(app);pleos=get(vm,"pleos");store=Class.forName("androidx.lifecycle.ViewModelStore").getConstructor().newInstance();call(store,"put","roundwise",vm);}
    static Object get(Object target,String property){if(target==null)return null;try{return target.getClass().getMethod("get"+Character.toUpperCase(property.charAt(0))+property.substring(1)).invoke(target);}catch(Exception e){return null;}}
    static Object call(Object target,String method,Object...args){if(target==null)return null;for(Method m:target.getClass().getMethods())if(m.getName().equals(method)&&m.getParameterCount()==args.length){try{return m.invoke(target,args);}catch(IllegalArgumentException ignored){}catch(Exception e){throw new IllegalStateException(method,e);}}throw new IllegalArgumentException(method);}
    static Object value(Object flow){if(flow==null)return null;try{return Class.forName("kotlinx.coroutines.flow.StateFlow").getMethod("getValue").invoke(flow);}catch(Exception e){return null;}}
    Object ui(){return value(get(vm,"ui"));}Object sdk(){return value(get(pleos,"status"));}
    static String str(Object o){return o==null?"":o.toString();}static String s(Object o,String k){return str(get(o,k));}static double n(Object o,String k){Object v=get(o,k);return v instanceof Number?((Number)v).doubleValue():0;}
    static boolean b(Object o,String k){return Boolean.TRUE.equals(get(o,k));}
    static List<?> list(Object o,String k){Object v=get(o,k);return v instanceof List?(List<?>)v:Collections.emptyList();}
    static Map<?,?> map(Object o,String k){Object v=get(o,k);return v instanceof Map?(Map<?,?>)v:Collections.emptyMap();}
    static Object enumValue(String name,String value){try{return Class.forName(name).getMethod("valueOf",String.class).invoke(null,value);}catch(Exception e){throw new IllegalStateException(e);}}
    void observeNavigation(){
        if(routeListener!=null||!"READY".equals(s(sdk(),"navi")))return;
        try{
            Field field=pleos.getClass().getDeclaredField("navi");field.setAccessible(true);navigation=field.get(pleos);
            Class<?> type=Class.forName("ai.pleos.playground.navi.helper.listener.NaviHelperEventListener");
            Object listener=Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},(proxy,method,args)->{
                String name=method.getName();
                if(name.equals("hashCode"))return System.identityHashCode(proxy);
                if(name.equals("equals"))return proxy==args[0];
                if(name.equals("toString"))return "RoundwiseRouteObserver";
                if(name.equals("onRouteStarted")){guidanceStarted=true;routeEvent="안내 시작 확인 · "+s(args[0],"name");}
                else if(name.equals("onError")){guidanceStarted=false;routeEvent="내비 오류 · "+str(args[0]);}
                else if(name.equals("onRouteCancelled")||name.equals("onRouteEnded")){guidanceStarted=false;requestedAt=0;routeEvent=name.equals("onRouteCancelled")?"경로 안내 취소":"경로 안내 종료";}
                else if(name.equals("onRoutingCompleted")&&!guidanceStarted){routeEvent="경로 탐색 완료 · 안내 시작 응답 대기";}
                if(name.startsWith("on"))android.util.Log.i("RoundwiseRoute",name);
                return null;
            });
            call(navigation,"addListener",listener);routeListener=listener;
        }catch(Exception e){android.util.Log.w("RoundwiseRoute","Cannot attach route observer",e);}
    }
    void beginRoute(){guidanceStarted=false;requestedAt=android.os.SystemClock.elapsedRealtime();routeEvent="경로 요청 전송 · 안내 응답 대기";}
    String routeStatus(){if(requestedAt>0&&!guidanceStarted&&!routeEvent.startsWith("내비 오류")&&android.os.SystemClock.elapsedRealtime()-requestedAt>20000)return "안내 시작 미확인 · 내비 서비스와 네트워크를 확인하세요";return routeEvent;}
    void openNavigation(){observeNavigation();if(navigation==null)throw new IllegalStateException("Navigation not connected");try{Object foreground=Class.forName("ai.pleos.playground.navi.data.NavigationScreenRequest$Foreground").getField("INSTANCE").get(null);call(navigation,"requestNavigationScreen",foreground);android.util.Log.i("RoundwiseRoute","Navigation foreground requested");}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    void start(boolean permission){call(pleos,"start",permission);}void close(){try{if(navigation!=null&&routeListener!=null)call(navigation,"removeListener",routeListener);}catch(Exception ignored){}try{call(store,"clear");}catch(Exception ignored){call(pleos,"release");}}
}
