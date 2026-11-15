package com.roundwise.pleos;
import android.content.*;
import android.os.Handler;
import android.os.Looper;
import java.lang.reflect.*;

/** Uses the platform UX restrictions; unknown automotive state stays locked. */
final class ParkingGate {
    interface Listener {void changed(boolean allowed,String description);}
    private Object car,manager,listener;
    private final Listener callback;
    private final Handler main=new Handler(Looper.getMainLooper());
    ParkingGate(Context context,Listener callback){
        this.callback=callback;
        if(!context.getPackageManager().hasSystemFeature("android.hardware.type.automotive")){publish(false,"차량 상태를 확인할 수 없어 여정 조작이 잠겨 있습니다.");return;}
        try{
            Class<?> carClass=Class.forName("android.car.Car");car=carClass.getMethod("createCar",Context.class).invoke(null,context);
            if(car==null)throw new IllegalStateException();
            manager=carClass.getMethod("getCarManager",String.class).invoke(car,carClass.getField("CAR_UX_RESTRICTION_SERVICE").get(null));
            if(manager==null)throw new IllegalStateException();
            Class<?> kind=Class.forName("android.car.drivingstate.CarUxRestrictionsManager$OnUxRestrictionsChangedListener");
            listener=Proxy.newProxyInstance(kind.getClassLoader(),new Class<?>[]{kind},(proxy,method,args)->{
                if(method.getName().equals("onUxRestrictionsChanged")){read(args[0]);return null;}
                if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
                if(method.getName().equals("equals"))return proxy==args[0];
                if(method.getName().equals("toString"))return "ParkingGateListener";
                return null;
            });
            manager.getClass().getMethod("registerListener",kind).invoke(manager,listener);
            read(manager.getClass().getMethod("getCurrentCarUxRestrictions").invoke(manager));
        }catch(Exception e){publish(false,"차량 주행 제한 상태를 확인하지 못했습니다. 주차 상태 확인 후 다시 열어 주세요.");}
    }
    private void read(Object restrictions){
        try{boolean blocked=(Boolean)restrictions.getClass().getMethod("isRequiresDistractionOptimization").invoke(restrictions);publish(!blocked,blocked?"주행 제한 중 · 여정 조작은 주차 후 가능합니다.":"차량 UX 제한 없음 · 주차 후 조작하세요.");}
        catch(Exception e){publish(false,"차량 상태 확인 불가 · 조작 잠금");}
    }
    private void publish(boolean allowed,String description){main.post(()->callback.changed(allowed,description));}
    void release(){try{if(manager!=null)manager.getClass().getMethod("unregisterListener").invoke(manager);}catch(Exception ignored){}try{if(car!=null)car.getClass().getMethod("disconnect").invoke(car);}catch(Exception ignored){}}
}