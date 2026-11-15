package com.roundwise.pleos;
import android.app.Application;
import java.lang.reflect.*;
import java.util.*;
final class RoundwiseBridge {
    final Object vm,pleos,store;
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
    void start(boolean permission){call(pleos,"start",permission);}void close(){try{call(store,"clear");}catch(Exception ignored){call(pleos,"release");}}
}
