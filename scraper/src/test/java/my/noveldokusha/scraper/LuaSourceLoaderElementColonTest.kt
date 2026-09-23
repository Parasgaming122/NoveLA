package my.noveldokusha.scraper

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import kotlinx.coroutines.runBlocking
import my.noveldokusha.network.NetworkClient
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.luaj.vm2.LuaValue
import java.io.File
import java.nio.file.Files
import java.util.Locale

/**
 * Регрессия: методы элемента `attr` и `select` должны поддерживать Lua-колон-вызов
 * `el:attr("x")` (= `el.attr(el, "x")` — self первым аргументом).
 *
 * Раньше они были OneArgFunction: LuaJ передавал в call() только первый аргумент —
 * таблицу элемента — и checkjstring() молча падал в catch, возвращая "".
 * Из-за этого у плагинов, использующих `el:attr(...)` (bacakomik, komikdewasa и др.),
 * в приложении пустели обложки. Тестер (plugin-tester-jvm) уже был исправлен
 * VarArgFunction-версией — здесь движок приведён к тому же поведению.
 */
class LuaSourceLoaderElementColonTest {

    private val tempDir: File = Files.createTempDirectory("lua-element-colon").toFile()

    private fun globals(): org.luaj.vm2.Globals {
        val networkClient = mock<NetworkClient>()
        runBlocking {
            whenever(networkClient.call(any(), any())).thenThrow(AssertionError("http_get not expected in this test"))
        }
        val context = mock<Context>()
        whenever(context.filesDir).thenReturn(tempDir)
        val resources = mock<Resources>()
        val configuration = Configuration()
        configuration.locale = Locale.US
        whenever(resources.configuration).thenReturn(configuration)
        whenever(context.resources).thenReturn(resources)

        val engine = LuaEngine(context, networkClient)
        return runBlocking { engine.loadScript("-- element colon probe") as org.luaj.vm2.Globals }
    }

    // Фрагмент каталога bacakomik: карточка .animepost > .animposx с .limit и img-ленивым placeholder'ом
    private val cardHtml = """
        <div class="animepost"><div class="animposx">
        <a itemprop="url" href="https://bacakomik.my/komik/test/"><div class="limit">
        <img src="data:image/svg+xml,placeholder" data-lazy-src="https://i0.wp.com/cdn/cover.jpg?resize=146,208">
        </div></a></div></div>
    """.trimIndent()

    private fun probe(globals: org.luaj.vm2.Globals, fn: String, html: String): String =
        globals.get(fn).call(LuaValue.valueOf(html)).tojstring()

    @Test
    fun `colon attr returns attribute value`() {
        val g = globals()
        val script = """
            function probe_colon_attr(html)
                local el = html_select_first(html, '.limit img')
                return el:attr('data-lazy-src')
            end
        """
        runBlocking { g.load(script).call() }
        assertEquals(
            "https://i0.wp.com/cdn/cover.jpg?resize=146,208",
            probe(g, "probe_colon_attr", cardHtml)
        )
    }

    @Test
    fun `dot attr still works`() {
        val g = globals()
        val script = """
            function probe_dot_attr(html)
                local el = html_select_first(html, '.limit img')
                return el.attr('src')
            end
        """
        runBlocking { g.load(script).call() }
        assertEquals("data:image/svg+xml,placeholder", probe(g, "probe_dot_attr", cardHtml))
    }

    @Test
    fun `colon select returns elements`() {
        val g = globals()
        val script = """
            function probe_colon_select(html)
                local limit = html_select_first(html, '.limit')
                local img = limit:select('img')[1]
                return img:attr('data-lazy-src')
            end
        """
        runBlocking { g.load(script).call() }
        assertEquals(
            "https://i0.wp.com/cdn/cover.jpg?resize=146,208",
            probe(g, "probe_colon_select", cardHtml)
        )
    }

    @Test
    fun `extractCover chain from bacakomik works`() {
        val g = globals()
        val script = """
            function probe_cover(html)
                local img = html_select_first(html, '.limit img')
                local lazy = img:attr('data-lazy-src') or ''
                if lazy ~= '' and not lazy:find('data:image') then return lazy end
                local src = img:attr('src') or ''
                if src ~= '' and not src:find('data:image') then return src end
                return ''
            end
        """
        runBlocking { g.load(script).call() }
        assertEquals(
            "https://i0.wp.com/cdn/cover.jpg?resize=146,208",
            probe(g, "probe_cover", cardHtml)
        )
    }
}