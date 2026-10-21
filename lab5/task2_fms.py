"""Завдання 2: роботизована гнучка виробнича система – два верстати, три роботи."""
import json
import statistics
from pathlib import Path

import fun_rand
from petri import PetriNet, const, exp, norm, unif
from runner import fmt, replicate, summary

RESULTS = Path(__file__).resolve().parent / "results"
ARRIVAL_MEAN = 40.0  # с
HANDLE = unif(8, 1)  # захоплення або вивільнення деталі
MACHINE_SLOTS = 3
POSITION = {"вхід": 0.0, "В1": 6.0, "В2": 13.0, "склад": 18.0}  # точки вздовж лінії переміщення, с ходу
# шлях: (назва, звідки, куди, позиція із запитом, позиція місць на верстаті призначення,
#        позиція місць, що звільняються після захоплення, позиція, куди потрапляє деталь)
ROUTES = [
    ("вхід–В1", "вхід", "В1", "Деталь на вході", "Вільні місця В1", None, "Деталь на В1"),
    ("В1–В2", "В1", "В2", "Оброблена на В1", "Вільні місця В2", "Вільні місця В1", "Деталь на В2"),
    ("В2–склад", "В2", "склад", "Оброблена на В2", None, "Вільні місця В2", "Склад"),
]
TIME, WARMUP, SEEDS = 100000.0, 10000.0, range(1, 21)
SATURATION = 10 ** 5  # початковий запас деталей на вході в режимі необмеженого надходження


def distance(a, b):
    return abs(POSITION[a] - POSITION[b])


def build(variant, arrival_mean=ARRIVAL_MEAN):
    """variant 1 – по одному роботу на кожен шлях; variant 2 – будь-який робот на будь-якому шляху,
    виконувати переміщення починає найближчий вільний робот. arrival_mean=None – необмежене надходження."""
    net = PetriNet(f"Гнучка виробнича система, варіант {variant}")
    net.place("Генератор", 1 if arrival_mean else 0)
    net.place("Деталь на вході", 0 if arrival_mean else SATURATION)
    for m in ("В1", "В2"):
        net.place(f"Вільні місця {m}", MACHINE_SLOTS)
        net.place(f"Деталь на {m}")
        net.place(f"Оброблена на {m}")
    net.place("Склад")
    for route, *_ in ROUTES:
        for stage in ("Робот біля деталі", "Деталь захоплена", "Деталь доставлена"):
            net.place(f"{stage} ({route})")
    if variant == 1:
        for k in range(1, 4):
            net.place(f"Робот {k} вільний", 1)
            net.place(f"Робот {k} повертається")
    else:
        for loc in POSITION:
            net.place(f"Вільний робот: {loc}", int(loc != "склад"))

    if arrival_mean:
        net.transition("Надходження", exp(arrival_mean), inputs={"Генератор": 1},
                       outputs={"Генератор": 1, "Деталь на вході": 1})
    net.transition("Обробка на В1", norm(60, 10), inputs={"Деталь на В1": 1}, outputs={"Оброблена на В1": 1},
                   carry={"Оброблена на В1": "Деталь на В1"})
    net.transition("Обробка на В2", exp(100), inputs={"Деталь на В2": 1}, outputs={"Оброблена на В2": 1},
                   carry={"Оброблена на В2": "Деталь на В2"})

    for k, (route, origin, target, request, slot, freed, result) in enumerate(ROUTES, start=1):
        at_part, held, delivered = (f"{s} ({route})" for s in ("Робот біля деталі", "Деталь захоплена",
                                                                "Деталь доставлена"))
        # робота призначають, лише якщо на верстаті призначення є вільне місце; місце резервується
        inputs = {request: 1, **({slot: 1} if slot else {})}
        if variant == 1:
            net.transition(f"Призначення робота {k}", inputs={**inputs, f"Робот {k} вільний": 1},
                           outputs={at_part: 1}, carry={at_part: request})
        else:
            for loc in POSITION:
                d = distance(loc, origin)
                net.transition(f"Виклик робота: {loc} → {route}", const(d),
                               inputs={**inputs, f"Вільний робот: {loc}": 1}, outputs={at_part: 1},
                               carry={at_part: request}, priority=int(100 - d))
        net.transition(f"Захоплення ({route})", HANDLE, inputs={at_part: 1},
                       outputs={held: 1, **({freed: 1} if freed else {})}, carry={held: at_part})
        net.transition(f"Переміщення ({route})", const(distance(origin, target)), inputs={held: 1},
                       outputs={delivered: 1}, carry={delivered: held})
        robot = f"Робот {k} повертається" if variant == 1 else f"Вільний робот: {target}"
        net.transition(f"Вивільнення ({route})", HANDLE, inputs={delivered: 1},
                       outputs={result: 1, robot: 1}, carry={result: delivered})
        if variant == 1:
            net.transition(f"Повернення робота {k}", const(distance(origin, target)),
                           inputs={f"Робот {k} повертається": 1}, outputs={f"Робот {k} вільний": 1})
    return net


