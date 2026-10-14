import math
import random

_rng = random.Random()


def seed(value):
    _rng.seed(value)


def exp(time_mean):
    a = 0.0
    while a == 0.0:
        a = _rng.random()
    return -time_mean * math.log(a)


def unif(time_min, time_max):
    a = 0.0
    while a == 0.0:
        a = _rng.random()
    return time_min + a * (time_max - time_min)


def norm(time_mean, time_deviation):
    return _rng.gauss(time_mean, time_deviation)


def erlang(time_mean, k):
    product = 1.0
    for _ in range(k):
        product *= 1.0 - _rng.random()
    return -time_mean / k * math.log(product)


def uniform01():
    return _rng.random()
