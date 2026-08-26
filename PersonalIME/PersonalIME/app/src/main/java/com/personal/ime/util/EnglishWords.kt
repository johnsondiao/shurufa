package com.personal.ime.util

/**
 * 内置常用英文词表，按常用度大致排序（越靠前越常用）。
 * 预测时词表索引作为基础得分，数据库中的科技词汇与用户学习记录会获得更高权重。
 */
object EnglishWords {

    val WORDS: List<String> = """
        the be to of and a in that have it for not on with he as you do at
        this but his by from they we say her she or an will my one all would
        there their what so up out if about who get which go me when make can
        like time no just him know take people into year your good some could
        them see other than then now look only come its over think also back
        after use two how our work first well way even new want because any
        these give day most us is are was were been being am has had did does
        done made makes go went gone comes came coming gets got getting took
        taken saw seen knew known thought find found gave given tell told ask
        asked seemed feel felt tried leave left call called keep kept let
        begin began help start started showed hear heard play played run move
        lived believe hold held brought happened write wrote written provide
        stand stood lose lost paid meet met learn lead understand watch follow
        stop create speak spent grew open walked win won teach taught offer
        remember consider appear bought serve sent build built stay fall fell
        mean meant put cut
        hello hi hey thanks thank please sorry welcome ok okay yes yeah
        morning afternoon evening night today tomorrow yesterday week month
        hour minute second man woman men women child children family friend
        friends father mother brother sister son daughter wife husband baby
        home house room door window table chair bed kitchen bathroom school
        class teacher student book books pen paper word words letter name
        number phone mobile computer laptop screen keyboard mouse file files
        folder page site website app data code server client user users
        password account email message messages photo photos picture pictures
        video videos music song songs movie movies game games food drink
        water coffee tea breakfast lunch dinner fruit apple rice bread meat
        fish milk egg eggs car bus train plane bike road street city country
        world place places thing things part kind sort type example question
        questions answer answers problem problems idea ideas plan plans
        reason fact case point line end side area money price cost market
        business company companies job jobs office team project projects
        task tasks report meeting meetings customer customers service
        product products order orders shop store sale news information story
        history life love heart mind head eye eyes face hand hands body
        health rest sleep dream hope happy sad angry busy free ready late
        early fast slow big small large little long short high low old young
        new hot cold warm cool dry wet clean dirty easy hard difficult simple
        complex important possible impossible different same similar best
        better worse worst great nice beautiful pretty cute funny interesting
        boring true false right wrong safe dangerous strong weak light dark
        quiet loud rich poor cheap expensive full empty open closed public
        private general common special normal strange local national
        international official popular famous modern ancient future past
        present recent able available necessary useful useless serious fun
        real whole half many much more less few several both each every some
        another such
        internet network online offline download upload update updates
        install version release feature features bug bugs test tests testing
        debug deploy database backup device system settings notification
        battery charge cable wifi bluetooth login logout register profile
        share link copy paste save delete search filter select choose edit
        view list grid menu button icon label title header footer content
        weekend holiday vacation travel trip flight hotel ticket map weather
        rain snow wind sun sky star tree flower grass river mountain sea
        beach park garden animal dog cat bird horse color red blue green
        yellow black white gray brown pink purple orange language english
        chinese japanese sentence grammar accent voice sound noise level
        amount total count average percent quarter double single pair group
        box bag gift card key lock clock glasses shoes shirt pants dress
        coat hat umbrella
    """.trimIndent().split(Regex("\\s+")).filter { it.isNotBlank() }.distinct()
}
