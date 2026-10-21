# Комп'ютерний практикум №6

Застосування алгоритму стохастичної мережі Петрі для реалізації моделей дискретно-подійних систем.
Моделі реалізовано на Java з бібліотекою [PetriObjModelPaint](https://github.com/StetsenkoInna/PetriObjModelPaint)
(release 2.3.0): мережі будуються з класів `PetriP`, `PetriT`, `ArcIn`, `ArcOut`, `PetriNet`, імітація виконується
методами `input()` / `output()` класу `PetriSim`.

| Файл | Призначення |
|---|---|
| `src/lab6/NetBuilder.java` | побудова мережі Петрі з координатами елементів |
| `src/lab6/Simulation.java` | імітація алгоритмом `PetriSim`, статистика з розгоном, трасування |
| `src/lab6/Conveyor.java` | завдання 1: конвеєрна система (задача 1 ЛР5) |
| `src/lab6/Inventory.java` | завдання 2: управління запасами (задача 4 ЛР5) |
| `src/lab6/Servers.java` | завдання 3: два сервери зі спільною базою даних |
| `src/lab6/NetExport.java` | збереження мереж у .png, .pnml, .pns і java-метод засобами редактора |
| `src/lab6/Stats.java`, `Queueing.java`, `Checks.java`, `Json.java`, `Output.java`, `Main.java` | допоміжні класи |
| `compare_lab5.py` | порівняння мереж і результатів з практикумом 5 |
| `plots.py` | графіки для завдання 3 |
| `nets/` | пакет файлів мереж: текст завдання, .png, .pnml, .pns, java-метод |
| `results/` | вивід програм |
| `ІП-33_Ломакін_ЛР6.pdf` | звіт |

Потрібна Java 23+; `run.sh` завантажує бібліотеку в `lib/`, компілює і запускає всі моделі:

```bash
cd lab6
./run.sh                 # усі завдання, порівняння з ЛР5, графіки
./run.sh servers         # окреме завдання: conveyor | inventory | servers | export
```
