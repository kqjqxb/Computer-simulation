package lab6;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Вивід програми: одночасно на консоль і у файл результатів. */
public final class Output {

    private final StringBuilder text = new StringBuilder();

    public void println(String line) {
        System.out.println(line);
        text.append(line).append('\n');
    }

    public void println() {
        println("");
    }

    public void printf(String format, Object... args) {
        String s = String.format(Locale.ROOT, format, args);
        System.out.print(s);
        text.append(s);
    }

    public void save(Path file) {
        write(file, text.toString());
    }

    public static void write(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
