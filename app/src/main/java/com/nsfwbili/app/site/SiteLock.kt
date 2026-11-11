package com.nsfwbili.app.site

import java.security.MessageDigest

/**
 * 站点地址像密码一样由用户填写：只保存用户填的内容，代码里只有它的 SHA-256。
 * 填对了才会启用站点，填错就是空页面。
 */
object SiteLock {
    private const val HASH = "68a66706b2db959d654586516f5bcebb9c063d087e12b6864d71f981431ca08d"

    /** "HTTPS://www.Example.com/abc" -> "example.com" */
    fun normalize(input: String): String {
        var s = input.trim().lowercase()
        s = s.substringAfter("://")
        s = s.substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':')
        if (s.startsWith("www.")) s = s.removePrefix("www.")
        return s.trimEnd('.')
    }

    fun unlocks(input: String?): Boolean {
        if (input.isNullOrBlank()) return false
        val h = MessageDigest.getInstance("SHA-256").digest(normalize(input).toByteArray())
            .joinToString("") { "%02x".format(it) }
        return h == HASH
    }
}
