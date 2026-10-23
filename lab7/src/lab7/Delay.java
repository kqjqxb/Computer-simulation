package lab7;

import java.util.Locale;

/** Затримка переходу: стала (const), exp(mean), unif(mean ± dev) або norm(mean, dev). */
public record Delay(String distribution, double mean, double dev) {

    public static Delay fixed(double mean) {
        return new Delay(null, mean, 0);
    }

    public static Delay exp(double mean) {
        return new Delay("exp", mean, 0);
    }

    public static Delay unif(double mean, double dev) {
        return new Delay("unif", mean, dev);
    }

    public static Delay norm(double mean, double dev) {
        return new Delay("norm", mean, dev);
    }

    @Override
    public String toString() {
        String m = String.format(Locale.ROOT, "%g", mean).replaceAll("\\.?0+$", "");
        String d = String.format(Locale.ROOT, "%g", dev).replaceAll("\\.?0+$", "");
        return switch (distribution == null ? "const" : distribution) {
            case "exp" -> "exp(" + m + ")";
            case "unif" -> m + " ± " + d;
            case "norm" -> "N(" + m + "; " + d + ")";
            default -> m;
        };
    }
}
