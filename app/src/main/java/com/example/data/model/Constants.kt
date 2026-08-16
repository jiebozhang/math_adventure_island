package com.example.data.model

object Constants {
    val KNOWLEDGE_MAP = listOf(
        Topic("add_carry", "计算城堡·两位数进位加法", "review", "个位满十，要向十位进1", "忘记进位"),
        Topic("sub_borrow", "计算城堡·两位数退位减法", "review", "个位不够减，从十位借1当10再减", "忘记退位，或借位后忘记减1"),
        Topic("mul_table", "计算城堡·乘法口诀", "review", "几个相同加数的和，可以用乘法表示", "口诀背错，或数错个数"),
        Topic("unit_len", "单位王国·长度单位换算", "review", "米、分米、厘米之间的进率都是10", "单位换算时把大小搞反"),
        Topic("perimeter", "图形山谷·长方形周长", "preview", "周长 = (长 + 宽) × 2", "漏乘2，或只加了一条长一条宽"),
        Topic("area", "图形山谷·长方形面积", "preview", "面积 = 长 × 宽", "把面积公式和周长公式搞混"),
        Topic("div_remainder", "计算城堡·有余数的除法", "preview", "余数一定要比除数小", "忘记写余数，或余数比除数还大"),
        Topic("spatial_observe", "图形山谷·观察物体", "preview", "站在物体的不同方向看过去，看到的图案可能不一样", "混淆了自己站的方向对应的是物体的哪一面")
    )

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

    val DEFAULT_QUESTION_BANK = listOf(
        Question(
            id = "q001",
            topicId = "add_carry",
            story = "粗心怪偷偷溜进了城堡大门的密码锁！",
            text = "城门上有两排密码，第一排是358，第二排比第一排多476。两排密码合在一起是多少？",
            answer = "834",
            methodHint = "加法",
            hiddenTrapsJson = "[\"注意个位相加是否满十，满十要向十位进1\",\"别忘记加上进位的那个1\"]",
            conditionsRef = "第一排358，第二排比第一排多476",
            questionRef = "两排密码合在一起是多少"
        ),
        Question(
            id = "q002",
            topicId = "add_carry",
            story = "计算怪在算式里换了一个数字，你能算出真正的答案吗？",
            text = "小明摘了267个苹果，小红比小明多摘155个，小红摘了多少个苹果？",
            answer = "422",
            methodHint = "加法",
            hiddenTrapsJson = "[\"先想清楚是谁比谁多，再决定用加法还是减法\",\"个位、十位是否都需要进位\"]",
            conditionsRef = "小明267个，小红比小明多155个",
            questionRef = "小红摘了多少个苹果"
        ),
        Question(
            id = "q003",
            topicId = "sub_borrow",
            story = "读题怪把条件的顺序打乱了！",
            text = "仓库里原来有503箱货物，运走了268箱，仓库里还剩多少箱？",
            answer = "235",
            methodHint = "减法",
            hiddenTrapsJson = "[\"个位0减8不够减，要向十位借1\",\"十位借出1之后，本身要先减1再计算\"]",
            conditionsRef = "原来503箱，运走268箱",
            questionRef = "还剩多少箱"
        ),
        Question(
            id = "q004",
            topicId = "sub_borrow",
            story = "检查怪在催你快点交卷，先别急！",
            text = "一根绳子长420厘米，剪掉了176厘米，还剩多少厘米？",
            answer = "244",
            methodHint = "减法",
            hiddenTrapsJson = "[\"个位0减6不够减，要连续向前借位\",\"算完之后回头检查一遍减法有没有错\"]",
            conditionsRef = "原长420厘米，剪掉176厘米",
            questionRef = "还剩多少厘米"
        ),
        Question(
            id = "q005",
            topicId = "mul_table",
            story = "计算怪把乘法口诀藏起来了！",
            text = "每盒巧克力有8颗，7盒一共有多少颗巧克力？",
            answer = "56",
            methodHint = "乘法",
            hiddenTrapsJson = "[\"想清楚是几个8相加，还是几个7相加\",\"用口诀'七八五十六'检查一下\"]",
            conditionsRef = "每盒8颗，一共7盒",
            questionRef = "一共有多少颗巧克力"
        ),
        Question(
            id = "q006",
            topicId = "mul_table",
            story = "读题怪把'每'字藏起来了，你能找到吗？",
            text = "操场上有6行小朋友做操，每行9人，一共有多少人？",
            answer = "54",
            methodHint = "乘法",
            hiddenTrapsJson = "[\"'每行9人'是关键条件，别看漏了\",\"6乘9，还是9乘6，结果是一样的\"]",
            conditionsRef = "6行，每行9人",
            questionRef = "一共有多少人"
        ),
        Question(
            id = "q007",
            topicId = "unit_len",
            story = "单位怪把厘米和分米的关系弄乱了！",
            text = "一条丝带长3米5分米，一共是多少分米？",
            answer = "35",
            methodHint = "单位换算+加法",
            hiddenTrapsJson = "[\"1米=10分米，先把3米换算成分米\",\"换算完别忘了把两部分加起来\"]",
            conditionsRef = "3米5分米",
            questionRef = "一共是多少分米"
        ),
        Question(
            id = "q008",
            topicId = "unit_len",
            story = "单位怪又在捣乱，这次是厘米换米！",
            text = "小华的身高是128厘米，比1米多多少厘米？",
            answer = "28",
            methodHint = "单位换算+减法",
            hiddenTrapsJson = "[\"先把1米换算成100厘米\",\"128厘米减去100厘米\"]",
            conditionsRef = "身高128厘米，1米=100厘米",
            questionRef = "比1米多多少厘米"
        ),
        Question(
            id = "q009",
            topicId = "perimeter",
            story = "概念怪把周长和面积的公式混在了一起！",
            text = "一块长方形菜地长12米，宽7米，围一圈篱笆需要多长？",
            answer = "38",
            methodHint = "周长公式",
            hiddenTrapsJson = "[\"'围一圈'说明求的是周长，不是面积\",\"周长 = (长+宽)×2，别漏乘2\"]",
            conditionsRef = "长12米，宽7米",
            questionRef = "围一圈篱笆需要多长（周长）"
        )
    )

    val STEP_NAMES = listOf("读题", "找条件", "找问题", "选方法", "列算式", "检查", "总结")
    val METHOD_OPTIONS = listOf("加法", "减法", "乘法", "除法", "单位换算", "周长公式", "面积公式", "有余数的除法")
    val DAILY_TASK_GOALS = mapOf("correct" to 3, "defeated" to 1, "notes" to 1)
    const val DAILY_TASK_REWARD_EXP = 20
}
