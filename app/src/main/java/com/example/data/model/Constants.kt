package com.example.data.model

object Constants {
    // v15: 全量知识图谱（人教版 1-6 年级 12 册 × 4 领域，60 关），
    // 由 PC 端 math_adventure_island_v15_data.py 生成，见 V15Data.kt
    val KNOWLEDGE_MAP: List<Topic> = V15Data.KNOWLEDGE_MAP

    val MONSTERS = mapOf(
        "careless" to Monster(
            "careless", "粗心怪", "visibility", "#5DADE2",
            "最喜欢趁 nǐ 抄数字的时候搞破坏。",
            "看到数字先用手指点一遍，抄完了再对一次原题，别让粗心怪钻空子。"
        ),
        "calc" to Monster(
            "calc", "计算怪", "calculate", "#E67E22",
            "专门在你计算的时候偷偷改答案。",
            "算完之后倒着再算一遍（比如减法算完用加法验算），计算怪最怕这一招。"
        ),
        "reading" to Monster(
            "reading", "读题怪", "menu_book", "#8E44AD",
            "躲在字里行间，把题目的条件搅乱。",
            "读题时把关键的数字和条件圈出来，读完试着自己用一句话把题目意思讲一遍。"
        ),
        "unit" to Monster(
            "unit", "单位怪", "square_foot", "#16A085",
            "背着尺子到处跑，让你写错单位。",
            "算完先问自己：这个答案配的单位对不对？米还是厘米，一定要写清楚。"
        ),
        "concept" to Monster(
            "concept", "概念怪", "extension", "#D35400",
            "把周长和面积这些概念混在一起。",
            "做题前先想一想：这道题问的到底是「边框有多长」还是「里面有多大」。"
        ),
        "check" to Monster(
            "check", "检查怪", "search", "#2980B9",
            "总是催你快点交卷，不让你检查。",
            "写完答案别急着交，深呼吸一下，把题目和算式再对一遍。"
        )
    )

    val DEFAULT_QUESTION_BANK: List<Question> = V15Data.QUESTION_BANK

    val STEP_NAMES = listOf("读题", "找条件", "找问题", "选方法", "列算式", "检查", "总结")
    val METHOD_OPTIONS = listOf("加法", "减法", "乘法", "除法", "单位换算", "周长公式", "面积公式", "有余数的除法")
    val DAILY_TASK_GOALS = mapOf("correct" to 3, "defeated" to 1, "notes" to 1)
    const val DAILY_TASK_REWARD_EXP = 20
}
