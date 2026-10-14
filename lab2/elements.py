import math

from element import Element


class Create(Element):
    def __init__(self, delay_mean, **kwargs):
        super().__init__(delay_mean=delay_mean, **kwargs)
        self.tnext = 0.0  # імітація розпочнеться з події Create

    def out_act(self):
        super().out_act()
        self.tnext = self.tcurr + self.get_delay()
        target = self.next_element()
        if target is not None:
            target.in_act()


class Dispose(Element):
    def __init__(self, name="DISPOSE"):
        super().__init__(name=name)

    def in_act(self):
        self.quantity += 1


class Process(Element):
    """Пристрій обслуговування з чергою обмеженої довжини та кількома ідентичними каналами."""

    def __init__(self, delay_mean, channels=1, maxqueue=math.inf, **kwargs):
        super().__init__(delay_mean=delay_mean, **kwargs)
        self.channels = channels
        self.channel_tnext = [math.inf] * channels
        self.maxqueue = maxqueue
        self.queue = 0
        self.failure = 0
        self.arrivals = 0
        self.queue_area = 0.0
        self.busy_area = 0.0

    @property
    def busy(self):
        return sum(t != math.inf for t in self.channel_tnext)

    def _refresh(self):
        self.tnext = min(self.channel_tnext)
        self.state = self.busy

    def in_act(self):
        self.arrivals += 1
        if self.busy < self.channels:
            free = self.channel_tnext.index(math.inf)
            self.channel_tnext[free] = self.tcurr + self.get_delay()
        elif self.queue < self.maxqueue:
            self.queue += 1
        else:
            self.failure += 1
        self._refresh()

    def out_act(self):
        finished = [i for i, t in enumerate(self.channel_tnext) if t == self.tcurr]
        for i in finished:
            self.quantity += 1
            self.channel_tnext[i] = math.inf
            if self.queue > 0:
                self.queue -= 1
                self.channel_tnext[i] = self.tcurr + self.get_delay()
        self._refresh()
        for _ in finished:
            target = self.next_element()
            if target is not None:
                target.in_act()

    def do_statistics(self, delta):
        self.queue_area += self.queue * delta
        self.busy_area += self.busy * delta

    def mean_queue(self, total_time):
        return self.queue_area / total_time

    def mean_load(self, total_time):
        """Середнє завантаження: частка часу, протягом якої канали пристрою зайняті."""
        return self.busy_area / (total_time * self.channels)

    def failure_probability(self):
        return self.failure / self.arrivals if self.arrivals else 0.0

    def print_info(self):
        print(f"{self.name} state = {self.state} queue = {self.queue} quantity = {self.quantity} "
              f"failure = {self.failure} tnext = {self.tnext:.4f}")
