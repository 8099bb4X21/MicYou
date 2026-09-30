import re
import io

path = r'E:/OpenCode/test/micyou-fork/.planning/2026-09-28-remote-key/ui.xml'
with io.open(path, 'r', encoding='utf-8') as f:
    src = f.read()

nodes = re.findall(r'<node .*?/>', src)
print('total nodes:', len(nodes))

targets = [(82, 905, 998, 1101), (323, 1443, 758, 1878), (41, 1390, 1039, 1930)]


def parse_bounds(s):
    m = re.search(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', s)
    return tuple(map(int, m.groups())) if m else None


def overlap(a, b):
    return a[0] < b[2] and a[2] > b[0] and a[1] < b[3] and a[3] > b[1]


def area(b):
    return (b[2] - b[0]) * (b[3] - b[1])


for t in targets:
    print('==== target', t, '====')
    cands = []
    for n in nodes:
        b = parse_bounds(n)
        if b and overlap(b, t):
            cands.append((area(b), b, n))
    cands.sort(key=lambda x: abs(x[0] - area(t)))
    for _, b, n in cands[:6]:
        cls = re.search(r'class="([^"]+)"', n)
        rid = re.search(r'resource-id="([^"]+)"', n)
        txt = re.search(r'text="([^"]+)"', n)
        print(b, (cls.group(1).split('.')[-1] if cls else '?'),
              '| id=' + (rid.group(1) if rid else '-'),
              '| text=' + (txt.group(1)[:24] if txt else '-'))
