package lab6;

/** Формули теорії масового обслуговування для перевірки моделі серверів. */
final class Queueing {

    private Queueing() {
    }

    /** Ймовірність очікування в системі M/M/c (формула Ерланга C), a = λ/μ. */
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

    /** Середній час перебування в системі M/M/c з інтенсивністю λ і середнім часом обслуговування s. */
    static double sojournMMc(int c, double lambda, double s) {
        double a = lambda * s;
        if (a >= c) {
            return Double.POSITIVE_INFINITY;
        }
        return erlangC(c, a) * s / (c - a) + s;
    }
}
