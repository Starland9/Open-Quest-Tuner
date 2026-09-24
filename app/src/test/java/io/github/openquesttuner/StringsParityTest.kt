package io.github.openquesttuner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** FR-029, SC-010 : chaque texte existe en anglais et en français, avec les mêmes paramètres. */
class StringsParityTest {

    private val resDir = listOf("src/main/res", "app/src/main/res").map(::File).first { it.isDirectory }

    /** Chaînes traduisibles : nom → texte. `translatable="false"` (comme `app_name`) est ignoré. */
    private fun strings(folder: String): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(resDir, "$folder/strings.xml"))
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length)
            .map { nodes.item(it) as Element }
            .filter { it.getAttribute("translatable") != "false" }
            .associate { it.getAttribute("name") to it.textContent }
    }

    /** Noms des `<plurals>`. */
    private fun plurals(folder: String): Set<String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(resDir, "$folder/strings.xml"))
        val nodes = document.getElementsByTagName("plurals")
        return (0 until nodes.length).mapTo(HashSet()) { (nodes.item(it) as Element).getAttribute("name") }
    }

    private val english = strings("values")
    private val french = strings("values-fr")

    @Test
    fun `les memes chaines existent en anglais et en francais`() {
        assertTrue(english.isNotEmpty())
        assertEquals("Chaînes absentes en français", emptySet<String>(), english.keys - french.keys)
        assertEquals("Chaînes absentes en anglais", emptySet<String>(), french.keys - english.keys)
    }

    @Test
    fun `les parametres de format sont identiques dans les deux langues`() {
        val placeholder = Regex("""%(\d+\$)?[sdf]|%\d+\$\.\d+f""")
        val mismatches = english.keys.intersect(french.keys).filter { name ->
            placeholder.findAll(english.getValue(name)).map { it.value }.toSet() !=
                placeholder.findAll(french.getValue(name)).map { it.value }.toSet()
        }
        assertEquals("Paramètres différents entre anglais et français", emptyList<String>(), mismatches)
    }

    @Test
    fun `les memes pluriels existent en anglais et en francais`() {
        val englishPlurals = plurals("values")
        val frenchPlurals = plurals("values-fr")
        assertEquals("Pluriels absents en français", emptySet<String>(), englishPlurals - frenchPlurals)
        assertEquals("Pluriels absents en anglais", emptySet<String>(), frenchPlurals - englishPlurals)
    }
}
