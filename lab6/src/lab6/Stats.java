package lab6;

import java.util.Collection;
import java.util.Locale;

/** Середнє, стандартне відхилення та півширина 95 % довірчого інтервалу за t-розподілом Стьюдента. */
public record Stats(double mean, double ci, int n) {

    public static Stats of(Collection<Double> values) {
        int n = values.size();
        double mean = values.stream().mapToDouble(Double::doubleValue).sum() / n;
        if (n < 2) {
            return new Stats(mean, 0.0, n);
        }
        double ss = values.stream().mapToDouble(v -> (v - mean) * (v - mean)).sum();
        return new Stats(mean, tQuantile975(n - 1) * Math.sqrt(ss / (n - 1) / n), n);
    }

    /** Квантиль рівня 0,975 t-розподілу (розклад Корніша – Фішера, похибка < 1e-4 при ν ≥ 5). */
    static double tQuantile975(int nu) {
        double z = 1.959963984540054;
        double z3 = z * z * z;
        double z5 = z3 * z * z;
        double z7 = z5 * z * z;
        return z + (z3 + z) / (4.0 * nu) + (5 * z5 + 16 * z3 + 3 * z) / (96.0 * nu * nu)
                + (3 * z7 + 19 * z5 + 17 * z3 - 15 * z) / (384.0 * nu * nu * nu);
    }

    public String format(int digits) {
        return String.format(Locale.ROOT, "%." + digits + "f ± %." + digits + "f", mean, ci);
    }

    @Override
    public String toString() {
        return format(4);
    }
}
