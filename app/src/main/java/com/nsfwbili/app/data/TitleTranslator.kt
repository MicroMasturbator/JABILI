package com.nsfwbili.app.data

import android.os.Handler
import android.os.Looper
import android.util.LruCache
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions

/**
 * 日语标题 → 中文（ML Kit 离线翻译，首次使用会下载约 30MB 的日中模型）。
 * 只翻译含有平假名 / 片假名的标题，中文标题不动。
 */
object TitleTranslator {
    private val kana = Regex("[\\u3040-\\u309F\\u30A0-\\u30FF]")
    private val cache = LruCache<String, String>(2000)
    private val waiting = HashMap<String, MutableList<(String) -> Unit>>()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var modelReady = false
    private var modelFailed = false

    private val translator by lazy {
        Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.JAPANESE)
                .setTargetLanguage(TranslateLanguage.CHINESE)
                .build()
        )
    }

    fun isJapanese(text: String) = kana.containsMatchIn(text)

    fun cached(text: String): String? = cache.get(text)

    /** 异步翻译，回调在主线程；失败时不回调 */
    fun translate(text: String, cb: (String) -> Unit) {
        cache.get(text)?.let { cb(it); return }
        if (modelFailed) return
        val list = waiting[text]
        if (list != null) { list.add(cb); return }
        waiting[text] = mutableListOf(cb)
        val run = {
            // 番号（开头的 ABC-123）保持原样，只翻后面的部分
            val m = Regex("^([A-Za-z0-9]+-[0-9]+\\s*)(.*)$", RegexOption.DOT_MATCHES_ALL).find(text)
            val head = m?.groupValues?.get(1).orEmpty()
            val body = m?.groupValues?.get(2) ?: text
            translator.translate(body)
                .addOnSuccessListener { r ->
                    val out = head + r
                    cache.put(text, out)
                    waiting.remove(text)?.forEach { it(out) }
                }
                .addOnFailureListener { waiting.remove(text) }
        }
        if (modelReady) run() else ensureModel { run() }
    }

    private var downloading = false
    private val afterModel = ArrayList<() -> Unit>()

    /** 模型只下载一次，期间的翻译请求排队 */
    private fun ensureModel(job: () -> Unit) {
        afterModel.add(job)
        if (downloading) return
        downloading = true
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener {
                modelReady = true; downloading = false
                val jobs = afterModel.toList(); afterModel.clear(); jobs.forEach { it() }
            }
            .addOnFailureListener {
                downloading = false; afterModel.clear(); waiting.clear(); retryLater()
            }
    }

    private fun retryLater() {
        // 模型下载失败（比如没网），暂时停用 30 秒
        modelFailed = true
        main.postDelayed({ modelFailed = false }, 30_000)
    }
}
