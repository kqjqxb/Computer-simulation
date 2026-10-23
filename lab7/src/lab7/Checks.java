package lab7;

/** Перевірка інваріантів моделей. */
final class Checks {

    private Checks() {
    }

    static void check(boolean ok, String what) {
        if (!ok) {
            throw new IllegalStateException("порушено інваріант: " + what);
        }
    }
}
