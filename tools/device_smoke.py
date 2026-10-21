#!/usr/bin/env python3
"""Uji asap kotak-hitam APK rilis di emulator/perangkat lewat adb + uiautomator dump.

Berjalan di luar proses aplikasi, jadi menguji persis APK yang dipasang pemain (R8, aturan ProGuard,
kotlinx.serialization untuk simpan/muat). Alur: splash → menu → permainan baru → lempar dadu →
kembali ke menu (simpan otomatis) → Lanjutkan (muat). Tangkapan layar ke folder keluaran.
"""
import os, re, subprocess, sys, time
import xml.etree.ElementTree as ET

PKG = "id.diran.lisbon1944"
OUT = sys.argv[1] if len(sys.argv) > 1 else "emu-shots"
os.makedirs(OUT, exist_ok=True)


def adb(*args, check=True, binary=False, timeout=60):
    r = subprocess.run(["adb", *args], capture_output=True, timeout=timeout)
    if check and r.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} gagal: {r.stderr.decode(errors='replace')}")
    return r.stdout if binary else r.stdout.decode(errors="replace")


def shot(name):
    with open(os.path.join(OUT, name + ".png"), "wb") as f:
        f.write(adb("exec-out", "screencap", "-p", binary=True))


def nodes():
    for _ in range(3):
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml", check=False, timeout=30)
        xml = adb("shell", "cat", "/sdcard/ui.xml", check=False)
        if "<hierarchy" in xml:
            try:
                return list(ET.fromstring(xml[xml.index("<hierarchy"):]).iter("node"))
            except ET.ParseError:
                pass
        time.sleep(1)
    return []


def find(pattern):
    rx = re.compile(pattern, re.I | re.S)
    for n in nodes():
        for attr in ("text", "content-desc"):
            v = n.get(attr) or ""
            if v and rx.fullmatch(v.strip()):
                return n
    return None


def center(n):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
    return (x1 + x2) // 2, (y1 + y2) // 2


def wait(pattern, what, timeout=90):
    end = time.time() + timeout
    while time.time() < end:
        n = find(pattern)
        if n is not None:
            return n
        time.sleep(1.5)
    shot("fail-" + re.sub(r"\W+", "-", what))
    crash = adb("logcat", "-d", "-s", "AndroidRuntime:E", check=False)
    seen = [(n.get("text") or n.get("content-desc") or "").strip() for n in nodes()]
    seen = [t for t in seen if t][:60]
    raise SystemExit(f"GAGAL: tidak menemukan '{what}' ({pattern}). Terlihat: {seen} {crash[-2000:]}")


def tap(n):
    x, y = center(n)
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(1.2)


def screen_size():
    m = re.search(r"(\d+)x(\d+)", adb("shell", "wm", "size", check=False))
    return (int(m.group(1)), int(m.group(2))) if m else (1080, 2400)


def swipe_up():
    w, h = screen_size()
    adb("shell", "input", "swipe", str(w // 2), str(int(h * 0.75)), str(w // 2), str(int(h * 0.3)), "300")
    time.sleep(1)


def main():
    print("Layar:", screen_size())
    adb("logcat", "-c", check=False)
    adb("shell", "am", "force-stop", PKG, check=False)
    adb("shell", "monkey", "-p", PKG, "-c", "android.intent.category.LAUNCHER", "1")
    time.sleep(2.5)
    shot("e01-splash")

    new_game = wait(r"(NEW GAME|PERMAINAN BARU)", "menu")
    shot("e02-menu")
    tap(new_game)

    wait(r"(Seat 1|Kursi 1)", "setup")
    shot("e03-setup")
    start = None
    for _ in range(12):
        start = find(r"(BEGIN OPERATION|MULAI OPERASI)")
        if start is not None:
            break
        swipe_up()
    if start is None:
        wait(r"(BEGIN OPERATION|MULAI OPERASI)", "tombol mulai", timeout=5)
    tap(start)

    skip = None
    end = time.time() + 8
    while time.time() < end and skip is None:
        skip = find(r"(Tap to skip|Ketuk untuk melewati)")
    if skip is not None:
        shot("e04-intro")
        tap(skip)

    roll = wait(r"(ROLL DICE|LEMPAR DADU)", "lempar dadu", timeout=240)
    shot("e05-game")
    tap(roll)
    time.sleep(2.5)
    shot("e06-after-roll")

    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    tap(wait(r"(TO MENU|KE MENU)", "dialog keluar"))
    cont = wait(r"(CONTINUE|LANJUTKAN)", "tombol lanjutkan")
    shot("e07-menu-continue")
    tap(cont)
    wait(r"(Act|Aksi).*", "permainan termuat", timeout=60)
    time.sleep(1.5)
    shot("e08-continued")

    crash = adb("logcat", "-d", "-s", "AndroidRuntime:E", check=False)
    if "FATAL EXCEPTION" in crash and PKG in crash:
        raise SystemExit("GAGAL: aplikasi crash\n" + crash[-3000:])
    print("OK: alur splash → menu → main → simpan → lanjutkan berhasil pada APK rilis")


if __name__ == "__main__":
    main()
