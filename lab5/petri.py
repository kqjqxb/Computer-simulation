"""Стохастична часова мережа Петрі.

Позиції містять маркери, переходи мають часову затримку, пріоритет і ймовірність спрацювання.
Переходи багатоканальні: у переході одночасно може перебувати кілька маркерів, для кожного з яких
запам'ятовується момент виходу. Вхідна дуга може бути інформаційною – вона перевіряє наявність маркерів
у позиції, але не забирає їх. Кожна дуга має кратність.

Маркер зберігає момент свого створення. Вихідна дуга з параметром carry переносить у вихідну позицію
маркери, взяті з указаної вхідної позиції, інші вихідні дуги створюють нові маркери. Так моделюється
рух вимоги мережею і вимірюється час між подіями з однією й тією самою вимогою. Позиція видає маркери
в порядку надходження, а маркери, що виходять з переходів одночасно, виводяться в порядку їх входу
в переходи.
"""
import heapq
import math
from collections import deque

import fun_rand


class Delay:
    """Затримка переходу: const – стала mean, exp – експоненційна із середнім mean,
    unif – рівномірна на [mean − dev, mean + dev], norm – нормальна (mean, dev), обмежена знизу нулем."""

    KINDS = ("const", "exp", "unif", "norm")

    def __init__(self, kind="const", mean=0.0, dev=0.0):
        if kind not in self.KINDS:
            raise ValueError(kind)
        self.kind, self.mean, self.dev = kind, float(mean), float(dev)

    def __call__(self):
        if self.kind == "exp":
            return fun_rand.exp(self.mean)
        if self.kind == "unif":
            return fun_rand.unif(self.mean - self.dev, self.mean + self.dev)
        if self.kind == "norm":
            return max(0.0, fun_rand.norm(self.mean, self.dev))
        return self.mean

    def __str__(self):
        m, d = f"{self.mean:g}", f"{self.dev:g}"
        return {"const": m, "exp": f"exp({m})", "unif": f"{m} ± {d}", "norm": f"N({m}; {d})"}[self.kind]

    def spec(self):
        return {"distribution": self.kind, "mean": self.mean, "deviation": self.dev}


def const(mean):
    return Delay("const", mean)


def exp(mean):
    return Delay("exp", mean)


def unif(mean, dev):
    return Delay("unif", mean, dev)


def norm(mean, dev):
    return Delay("norm", mean, dev)


class Token:
    __slots__ = ("born",)

    def __init__(self, born):
        self.born = born


class Place:
    def __init__(self, name, mark=0):
        self.name, self.initial = name, mark
        self.tokens = deque(Token(0.0) for _ in range(mark))
        self.dependents = []  # переходи, для яких позиція є вхідною
        self.area = 0.0
        self.last = self.start = 0.0
        self.max_mark = self.min_mark = mark

    @property
    def mark(self):
        return len(self.tokens)

    def _touch(self, t):
        self.area += len(self.tokens) * (t - self.last)
        self.last = t

    def put(self, t, tokens):
        self._touch(t)
        self.tokens.extend(tokens)
        self.max_mark = max(self.max_mark, len(self.tokens))

    def take(self, t, n):
        self._touch(t)
        taken = [self.tokens.popleft() for _ in range(n)]
        self.min_mark = min(self.min_mark, len(self.tokens))
        return taken

    def reset_statistics(self, t):
        self._touch(t)
        self.area, self.start = 0.0, t
        self.max_mark = self.min_mark = len(self.tokens)

    def mean(self, t):
        self._touch(t)
        return self.area / (t - self.start) if t > self.start else float(len(self.tokens))


