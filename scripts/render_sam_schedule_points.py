"""Sam source audit; shares the project's existing TMX/numbered-map renderer.

Run through render_festival_actor_map.py --sam-schedule --out <directory>.
No Minecraft coordinates or runtime resources are changed by this authoring tool.
"""
from __future__ import annotations

import csv
import hashlib
import html
import json
import re
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

import render_festival_actor_map as maps


# Curated descriptions and ordering; coverage is checked against parsed source.
FIXED = [
    ("SamHouse", 15, 13, "卧室柜子前／上午起始停留"),
    ("SamHouse", 17, 15, "卧室弹吉他"),
    ("SamHouse", 8, 6, "厨房玩掌机（雨天）"),
    ("SamHouse", 17, 13, "卧室书架前／返家停留"),
    ("SamHouse", 22, 16, "卧室电脑桌旁／傍晚停留"),
    ("SamHouse", 22, 13, "原生床位／睡觉／默认出生点"),
    ("SamHouse", 6, 13, "客厅停留／绿雨避雨"),
    ("SamHouse", 8, 5, "婚后返家：厨房停留"),
    ("SamHouse", 3, 13, "婚后周一：客厅停留"),
    ("SamHouse", 19, 13, "周六下午：卧室停留"),
    ("Town", 14, 90, "婚前周五：滑板"),
    ("Town", 11, 94, "秋11体检后：镇内停留"),
    ("Town", 12, 94, "周六傍晚：镇内停留"),
    ("Town", 18, 94, "周六上午：玩掌机"),
    ("Town", 24, 97, "9／23日下午：玩掌机"),
    ("Town", 25, 98, "周六早晨：镇内停留"),
    ("Town", 43, 79, "婚后周五：滑板（不同地点）"),
    ("Town", 71, 99, "春季常规：河边停留"),
    ("Town", 79, 95, "9／23日中午：镇东停留"),
    ("Saloon", 36, 21, "周五台球"),
    ("Saloon", 38, 18, "雨天第二方案：游戏区停留"),
    ("Saloon", 20, 19, "冬季常规：酒吧停留"),
    ("Beach", 46, 24, "夏季常规：海边停留"),
    ("Beach", 11, 34, "冬17：夜市停留"),
    ("Forest", 66, 50, "秋季常规：森林停留"),
    ("Hospital", 14, 15, "秋11：候诊时玩掌机"),
    ("Hospital", 4, 6, "秋11：检查室"),
    ("JojaMart", 16, 5, "周一／周三：扫地工作"),
    ("ArchaeologyHouse", 40, 4, "Joja不可达后的替代扫地点"),
    ("Desert", 21, 59, "沙漠节首日（春15）：参观"),
]

SCENES = [
    ("01-home", "Sam 家", "SamHouse", (0, 0, 25, 25), list(range(1, 11))),
    ("02-town-west", "鹈鹕镇·住宅区", "Town", (6, 74, 48, 105), list(range(11, 18))),
    ("03-town-east", "鹈鹕镇·东侧河边", "Town", (62, 84, 87, 106), [18, 19]),
    ("04-saloon", "星之果实酒吧", "Saloon", (12, 10, 45, 25), [20, 21, 22]),
    ("05-beach", "夏季海滩", "Beach", (32, 13, 60, 35), [23]),
    ("06-night-market", "冬17·夜市", "Beach-NightMarket", (0, 24, 27, 46), [24]),
    ("07-forest", "煤矿森林", "Forest", (52, 39, 80, 66), [25]),
    ("08-hospital", "诊所", "Hospital", (0, 0, 24, 20), [26, 27]),
    ("09-joja", "Joja 超市", "JojaMart", (0, 0, 30, 23), [28]),
    ("10-museum", "博物馆·替代工作地点", "ArchaeologyHouse", (24, 0, 50, 20), [29]),
    ("11-desert", "春15·沙漠节", "Desert-Festival", (9, 46, 35, 70), [30]),
    ("12-bus-stop", "婚后往返·公交站过渡锚点", "BusStop", (3, 12, 26, 29), [31, 32]),
]