def robots_busy(net):
    """Кількість зайнятих роботів (з урахуванням холостого ходу)."""
    if "Робот 1 вільний" in net.places:
        return 3 - sum(net.mark(f"Робот {k} вільний") for k in range(1, 4))
    return 3 - sum(net.mark(f"Вільний робот: {loc}") for loc in POSITION)


def inside(net, prefix, suffix=""):
    """Кількість маркерів у переходах, назва яких починається з prefix і закінчується suffix."""
    return sum(len(t.buffer) for name, t in net.transitions.items()
               if name.startswith(prefix) and name.endswith(suffix))


def check_invariants(net):
    """P-інваріанти роботів і місць на верстатах; перевіряються в моменти, коли в переходах немає
    маркерів з нульовою затримкою."""
    if any(t.next_time == net.time for t in net.transitions.values()):
        return
    on_route = {route: sum(net.mark(f"{s} ({route})") for s in ("Робот біля деталі", "Деталь захоплена",
                                                               "Деталь доставлена"))
                + sum(inside(net, f"{s} ({route})") for s in ("Захоплення", "Переміщення", "Вивільнення"))
                for route, *_ in ROUTES}
    if "Робот 1 вільний" in net.places:
        for k, (route, *_) in enumerate(ROUTES, start=1):
            robot = net.mark(f"Робот {k} вільний") + net.mark(f"Робот {k} повертається")
            assert robot + inside(net, f"Повернення робота {k}") + on_route[route] == 1
    else:
        free = sum(net.mark(f"Вільний робот: {loc}") for loc in POSITION)
        assert free + inside(net, "Виклик") + sum(on_route.values()) == 3
    # місце на верстаті зайняте від призначення робота, який везе деталь, до захоплення її наступним роботом
    for m, into, out in (("В1", "вхід–В1", "В1–В2"), ("В2", "В1–В2", "В2–склад")):
        on_machine = net.mark(f"Деталь на {m}") + inside(net, f"Обробка на {m}") \
            + net.mark(f"Оброблена на {m}")
        before_grab = net.mark(f"Робот біля деталі ({out})") + inside(net, f"Захоплення ({out})") \
            + inside(net, "Виклик", out)
        reserved = inside(net, "Виклик", into) + on_route[into]
        assert net.mark(f"Вільні місця {m}") + reserved + on_machine + before_grab == MACHINE_SLOTS


