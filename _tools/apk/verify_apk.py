"""校验从 GitHub Release 下载的 APK：sha256、zip 完整性、版本文件与词库资产。

用法：python _tools/apk/verify_apk.py 27_v1.0.58_release.apk [27_v1.0.58_debug.apk ...]

期望 sha256 从 Release API 的 asset.digest（`sha256:...`）抄进来，校验不通过会打印 MISMATCH。
"""
import hashlib
import os
import sys
import zipfile

# key = 相对/绝对路径，value = Release API 返回的 sha256（不带 sha256: 前缀）
EXPECTED = {
    "27_v1.0.58_release.apk": "2ff929713b1e483f4f6c15f198f76042fc8817f533db5f8d8cd7c70924b71868",
    "27_v1.0.58_debug.apk": "070d32f690f1b5b15e35f98d30c2c665b1ae667863d801ce93ca8ba99dea0f49",
}


def verify(path: str) -> bool:
    print("=" * 60)
    print(path)
    ok = True
    with open(path, "rb") as f:
        blob = f.read()
    got = hashlib.sha256(blob).hexdigest()
    want = EXPECTED.get(os.path.basename(path), "")
    print("  字节数 :", len(blob))
    if want:
        print("  sha256 :", "OK" if got == want else f"MISMATCH {got} (want {want})")
        ok &= got == want
    with zipfile.ZipFile(path) as z:
        print("  zip    :", "完整" if z.testzip() is None else "损坏")
        for n in sorted(z.namelist()):
            if not n.startswith("assets/"):
                continue
            if n.endswith(".version"):
                print(f"  {n} = {z.read(n).decode(errors='replace').strip()}")
            elif n.endswith(".db"):
                print(f"  {n} = {z.getinfo(n).file_size} 字节")
    return ok


if __name__ == "__main__":
    here = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    args = sys.argv[1:] or [os.path.join(here, "_apk", n) for n in EXPECTED]
    here = os.path.dirname(os.path.abspath(__file__))
    results = [verify(p if os.path.isabs(p) else os.path.join(here, p)) for p in args]
    print("=" * 60)
    print("全部通过" if all(results) else "存在不一致")
    sys.exit(0 if all(results) else 1)
