"""
星露谷物语维基百科 - 作物图片爬取脚本
从 zh.stardewvalleywiki.com 的"农作物"页面下载所有作物图片，
以中文名称命名保存到本地文件夹。
"""

import os
import re
import sys
import requests
from concurrent.futures import ThreadPoolExecutor, as_completed

# ─── 配置 ───────────────────────────────────────────────────────
WIKI_API_URL = "https://zh.stardewvalleywiki.com/mediawiki/api.php"
PAGE_NAME = "农作物"
OUTPUT_DIR = "作物图片"

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/120.0.0.0 Safari/537.36"
    )
}

# 非作物的 h3 标题（信息段，不需要下载图片）
NON_CROP_HEADINGS = {
    "怪物", "乌鸦", "农场动物", "施肥和播种", "棚架作物",
    "生长时间", "季节结束", "巨大作物", "作物品质", "每日收益",
}

# 野生种子的季节种子映射
WILD_SEEDS_MAP = {
    "Spring_Seeds": "野生种子(春)",
    "Summer_Seeds": "野生种子(夏)",
    "Fall_Seeds": "野生种子(秋)",
    "Winter_Seeds": "野生种子(冬)",
}


def fetch_page_html():
    """通过 MediaWiki API 获取页面 HTML 内容"""
    print(f"正在获取页面「{PAGE_NAME}」的内容...")
    params = {
        "action": "parse",
        "page": PAGE_NAME,
        "format": "json",
        "prop": "text",
    }
    resp = requests.get(WIKI_API_URL, params=params, headers=HEADERS, timeout=30)
    resp.raise_for_status()
    data = resp.json()
    html = data["parse"]["text"]["*"]
    print(f"  页面 HTML 长度: {len(html):,} 字符")
    return html


def extract_crops(html):
    """
    从 HTML 中提取作物名称和图片 URL。
    返回 [(中文名, 图片URL), ...] 列表。
    """
    crops = []

    # 按 <h3> 分割，逐段解析
    sections = re.split(r"<h3>", html)

    for section in sections[1:]:  # 跳过第一段（第一个 h3 之前的内容）
        header_area = section[:800]  # 只需看开头部分

        # 提取中文名称：从 <a title="中文名"> 中获取
        name_match = re.search(
            r'<a\s+href="[^"]*"\s+title="([^"]+)">[^<]+</a>\s*</span>',
            header_area,
        )
        if not name_match:
            continue
        cn_name = name_match.group(1)

        # 跳过非作物段
        if cn_name in NON_CROP_HEADINGS:
            continue

        # ── 标准作物：有 <a href="/File:xxx" class="image"><img src="..."> 结构
        img_match = re.search(
            r'<a\s+href="/File:[^"]+"\s+class="image">\s*<img[^>]*src="([^"]+)"',
            header_area,
        )
        if img_match:
            img_url = img_match.group(1)
            crops.append((cn_name, img_url))
            continue

        # ── 特殊处理：野生种子（内嵌多张季节种子缩略图）
        if cn_name == "野生种子":
            seed_imgs = re.findall(
                r'<img[^>]*alt="((?:Spring|Summer|Fall|Winter)_Seeds\.png)"[^>]*'
                r'src="[^"]*?/images/([^"]+)"',
                header_area,
            )
            if not seed_imgs:
                # 备选：直接匹配 src 中的文件名
                seed_imgs_src = re.findall(
                    r'src="[^"]*?/images/(?:thumb/)?[0-9a-f]/[0-9a-f]{2}/'
                    r'((?:Spring|Summer|Fall|Winter)_Seeds\.png)',
                    header_area,
                )
                for seed_file in seed_imgs_src:
                    seed_key = seed_file.replace(".png", "")
                    if seed_key in WILD_SEEDS_MAP:
                        full_url = (
                            f"https://stardewvalleywiki.com/mediawiki/images/"
                            f"{seed_file}"
                        )
                        crops.append((WILD_SEEDS_MAP[seed_key], full_url))
            else:
                for alt, path in seed_imgs:
                    seed_key = alt.replace(".png", "")
                    if seed_key in WILD_SEEDS_MAP:
                        full_url = f"https://stardewvalleywiki.com/mediawiki/images/{path}"
                        crops.append((WILD_SEEDS_MAP[seed_key], full_url))

            # 如果上面都没匹配到，用已知的固定 URL 兜底
            wild_found = any(c[0].startswith("野生种子") for c in crops)
            if not wild_found:
                for seed_key, label in WILD_SEEDS_MAP.items():
                    crops.append((
                        label,
                        f"https://stardewvalleywiki.com/mediawiki/images/"
                        f"{seed_key}.png",
                    ))
            continue

        # ── 备选：h3 内没有 File: 链接，但有直接的 img src（如仙人掌果子）
        fallback_img = re.search(
            r'<img[^>]*src="(https://stardewvalleywiki\.com/mediawiki/images/'
            r'[0-9a-f]/[0-9a-f]{2}/[^"]+)"',
            section[:2000],
        )
        if fallback_img:
            img_url = fallback_img.group(1)
            # 排除 Quality/Gold 等非作物图标
            if not any(
                kw in img_url
                for kw in ["Quality", "Gold.png", "Energy", "Health", "Silver"]
            ):
                crops.append((cn_name, img_url))
                continue

        print(f"  [警告] 未找到图片: {cn_name}")

    return crops


def download_image(name, url, output_dir):
    """下载单张图片并保存"""
    try:
        resp = requests.get(url, headers=HEADERS, timeout=30)
        resp.raise_for_status()

        filename = f"{name}.png"
        filepath = os.path.join(output_dir, filename)
        with open(filepath, "wb") as f:
            f.write(resp.content)

        size_kb = len(resp.content) / 1024
        return (True, name, f"{size_kb:.1f} KB")
    except Exception as e:
        return (False, name, str(e))


def main():
    # 确定输出目录（脚本所在目录下）
    script_dir = os.path.dirname(os.path.abspath(__file__))
    output_dir = os.path.join(script_dir, OUTPUT_DIR)

    # 1. 获取页面 HTML
    html = fetch_page_html()

    # 2. 解析作物数据
    print("\n正在解析作物列表...")
    crops = extract_crops(html)
    print(f"  共找到 {len(crops)} 种作物图片待下载")

    if not crops:
        print("[错误] 未找到任何作物，请检查页面结构是否变化。")
        sys.exit(1)

    # 打印作物列表
    print("\n作物列表:")
    for i, (name, url) in enumerate(crops, 1):
        print(f"  {i:2d}. {name:<8s}  {url.split('/')[-1]}")

    # 3. 创建输出目录
    os.makedirs(output_dir, exist_ok=True)
    print(f"\n输出目录: {output_dir}")

    # 4. 并行下载图片
    print(f"\n开始下载（5 个并行线程）...")
    success_count = 0
    fail_count = 0

    with ThreadPoolExecutor(max_workers=5) as executor:
        futures = {
            executor.submit(download_image, name, url, output_dir): (name, url)
            for name, url in crops
        }

        for future in as_completed(futures):
            ok, name, info = future.result()
            if ok:
                success_count += 1
                print(f"  [OK] {name:<10s} ({info})")
            else:
                fail_count += 1
                print(f"  [FAIL] {name:<10s} failed: {info}")

    # 5. 输出统计
    print(f"\n{'='*50}")
    print(f"下载完成！成功: {success_count}, 失败: {fail_count}")
    print(f"图片保存在: {output_dir}")

    if fail_count > 0:
        print(f"\n[注意] 有 {fail_count} 张图片下载失败，可重新运行脚本重试。")


if __name__ == "__main__":
    main()