class Transition:
    def __init__(self, name, delay, priority=0, probability=1.0, channels=math.inf):
        self.name = name
        self.delay = delay if isinstance(delay, Delay) else const(delay)
        self.priority, self.probability, self.channels = priority, probability, channels
        self.inputs = []  # (позиція, кратність, інформаційна)
        self.outputs = []  # (позиція, кратність, ім'я вхідної позиції, з якої переносяться маркери)
        self.buffer = []  # купа (момент виходу, номер входу, {позиція: взяті маркери})
        self.on_out = None  # on_out(t, consumed) – спостерігач виходу маркера з переходу
        self.entered = self.fired = 0
        self.area = 0.0
        self.last = self.start = 0.0
        self.max_busy = 0

    def enabled(self):
        if len(self.buffer) >= self.channels:
            return False
        return all(len(p.tokens) >= n for p, n, _ in self.inputs)

    @property
    def next_time(self):
        return self.buffer[0][0] if self.buffer else math.inf

    def _touch(self, t):
        self.area += len(self.buffer) * (t - self.last)
        self.last = t

    def act_in(self, t, number):
        consumed = {p.name: p.take(t, n) for p, n, info in self.inputs if not info}
        self._touch(t)
        heapq.heappush(self.buffer, (t + self.delay(), number, consumed))
        self.entered += 1
        self.max_busy = max(self.max_busy, len(self.buffer))

    def act_out(self, t):
        self._touch(t)
        _, _, consumed = heapq.heappop(self.buffer)
        for place, n, carry in self.outputs:
            moved = consumed.get(carry, [])[:n]
            place.put(t, moved + [Token(t) for _ in range(n - len(moved))])
        self.fired += 1
        if self.on_out:
            self.on_out(t, consumed)

    def reset_statistics(self, t):
        self._touch(t)
        self.area, self.start = 0.0, t
        self.entered = self.fired = 0
        self.max_busy = len(self.buffer)

    def mean_busy(self, t):
        self._touch(t)
        return self.area / (t - self.start) if t > self.start else float(len(self.buffer))


class Watch:
    """Середнє за часом довільної функції стану мережі та її розподіл за часом."""

    def __init__(self, fn):
        self.fn = fn
        self.area = self.span = 0.0
        self.hist = {}

    def add(self, net, dt):
        if dt > 0:
            value = self.fn(net)
            self.area += value * dt
            self.span += dt
            self.hist[value] = self.hist.get(value, 0.0) + dt

    def reset_statistics(self):
        self.area = self.span = 0.0
        self.hist = {}


def _grouped(names):
    """Список імен з об'єднанням однакових сусідніх: «Доставка ×72»."""
    groups = []
    for name in names:
        if groups and groups[-1][0] == name:
            groups[-1][1] += 1
        else:
            groups.append([name, 1])
    return ", ".join(name if n == 1 else f"{name} ×{n}" for name, n in groups)


