package com.geno1024.ai.gits.ui.settings

/**
 * One term as the two references the app works from render it.
 *
 * [git] is the wording of Git's own Chinese glossary, the one printed at the top of
 * `po/zh_CN.po`, falling back to how the messages themselves translate it when the
 * glossary has no entry. [proGit2] is the equivalent entry of Pro Git 2's translation
 * notes. Where the two disagree the app writes [git], so the column is a record of
 * that decision rather than an open question.
 */
data class GlossaryEntry(
    val english: String,
    val git: String,
    val proGit2: String,
)

/**
 * The git words this app puts on screen, in the order they are met.
 *
 * Only terms the interface actually shows are listed, so that every row can be checked
 * against a string in `values-zh-rCN/strings.xml` by hand.
 */
val GLOSSARY: List<GlossaryEntry> = listOf(
    GlossaryEntry("commit", "提交", "提交"),
    GlossaryEntry("commit message", "提交说明", "提交说明"),
    GlossaryEntry("stage", "暂存区（即索引）；暂存", "暂存"),
    GlossaryEntry("staged", "已暂存", "已暂存的"),
    GlossaryEntry("unstage", "取消暂存", "取消暂存"),
    GlossaryEntry("stash", "储藏区；储藏", "进度保存、保存进度、贮藏"),
    GlossaryEntry("working tree", "工作区", "工作区"),
    GlossaryEntry("repository", "仓库", "仓库"),
    GlossaryEntry("remote", "远程，远程仓库", "远程、远程仓库、远端"),
    GlossaryEntry("branch", "分支", "分支"),
    GlossaryEntry("clone", "克隆", "克隆"),
    GlossaryEntry("pull", "拉，拉取", "拉、拉取"),
    GlossaryEntry("push", "推，推送", "推、推送"),
    GlossaryEntry("log", "日志", "日志"),
    GlossaryEntry("sign", "签名", "签名"),
)
