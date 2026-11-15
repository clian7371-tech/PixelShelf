"""
生成封面名索引（app/src/main/assets/index_*.txt）。

用途：PixelShelf 需要一份「游戏封面文件名列表」才能在本地做离线模糊匹配。
本脚本从公开缩略图库抓取目录清单，只保留**游戏标题字符串**，不下载任何图片。

说明：
- 输出目录相对本脚本定位，因此本地与 CI 都能直接运行。
- 索引不随仓库分发（见 .gitignore），构建时按需生成。
- 若抓取失败，脚本以非 0 退出码结束；构建流程会退化为「无网络封面」而不是直接失败。
- 请勿过度频繁运行：该图库由第三方免费提供。

用法：
    python build_index.py
"""

import os
import re
import ssl
import sys
import time
import urllib.parse
import urllib.request

BASE = 'https://thumbnails.libretro.com/'
UA = 'PixelShelf-build-script/1.0'

SYS = {
    'gba': 'Nintendo - Game Boy Advance',
    'nds': 'Nintendo - Nintendo DS',
    'psp': 'Sony - PlayStation Portable',
    'gbc': 'Nintendo - Game Boy Color',
}

ROOT = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(ROOT, 'app', 'src', 'main', 'assets')


def fetch_names(sysname):
    url = BASE + urllib.parse.quote(sysname + '/Named_Boxarts/')
    t0 = time.time()
    req = urllib.request.Request(url, headers={'User-Agent': UA})
    ctx = ssl.create_default_context()
    with urllib.request.urlopen(req, timeout=180, context=ctx) as resp:
        html = resp.read().decode('utf-8', 'replace')

    raw = re.findall(r'href="([^"]+\.png)"', html)
    names = []
    for n in raw:
        n = urllib.parse.unquote(n)
        if n.endswith('.png'):
            n = n[:-4]
        n = n.strip()
        if n:
            names.append(n)
    names = sorted(set(names))
    print(f'  {sysname}: {len(names)} names in {time.time() - t0:.0f}s')
    return names


def main():
    os.makedirs(OUT, exist_ok=True)
    failed = []
    for key, sysname in SYS.items():
        try:
            names = fetch_names(sysname)
        except Exception as e:                      # noqa: BLE001
            print(f'  {sysname}: 抓取失败 {type(e).__name__}: {e}')
            failed.append(key)
            continue

        if not names:
            print(f'  {sysname}: 结果为空，跳过')
            failed.append(key)
            continue

        path = os.path.join(OUT, f'index_{key}.txt')
        with open(path, 'w', encoding='utf-8', newline='\n') as f:
            f.write('\n'.join(names))
        kb = os.path.getsize(path) / 1024
        print(f'  -> {os.path.relpath(path, ROOT)}  ({kb:.0f} KB)')

    if failed:
        print(f'\n以下平台未生成索引: {", ".join(failed)}')
        print('应用仍可构建，但这些平台将没有自动封面。')
        return 1

    print('\n全部索引生成完成。')
    return 0


if __name__ == '__main__':
    sys.exit(main())
