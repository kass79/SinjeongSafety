package com.sinjeong.safety.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * manual.json · today.json 읽기 — 글자에서 자료 모양으로만 바꾼다.
 *
 * [ManualRepository] 안에 있던 것을 떼어 냈다. 저장소는 Firebase·Context 를 물고 있어
 * JVM 단위 테스트가 부를 수 없는데, 그러면 **실제 매뉴얼 71개 섹션을 실제 생성기에 통과시키는
 * 점검**을 할 수 없다(1차 구현 때 합성 자료로만 확인한 이유). org.json 은 안드로이드에는
 * 기본으로 있고, 테스트에서는 `testImplementation("org.json:json")` 이 같은 자리를 채운다.
 */
object ManualJson {

    fun parseDoc(text: String): ManualDoc {
        val root = JSONObject(text)
        val arr = root.optJSONArray("sections")
        val sections = ArrayList<ManualSection>(arr?.length() ?: 0)
        for (i in 0 until (arr?.length() ?: 0)) {
            val o = arr!!.getJSONObject(i)
            val pathArr = o.optJSONArray("path")
            val path = ArrayList<String>(pathArr?.length() ?: 0)
            for (p in 0 until (pathArr?.length() ?: 0)) path.add(pathArr!!.optString(p))

            val blocks = parseBlocks(o.optJSONArray("blocks"), 0)
            sections.add(
                ManualSection(
                    id = o.optString("id"),
                    path = path,
                    title = o.optString("title"),
                    blocks = blocks
                )
            )
        }
        return ManualDoc(
            title = root.optString("title"),
            edition = root.optString("edition"),
            sections = sections
        )
    }

    /**
     * 블록 목록. 스키마 v2 에서는 표 셀에도 `blocks` 가 올 수 있어(표 안의 표·셀 안 그림) 같은 함수로 다시 읽는다.
     * 모르는 필드는 그냥 지나치고, 모르는 `type` 은 그대로 담아 둔다(화면이 문단처럼 그린다).
     */
    private fun parseBlocks(arr: JSONArray?, depth: Int): List<ManualBlock> {
        if (arr == null || depth > ManualFormat.MAX_DEPTH) return emptyList()
        val blocks = ArrayList<ManualBlock>(arr.length())
        for (b in 0 until arr.length()) {
            val bo = arr.optJSONObject(b) ?: continue
            val rows = ArrayList<List<ManualCell>>()
            bo.optJSONArray("rows")?.let { rowArr ->
                for (r in 0 until rowArr.length()) {
                    val cellArr = rowArr.optJSONArray(r) ?: continue
                    val cells = ArrayList<ManualCell>(cellArr.length())
                    for (c in 0 until cellArr.length()) {
                        val co = cellArr.optJSONObject(c) ?: continue
                        cells.add(
                            ManualCell(
                                text = co.optString("text"),
                                colspan = co.optInt("colspan", 1).coerceAtLeast(1),
                                rowspan = co.optInt("rowspan", 1).coerceAtLeast(1),
                                blocks = parseBlocks(co.optJSONArray("blocks"), depth + 1)
                            )
                        )
                    }
                    rows.add(cells)
                }
            }
            blocks.add(
                ManualBlock(
                    type = bo.optString("type"),
                    text = bo.optString("text"),
                    level = bo.optInt("level", 1),
                    rows = rows,
                    file = bo.optString("file"),
                    caption = bo.optString("caption"),
                    alt = if (bo.isNull("alt")) "" else bo.optString("alt"),
                    flow = bo.optBoolean("flow", false)
                )
            )
        }
        return blocks
    }

    /**
     * today.json `{edition, items:[{sectionId, title, hook, easy, keySteps:[…], role}]}`.
     * 선택 파일이라 너그럽게 읽는다 — sectionId·title 이 없는 항목만 버리고, 나머지 칸은 비어도 된다.
     */
    fun parseToday(text: String): TodayManual {
        val root = JSONObject(text)
        val arr = root.optJSONArray("items")
        val items = ArrayList<TodayManualItem>(arr?.length() ?: 0)
        for (i in 0 until (arr?.length() ?: 0)) {
            val o = arr!!.optJSONObject(i) ?: continue
            val sid = o.optString("sectionId").trim()
            val title = o.optString("title").trim()
            if (sid.isEmpty() || title.isEmpty()) continue
            val stepArr = o.optJSONArray("keySteps")
            val steps = ArrayList<String>(stepArr?.length() ?: 0)
            for (k in 0 until (stepArr?.length() ?: 0)) {
                val t = stepArr!!.optString(k)
                if (t.isNotBlank()) steps.add(t)
            }
            items.add(
                TodayManualItem(
                    sectionId = sid,
                    title = title,
                    hook = o.optString("hook").trim(),
                    easy = o.optString("easy").trim(),
                    keySteps = steps,
                    role = o.optString("role").trim()
                )
            )
        }
        return TodayManual(edition = root.optString("edition").trim(), items = items)
    }
}
