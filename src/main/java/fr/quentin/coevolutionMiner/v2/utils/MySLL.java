package fr.quentin.coevolutionMiner.v2.utils;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class MySLL<T> implements Iterable<T> {
    public final T head;
    public final MySLL<T> tail;
    public static final MySLL EMPTY = new MySLL(null, null);

    private MySLL(T head, MySLL<T> tail) {
        this.head = head;
        this.tail = tail;
    }

    public MySLL<T> cons(T head) {
        return new MySLL<>(head, this);
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {

            MySLL<T> curr = MySLL.this;

            @Override
            public boolean hasNext() {
                return curr != EMPTY;
            }

            @Override
            public T next() {
                if (curr == EMPTY) {
                    throw new NoSuchElementException();
                }
                T tmp = curr.head;
                curr = curr.tail;
                return tmp;
            }

        };
    }

}