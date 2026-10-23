package lab7;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Запуск моделей: java lab7.Main [network] [fms] [minibus] [conveyor] [export]. */
public final class Main {

    private static final Path RESULTS = Path.of("results");

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        java.util.logging.LogManager.getLogManager().reset(); // журнал бібліотеки не потрібен
        List<String> tasks = args.length > 0 ? List.of(args)
                : List.of("network", "fms", "minibus", "conveyor", "export");
        for (String task : tasks) {
            switch (task) {
                case "network" -> task("task1", QueueNetwork::main);
                case "fms" -> task("task2", Fms::main);
                case "minibus" -> task("task3", Minibus::main);
                case "conveyor" -> task("task4", Conveyor::main);
                case "export" -> Export.main(Path.of("nets"), RESULTS);
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
