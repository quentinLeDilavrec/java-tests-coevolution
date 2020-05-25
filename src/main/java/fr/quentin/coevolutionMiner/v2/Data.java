package fr.quentin.coevolutionMiner.v2;

import java.lang.ref.SoftReference;
import java.util.concurrent.locks.ReentrantLock;

public class Data<T> {
    // uses SoftReferences here but could also use a Cache Map in *Handler
    private SoftReference<T> data;

    public T get() {
        if (data == null)
            return null;
        return data.get();
    }

    public void set(T data) {
        this.data = new SoftReference<T>(data);
    }

    public final ReentrantLock lock = new ReentrantLock();
}