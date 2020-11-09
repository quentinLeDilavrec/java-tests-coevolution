package fr.quentin.coevolutionMiner.v2.coevolution.miners;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

class Chain<T> implements Collection<T> {
    /**
	 *
	 */
	private final ApplierHelper.EvoStateMaintainer evoStateMaintainer;
	public final Chain<T> prev;
    public final T curr;

    public Chain(ApplierHelper.EvoStateMaintainer evoStateMaintainer, T curr) {
        this.evoStateMaintainer = evoStateMaintainer;
		this.prev = null;
        this.curr = curr;
    }

    public Chain(ApplierHelper.EvoStateMaintainer evoStateMaintainer, T curr, Chain<T> prev) {
        this.evoStateMaintainer = evoStateMaintainer;
		this.prev = prev;
        this.curr = curr;
    }

    @Override
    public int size() {
        return (prev != null) ? 1 + prev.size() : 1;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean contains(Object o) {
        if (o == curr) {
            return true;
        }
        if (prev == null) {
            return false;
        } else {
            return prev.contains(o);
        }
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            Chain<T> it = Chain.this;

            @Override
            public boolean hasNext() {
                return it != null;
            }

            @Override
            public T next() {
                if (hasNext()) {
                    T tmp = it.curr;
                    it = it.prev;
                    return tmp;
                } else {
                    throw new NoSuchElementException();
                }
            }

        };
    }

    @Override
    public Object[] toArray() {
        List<T> aaa = new ArrayList<>();
        iterator().forEachRemaining(x->aaa.add((T) x));
        return aaa.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        List<T> aaa = new ArrayList<>();
        iterator().forEachRemaining(x->aaa.add((T) x));
        return aaa.toArray(a);
    }

    @Override
    public boolean add(T e) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object object : c) {
            if(contains(object)){
                return true;
            }
        };
        return false;
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();

    }
}