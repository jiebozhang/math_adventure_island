# -*- coding: utf-8 -*-
"""
把 PC 端 math_adventure_island_v15_data.py 的题库转成 Android 端 Kotlin 数据文件。
运行：python scripts/gen_v15_data.py
输出：app/src/main/java/com/example/data/model/V15Data.kt
"""
import json
import os
import sys

PC_DIR = r"C:\Users\zhang\PycharmProjects\PythonProject\Math_Adventure_Island"
OUT_PATH = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                        "app", "src", "main", "java", "com", "example", "data", "model", "V15Data.kt")

sys.path.insert(0, PC_DIR)
import math_adventure_island_v15_data as d  # noqa: E402


def esc(s: str) -> str:
    """Kotlin 双引号字符串转义（含 $ 模板符）。"""
    return (s.replace("\\", "\\\\")
             .replace("\"", "\\\"")
             .replace("\n", "\\n")
             .replace("$", "\\$"))


def kstr(s, quoted=True) -> str:
    if s is None:
        return "\"\""
    if isinstance(s, (int, float)):
        return str(s)
    return ("\"" + esc(str(s)) + "\"") if quoted else esc(str(s))


def image_path(p: str) -> str:
    """assets/v15/x.png -> file:///android_asset/v15/x.png（空串保持）。"""
    if not p:
        return ""
    if p.startswith("assets/"):
        return "file:///android_asset/" + p[len("assets/"):]
    return p


def gen_thinking_tags() -> str:
    lines = ["    val THINKING_TAGS = mapOf("]
    for key, meta in d.THINKING_TAGS.items():
        lines.append(
            "        {} to ThinkingTagMeta(name = {}, emoji = {}, tagLabel = {}),".format(
                kstr(key), kstr(meta["name"]), kstr(meta["emoji"]), kstr(meta["tag_label"])))
    lines.append("    )")
    return "\n".join(lines)


def gen_strands() -> str:
    lines = ["    val STRANDS = mapOf("]
    for key, meta in d.STRAND_META.items():
        lines.append("        {} to StrandMeta(label = {}, emoji = {}),".format(
            kstr(key), kstr(meta["label"]), kstr(meta["emoji"])))
    lines.append("    )")
    return "\n".join(lines)


def gen_topics() -> str:
    lines = ["    val KNOWLEDGE_MAP: List<Topic> = listOf("]
    for t in d.KNOWLEDGE_MAP_V15:
        lines.append(
            "        Topic(id = {id}, title = {title}, strand = {strand}, thinkingTag = {tag}, "
            "coreConcept = {cc}, commonMistake = {cm}, grade = {g}, semester = {s}, "
            "unitOrder = {uo}, unitName = {un}),".format(
                id=kstr(t["topic_id"]), title=kstr(t["title"]), strand=kstr(t["strand"]),
                tag=kstr(t["thinking_tag"]), cc=kstr(t["core_concept"]),
                cm=kstr(t["common_mistake"]), g=t["grade"], s=t["semester"],
                uo=t["unit_order"], un=kstr(t["unit_name"])))
    lines.append("    )")
    return "\n".join(lines)


def gen_questions() -> str:
    lines = ["    val QUESTION_BANK: List<Question> = listOf("]
    for q in d.QUESTION_BANK_V15:
        ref = q.get("reference") or {}
        traps = json.dumps(q.get("hidden_traps") or [], ensure_ascii=False)
        lines.append(
            "        Question(id = {id}, topicId = {tid}, story = {story}, text = {text}, "
            "answer = {ans}, methodHint = {mh}, hiddenTrapsJson = {traps}, "
            "conditionsRef = {cond}, questionRef = {qref}, image = {img}, imageDesc = {idesc}),".format(
                id=kstr(q["id"]), tid=kstr(q["topic_id"]), story=kstr(q["story"]),
                text=kstr(q["text"]), ans=kstr(str(q["answer"])),
                mh=kstr(q.get("method_hint", "")), traps=kstr(traps),
                cond=kstr(ref.get("conditions", "")), qref=kstr(ref.get("question", "")),
                img=kstr(image_path(q.get("image", ""))),
                idesc=kstr(q.get("image_desc", ""))))
    lines.append("    )")
    return "\n".join(lines)


HEADER = """package com.example.data.model

// 由 PC 端 math_adventure_island_v15_data.py 自动生成（scripts/gen_v15_data.py），勿手改。
// 源数据：人教版 1-6 年级 12 册知识图谱 + v15 题库。

object V15Data {{

    data class ThinkingTagMeta(val name: String, val emoji: String, val tagLabel: String)
    data class StrandMeta(val label: String, val emoji: String)

{thinking}
{strands}
{topics}
{questions}
}}
"""


def main():
    content = HEADER.format(
        thinking=gen_thinking_tags(),
        strands=gen_strands(),
        topics=gen_topics(),
        questions=gen_questions(),
    )
    os.makedirs(os.path.dirname(OUT_PATH), exist_ok=True)
    with open(OUT_PATH, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)
    print("written:", OUT_PATH)
    print("topics:", len(d.KNOWLEDGE_MAP_V15), "questions:", len(d.QUESTION_BANK_V15))


if __name__ == "__main__":
    main()
