package lab6;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Запуск моделей: java lab6.Main [conveyor] [inventory] [servers] [export]. */
public final class Main {

    private static final Path RESULTS = Path.of("results");

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        java.util.logging.LogManager.getLogManager().reset(); // журнал бібліотеки не потрібен
        List<String> tasks = args.length > 0 ? List.of(args)
                : List.of("conveyor", "inventory", "servers", "export");
        for (String task : tasks) {
            switch (task) {
                case "conveyor" -> task("task1", Conveyor::main);
                case "inventory" -> task("task2", Inventory::main);
                case "servers" -> task("task3", Servers::main);
                case "export" -> NetExport.main(Path.of("nets"), RESULTS);



                default -> throw new IllegalArgumentException(task);
            }
        }
    }

    private static void task(String name, Function<Output, Map<String, Object>> model) {
        Output out = new Output();
        long start = System.nanoTime();
        Map<String, Object> json = model.apply(out);
        out.save(RESULTS.resolve(name + "_output.txt"));
        Output.write(RESULTS.resolve(name + ".json"), Json.write(json));
        System.out.printf("[%s: %.1f с]%n%n", name, (System.nanoTime() - start) / 1e9);
    }
}