BRANCH_NOTES = {
    "rain": "雨天方案一；仅在更高优先级日程未命中时使用。",
    "rain2": "雨天方案二；原版 NextBool 随机选择，非固定星期。",
    "GreenRain": "仅第一年绿雨优先使用。",
    "marriage_Mon": "婚后非雨天周一；bed 动态返回玩家家。",
    "marriage_Fri": "婚后非雨天周五；滑板点与婚前不同。",
    "marriage_DesertFestival_1": "婚后沙漠节首日；沙漠节已可用。",
    "DesertFestival_1": "未婚沙漠节首日；沙漠节已可用。",
    "winter_17": "冬17夜市；日期专用日程优先于下雨／星期。",
    "summer": "夏季常规兜底；专用日期、雨天、星期优先。",
    "fall": "秋季常规兜底；专用日期、雨天、星期优先。",
    "winter": "冬季常规兜底；专用日期、雨天、星期优先。",
    "fall_11": "秋11年度体检；非普通星期路线。",
    "Sat": "周六；优先级低于日期专用和雨天。",
    "9_6": "每月9日：原版按 Sam 好感≥6心选此键，跳转 spring。",
    "23_6": "每月23日：原版按 Sam 好感≥6心选此键，跳转 spring。",
    "9": "每月9日低于上述 Sam 心级：另检查 Penny；任一玩家对 Penny≥6心则转 spring，否则执行本路线。",
    "23": "每月23日：同样检查 Penny 后跳转9；不是第23年／6月。",
    "Mon": "周一工作；Joja不可达时同一节点替换成博物馆，保留 sam_work。",
    "Wed": "周三跳转 Mon。",
    "JojaMart_Replacement": "地点替换参数，不是独立日程，也不是当天多跑一站。",
    "Fri": "未婚周五；滑板、台球。",
    "spring": "春季常规，以及显式 GOTO spring 的共同回退。",
}
ACTION_NAMES = {"sam_guitar": "吉他", "sam_gameboy": "掌机", "sam_work": "扫地",
                "sam_skateboarding": "滑板", "sam_pool": "台球", "sam_sleep": "睡眠"}


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def hhmm(value):
    return f"{int(value) // 100:02d}:{int(value) % 100:02d}"


