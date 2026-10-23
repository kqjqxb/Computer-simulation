package lab7;

/** Формули теорії масового обслуговування для перевірки моделей. */
final class Queueing {

    private Queueing() {
    }

    /** Ймовірність очікування в системі M/M/c (формула Ерланга C), a = λ·s. */
    static double erlangC(int c, double a) {
        double term = 1;
        double sum = 1;
        for (int k = 1; k < c; k++) {
            term *= a / k;
            sum += term;
        }
        double last = term * a / c / (1 - a / c);
        return last / (sum + last);
    }

    /** Середня довжина черги M/M/c. */
    static double queueMMc(int c, double lambda, double s) {
        double a = lambda * s;
        return erlangC(c, a) * a / (c - a);
    }

    /**
     * Частка вимог, яку обслуговує перший з двох однакових каналів M/M/2, якщо вимога, що застала обидва
     * канали вільними, завжди займає перший: з рівняння балансу стану «зайнятий лише другий канал»
     * μ·p_2 = (λ + μ)·p_{1,2} імовірність зайнятості першого каналу p_1 − p_{1,2} + P(N ≥ 2).
     */
    static double preferredShare(double lambda, double s) {
        double a = lambda * s;
        double rho = a / 2;
        double p0 = 1 / (1 + a + a * a / 2 / (1 - rho));
        double p1 = a * p0;
        double p2 = a * a / 2 * p0;
        double onlySecond = p2 / (1 + a);
        double busyFirst = p1 - onlySecond + p2 / (1 - rho);
        return busyFirst / a;
    }
}