class PetriNet:
    def __init__(self, name):
        self.name = name
        self.places, self.transitions, self.watches = {}, {}, {}
        self.time = 0.0
        self.stats_start = 0.0
        self.events = 0

    def place(self, name, mark=0):
        self.places[name] = Place(name, mark)
        return self.places[name]

    def transition(self, name, delay=0.0, inputs=None, outputs=None, info=None, carry=None, priority=0,
                   probability=1.0, channels=math.inf):
        """inputs, info, outputs – словники {позиція: кратність дуги}; carry – {вихідна позиція: вхідна}."""
        tr = Transition(name, delay, priority, probability, channels)
        carry = carry or {}
        for arcs, is_info in ((inputs or {}, False), (info or {}, True)):
            for pname, n in arcs.items():
                place = self.places[pname]
                tr.inputs.append((place, n, is_info))
                place.dependents.append(tr)
        for pname, n in (outputs or {}).items():
            tr.outputs.append((self.places[pname], n, carry.get(pname)))
        self.transitions[name] = tr
        return tr

    def watch(self, name, fn):
        self.watches[name] = Watch(fn)

    def mark(self, name):
        return len(self.places[name].tokens)

    def _fire_inputs(self, t, dirty, enabled, entered):
        """Вхід маркерів у переходи: поки є дозволені переходи, спрацьовує перехід з найвищим
        пріоритетом; конфлікт переходів з однаковим пріоритетом розв'язується за ймовірностями."""
        while True:
            for tr in dirty:
                if tr.enabled():
                    enabled.add(tr)
                else:
                    enabled.discard(tr)
            dirty.clear()
            if not enabled:
                return
            top = max(tr.priority for tr in enabled)
            group = sorted((tr for tr in enabled if tr.priority == top), key=self._order.get)
            chosen = group[0]
            if len(group) > 1:
                r = fun_rand.uniform01() * sum(tr.probability for tr in group)
                for chosen in group:
                    r -= chosen.probability
                    if r < 0:
                        break
            self._number += 1
            chosen.act_in(t, self._number)
            entered.append(chosen.name)
            dirty.add(chosen)
            for place, _, info in chosen.inputs:
                if not info:
                    dirty.update(place.dependents)

    def _advance(self, t_new, warmup):
        t_old = self.time
        if t_old < warmup <= t_new and not self._reset_done:
            for w in self.watches.values():
                w.add(self, warmup - t_old)
            self.reset_statistics(warmup)
            t_old = warmup
        for w in self.watches.values():
            w.add(self, t_new - t_old)
        self.time = t_new

    def reset_statistics(self, t):
        self._reset_done = True
        self.stats_start = t
        for obj in (*self.places.values(), *self.transitions.values()):
            obj.reset_statistics(t)
        for w in self.watches.values():
            w.reset_statistics()

    def simulate(self, time, warmup=0.0, trace=0, hooks=(), out=print):
        """Імітація до моменту time. hooks – функції hook(net), що викликаються після кожної події;
        trace – кількість перших подій, для яких виводиться стан мережі."""
        self._order = {tr: i for i, tr in enumerate(self.transitions.values())}
        self._number = 0
        self._reset_done = warmup <= 0.0
        dirty, enabled = set(self.transitions.values()), set()
        entered, fired = [], []
        while True:
            self._fire_inputs(self.time, dirty, enabled, entered)
            for hook in hooks:
                hook(self)
            if self.events < trace:
                self._print_state(fired, entered, out)
            entered, fired = [], []
            tnext = min(tr.next_time for tr in self.transitions.values())
            if tnext > time:
                self._advance(time, warmup)
                break
            self._advance(tnext, warmup)
            ready = [tr for tr in self.transitions.values() if tr.next_time == tnext]
            while ready:
                # одночасні виходи – у порядку входу маркерів у переходи
                tr = min(ready, key=lambda x: x.buffer[0][1])
                tr.act_out(tnext)
                fired.append(tr.name)
                dirty.add(tr)
                for place, _, _ in tr.outputs:
                    dirty.update(place.dependents)
                if tr.next_time != tnext:
                    ready.remove(tr)
            self.events += 1
        return self.results()

    def _print_state(self, fired, entered, out):
        lines = [f"t = {self.time:.4f}"]
        if fired:
            lines.append("  вихід з переходів: " + _grouped(fired))
        if entered:
            lines.append("  вхід у переходи:   " + _grouped(entered))
        marks = [f"{p.name}={p.mark}" for p in self.places.values() if p.mark]
        busy = [f"{tr.name}={len(tr.buffer)} (вихід {tr.next_time:.3f})" for tr in self.transitions.values()
                if tr.buffer]
        lines.append("  маркування: " + ", ".join(marks))
        lines.append("  у переходах: " + ", ".join(busy))
        out("\n".join(lines) + "\n")

    def results(self):
        t = self.time
        return {
            "time": t - self.stats_start,
            "places": {p.name: {"mean": p.mean(t), "min": p.min_mark, "max": p.max_mark, "final": p.mark}
                       for p in self.places.values()},
            "transitions": {tr.name: {"fired": tr.fired, "entered": tr.entered, "mean_busy": tr.mean_busy(t),
                                      "max_busy": tr.max_busy} for tr in self.transitions.values()},
            "watches": {name: {"mean": w.area / w.span if w.span else math.nan,
                               "distribution": {v: s / w.span for v, s in sorted(w.hist.items())}}
                        for name, w in self.watches.items()},
        }

    def describe(self):
        """Опис структури мережі (для відтворення в інших реалізаціях)."""
        return {
            "name": self.name,
            "places": [{"name": p.name, "mark": p.initial} for p in self.places.values()],
            "transitions": [{
                "name": tr.name, **tr.delay.spec(), "priority": tr.priority, "probability": tr.probability,
                "channels": None if tr.channels == math.inf else tr.channels,
                "inputs": [{"place": p.name, "n": n} for p, n, info in tr.inputs if not info],
                "info_inputs": [{"place": p.name, "n": n} for p, n, info in tr.inputs if info],
                "outputs": [{"place": p.name, "n": n, **({"carry": c} if c else {})}
                            for p, n, c in tr.outputs],
            } for tr in self.transitions.values()],
        }