def generate(output: Path, scale: int) -> Path:
    if scale < 2:
        raise ValueError("Use scale >= 2 so adjacent tile markers remain legible.")
    output.mkdir(parents=True, exist_ok=True)
    source = maps.SCHEDULES_DIR / "Sam.json"
    schedules = read_json(source)
    if schedules.keys() != BRANCH_NOTES.keys():
        raise ValueError("Sam schedule keys changed; review the branch descriptions.")
    points = [dict(id=i, map=m, x=x, y=y, description=d, kind="固定日程", occurrences=[])
              for i, (m, x, y, d) in enumerate(FIXED, 1)]
    lookup = {(p["map"], p["x"], p["y"]): p for p in points}
    seen = set()
    branches = []
    for key, raw in schedules.items():
        commands = []
        for command in raw.split("/"):
            if command.startswith(("GOTO ", "NOT ")):
                commands.append(dict(control=command))
                continue
            match = re.fullmatch(r"(?:(\d+) )?(\w+) (\d+) (\d+) ([0-3])(?: (.*))?", command)
            if match:
                time, location, x, y, facing, tail = match.groups()
                tile = (location, int(x), int(y))
                point = lookup[tile]
                seen.add(tile)
                action = (tail or "").split(" ")[0]
                action = action if action.startswith("sam_") else None
                occurrence = dict(branch=key, time=time, action=action, facing=int(facing), raw=command)
                point["occurrences"].append(occurrence)
                commands.append(dict(point=point["id"], **occurrence))
            elif re.fullmatch(r"\d+ bed", command):
                commands.append(dict(time=command.split()[0], target="婚后玩家家床位（动态）", via_point=32))
            else:
                raise ValueError(f"Unparsed Sam schedule command: {key}: {command}")
        branches.append(dict(key=key, note=BRANCH_NOTES[key], commands=commands, raw=raw))
    if seen != set(lookup):
        raise ValueError(f"Fixed point manifest mismatch: {seen ^ set(lookup)}")
    by_key = {b["key"]: b for b in branches}
    def expand(key, stack=()):
        if key in stack:
            raise ValueError(f"Schedule redirect cycle: {stack + (key,)}")
        expanded = []
        for command in by_key[key]["commands"]:
            if command.get("control", "").startswith("GOTO "):
                expanded.extend(expand(command["control"].split()[1], stack + (key,)))
            elif "control" not in command:
                expanded.append(command)
        return expanded
    for branch in branches:
        branch["expanded_commands"] = expand(branch["key"])
        if branch["key"] in {"9", "23"}:
            branch["penny_six_heart_fallback"] = expand("spring")

    # Keep runtime-derived transition anchors separate from ordinary activity stops.
    npc_source = maps.ROOT / "源文件/StardewValley/NPC.cs"
    npc_text = npc_source.read_text()
    for snippet in ['new Point(10, 23)', 'xLocation = 9;\n\t\t\t\t\t\tyLocation = 23;']:
        if snippet not in npc_text:
            raise ValueError("Marriage routing source changed.")
    for x, description in [(10, "婚后外出路径起算点（非活动站点）"), (9, "婚后 bed 指令返家过渡点（不是床）")]:
        points.append(dict(id=len(points)+1, map="BusStop", x=x, y=23,
                           description=description, kind="路径过渡", occurrences=[]))

    assert len(points) == 32

    # No collision/path-helper layers; no rotations or mirroring of the original maps.
    layers = ("Back", "Back2", "Buildings", "Buildings2", "Front", "AlwaysFront")
    images = []
    point_images = defaultdict(list)
    for slug, title, map_name, crop, ids in SCENES:
        tmx = maps.MAPS_DIR / f"{map_name}.tmx"
        root = ET.parse(tmx).getroot()
        if any(t.image is None for t in maps.load_tilesets(root, tmx)):
            raise ValueError(f"Missing map tilesheet: {map_name}")
        base = maps.render_tmx(tmx, scale, layers=layers)
        step = 16 * scale
        selected = [points[i-1] for i in ids]
        assert ids == list(range(ids[0], ids[-1]+1))
        for p in selected:
            assert crop[0] <= p["x"] < crop[2] and crop[1] <= p["y"] < crop[3]
            assert 0 <= p["x"] < int(root.get("width")) and 0 <= p["y"] < int(root.get("height"))
            point_images[p["id"]].append(f"{slug}.png")
        base = base.crop(tuple(n*step for n in crop))
        labeled = maps.draw_numbered_source_points(
            base, [(f"({p['x']},{p['y']}) {p['description']}",p["x"]+.5,p["y"]+.5) for p in selected],
            crop[:2], scale, f"Sam / {title} / {ids[0]}–{ids[-1]}", start_index=ids[0],
            marker_radius=17, marker_font_size=20, legend_title="点位对照")
        labeled.convert("RGB").save(output / f"{slug}.png")
        images.append(dict(file=f"{slug}.png", title=title, ids=ids, crop=crop, map=map_name))
    assert set(point_images) == set(range(1,33))
    for p in points:
        p["images"] = point_images[p["id"]]

    approval_path = maps.ROOT / "scripts/data/sam_schedule_point_approvals.json"
    approvals = read_json(approval_path)
    for point in points:
        point["minecraft"] = approvals["points"].get(str(point["id"]))
        point["status"] = ("节日，本轮跳过" if point["id"] in approvals["excluded_point_ids"]
                           else "婚后，暂不做" if point["id"] in approvals["deferred_point_ids"]
                           else "已确认家具" if point["minecraft"] and point["minecraft"]["target_kind"] == "furniture"
                           else "已确认" if point["minecraft"] else "待补坐标")

    # Preserve all original branches for the reference atlas, but produce an
    # explicit implementation scope so deferred points cannot silently enter it.
    excluded_keys = {"winter_17", "DesertFestival_1"}
    active_branches = [b for b in branches if not b["key"].startswith("marriage_")
                       and b["key"] not in excluded_keys]
    active_ids = {p["id"] for p in points if p["id"] not in
                  set(approvals["excluded_point_ids"] + approvals["deferred_point_ids"])}
    assert len(active_ids) == 25 and all(points[i-1]["minecraft"] for i in active_ids)
    plan = {"npc_id": "sam", "status": approvals["runtime_status"],
            "scope": "未婚、非节日、无姜岛", "active_point_ids": sorted(active_ids),
            "coordinates": {str(i): points[i-1]["minecraft"] for i in sorted(active_ids)},
            "branches": active_branches}
    assert all(c["point"] in active_ids for b in active_branches
               for c in b["expanded_commands"] if "point" in c)
    (output / "日程接入清单.json").write_text(json.dumps(plan, ensure_ascii=False, indent=2)+"\n")

    sources = [source, npc_source, approval_path, maps.DATA_DIR / "Characters.json",
               maps.DATA_DIR / "animationDescriptions.json", maps.DATA_DIR / "PassiveFestivals.json"]
    sources += [maps.MAPS_DIR / f"{name}.tmx" for name in sorted({s[2] for s in SCENES})]
    audit = dict(source_version="1.6.15.24356 (local AssemblyInfo.cs)", schedule_records=len(schedules),
                 fixed_unique_tiles=30, transition_anchors=2, island_in_scope=False, total_numbered_points=32,
                 branch_keys_covered=list(schedules), unmatched_commands=0, missing_map_tilesheets=0,
                 image_count=len(images), orientation="north-up; original TMX x-right/y-down",
                 approved_points=len(approvals["points"]), excluded_point_ids=approvals["excluded_point_ids"],
                 deferred_point_ids=approvals["deferred_point_ids"], active_points=25,
                 missing_point_ids=approvals["missing_point_ids"],
                 sources={str(p.relative_to(maps.ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources})
    (output / "points.json").write_text(json.dumps(dict(audit=audit, points=points, branches=branches, images=images), ensure_ascii=False, indent=2)+"\n")
    with (output / "点位表.csv").open("w", encoding="utf-8-sig", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["编号", "场景", "原版格X", "原版格Y", "用途", "类别", "MC_X", "MC_Y", "MC_Z", "状态", "目标类型"])
        for p in points:
            mc = p["minecraft"] or {}
            writer.writerow([p["id"],p["map"],p["x"],p["y"],p["description"],p["kind"],
                             mc.get("x",""),mc.get("y",""),mc.get("z",""),p["status"],mc.get("furniture",mc.get("target_kind",""))])
    write_report(output, points, branches, images, audit)
    return output / "index.html"


def write_report(output, points, branches, images, audit):
    intro = [
        "本轮未婚、非节日范围：25个点位全部已确认。另保存4个已给出的婚后坐标；8／9／17／31／32婚后点暂不做，24／30节日点跳过。原始编号保留。用户给定朝向优先于原版，家具朝向读取方块状态。",
        "依据本地原版 1.6.15.24356 的 Sam.json 和 NPC.cs，覆盖 Sam 的全部22条日程记录。",
        "1–30 为固定日程点；31–32 为婚后路径过渡锚点。姜岛按用户要求不做。编号全局连续，不按图片重置。",
        "坐标是原版地图从0开始的格坐标，不是 Minecraft 世界坐标。金色圈中心是目标格中心；上北下南、左西右东。人物朝向不列在表中，原始值按每条日程保存在 points.json。",
        "图片取自原版 TMX 与贴图；夜市、沙漠节采用对应节庆地图。静态图不包含运行时生成的节庆商贩等实体。",
    ]
    notes = [
        "6号床已确认(-9,39,26)，5号木制餐椅已确认(-8,39,30)，均为家具本体坐标。运行时读取家具朝向和占地，计算接近点、坐姿／躺姿对齐与退出位置；不能把这两个坐标直接当作普通寻路终点。已接入原生家具动作状态机，实际游戏中的路线与家具占地仍需验收。",
        "普通未婚日程优先级：第一年绿雨 → 被动节日 → 季节专用日期 → 日期心级 → 日期 → 雨天 → 季节星期／星期 → 季节 → spring。婚后走独立分支；非雨天才采用 marriage_Mon／Fri。",
        "9_6／23_6 的6心指对 Sam 的好感；9／23 内的 NOT friendship Penny 6 则检查 Penny。两种条件不同，都可能转 spring，即使当前不是春季。多人好感按原版聚合／任一玩家判断。",
        "表内时刻是日程脚本的出发时刻，不能全部当到达时刻；带 a 前缀才表示指定到达时刻；本轮固定日程中没有该前缀。24:00以后沿用原版记法。",
        "博物馆29是 Joja28不可达时的替换，不是同一天追加一站。原版保留 sam_work，并根据所在地图穿工作服。",
        "掌机在3、14、15、26；吉他在2；滑板在11、17；扫地在28／29；台球在20；睡眠在6。5只写停留，没有电脑操作动画指令；不能把所有停留都改成坐下。",
        "婚后床位随玩家房屋和床家具解析；原版bed先走公交站32，玩家家床不是BusStop，也不是固定的SamHouse6。婚后农场／屋内自由活动不拥有一套恒定地图格，后续接入需要家具与活动锚点。",
        "范围边界：本表是日程停留点与其动态补充，含夜市和沙漠节日程。普通节日会场演员站位、好感剧情镜头／移动路径属于事件系统，未冒充日程点混入；本表不声称覆盖全部事件演出站位。",
        "25个有效点位已写入正式日程：雨天按日稳定选择，6心使用门槛判断，Penny条件不满足回退spring，Joja关闭事件后改去博物馆。吉他、掌机、滑板、扫地、台球场景手势、坐椅子和睡眠均接入原生动作；进入、持续、退出由服务器同步，聊天等待动作退出。编译、定向数值检查与Blockbench预览已完成，实际地图寻路仍待游戏内验收。",
    ]
    header = ["编号", "场景", "原版格", "用途", "MC 坐标", "状态"]
    rows = [[str(p["id"]),p["map"],f"({p['x']}, {p['y']})",p["description"],
             f"{p['minecraft']['x']} / {p['minecraft']['y']} / {p['minecraft']['z']}" if p["minecraft"] else "—",p["status"]] for p in points]
    def route_label(branch):
        parts=[]
        for c in branch["expanded_commands"]:
            if "control" in c:
                parts.append(c["control"])
            elif "point" in c:
                time=hhmm(c["time"])+" " if c["time"] else ""
                parts.append(f"{time}#{c['point']}"+(f"（{ACTION_NAMES.get(c['action'],c['action'])}）" if c["action"] else ""))
            else:
                parts.append(f"{hhmm(c['time'])} 玩家家床（经#32）")
        return " → ".join(parts)
    markdown = ["# Sam 原版日程点位表", "", *[p+"\n" for p in intro],
                "| "+" | ".join(header)+" |", "|---|---|---|---|---|---|"]
    markdown += ["| "+" | ".join(row)+" |" for row in rows]
    markdown += ["", "## 场景图片", ""]
    for img in images:
        markdown += [f"### {img['title']}（{img['ids'][0]}–{img['ids'][-1]}）", "",
                     f"![{img['title']}]({output.resolve()/img['file']})", ""]
    markdown += ["## 全部22条日程", "", "| 原版键 | 点位顺序／原版时刻 | 条件 |", "|---|---|---|"]
    markdown += [f"| {b['key']} | {route_label(b)} | {b['note']} |" for b in branches]
    markdown += ["", "## 接入时必须保留的规则", ""] + [f"- {n}" for n in notes]
    markdown += ["", "## 校验", "", f"原版记录22/22；固定格30/30；总编号32/32；图片12张；未解析指令0；缺贴图0。原始指令与来源SHA-256见 points.json。", ""]
    (output / "Sam原版点位与日程.md").write_text("\n".join(markdown))
    e=html.escape
    table = "<table><thead><tr>"+"".join(f"<th>{h}</th>" for h in header)+"</tr></thead><tbody>"
    for p,row in zip(points,rows):
        image=p["images"][0].removesuffix(".png")
        table += "<tr>"+f'<td><a href="#{image}">{p["id"]}</a></td>'+"".join(f"<td>{e(v)}</td>" for v in row[1:])+"</tr>"
    table += "</tbody></table>"
    gallery = "".join(f'<section id="{i["file"].removesuffix(".png")}"><h2>{e(i["title"])} · {i["ids"][0]}–{i["ids"][-1]}</h2><a href="{i["file"]}" target="_blank"><img src="{i["file"]}" alt="{e(i["title"])}"></a><a href="#points">返回点位表 ↑</a></section>' for i in images)
    routes="".join(f'<details><summary>{e(b["key"])} · {e(b["note"])}</summary><p>{e(route_label(b))}</p><pre>{e(b["raw"])}</pre></details>' for b in branches)
    page='''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Sam · 原版点位核对</title>
<style>body{margin:0;background:#f5f1e8;color:#253629;font:16px/1.7 system-ui,sans-serif}main{max-width:1440px;margin:auto;padding:36px}h1{font-size:36px}h2{margin-top:44px}a{color:#286150}p{max-width:1040px}table{border-collapse:collapse;width:100%;background:#fffdf7}th,td{padding:9px 13px;text-align:left;border-bottom:1px solid #deded3}thead{position:sticky;top:0;background:#dae5d5}td:first-child{font-weight:700}section{scroll-margin-top:60px}img{display:block;max-width:100%;height:auto;image-rendering:pixelated;margin:18px 0;border:1px solid #d2d7c9}details{padding:12px 0;border-bottom:1px solid #cdd4c7}summary{cursor:pointer}pre{white-space:pre-wrap;overflow-wrap:anywhere;font-size:13px}.meta{color:#65715c;font-size:14px}nav{display:flex;gap:24px;flex-wrap:wrap}.scroll{overflow-x:auto}@media(max-width:600px){main{padding:16px}th,td{padding:6px;font-size:13px}}</style><main>
'''
    page += '<p class="meta">STARDEW VALLEY 1.6.15 · SAM · 原版点位核对</p><h1>先把 Sam 的一天放回地图</h1>'
    page += "".join(f"<p>{e(p)}</p>" for p in intro)
    page += '<nav><a href="#points">25个本轮点位／32个原始编号</a><a href="#gallery">12张参考图</a><a href="#routes">22条原版记录</a><a href="点位表.csv">已确认坐标 CSV</a><a href="日程接入清单.json">本轮日程接入清单</a><a href="points.json">源数据与校验</a></nav>'
    page += '<h2 id="points">点位表</h2><p class="meta">点编号跳到对应图片；点图片可打开原尺寸。已填入本次确认的MC坐标；家具坐标是床／椅子本体，不是寻路终点。</p><div class="scroll">'+table+'</div><div id="gallery">'+gallery+'</div>'
    page += '<h2 id="routes">22条原版日程</h2>'+routes+'<h2>接入规则与边界</h2><ul>'+"".join(f"<li>{e(n)}</li>" for n in notes)+'</ul><p class="meta">校验：22/22日程 · 30/30固定格 · 32/32编号 · 12张图 · 0未解析指令 · 0缺失贴图。完整来源哈希保存在points.json。</p></main></html>'
    (output / "index.html").write_text(page)
