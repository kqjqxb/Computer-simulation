import math
import statistics

from elements import Create, Dispose, Process


class Model:
    """Імітація за принципом найближчої події.

    hooks – функції hook(model), які викликаються після кожної події (правила, що змінюють стан кількох
    елементів одночасно). warmup – тривалість розгону: накопичена до цього моменту статистика скидається.
    """

    def __init__(self, elements, hooks=()):
        self.elements = elements
        self.hooks = list(hooks)
        self.tcurr = 0.0
        self.stats_start = 0.0
        self.in_system_area = 0.0

    def processes(self):
        return [e for e in self.elements if isinstance(e, Process)]

    def in_system(self):
        return sum(len(p.queue) + p.busy for p in self.processes())

    def _accumulate(self, delta):
        for e in self.elements:
            e.do_statistics(delta)
        self.in_system_area += self.in_system() * delta

    def _reset(self, moment):
        self.stats_start = moment
        self.in_system_area = 0.0
        for e in self.elements:
            e.reset_statistics()
            if isinstance(e, Dispose):
                e.stats_start = moment

    def simulate(self, time, warmup=0.0, trace_events=0):
        events = 0
        while self.tcurr < time:
            tnext = min(e.tnext for e in self.elements)
            t_stop = min(tnext, time)
            if self.tcurr < warmup <= t_stop:
                self._accumulate(warmup - self.tcurr)
                self._reset(warmup)
                self._accumulate(t_stop - warmup)
            else:
                self._accumulate(t_stop - self.tcurr)
            if tnext > time:
                self.tcurr = time
                break

            self.tcurr = tnext
            for e in self.elements:
                e.tcurr = self.tcurr
            active = [e for e in self.elements if e.tnext == self.tcurr]
            for e in active:
                e.out_act()
            for hook in self.hooks:
                hook(self)

            events += 1
            if events <= trace_events:
                names = ", ".join(e.name for e in active)
                print(f"\nIt's time for event in {names}, time = {self.tcurr:.4f}")
                for e in self.elements:
                    e.print_info()
        for e in self.elements:
            e.tcurr = self.tcurr

    def results(self):
        total = self.tcurr - self.stats_start
        disposes = [e for e in self.elements if isinstance(e, Dispose)]
        times = [t for d in disposes for t in d.all_times()]
        exits = sorted(t for d in disposes for t in d.exit_times)
        out = {
            "time": total,
            "created": sum(e.quantity for e in self.elements if isinstance(e, Create)),
            "disposed": sum(d.quantity for d in disposes),
            "failures": sum(p.failure for p in self.processes()),
            "mean_in_system": self.in_system_area / total,
            "mean_time_in_system": statistics.mean(times) if times else math.nan,
            "mean_exit_interval": (exits[-1] - exits[0]) / (len(exits) - 1) if len(exits) > 1 else math.nan,
            "processes": {},
            "groups": {},
        }
        for p in self.processes():
            out["processes"][p.name] = {
                "arrivals": p.arrivals, "served": p.quantity, "failure": p.failure,
                "mean_queue": p.mean_queue(total), "mean_load": p.mean_load(total),
                "mean_wait": p.mean_wait(),
                "wait_by_kind": {k: v[0] / v[1] for k, v in p.wait.items() if v[1]},
            }
        for d in disposes:
            for group, values in d.times.items():
                out["groups"][f"{d.name}: {group}"] = statistics.mean(values)
        return out
