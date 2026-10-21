# Pixel-art mod icon on a 32x32 grid: a zombie's head in the dark, its eyes lit, listening to sound waves
# coming from the right. Night palette: deep blue-black, zombie green, amber for the sound.
# Usage: python3 docs/make-icon.py icon.png common/src/main/resources/assets/mobsenses/icon.png (needs Pillow).
# Writes the 512px store icon and the 128px icon packed into the mod.
import random, sys
from PIL import Image

N = 32
BG, BG_GLOW, BORDER = (17, 19, 26), (27, 31, 41), (48, 54, 66)
GREEN = [(74, 128, 58), (86, 143, 66), (66, 114, 52), (95, 156, 72)]
GREEN_DARK, SOCKET, MOUTH = (46, 82, 38), (20, 30, 18), (28, 40, 24)
EYE, EYE_GLOW = (255, 196, 76), (255, 150, 40)
WAVE = [(255, 190, 80), (226, 150, 60), (170, 108, 46)]

img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
px = img.load()

def inside(x, y, inset):
    r = 5 - inset
    lo, hi = inset, N - 1 - inset
    if not (lo <= x <= hi and lo <= y <= hi):
        return False
    cx = min(max(x, lo + r), hi - r)
    cy = min(max(y, lo + r), hi - r)
    return (x - cx) ** 2 + (y - cy) ** 2 <= r * r + 1

# Rounded square, a faint glow behind the head
for y in range(N):
    for x in range(N):
        if inside(x, y, 0):
            px[x, y] = BORDER + (255,)
        if inside(x, y, 1):
            d = ((x - 11) ** 2 + (y - 16) ** 2) ** 0.5
            t = max(0.0, 1 - d / 15)
            px[x, y] = tuple(int(BG[i] + (BG_GLOW[i] - BG[i]) * t) for i in range(3)) + (255,)

def put(x, y, c):
    px[x, y] = c + (255,)

# The head: a block of mottled green, like Minecraft's zombie, darker at the bottom edge
random.seed(4)
x0, y0, size = 4, 8, 15
for y in range(y0, y0 + size):
    for x in range(x0, x0 + size):
        c = random.choice(GREEN)
        if y == y0 + size - 1 or x == x0:
            c = GREEN_DARK
        put(x, y, c)
# Eye sockets with lit eyes, looking right (towards the sound)
for ex in (x0 + 3, x0 + 9):
    for y in range(y0 + 5, y0 + 8):
        for x in range(ex, ex + 3):
            put(x, y, SOCKET)
    put(ex + 2, y0 + 6, EYE)
    put(ex + 1, y0 + 6, EYE_GLOW)
# Mouth
for x in range(x0 + 5, x0 + 10):
    put(x, y0 + 11, MOUTH)
for x in (x0 + 4, x0 + 10):
    put(x, y0 + 12, MOUTH)

# Sound waves from the right: hand-placed pixel arcs (a column with bent ends), brightest nearest the head
def wave(x, top, bottom, bends, color):
    for y in range(top, bottom + 1):
        put(x, y, color)
    for i, (above, below) in enumerate(bends, start=1):
        for y in above + below:
            put(x + i, y, color)
wave(21, 14, 17, [((13,), (18,))], WAVE[0])
wave(24, 12, 19, [((10, 11), (20, 21)), ((9,), (22,))], WAVE[1])
wave(27, 11, 20, [((8, 9, 10), (21, 22, 23)), ((7,), (24,))], WAVE[2])

for size, out in ((512, sys.argv[1]), (128, sys.argv[2])):
    img.resize((size, size), Image.NEAREST).save(out, "PNG", optimize=True)
