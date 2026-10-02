"""从 GitHub Release 取回已编译的 APK（本机到 github.com 直连常被拦，走 assets API）。

用法：
    python _tools/apk/download_release.py                 # 自动取最新 Release 的 app-release.apk
    python _tools/apk/download_release.py --tag v1.0.56  # 指定 tag
    python _tools/apk/download_release.py --asset app-debug.apk --out _apk/27_v1.0.58_debug.apk

流程：查 Release API → 校验 head_sha 是否本地 HEAD → assets API 二进制下载
      → sha256 对账 → zip 完整性 → 读 assets/dict/*.version 确认打包进去的词库版本。

免鉴权接口限流 60 次/小时/IP，够用；失败多为间歇，重试一次通常就好。
"""
import argparse
import hashlib
import json
import os
import re
import sys
import subprocess
import urllib.request
import zipfile
from pathlib import Path

REPO = "johnsondiao/shurufa"
ASSET_DEFAULT = "app-release.apk"
OUT_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))), "_apk")


def get_json(url: str):
    req = urllib.request.Request(url, headers={"Accept": "application/vnd.github+json",
                                               "User-Agent": "shurufa-apk-fetch"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.load(r)


def pick_release(tag: str | None) -> dict:
    url = f"https://api.github.com/repos/{REPO}/releases/latest" if tag is None else \
          f"https://api.github.com/repos/{REPO}/releases/tags/{tag}"
    rel = get_json(url)
    # release 接口不返回 head sha；CI 的 release body 里写了「提交 <sha>」，从中取
    # body 形如「自动构建（提交 78fa80b...)」；sha 后面可能紧跟半角/全角括号，只认 40 位 hex 最稳
    m = re.search(r"\b[0-9a-f]{40}\b", rel.get("body", ""))
    rel["head_sha"] = m.group(0) if m else ""
    return rel


def asset_of(rel: dict, name: str) -> dict:
    for a in rel["assets"]:
        if a["name"] == name:
            return a
    raise SystemExit(f"Release {rel['tag_name']} 里没有 asset {name}；现有："
                     f"{[a['name'] for a in rel['assets']]}")


def download(asset: dict, dest: str) -> bytes:
    """通道 2：assets API。Accept: application/octet-stream + 跟随 302 到 objects.githubusercontent.com。"""
    req = urllib.request.Request(asset["url"], headers={"Accept": "application/octet-stream",
                                                        "User-Agent": "shurufa-apk-fetch"})
    with urllib.request.urlopen(req, timeout=180) as r:
        blob = r.read()
    with open(dest, "wb") as f:
        f.write(blob)
    return blob


def check(blob: bytes, asset: dict, path: str) -> bool:
    got = hashlib.sha256(blob).hexdigest()
    want = asset.get("digest", "").removeprefix("sha256:")
    ok = got == want == asset["digest"].removeprefix("sha256:")
    print(f"  字节数     {len(blob)}（API {asset['size']}）")
    print(f"  sha256     {'一致' if ok else f'不一致 got={got} want={want}'}")
    print(f"  zip 完整性 {'通过' if zipfile.ZipFile(path).testzip() is None else '损坏'}")
    return ok


def check_dict_versions(path: str, want: str | None) -> None:
    with zipfile.ZipFile(path) as z:
        for n in sorted(z.namelist()):
            if n.startswith("assets/dict/") and n.endswith(".version"):
                v = z.read(n).decode(errors="replace").strip()
                if want and n == "assets/dict/base_words.version":
                    print(f"  {n} = {v}  {'与本地词库一致' if v == want else f'与本地 {want} 不一致！'}")


def default_name(rel: dict, asset_name: str) -> str:
    """对齐 _apk/ 目录既有命名：<最大序号+1>_v<tag>_<release|debug>.apk"""
    nxt = 0
    for f in os.listdir(OUT_DIR) if os.path.isdir(OUT_DIR) else []:
        m = re.match(r"^(\d+)_v", f)
        if m:
            nxt = max(nxt, int(m.group(1)))
    kind = "debug" if asset_name.endswith("-debug.apk") else "release"
    return f"{nxt + 1}_v{rel['tag_name'].lstrip('v')}_{kind}.apk"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--tag", help="Release tag，默认 latest")
    ap.add_argument("--asset", default=ASSET_DEFAULT)
    ap.add_argument("--out", help="落盘路径，默认 _apk/<序号>_<tag>_<asset>")
    ap.add_argument("--no-local-sha-check", action="store_true", help="跳过 head_sha 与本地 HEAD 比对")
    args = ap.parse_args()

    rel = pick_release(args.tag)
    print(f"Release {rel['tag_name']}  发布 {rel['published_at']}")
    if rel["head_sha"]:
        repo_root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
        local = subprocess.run(["git", "-C", repo_root, "rev-parse", "HEAD"],
                               capture_output=True, text=True).stdout.strip()
        same = rel["head_sha"] == local
        print(f"  head_sha {rel['head_sha']} vs 本地 {local}  {'一致' if same else '不一致（拿到的是旧构建）'}")
        if not same and not args.no_local_sha_check:
            print("  用 --no-local-sha-check 强行继续，或等新一轮 CI。")
            return 1
    asset = asset_of(rel, args.asset)
    out = args.out or os.path.join(OUT_DIR, default_name(rel, args.asset))
    os.makedirs(os.path.dirname(out), exist_ok=True)
    print(f"下载 {asset['name']} → {out}")
    cached = os.path.exists(out) and \
        hashlib.sha256(open(out, "rb").read()).hexdigest() == asset["digest"].removeprefix("sha256:")
    if cached:
        print("  本地已有同 sha256 副本，跳过重下（要重下先删文件）")
        blob = open(out, "rb").read()
    else:
        try:
            blob = download(asset, out)
        except Exception as e:  # 直连被拦 / 间歇性失败，重试一次
            print("  首次失败，重试一次：", e)
            blob = download(asset, out)
    ok = check(blob, asset, out)
    repo_root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    local_ver = next((p for p in Path(repo_root).rglob("base_words.version")), None)
    check_dict_versions(out, local_ver.read_text().strip() if local_ver else None)
    print("OK" if ok else "校验未过")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