def run(variant, arrival_mean=ARRIVAL_MEAN, time=TIME, warmup=WARMUP, hooks=(), trace=0, out=print,
        tail=5000.0):
    net = build(variant, arrival_mean)
    times = []

    def record(t, consumed):
        born = next(iter(consumed.values()))[0].born
        if warmup <= born <= time - tail:
            times.append(t - born)

    net.transitions["Вивільнення (В2–склад)"].on_out = record
    net.watch("Зайняті роботи", robots_busy)
    r = net.simulate(time, warmup=warmup, hooks=hooks, trace=trace, out=out)
    tr, pl = r["transitions"], r["places"]
    hour = r["time"] / 3600
    result = {
        "throughput": tr["Вивільнення (В2–склад)"]["fired"] / hour,
        "arrivals": tr["Надходження"]["fired"] / hour if arrival_mean else None,
        "time_in_system": statistics.fmean(times) if times else None,
        "input_queue": pl["Деталь на вході"]["mean"],
        "input_queue_final": pl["Деталь на вході"]["final"],
        "robots_busy": r["watches"]["Зайняті роботи"]["mean"],
        "m1_slots": MACHINE_SLOTS - pl["Вільні місця В1"]["mean"],
        "m2_slots": MACHINE_SLOTS - pl["Вільні місця В2"]["mean"],
        "m1_processing": tr["Обробка на В1"]["mean_busy"],
        "m2_processing": tr["Обробка на В2"]["mean_busy"],
        "m1_done_waiting": pl["Оброблена на В1"]["mean"],
        "m2_done_waiting": pl["Оброблена на В2"]["mean"],
        "idle_moves": sum(v["mean_busy"] for k, v in tr.items() if k.startswith(("Виклик", "Повернення"))),
    }
    if variant == 1:
        result["robot_load"] = [1 - pl[f"Робот {k} вільний"]["mean"] for k in range(1, 4)]
    else:
        result["calls"] = {k: v["fired"] for k, v in tr.items() if k.startswith("Виклик")}
    return result


INTERVALS = (45.0, 50.0, 55.0, 60.0, 70.0, 80.0)
METRICS = [
    ("throughput", "пропускна здатність, деталей/год"),
    ("time_in_system", "середній час перебування деталі, с"),
    ("input_queue", "середня черга на вході"),
    ("robots_busy", "середня кількість зайнятих роботів"),
    ("m1_slots", "зайняті місця В1 (з резервом)"),
    ("m2_slots", "зайняті місця В2 (з резервом)"),
    ("m1_processing", "деталей в обробці на В1"),
    ("m2_processing", "деталей в обробці на В2"),
    ("m1_done_waiting", "оброблені на В1 чекають робота"),
    ("m2_done_waiting", "оброблені на В2 чекають робота"),
    ("idle_moves", "роботів у холостому ході"),
]


def far_call(state):
    """У стані мережі є вхід у перехід виклику робота, що стоїть не на початку шляху."""
    for line in state.splitlines():
        if line.strip().startswith("вхід у переходи"):
            for name in line.split(":", 1)[1].split(", "):
                if name.strip().startswith("Виклик робота"):
                    loc, route = name.strip().removeprefix("Виклик робота: ").split(" → ")
                    if not route.startswith(loc):
                        return True
    return False


def series(variant, arrival_mean, keys):
    runs = replicate(lambda: run(variant, arrival_mean), SEEDS)
    return {k: summary(r[k] for r in runs) for k in keys if runs[0][k] is not None}, runs


