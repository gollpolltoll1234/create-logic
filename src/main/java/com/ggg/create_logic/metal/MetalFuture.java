package com.ggg.create_logic.metal;

import com.ggg.create_logic.metal.Metal.MetalVariable;

import java.util.List;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.BiFunction;

public class MetalFuture {
    private MetalVariable result;
    private boolean done = false;
    private List<Consumer<MetalVariable>> callbacks = new ArrayList<>();
    
    public static MetalFuture completed(MetalVariable value) {
        MetalFuture future = new MetalFuture();
        future.complete(value);
        return future;
    }
    
    public void complete(MetalVariable value) {
        if (done) return;
        this.result = value;
        this.done = true;
        for (Consumer<MetalVariable> callback : callbacks) {
            callback.accept(value);
        }
        callbacks.clear();
    }
    
    public boolean isDone() { return done; }
    public MetalVariable get() { return result; }
    
    public void onReceive(Consumer<MetalVariable> callback) {
        if (done) {
            callback.accept(result);
        } else {
            callbacks.add(callback);
        }
    }
    public MetalFuture then(Consumer<MetalVariable> action) {
        MetalFuture next = new MetalFuture();
        this.onReceive(v -> {
            action.accept(v);
            next.complete(v);
        });
        return next;
    }
    
    public MetalFuture thenCombine(MetalFuture other, BiFunction<MetalVariable, MetalVariable, MetalVariable> combiner) {
        MetalFuture result = new MetalFuture();
        
        this.onReceive(v1 -> {
            other.onReceive(v2 -> {
                result.complete(combiner.apply(v1, v2));
            });
        });
        
        return result;
    }
}