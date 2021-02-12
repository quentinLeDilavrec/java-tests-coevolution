package fr.quentin.coevolutionMiner.v2.utils;

public class Tuple<T, R> {
    public final T first;
    public final R second;

    public Tuple(T first, R second) {
        this.first = first;
        this.second = second;

        this.hashCode = hashCodeCompute();
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    private final int hashCode;

    private int hashCodeCompute() {
        int result = 1;
        result = 31 * result + ((first == null) ? 0 : first.hashCode());
        result = 31 * result + ((second == null) ? 0 : second.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Tuple other = (Tuple) obj;
        if (first == null) {
            if (other.first != null)
                return false;
        } else if (!first.equals(other.first))
            return false;
        if (second == null) {
            if (other.second != null)
                return false;
        } else if (!second.equals(other.second))
            return false;
        return true;
    }
}