def main():
    for variant in (1, 2):
        with open(RESULTS / f"net2_fms_variant{variant}.json", "w", encoding="utf-8") as f:
            json.dump(build(variant).describe(), f, ensure_ascii=False, indent=1)
    print(f"ГВС: надходження exp({ARRIVAL_MEAN:g}) с, захоплення/вивільнення {HANDLE} с, В1 N(60; 10) с, "
          f"В2 exp(100) с, по {MACHINE_SLOTS} місця на верстаті; ходи вхід–В1 6 с, В1–В2 7 с, В2–склад 5 с\n")
    for variant, events in ((1, 9), (2, 9)):
        print(f"Трасування перших подій, варіант {variant}:\n")
        fun_rand.seed(4)
        run(variant, trace=events)
    print("Варіант 2, перший виклик робота, що стоїть не на початку шляху (холостий хід):\n")
    fun_rand.seed(4)
    lines = []
    run(2, time=3000.0, warmup=0.0, trace=10 ** 6, out=lines.append)
    first = next(i for i, s in enumerate(lines) if far_call(s))
    print("\n".join(lines[first - 1:first + 2]))

    for variant in (1, 2):
        fun_rand.seed(100 + variant)
        checked = []
        run(variant, hooks=[check_invariants, lambda n: checked.append(1)])
        print(f"Перевірка інваріантів, варіант {variant}: {len(checked)} подій, порушень немає")

    base, capacity, sweep = {}, {}, {}
    for variant in (1, 2):
        base[variant], runs = series(variant, ARRIVAL_MEAN, [k for k, _ in METRICS] + ["arrivals"])
        if variant == 1:
            base[variant]["robot_load"] = [summary(r["robot_load"][k] for r in runs) for k in range(3)]
        else:
            calls = {k: statistics.fmean(r["calls"][k] for r in runs) for k in runs[0]["calls"]}
            base[variant]["calls"] = calls
        capacity[variant], _ = series(variant, None, [k for k, _ in METRICS])
        sweep[variant] = {a: series(variant, a, ["throughput", "time_in_system", "input_queue"])[0]
                          for a in INTERVALS}

    print(f"\nРЕЗУЛЬТАТИ при надходженні кожні {ARRIVAL_MEAN:g} с ({len(SEEDS)} прогонів по {TIME:.0f} с, "
          f"розгін {WARMUP:.0f} с, 95 % довірчий інтервал):")
    print(f"  {'показник':40} {'варіант 1':>20} {'варіант 2':>20}")
    print(f"  {'інтенсивність надходження, деталей/год':40} {fmt(base[1]['arrivals'], 2):>20} "
          f"{fmt(base[2]['arrivals'], 2):>20}")
    for key, label in METRICS:
        print(f"  {label:40} {fmt(base[1][key], 2):>20} {fmt(base[2][key], 2):>20}")
    print("  завантаження роботів 1–3 (варіант 1): " + ", ".join(fmt(v, 3) for v in base[1]["robot_load"]))

    print("\nНеобмежене надходження деталей (пропускна здатність системи):")
    for key, label in METRICS:
        if key in capacity[1]:
            print(f"  {label:40} {fmt(capacity[1][key], 2):>20} {fmt(capacity[2][key], 2):>20}")

    print("\nЗалежність від середнього інтервалу надходження:")
    print(f"  {'інтервал, с':>11} {'вар. 1, дет./год':>17} {'вар. 2, дет./год':>17} {'вар. 1: час, с':>18} "
          f"{'вар. 2: час, с':>18}")
    for a in INTERVALS:
        s1, s2 = sweep[1][a], sweep[2][a]
        print(f"  {a:11.0f} {fmt(s1['throughput'], 2):>17} {fmt(s2['throughput'], 2):>17} "
              f"{fmt(s1['time_in_system'], 0):>18} {fmt(s2['time_in_system'], 0):>18}")

    calls = base[2]["calls"]
    total = sum(calls.values())
    print("\nВаріант 2, частка викликів робота за місцем, де він стояв:")
    for name, count in sorted(calls.items(), key=lambda x: -x[1]):
        if count:
            print(f"  {name:32} {count / total:.4f}")
    by_route = {}
    for name, count in calls.items():
        loc, route = name.removeprefix("Виклик робота: ").split(" → ")
        origin = next(r[1] for r in ROUTES if r[0] == route)
        n, d = by_route.get(route, (0.0, 0.0))
        by_route[route] = (n + count, d + count * distance(loc, origin))
    idle = {route: d / n for route, (n, d) in by_route.items()}
    print("  середній холостий хід до деталі, с: " + ", ".join(f"{r} {v:.2f}" for r, v in idle.items()))

    x = capacity[1]["throughput"][0] / 3600
    print("\nПеревірка (варіант 1, необмежене надходження): за пропускної здатності X = "
          f"{capacity[1]['throughput'][0]:.2f} дет./год")
    print(f"  в обробці на В1 X·60 = {x * 60:.3f} (імітація {capacity[1]['m1_processing'][0]:.3f}), "
          f"на В2 X·100 = {x * 100:.3f} (імітація {capacity[1]['m2_processing'][0]:.3f})")
    cycle = [2 * 8 + 2 * distance(r[1], r[2]) for r in ROUTES]
    print(f"  зайняті роботи X·(28 + 30 + 26) = {x * sum(cycle):.3f} "
          f"(імітація {capacity[1]['robots_busy'][0]:.3f})")

    with open(RESULTS / "task2.json", "w", encoding="utf-8") as f:
        json.dump({"base": base, "capacity": capacity, "sweep": {v: {str(a): s for a, s in d.items()}
                                                                 for v, d in sweep.items()},
                   "idle": idle, "metrics": METRICS}, f, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
