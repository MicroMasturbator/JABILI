package com.nsfwbili.app.site

/** 兜底：任意网站，只靠网络嗅探 m3u8 / mp4，不拦截跳转。 */
object GenericExtractor : SiteExtractor {
    override val name = "通用"
    override val homeUrl = "about:blank"
    override val allowedHosts = emptyList<String>()
    override fun matches(host: String) = true
}
