package br.com.pratoja.pratoja.service;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
@Service
public class RealtimeHub {
    private final Map<Long,List<SseEmitter>> orders=new ConcurrentHashMap<>();private final List<SseEmitter> admins=new CopyOnWriteArrayList<>();
    public SseEmitter subscribe(Long id){SseEmitter e=new SseEmitter(30*60_000L);orders.computeIfAbsent(id,x->new CopyOnWriteArrayList<>()).add(e);cleanup(e,()->orders.getOrDefault(id,List.of()).remove(e));send(e,"connected",Map.of("orderId",id));return e;}
    public SseEmitter subscribeAdmin(){SseEmitter e=new SseEmitter(30*60_000L);admins.add(e);cleanup(e,()->admins.remove(e));send(e,"connected",Map.of("ok",true));return e;}
    public void publish(Long id,String status){orders.getOrDefault(id,List.of()).forEach(e->send(e,"status",Map.of("orderId",id,"status",status)));admins.forEach(e->send(e,"order",Map.of("orderId",id,"status",status)));}
    private void cleanup(SseEmitter e,Runnable r){e.onCompletion(r);e.onTimeout(r);e.onError(x->r.run());}
    private void send(SseEmitter e,String name,Object data){try{e.send(SseEmitter.event().name(name).data(data));}catch(IOException ex){e.complete();}}
}
