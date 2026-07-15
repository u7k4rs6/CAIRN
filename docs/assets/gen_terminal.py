#!/usr/bin/env python3
"""Regenerates docs/assets/terminal.svg (the animated quickstart terminal)."""

CH = 8.4          # character advance at 14px in a typical mono face
LH = 26           # line height
X0, Y0 = 34, 92   # first line baseline
LOOP = 15.0       # seconds

GREEN, DIM, FG, OX, BLUE = "#9BB380", "#66717C", "#D7DEDA", "#B76B45", "#7FA8C4"

# (kind, text, color, start_time)  kind: "cmd" types out, "out" fades in
LINES = [
    ("cmd", "docker compose up -d", FG, 0.4),
    ("out", "  \u2714 Container cairn-db-1   Started", GREEN, 1.7),
    ("out", "  \u2714 Container cairn-api-1  Started", GREEN, 1.95),
    ("out", "  \u2714 Container cairn-web-1  Started", GREEN, 2.2),
    ("cmd", 'docker compose logs api | grep -A3 "Cairn dev data seeded"', FG, 3.0),
    ("out", "api-1  | Cairn dev data seeded", DIM, 4.9),
    ("out", "api-1  |   repo   acme/demo (public)", DIM, 5.1),
    ("out", "api-1  |   pull   http://localhost:3000/acme/demo", DIM, 5.3),
    ("out", "api-1  |   token  cairn_pat_7f3d9c\u2026", OX, 5.5),
    ("cmd", "curl -u acme:$TOKEN localhost:8080/api/repos/acme/demo/commits/main", FG, 6.3),
    ("out", '[ { "sha": "4f0a1b9c8e2d7a3f", "message": "Add object store", \u2026 },', BLUE, 8.5),
    ("out", '  { "sha": "8c21ee0b4d19f5aa", "message": "Myers diff", \u2026 } ]', BLUE, 8.7),
]

W = 1200
H = Y0 + LH * len(LINES) + 34

body, styles = [], []

for i, (kind, text, color, t) in enumerate(LINES):
    y = Y0 + i * LH
    esc = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    x = X0
    if kind == "cmd":
        body.append(f'  <text class="mono pr l{i}" x="{X0}" y="{y}">$</text>')
        x = X0 + CH * 2
    body.append(f'  <text class="mono l{i}" x="{x}" y="{y}" fill="{color}">{esc}</text>')

    if kind == "cmd":
        dur = max(0.5, len(text) * 0.028)
        w = len(text) * CH + 12
        body.append(
            f'  <rect class="cover c{i}" x="{x - 2}" y="{y - 15}" '
            f'width="{w:.0f}" height="20" fill="#0E1216"/>'
        )
        # prompt appears, then the cover retracts left-to-right in character steps
        a, b = t / LOOP * 100, (t + 0.05) / LOOP * 100
        styles.append(
            f"    .l{i} {{ animation: fade{i} {LOOP}s steps(1,end) infinite }}\n"
            f"    @keyframes fade{i} {{ 0%,{a:.2f}% {{ opacity:0 }} {b:.2f}%,97% {{ opacity:1 }} 100% {{ opacity:0 }} }}"
        )
        s, e = (t + 0.05) / LOOP * 100, (t + 0.05 + dur) / LOOP * 100
        styles.append(
            f"    .c{i} {{ animation: type{i} {LOOP}s steps({len(text)},end) infinite;"
            f" transform-origin: {x + w - 2:.0f}px 0 }}\n"
            f"    @keyframes type{i} {{ 0%,{s:.2f}% {{ transform:scaleX(1) }}"
            f" {e:.2f}%,97% {{ transform:scaleX(0) }} 100% {{ transform:scaleX(1) }} }}"
        )
    else:
        a, b = t / LOOP * 100, (t + 0.18) / LOOP * 100
        styles.append(
            f"    .l{i} {{ animation: fade{i} {LOOP}s ease-out infinite }}\n"
            f"    @keyframes fade{i} {{ 0%,{a:.2f}% {{ opacity:0; transform:translateX(-4px) }}"
            f" {b:.2f}%,97% {{ opacity:1; transform:translateX(0) }} 100% {{ opacity:0 }} }}"
        )

cursor_y = Y0 + LH * len(LINES) - 15
styles.append(
    f"    .cursor {{ animation: show {LOOP}s steps(1,end) infinite, blink 1.05s steps(1,end) infinite }}\n"
    f"    @keyframes show {{ 0%,{9.2 / LOOP * 100:.2f}% {{ opacity:0 }} {9.3 / LOOP * 100:.2f}%,97% {{ opacity:1 }} 100% {{ opacity:0 }} }}\n"
    "    @keyframes blink { 0%,50% { fill-opacity:1 } 51%,100% { fill-opacity:0 } }"
)

reduced = ", ".join(f".l{i}" for i in range(len(LINES)))
covers = ", ".join(f".c{i}" for i, (k, *_) in enumerate(LINES) if k == "cmd")

svg = f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W}" height="{H}" role="img" aria-label="Terminal: docker compose up starts the database, API and web containers, the seeded access token is printed in the API logs, and a curl against the commits endpoint returns real commit objects">
  <title>Quickstart</title>
  <style>
    .mono {{ font-family: ui-monospace, "SF Mono", SFMono-Regular, Menlo, Consolas, "DejaVu Sans Mono", monospace; font-size: 14px; }}
    .pr {{ fill: {GREEN}; }}
    .chrome {{ font-size: 12px; fill: #66717C; letter-spacing: .6px; }}
{chr(10).join(styles)}
    @media (prefers-reduced-motion: reduce) {{
      {reduced}, .cursor {{ animation: none; opacity: 1 }}
      {covers} {{ display: none }}
    }}
  </style>
  <rect width="{W}" height="{H}" rx="12" fill="#0E1216"/>
  <rect x=".5" y=".5" width="{W - 1}" height="{H - 1}" rx="12" fill="none" stroke="#2A323B"/>
  <path d="M0 12 A12 12 0 0 1 12 0 H{W - 12} A12 12 0 0 1 {W} 12 V46 H0 Z" fill="#171E22"/>
  <line x1="0" y1="46" x2="{W}" y2="46" stroke="#2A323B"/>
  <circle cx="26" cy="23" r="5.5" fill="#3B444E"/>
  <circle cx="46" cy="23" r="5.5" fill="#3B444E"/>
  <circle cx="66" cy="23" r="5.5" fill="#3B444E"/>
  <text class="mono chrome" x="{W / 2}" y="27" text-anchor="middle">cairn &#8212; requires docker and docker compose, nothing else</text>
{chr(10).join(body)}
  <rect class="cursor" x="{X0}" y="{cursor_y}" width="8" height="17" fill="{GREEN}"/>
</svg>
'''

with open(__file__.rsplit("/", 1)[0] + "/terminal.svg", "w") as f:
    f.write(svg)
print("wrote terminal.svg")
