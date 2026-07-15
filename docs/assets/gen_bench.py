#!/usr/bin/env python3
"""
Regenerates docs/assets/bench.svg.

The numbers below are PLACEHOLDERS. Replace them with real medians from your
JMH / benchmark run, then:

    python3 docs/assets/gen_bench.py

Each entry is (operation, median in microseconds, the complexity bound this
operation is claimed to have in docs/COMPLEXITY.md).
"""

RESULTS = [
    ("object write (1 KiB blob)", 41.0, "O(n) in content size"),
    ("object read (warm)", 12.0, "O(n) in content size"),
    ("ref resolve", 0.8, "O(1)"),
    ("merge-base (10k-commit DAG)", 310.0, "O(V+E), gen-number pruned"),
    ("diff, 1k lines (Myers)", 1900.0, "O(ND)"),
    ("three-way merge", 2400.0, "O(ND) per file"),
    ("packfile encode (1k objects)", 88000.0, "O(n log n) window sort"),
    ("trigram query", 240.0, "O(posting list)"),
]

W, PAD_L, PAD_R = 1200, 300, 210
ROW_H, TOP = 40, 96
H = TOP + ROW_H * len(RESULTS) + 76
TRACK = W - PAD_L - PAD_R
MAXV = max(v for _, v, _ in RESULTS)


def fmt(us: float) -> str:
    if us >= 1000:
        return f"{us / 1000:.1f} ms".replace(".0 ", " ")
    if us < 1:
        return f"{us * 1000:.0f} ns"
    return f"{us:.0f} \u00b5s"


def bar_w(v: float) -> float:
    # log scale: these operations span five orders of magnitude, and a linear
    # scale would render every row except the packfile as a hairline.
    import math
    lo, hi = math.log10(0.5), math.log10(MAXV * 1.15)
    return max(6.0, TRACK * (math.log10(v) - lo) / (hi - lo))


# Rough monospace advance per class, used only to decide whether a value label
# placed after its bar would run into the right-anchored bound label - not for
# precise typesetting, just enough margin to catch real collisions (the
# packfile-encode row: its bar is the longest of the eight, and its bound text
# is one of the longest, so "after the bar" and "before the bound" overlapped).
VAL_CHAR_W, BOUND_CHAR_W, SAFETY = 7.8, 6.55, 16

rows = []
for i, (name, v, bound) in enumerate(RESULTS):
    y = TOP + i * ROW_H
    w = bar_w(v)
    value_text = fmt(v)
    bar_end = PAD_L + w
    value_end = bar_end + 12 + len(value_text) * VAL_CHAR_W
    bound_start = (W - 48) - len(bound) * BOUND_CHAR_W

    if value_end + SAFETY > bound_start:
        # Too long to sit between the bar and the bound label - draw it inside
        # the bar's own (already-earned) width instead, right-aligned, in dark
        # text for contrast against the gradient's light end.
        value_label = f'<text class="mono" x="{bar_end - 10:.1f}" y="{y + 19}" text-anchor="end" font-size="13" fill="#12161B">{value_text}</text>'
    else:
        value_label = f'<text class="mono val" x="{bar_end + 12:.1f}" y="{y + 19}">{value_text}</text>'

    rows.append(f'''  <text class="mono op" x="48" y="{y + 19}">{name}</text>
  <rect x="{PAD_L}" y="{y + 6}" width="{TRACK}" height="18" rx="3" fill="#171E22"/>
  <rect class="bar b{i}" x="{PAD_L}" y="{y + 6}" width="{w:.1f}" height="18" rx="3" fill="url(#g)"/>
  {value_label}
  <text class="mono bound" x="{W - 48}" y="{y + 19}" text-anchor="end">{bound}</text>''')

delays = "\n".join(
    f"    .b{i} {{ animation-delay: {0.06 * i:.2f}s }}" for i in range(len(RESULTS))
)

svg = f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W}" height="{H}" role="img" aria-label="Benchmark medians per operation, log scale, each annotated with its claimed complexity bound">
  <title>Benchmarks</title>
  <defs>
    <linearGradient id="g" x1="0" y1="0" x2="1" y2="0">
      <stop offset="0%" stop-color="#5F7A4E"/>
      <stop offset="100%" stop-color="#9BB380"/>
    </linearGradient>
  </defs>
  <style>
    .mono {{ font-family: ui-monospace, "SF Mono", SFMono-Regular, Menlo, Consolas, "DejaVu Sans Mono", monospace; }}
    .title {{ font-size: 13px; letter-spacing: 3px; fill: #7C8894; }}
    .op {{ font-size: 13px; fill: #A9B4BD; }}
    .val {{ font-size: 13px; fill: #E4E9E6; }}
    .bound {{ font-size: 11.5px; fill: #B76B45; }}
    .foot {{ font-size: 12px; fill: #7C8894; }}
    .bar {{ animation: grow .9s cubic-bezier(.2,.75,.3,1) backwards; transform-origin: {PAD_L}px 0; }}
{delays}
    @keyframes grow {{ from {{ transform: scaleX(0) }} to {{ transform: scaleX(1) }} }}
    @media (prefers-reduced-motion: reduce) {{ .bar {{ animation: none }} }}
  </style>
  <rect width="{W}" height="{H}" rx="14" fill="#12161B"/>
  <rect x=".5" y=".5" width="{W - 1}" height="{H - 1}" rx="14" fill="none" stroke="#2A323B"/>
  <text class="mono title" x="48" y="42">MEDIAN PER OPERATION &#183; LOG SCALE</text>
  <text class="mono bound" x="{W - 48}" y="42" text-anchor="end">CLAIMED BOUND</text>
  <line x1="48" y1="64" x2="{W - 48}" y2="64" stroke="#2A323B"/>
{chr(10).join(rows)}
  <line x1="48" y1="{H - 56}" x2="{W - 48}" y2="{H - 56}" stroke="#2A323B"/>
  <text class="mono foot" x="48" y="{H - 26}">Every bound on the right is cited to the method that implements it in docs/COMPLEXITY.md, and checked against the code, not assumed.</text>
</svg>
'''

with open(__file__.rsplit("/", 1)[0] + "/bench.svg", "w") as f:
    f.write(svg)
print("wrote bench.svg")
