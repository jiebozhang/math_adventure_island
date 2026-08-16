package com.example.util

/**
 * Pinyin utility for Chinese characters in Math Adventure Island.
 * Maps common Chinese characters used in math problems to their Pinyin with tones.
 */
object PinyinUtils {
    private val pinyinMap = mapOf(
        '数' to "shù", '学' to "xué", '冒' to "mào", '险' to "xiǎn", '岛' to "dǎo",
        '城' to "chéng", '堡' to "bǎo", '两' to "liǎng", '位' to "wèi", '加' to "jiā",
        '法' to "fǎ", '进' to "jìn", '减' to "jiǎn", '退' to "tuì", '乘' to "chéng",
        '口' to "kǒu", '诀' to "jué", '单' to "dān", '王' to "wáng", '国' to "guó",
        '长' to "cháng", '度' to "dù", '换' to "huàn", '算' to "suàn", '图' to "tú",
        '形' to "xíng", '山' to "shān", '谷' to "gǔ", '周' to "zhōu", '面' to "miàn",
        '积' to "jī", '有' to "yǒu", '余' to "yú", '除' to "chú", '观' to "guān",
        '察' to "chá", '物' to "wù", '体' to "tǐ", '粗' to "cū", '心' to "xīn",
        '计' to "jì", '读' to "dú", '题' to "tí", '概' to "gài", '念' to "niàn",
        '检' to "jiǎn", '查' to "chá", '怪' to "guài", '排' to "pái", '密' to "mì",
        '码' to "mǎ", '锁' to "suǒ", '合' to "hé", '在' to "zài", '一' to "yī",
        '起' to "qǐ", '是' to "shì", '多' to "duō", '少' to "shǎo", '小' to "xiǎo",
        '明' to "míng", '摘' to "zhāi", '了' to "le", '苹' to "píng", '果' to "guǒ",
        '红' to "hóng", '比' to "bǐ", '仓' to "cāng", '库' to "kù", '里' to "lǐ",
        '原' to "yuán", '来' to "lái", '货' to "huò", '运' to "yùn", '走' to "zǒu",
        '还' to "hái", '剩' to "shèng", '绳' to "shéng", '子' to "zǐ", '剪' to "jiǎn",
        '掉' to "diào", '厘' to "lí", '米' to "mǐ", '每' to "měi", '盒' to "hé",
        '巧' to "qiǎo", '克' to "kè", '力' to "lì", '颗' to "kē", '共' to "gòng",
        '操' to "cāo", '场' to "chǎng", '上' to "shàng", '行' to "háng", '做' to "zuò",
        '条' to "tiáo", '丝' to "sī", '带' to "dài", '分' to "fēn", '华' to "huá",
        '身' to "shēn", '高' to "gāo", '块' to "kuài", '菜' to "cài", '地' to "dì",
        '围' to "wéi", '圈' to "quān", '篱' to "lí", '笆' to "ba", '需' to "xū",
        '要' to "yào", '我' to "wǒ", '你' to "nǐ", '他' to "tā", '她' to "tā",
        '它' to "tā", '问' to "wèn", '答' to "dá", '解' to "jiě", '思' to "sī",
        '路' to "lù", '式' to "shì", '结' to "jié", '果' to "guǒ", '最' to "zuì",
        '好' to "hǎo", '快' to "kuài", '来' to "lái", '试' to "shì", '下' to "xià",
        '巴' to "bā", '抓' to "zhuā", '住' to "zhù", '这' to "zhè", '只' to "zhī",
        '成' to "chéng", '长' to "zhǎng", '足' to "zú", '迹' to "jì", '复' to "fù",
        '习' to "xí", '预' to "yù", '推' to "tuī", '荐' to "jiàn", '勇' to "yǒng",
        '者' to "zhě", '战' to "zhàn", '胜' to "shèng", '帮' to "bāng", '助' to "zhù"
    )

    fun isChinese(c: Char): Boolean = c in '\u4e00'..'\u9fff'

    fun getPinyin(c: Char): String = pinyinMap[c] ?: ""
}
