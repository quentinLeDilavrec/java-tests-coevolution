package fr.quentin.coevolutionMiner.v2.utils;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Function;

public class Iterators2 {

    public static <I> Iterable<I> createChainIterable(Iterable<Iterable<I>> input) {
        return new Iterable<I>() {
            @Override
            public Iterator<I> iterator() {
                return new Iterators2.ChainIterable<>(input);
            }
        };
    }

    private static class ChainIterable<I> implements Iterator<I> {
        private final Iterator<Iterable<I>> inputs;
        private Iterator<I> current;

        ChainIterable(Iterable<Iterable<I>> inputs) {
            this.inputs = inputs.iterator();
        }

        @Override
        public boolean hasNext() {
            if (current != null && current.hasNext()) {
                return true;
            } else {
                while (inputs.hasNext()) {
                    current = inputs.next().iterator();
                    if (current.hasNext()) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public I next() {
            if (hasNext()) {
                return current.next();
            } else {
                throw new NoSuchElementException();
            }
        }
    }

    public static <I> Iterator<I> createCompoundIterator(I input, Function<I, Iterator<I>> convertor) {
        return new Iterators2.CompoundIterator<>(input, convertor);
    }

    private static class CompoundIterator<I> implements Iterator<I> {

        private final Iterator<I> inputs;
        private final Function<I, Iterator<I>> convertor;
        @SuppressWarnings("unchecked")
        private Iterator<I> currentIterator = EMPTY;
        private I input;

        public CompoundIterator(I input, Function<I, Iterator<I>> convertor) {
            this.input = input;
            this.inputs = convertor.apply(input);
            this.convertor = convertor;
        }

        public boolean hasNext() {
            if (input != null) {
                return true;
            } else if (currentIterator != null && currentIterator.hasNext()) {
                return true;
            } else {
                while (inputs.hasNext()) {
                    input = inputs.next();
                    currentIterator = convertor.apply(input);
                    if (currentIterator.hasNext()) {
                        return true;
                    }
                }
            }
            return false;
        }

        public I next() {
            if (currentIterator == EMPTY && !hasNext()) {
                throw new NoSuchElementException();
            }
            if (input != null) {
                I tmp = input;
                input = null;
                return tmp;
            }
            return currentIterator.next();
        }
    }

    public static abstract class IteratorPairCustom<U, T> implements Iterator<T> {
	    U prev = null;
	    Iterator<U> rootIt;
	    Iterator<T> it = null;
	
	    public IteratorPairCustom(Iterator<U> iterator) {
	        this.rootIt = iterator;
            prev = rootIt.next();
	    }
	
	    public abstract Iterator<T> makeIt(U prev, U next);
	
	    @Override
	    public boolean hasNext() {
	        while (true) {
	            if (it != null && it.hasNext()) {
	                return true;
	            } else if (rootIt.hasNext()) {
	                U tmp = rootIt.next();
	                it = makeIt(prev, tmp);
	                prev = tmp;
	            } else {
	                return false;
	            }
	        }
	    }
	
	    @Override
	    public T next() {
	        if (hasNext()) {
	            return it.next();
	        } else {
	            throw new NoSuchElementException();
	        }
	    }
	}

	@SuppressWarnings("rawtypes")
    private final static Iterator EMPTY = new Iterator() {
        public boolean hasNext() {
            return false;
        }

        @Override
        public Object next() {
            return null;
        }
    };
}