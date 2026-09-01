#!/usr/bin/env bash
#
# 下载阅读字体到 app/src/main/assets/fonts/
#
# 重要：本脚本只下载明确允许再分发的开源字体（SIL OFL 1.1）。
# 受版权保护的字体（如京华老宋体，专有授权）不在下载范围内，
# 请通过应用内「导入字体」功能自行使用，并自行承担授权责任。
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FONT_DIR="${REPO_ROOT}/app/src/main/assets/fonts"

mkdir -p "${FONT_DIR}"

echo "字体目录: ${FONT_DIR}"
echo

# ---------------------------------------------------------------------------
# 霞鹜文楷 LXGW WenKai —— SIL OFL 1.1，允许再分发（需保留署名与许可证）
# ---------------------------------------------------------------------------
LXGW_ASSET="LXGWWenKai-Regular.ttf"
LXGW_API="https://api.github.com/repos/lxgw/LxgwWenKai/releases/latest"

if [ -f "${FONT_DIR}/${LXGW_ASSET}" ]; then
    echo "[跳过] ${LXGW_ASSET} 已存在"
else
    echo "[下载] ${LXGW_ASSET}（霞鹜文楷，SIL OFL 1.1）"

    if ! command -v python3 >/dev/null 2>&1; then
        echo "错误：需要 python3 解析 GitHub Release API" >&2
        exit 1
    fi

    DOWNLOAD_URL="$(python3 - "${LXGW_API}" "${LXGW_ASSET}" <<'PY'
import json, sys, urllib.request

api_url, asset_name = sys.argv[1], sys.argv[2]
request = urllib.request.Request(api_url, headers={"Accept": "application/vnd.github+json"})
with urllib.request.urlopen(request, timeout=30) as response:
    release = json.load(response)

for asset in release.get("assets", []):
    if asset["name"] == asset_name:
        print(asset["browser_download_url"])
        break
else:
    print("", end="")
PY
)"

    if [ -z "${DOWNLOAD_URL}" ]; then
        echo "错误：未能从 ${LXGW_API} 定位 ${LXGW_ASSET}" >&2
        echo "      请手动从 https://github.com/lxgw/LxgwWenKai/releases 下载并放入字体目录" >&2
        exit 1
    fi

    curl -fL --progress-bar -o "${FONT_DIR}/${LXGW_ASSET}.part" "${DOWNLOAD_URL}"
    mv "${FONT_DIR}/${LXGW_ASSET}.part" "${FONT_DIR}/${LXGW_ASSET}"
    echo "[完成] ${LXGW_ASSET}"
fi

echo
echo "------------------------------------------------------------------"
echo "未包含在本脚本中的字体（授权受限，需自行获取）："
echo
echo "  * 京华老宋体 KingHwa Old Song —— 专有授权"
echo "    © 2022 TerryWang. All rights reserved，禁止再分发。"
echo "    如已合法获得，请用应用内「导入字体」功能加载，不要放进本目录打包分发。"
echo
echo "  * 寒蝉活仿宋 —— 霞鹜文楷衍生字体"
echo "    请先核实其衍生许可证条款，确认允许再分发后再放入本目录。"
echo "------------------------------------------------------------------"
echo
echo "当前字体目录内容："
ls -lh "${FONT_DIR}" | tail -n +2 || true
