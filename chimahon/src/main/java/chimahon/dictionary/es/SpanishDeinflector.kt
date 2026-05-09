package chimahon.dictionary.es

import chimahon.dictionary.DeinflectionResult
import chimahon.dictionary.Deinflector
import chimahon.dictionary.deinflectRecursive
import chimahon.dictionary.Rule
import chimahon.dictionary.prefixInflection
import chimahon.dictionary.suffixInflection
import chimahon.dictionary.wholeWordInflection

object SpanishDeinflector : Deinflector {

    override fun deinflect(
        text: String,
        languageCode: String,
    ): List<DeinflectionResult> {
        return deinflectRecursive(text, allRules, languageCode)
    }

    private val vowelAccents = mapOf('a' to 'á', 'e' to 'é', 'i' to 'í', 'o' to 'ó', 'u' to 'ú')

    private fun addAccent(c: Char) = vowelAccents[c] ?: c.toString()

    private fun stemChangingRule(
        pattern: Regex,
        stemVowel: String,
        replacement: String,
        suffixReplace: Regex,
        verbEnding: String,
        conditionsIn: Set<String>,
        conditionsOut: Set<String>,
        specialCases: Map<String, String> = emptyMap(),
    ): Rule.Custom = Rule.Custom(
        conditionsIn = conditionsIn,
        conditionsOut = conditionsOut,
        isInflected = pattern,
        deinflectFn = { term ->
            val match = specialCases.entries.firstOrNull { (key, _) -> term.startsWith(key) }
            if (match != null) {
                term.replace(Regex(stemVowel), match.value).replace(suffixReplace, verbEnding)
            } else {
                term.replace(Regex(stemVowel), replacement).replace(suffixReplace, verbEnding)
            }
        },
    )

    private val allRules: List<Rule> = buildList {
        // Plural nouns
        add(suffixInflection("s", "", setOf("np"), setOf("ns")))
        add(suffixInflection("es", "", setOf("np"), setOf("ns")))
        add(suffixInflection("ces", "z", setOf("np"), setOf("ns")))
        for (v in listOf('a', 'e', 'i', 'o', 'u')) {
            add(suffixInflection("${v}ses", "${addAccent(v)}s", setOf("np"), setOf("ns")))
            add(suffixInflection("${v}nes", "${addAccent(v)}n", setOf("np"), setOf("ns")))
        }

        // Feminine adjectives
        add(suffixInflection("a", "o", setOf("adj"), setOf("adj")))
        add(suffixInflection("a", "", setOf("adj"), setOf("adj")))
        for (v in listOf('a', 'e', 'i', 'o')) {
            add(suffixInflection("${v}na", "${addAccent(v)}n", setOf("adj"), setOf("adj")))
            add(suffixInflection("${v}sa", "${addAccent(v)}s", setOf("adj"), setOf("adj")))
        }

        // Present indicative
        // e->ie stem changing
        fun eToIERule(verbEnding: String, suffixPattern: Regex, conditionsIn: Set<String>) = stemChangingRule(
            Regex("ie[a-z]*(o|as|a|an|es|e|en)$"), "ie", "e", suffixPattern, verbEnding, conditionsIn, conditionsIn
        )
        // o->ue stem changing
        fun oToUERule(verbEnding: String, suffixPattern: Regex, conditionsIn: Set<String>) = stemChangingRule(
            Regex("ue[a-z]*(o|as|a|an|es|e|en)$"), "ue", "o", suffixPattern, verbEnding, conditionsIn, conditionsIn,
            mapOf("jue" to "u", "hue" to "o")
        )
        // e->i stem changing
        fun eToIRule(verbEnding: String, suffixPattern: Regex, conditionsIn: Set<String>) = stemChangingRule(
            Regex("i[a-z]*(o|es|e|en)$"), "i", "e", suffixPattern, verbEnding, conditionsIn, conditionsIn
        )

        // Present indicative regular endings
        val arPres = suffixInflection("o", "ar", setOf("v_ar"), setOf("v_ar"))
        val asPres = suffixInflection("as", "ar", setOf("v_ar"), setOf("v_ar"))
        val aPres = suffixInflection("a", "ar", setOf("v_ar"), setOf("v_ar"))
        val amosPres = suffixInflection("amos", "ar", setOf("v_ar"), setOf("v_ar"))
        val aisPres = suffixInflection("áis", "ar", setOf("v_ar"), setOf("v_ar"))
        val anPres = suffixInflection("an", "ar", setOf("v_ar"), setOf("v_ar"))
        // e->ie for -ar
        add(stemChangingRule(Regex("ie[a-z]*(o|as|a|an)$"), "ie", "e", Regex("(o|as|a|an)$"), "ar", setOf("v_ar"), setOf("v_ar")))
        // o->ue for -ar
        add(stemChangingRule(Regex("ue[a-z]*(o|as|a|an)$"), "ue", "o", Regex("(o|as|a|an)$"), "ar", setOf("v_ar"), setOf("v_ar"), mapOf("jue" to "u")))

        val erPres = suffixInflection("o", "er", setOf("v_er"), setOf("v_er"))
        val esPres = suffixInflection("es", "er", setOf("v_er"), setOf("v_er"))
        val ePres = suffixInflection("e", "er", setOf("v_er"), setOf("v_er"))
        val emosPres = suffixInflection("emos", "er", setOf("v_er"), setOf("v_er"))
        val eisPres = suffixInflection("éis", "er", setOf("v_er"), setOf("v_er"))
        val enPres = suffixInflection("en", "er", setOf("v_er"), setOf("v_er"))
        add(stemChangingRule(Regex("ie[a-z]*(o|es|e|en)$"), "ie", "e", Regex("(o|es|e|en)$"), "er", setOf("v_er"), setOf("v_er")))
        add(stemChangingRule(Regex("ue[a-z]*(o|es|e|en)$"), "ue", "o", Regex("(o|es|e|en)$"), "er", setOf("v_er"), setOf("v_er"), mapOf("hue" to "o")))

        val irPres = suffixInflection("o", "ir", setOf("v_ir"), setOf("v_ir"))
        val iesPres = suffixInflection("es", "ir", setOf("v_ir"), setOf("v_ir"))
        val iePres = suffixInflection("e", "ir", setOf("v_ir"), setOf("v_ir"))
        val imosPres = suffixInflection("imos", "ir", setOf("v_ir"), setOf("v_ir"))
        val isPres = suffixInflection("ís", "ir", setOf("v_ir"), setOf("v_ir"))
        val enIrrPres = suffixInflection("en", "ir", setOf("v_ir"), setOf("v_ir"))
        add(stemChangingRule(Regex("ie[a-z]*(o|es|e|en)$"), "ie", "e", Regex("(o|es|e|en)$"), "ir", setOf("v_ir"), setOf("v_ir")))
        add(stemChangingRule(Regex("ue[a-z]*(o|es|e|en)$"), "ue", "o", Regex("(o|es|e|en)$"), "ir", setOf("v_ir"), setOf("v_ir")))
        add(stemChangingRule(Regex("i[a-z]*(o|es|e|en)$"), "i", "e", Regex("(o|es|e|en)$"), "ir", setOf("v_ir"), setOf("v_ir")))

        // i->y verbs (incluir, huir, etc.)
        add(suffixInflection("uyo", "uir", setOf("v_ir"), setOf("v_ir")))
        add(suffixInflection("uyes", "uir", setOf("v_ir"), setOf("v_ir")))
        add(suffixInflection("uye", "uir", setOf("v_ir"), setOf("v_ir")))
        add(suffixInflection("uyen", "uir", setOf("v_ir"), setOf("v_ir")))

        // Irregular present tense verbs
        add(suffixInflection("tengo", "tener", setOf("v"), setOf("v")))
        add(suffixInflection("tienes", "tener", setOf("v"), setOf("v")))
        add(suffixInflection("tiene", "tener", setOf("v"), setOf("v")))
        add(suffixInflection("tenemos", "tener", setOf("v"), setOf("v")))
        add(suffixInflection("tenéis", "tener", setOf("v"), setOf("v")))
        add(suffixInflection("tienen", "tener", setOf("v"), setOf("v")))
        add(suffixInflection("oigo", "oír", setOf("v"), setOf("v")))
        add(suffixInflection("oyes", "oír", setOf("v"), setOf("v")))
        add(suffixInflection("oye", "oír", setOf("v"), setOf("v")))
        add(suffixInflection("oímos", "oír", setOf("v"), setOf("v")))
        add(suffixInflection("oís", "oír", setOf("v"), setOf("v")))
        add(suffixInflection("oyen", "oír", setOf("v"), setOf("v")))
        add(suffixInflection("vengo", "venir", setOf("v"), setOf("v")))
        add(suffixInflection("vienes", "venir", setOf("v"), setOf("v")))
        add(suffixInflection("viene", "venir", setOf("v"), setOf("v")))
        add(suffixInflection("venimos", "venir", setOf("v"), setOf("v")))
        add(suffixInflection("venís", "venir", setOf("v"), setOf("v")))
        add(suffixInflection("vienen", "venir", setOf("v"), setOf("v")))
        add(suffixInflection("go", "guir", setOf("v"), setOf("v")))
        add(suffixInflection("jo", "ger", setOf("v"), setOf("v")))
        add(suffixInflection("jo", "gir", setOf("v"), setOf("v")))
        add(suffixInflection("aigo", "aer", setOf("v"), setOf("v")))
        add(suffixInflection("zco", "cer", setOf("v"), setOf("v")))
        add(suffixInflection("zco", "cir", setOf("v"), setOf("v")))
        add(suffixInflection("hago", "hacer", setOf("v"), setOf("v")))
        add(suffixInflection("pongo", "poner", setOf("v"), setOf("v")))
        add(suffixInflection("lgo", "lir", setOf("v"), setOf("v")))
        add(suffixInflection("lgo", "ler", setOf("v"), setOf("v")))

        add(wholeWordInflection("quepo", "caber", setOf("v"), setOf("v")))
        add(wholeWordInflection("doy", "dar", setOf("v"), setOf("v")))
        add(wholeWordInflection("sé", "saber", setOf("v"), setOf("v")))
        add(wholeWordInflection("veo", "ver", setOf("v"), setOf("v")))
        add(wholeWordInflection("soy", "ser", setOf("v"), setOf("v")))
        add(wholeWordInflection("eres", "ser", setOf("v"), setOf("v")))
        add(wholeWordInflection("es", "ser", setOf("v"), setOf("v")))
        add(wholeWordInflection("somos", "ser", setOf("v"), setOf("v")))
        add(wholeWordInflection("sois", "ser", setOf("v"), setOf("v")))
        add(wholeWordInflection("son", "ser", setOf("v"), setOf("v")))
        add(wholeWordInflection("estoy", "estar", setOf("v"), setOf("v")))
        add(wholeWordInflection("estás", "estar", setOf("v"), setOf("v")))
        add(wholeWordInflection("está", "estar", setOf("v"), setOf("v")))
        add(wholeWordInflection("estamos", "estar", setOf("v"), setOf("v")))
        add(wholeWordInflection("estáis", "estar", setOf("v"), setOf("v")))
        add(wholeWordInflection("están", "estar", setOf("v"), setOf("v")))
        add(wholeWordInflection("voy", "ir", setOf("v"), setOf("v")))
        add(wholeWordInflection("vas", "ir", setOf("v"), setOf("v")))
        add(wholeWordInflection("va", "ir", setOf("v"), setOf("v")))
        add(wholeWordInflection("vamos", "ir", setOf("v"), setOf("v")))
        add(wholeWordInflection("vais", "ir", setOf("v"), setOf("v")))
        add(wholeWordInflection("van", "ir", setOf("v"), setOf("v")))
        add(wholeWordInflection("he", "haber", setOf("v"), setOf("v")))
        add(wholeWordInflection("has", "haber", setOf("v"), setOf("v")))
        add(wholeWordInflection("ha", "haber", setOf("v"), setOf("v")))
        add(wholeWordInflection("hemos", "haber", setOf("v"), setOf("v")))
        add(wholeWordInflection("habéis", "haber", setOf("v"), setOf("v")))
        add(wholeWordInflection("han", "haber", setOf("v"), setOf("v")))

        // Preterite
        add(stemChangingRule(Regex("i[a-z]*(ió|ieron)$"), "i", "e", Regex("(ió|ieron)$"), "ir", setOf("v_ir"), setOf("v_ir")))
        add(stemChangingRule(Regex("u[a-z]*(ió|ieron)$"), "u", "o", Regex("(ió|ieron)$"), "ir", setOf("v_ir"), setOf("v_ir")))
        add(suffixInflection("é", "ar", setOf("v_ar"), setOf("v_ar")))
        add(suffixInflection("aste", "ar", setOf("v_ar"), setOf("v_ar")))
        add(suffixInflection("ó", "ar", setOf("v_ar"), setOf("v_ar")))
        add(suffixInflection("amos", "ar", setOf("v_ar"), setOf("v_ar")))
        add(suffixInflection("asteis", "ar", setOf("v_ar"), setOf("v_ar")))
        add(suffixInflection("aron", "ar", setOf("v_ar"), setOf("v_ar")))
        add(suffixInflection("í", "er", setOf("v_er"), setOf("v_er")))
        add(suffixInflection("iste", "er", setOf("v_er"), setOf("v_er")))
        add(suffixInflection("ió", "er", setOf("v_er"), setOf("v_er")))
        add(suffixInflection("imos", "er", setOf("v_er"), setOf("v_er")))
        add(suffixInflection("isteis", "er", setOf("v_er"), setOf("v_er")))
        add(suffixInflection("ieron", "er", setOf("v_er"), setOf("v_er")))
        add(suffixInflection("í", "ir", setOf("v_ir"), setOf("v_ir")))
        add(suffixInflection("iste", "ir", setOf("v_ir"), setOf("v_ir")))
        add(suffixInflection("ió", "ir", setOf("v_ir"), setOf("v_ir")))
        add(suffixInflection("imos", "ir", setOf("v_ir"), setOf("v_ir")))
        add(suffixInflection("isteis", "ir", setOf("v_ir"), setOf("v_ir")))
        add(suffixInflection("ieron", "ir", setOf("v_ir"), setOf("v_ir")))
        // -car, -gar, -zar
        add(suffixInflection("qué", "car", setOf("v"), setOf("v")))
        add(suffixInflection("gué", "gar", setOf("v"), setOf("v")))
        add(suffixInflection("cé", "zar", setOf("v"), setOf("v")))
        // -uir verbs preterite
        add(suffixInflection("í", "uir", setOf("v"), setOf("v")))

        // Irregular preterite verbs
        // Ser/Ir share forms
        for (infinitive in listOf("ser", "ir")) {
            add(wholeWordInflection("fui", infinitive, setOf("v"), setOf("v")))
            add(wholeWordInflection("fuiste", infinitive, setOf("v"), setOf("v")))
            add(wholeWordInflection("fue", infinitive, setOf("v"), setOf("v")))
            add(wholeWordInflection("fuimos", infinitive, setOf("v"), setOf("v")))
            add(wholeWordInflection("fuisteis", infinitive, setOf("v"), setOf("v")))
            add(wholeWordInflection("fueron", infinitive, setOf("v"), setOf("v")))
        }
        add(wholeWordInflection("di", "dar", setOf("v"), setOf("v")))
        add(wholeWordInflection("diste", "dar", setOf("v"), setOf("v")))
        add(wholeWordInflection("dio", "dar", setOf("v"), setOf("v")))
        add(wholeWordInflection("dimos", "dar", setOf("v"), setOf("v")))
        add(wholeWordInflection("disteis", "dar", setOf("v"), setOf("v")))
        add(wholeWordInflection("dieron", "dar", setOf("v"), setOf("v")))
        add(suffixInflection("hice", "hacer", setOf("v"), setOf("v")))
        add(suffixInflection("hiciste", "hacer", setOf("v"), setOf("v")))
        add(suffixInflection("hizo", "hacer", setOf("v"), setOf("v")))
        add(suffixInflection("hicimos", "hacer", setOf("v"), setOf("v")))
        add(suffixInflection("hicisteis", "hacer", setOf("v"), setOf("v")))
        add(suffixInflection("hicieron", "hacer", setOf("v"), setOf("v")))
        add(suffixInflection("puse", "poner", setOf("v"), setOf("v")))
        add(suffixInflection("pusiste", "poner", setOf("v"), setOf("v")))
        add(suffixInflection("puso", "poner", setOf("v"), setOf("v")))
        add(suffixInflection("pusimos", "poner", setOf("v"), setOf("v")))
        add(suffixInflection("pusisteis", "poner", setOf("v"), setOf("v")))
        add(suffixInflection("pusieron", "poner", setOf("v"), setOf("v")))
        add(suffixInflection("dije", "decir", setOf("v"), setOf("v")))
        add(suffixInflection("dijiste", "decir", setOf("v"), setOf("v")))
        add(suffixInflection("dijo", "decir", setOf("v"), setOf("v")))
        add(suffixInflection("dijimos", "decir", setOf("v"), setOf("v")))
        add(suffixInflection("dijisteis", "decir", setOf("v"), setOf("v")))
        add(suffixInflection("dijeron", "decir", setOf("v"), setOf("v")))
        add(suffixInflection("vine", "venir", setOf("v"), setOf("v")))
        add(suffixInflection("viniste", "venir", setOf("v"), setOf("v")))
        add(suffixInflection("vino", "venir", setOf("v"), setOf("v")))
        add(suffixInflection("vinimos", "venir", setOf("v"), setOf("v")))
        add(suffixInflection("vinisteis", "venir", setOf("v"), setOf("v")))
        add(suffixInflection("vinieron", "venir", setOf("v"), setOf("v")))

        add(wholeWordInflection("quise", "querer", setOf("v"), setOf("v")))
        add(wholeWordInflection("quisiste", "querer", setOf("v"), setOf("v")))
        add(wholeWordInflection("quiso", "querer", setOf("v"), setOf("v")))
        add(wholeWordInflection("quisimos", "querer", setOf("v"), setOf("v")))
        add(wholeWordInflection("quisisteis", "querer", setOf("v"), setOf("v")))
        add(wholeWordInflection("quisieron", "querer", setOf("v"), setOf("v")))
        add(suffixInflection("tuve", "tener", setOf("v"), setOf("v")))
        add(suffixInflection("tuviste", "tener", setOf("v"), setOf("v")))
        add(suffixInflection("tuvo", "tener", setOf("v"), setOf("v")))
        add(suffixInflection("tuvimos", "tener", setOf("v"), setOf("v")))
        add(suffixInflection("tuvisteis", "tener", setOf("v"), setOf("v")))
        add(suffixInflection("tuvieron", "tener", setOf("v"), setOf("v")))
        add(wholeWordInflection("pude", "poder", setOf("v"), setOf("v")))
        add(wholeWordInflection("pudiste", "poder", setOf("v"), setOf("v")))
        add(wholeWordInflection("pudo", "poder", setOf("v"), setOf("v")))
        add(wholeWordInflection("pudimos", "poder", setOf("v"), setOf("v")))
        add(wholeWordInflection("pudisteis", "poder", setOf("v"), setOf("v")))
        add(wholeWordInflection("pudieron", "poder", setOf("v"), setOf("v")))
        add(wholeWordInflection("supe", "saber", setOf("v"), setOf("v")))
        add(wholeWordInflection("supiste", "saber", setOf("v"), setOf("v")))
        add(wholeWordInflection("supo", "saber", setOf("v"), setOf("v")))
        add(wholeWordInflection("supimos", "saber", setOf("v"), setOf("v")))
        add(wholeWordInflection("supisteis", "saber", setOf("v"), setOf("v")))
        add(wholeWordInflection("supieron", "saber", setOf("v"), setOf("v")))
        add(wholeWordInflection("estuve", "estar", setOf("v"), setOf("v")))
        add(wholeWordInflection("estuviste", "estar", setOf("v"), setOf("v")))
        add(wholeWordInflection("estuvo", "estar", setOf("v"), setOf("v")))
        add(wholeWordInflection("estuvimos", "estar", setOf("v"), setOf("v")))
        add(wholeWordInflection("estuvisteis", "estar", setOf("v"), setOf("v")))
        add(wholeWordInflection("estuvieron", "estar", setOf("v"), setOf("v")))
        add(wholeWordInflection("anduve", "andar", setOf("v"), setOf("v")))
        add(wholeWordInflection("anduviste", "andar", setOf("v"), setOf("v")))
        add(wholeWordInflection("anduvo", "andar", setOf("v"), setOf("v")))
        add(wholeWordInflection("anduvimos", "andar", setOf("v"), setOf("v")))
        add(wholeWordInflection("anduvisteis", "andar", setOf("v"), setOf("v")))
        add(wholeWordInflection("anduvieron", "andar", setOf("v"), setOf("v")))

        // Imperfect
        add(suffixInflection("aba", "ar", setOf("v_ar"), setOf("v_ar")))
        add(suffixInflection("abas", "ar", setOf("v_ar"), setOf("v_ar")))
        add(suffixInflection("ábamos", "ar", setOf("v_ar"), setOf("v_ar")))
        add(suffixInflection("abais", "ar", setOf("v_ar"), setOf("v_ar")))
        add(suffixInflection("aban", "ar", setOf("v_ar"), setOf("v_ar")))
        add(suffixInflection("ía", "er", setOf("v_er"), setOf("v_er")))
        add(suffixInflection("ías", "er", setOf("v_er"), setOf("v_er")))
        add(suffixInflection("íamos", "er", setOf("v_er"), setOf("v_er")))
        add(suffixInflection("íais", "er", setOf("v_er"), setOf("v_er")))
        add(suffixInflection("ían", "er", setOf("v_er"), setOf("v_er")))
        add(suffixInflection("ía", "ir", setOf("v_ir"), setOf("v_ir")))
        add(suffixInflection("ías", "ir", setOf("v_ir"), setOf("v_ir")))
        add(suffixInflection("íamos", "ir", setOf("v_ir"), setOf("v_ir")))
        add(suffixInflection("íais", "ir", setOf("v_ir"), setOf("v_ir")))
        add(suffixInflection("ían", "ir", setOf("v_ir"), setOf("v_ir")))
        add(suffixInflection("eía", "ir", setOf("v_ir"), setOf("v_ir")))
        add(suffixInflection("eías", "ir", setOf("v_ir"), setOf("v_ir")))
        add(suffixInflection("eíamos", "ir", setOf("v_ir"), setOf("v_ir")))
        add(suffixInflection("eíais", "ir", setOf("v_ir"), setOf("v_ir")))
        add(suffixInflection("eían", "ir", setOf("v_ir"), setOf("v_ir")))

        add(wholeWordInflection("era", "ser", setOf("v"), setOf("v")))
        add(wholeWordInflection("eras", "ser", setOf("v"), setOf("v")))
        add(wholeWordInflection("éramos", "ser", setOf("v"), setOf("v")))
        add(wholeWordInflection("erais", "ser", setOf("v"), setOf("v")))
        add(wholeWordInflection("eran", "ser", setOf("v"), setOf("v")))
        add(wholeWordInflection("iba", "ir", setOf("v"), setOf("v")))
        add(wholeWordInflection("ibas", "ir", setOf("v"), setOf("v")))
        add(wholeWordInflection("íbamos", "ir", setOf("v"), setOf("v")))
        add(wholeWordInflection("ibais", "ir", setOf("v"), setOf("v")))
        add(wholeWordInflection("iban", "ir", setOf("v"), setOf("v")))
        add(wholeWordInflection("veía", "ver", setOf("v"), setOf("v")))
        add(wholeWordInflection("veías", "ver", setOf("v"), setOf("v")))
        add(wholeWordInflection("veíamos", "ver", setOf("v"), setOf("v")))
        add(wholeWordInflection("veíais", "ver", setOf("v"), setOf("v")))
        add(wholeWordInflection("veían", "ver", setOf("v"), setOf("v")))

        // Progressive (Gerund)
        add(stemChangingRule(Regex("i[a-z]*(iendo)$"), "i", "e", Regex("(iendo)$"), "ir", setOf("v_ir"), setOf("v_ir")))
        add(stemChangingRule(Regex("u[a-z]*(iendo)$"), "u", "o", Regex("(iendo)$"), "er", setOf("v_er"), setOf("v_er")))
        add(stemChangingRule(Regex("u[a-z]*(iendo)$"), "u", "o", Regex("(iendo)$"), "ir", setOf("v_ir"), setOf("v_ir")))
        add(suffixInflection("ando", "ar", setOf("v_ar"), setOf("v_ar")))
        add(suffixInflection("iendo", "er", setOf("v_er"), setOf("v_er")))
        add(suffixInflection("iendo", "ir", setOf("v_ir"), setOf("v_ir")))
        add(suffixInflection("ayendo", "aer", setOf("v_er"), setOf("v_er")))
        add(suffixInflection("eyendo", "eer", setOf("v_er"), setOf("v_er")))
        add(suffixInflection("uyendo", "uir", setOf("v_ir"), setOf("v_ir")))
        add(wholeWordInflection("oyendo", "oír", setOf("v"), setOf("v")))
        add(wholeWordInflection("yendo", "ir", setOf("v"), setOf("v")))

        // Imperative - formal and informal
        add(stemChangingRule(Regex("ie[a-z]*(a|e|en)$"), "ie", "e", Regex("(a|e|en)$"), "ar", setOf("v_ar"), setOf("v_ar")))
        add(stemChangingRule(Regex("ie[a-z]*(e|a|an)$"), "ie", "e", Regex("(e|a|an)$"), "er", setOf("v_er"), setOf("v_er")))
        add(stemChangingRule(Regex("ie[a-z]*(e|a|an)$"), "ie", "e", Regex("(e|a|an)$"), "ir", setOf("v_ir"), setOf("v_ir")))
        add(stemChangingRule(Regex("ue[a-z]*(a|e|en)$"), "ue", "o", Regex("(a|e|en)$"), "ar", setOf("v_ar"), setOf("v_ar"), mapOf("jue" to "u")))
        add(stemChangingRule(Regex("ue[a-z]*(e|a|an)$"), "ue", "o", Regex("(e|a|an)$"), "er", setOf("v_er"), setOf("v_er"), mapOf("hue" to "o")))
        add(stemChangingRule(Regex("ue[a-z]*(e|a|an)$"), "ue", "o", Regex("(e|a|an)$"), "ir", setOf("v_ir"), setOf("v_ir")))
        add(stemChangingRule(Regex("i[a-z]*(e|a|an)$"), "i", "e", Regex("(e|a|an)$"), "ir", setOf("v_ir"), setOf("v_ir")))

        add(suffixInflection("a", "ar", setOf("v_ar"), setOf("v_ar")))
        add(suffixInflection("emos", "ar", setOf("v_ar"), setOf("v_ar")))
        add(suffixInflection("ad", "ar", setOf("v_ar"), setOf("v_ar")))
        add(suffixInflection("e", "er", setOf("v_er"), setOf("v_er")))
        add(suffixInflection("amos", "er", setOf("v_er"), setOf("v_er")))
        add(suffixInflection("ed", "er", setOf("v_er"), setOf("v_er")))
        add(suffixInflection("e", "ir", setOf("v_ir"), setOf("v_ir")))
        add(suffixInflection("amos", "ir", setOf("v_ir"), setOf("v_ir")))
        add(suffixInflection("id", "ir", setOf("v_ir"), setOf("v_ir")))

        add(wholeWordInflection("diga", "decir", setOf("v"), setOf("v")))
        add(wholeWordInflection("sé", "ser", setOf("v"), setOf("v")))
        add(wholeWordInflection("ve", "ir", setOf("v"), setOf("v")))
        add(wholeWordInflection("ten", "tener", setOf("v"), setOf("v")))
        add(wholeWordInflection("ven", "venir", setOf("v"), setOf("v")))
        add(wholeWordInflection("haz", "hacer", setOf("v"), setOf("v")))
        add(wholeWordInflection("di", "decir", setOf("v"), setOf("v")))
        add(wholeWordInflection("pon", "poner", setOf("v"), setOf("v")))
        add(wholeWordInflection("sal", "salir", setOf("v"), setOf("v")))

        // Negative imperative
        add(suffixInflection("es", "ar", setOf("v_ar"), setOf("v_ar")))
        add(suffixInflection("éis", "ar", setOf("v_ar"), setOf("v_ar")))
        add(suffixInflection("as", "er", setOf("v_er"), setOf("v_er")))
        add(suffixInflection("áis", "er", setOf("v_er"), setOf("v_er")))
        add(suffixInflection("as", "ir", setOf("v_ir"), setOf("v_ir")))
        add(suffixInflection("áis", "ir", setOf("v_ir"), setOf("v_ir")))

        // Conditional
        add(suffixInflection("ía", "", setOf("v"), setOf("v")))
        add(suffixInflection("ías", "", setOf("v"), setOf("v")))
        add(suffixInflection("íamos", "", setOf("v"), setOf("v")))
        add(suffixInflection("íais", "", setOf("v"), setOf("v")))
        add(suffixInflection("ían", "", setOf("v"), setOf("v")))

        // Irregular conditional
        for ((forms, infinitive) in listOf(
            listOf("diría", "dirías", "diría", "diríamos", "diríais", "dirían") to "decir",
            listOf("haría", "harías", "haría", "haríamos", "haríais", "harían") to "hacer",
            listOf("pondría", "pondrías", "pondría", "pondríamos", "pondríais", "pondrían") to "poner",
            listOf("saldría", "saldrías", "saldría", "saldríamos", "saldríais", "saldrían") to "salir",
            listOf("tendría", "tendrías", "tendría", "tendríamos", "tendríais", "tendrían") to "tener",
            listOf("vendría", "vendrías", "vendría", "vendríamos", "vendríais", "vendrían") to "venir",
            listOf("querría", "querrías", "querría", "querríamos", "querríais", "querrían") to "querer",
            listOf("podría", "podrías", "podría", "podríamos", "podríais", "podrían") to "poder",
            listOf("sabría", "sabrías", "sabría", "sabríamos", "sabríais", "sabrían") to "saber",
        )) {
            for (form in forms) {
                add(wholeWordInflection(form, infinitive, setOf("v"), setOf("v")))
            }
        }

        // Future
        add(suffixInflection("é", "", setOf("v"), setOf("v")))
        add(suffixInflection("ás", "", setOf("v"), setOf("v")))
        add(suffixInflection("á", "", setOf("v"), setOf("v")))
        add(suffixInflection("emos", "", setOf("v"), setOf("v")))
        add(suffixInflection("éis", "", setOf("v"), setOf("v")))
        add(suffixInflection("án", "", setOf("v"), setOf("v")))

        // Irregular future
        for ((forms, infinitive) in listOf(
            listOf("diré", "dirás", "dirá", "diremos", "diréis", "dirán") to "decir",
            listOf("haré", "harás", "hará", "haremos", "haréis", "harán") to "hacer",
            listOf("pondré", "pondrás", "pondrá", "pondremos", "pondréis", "pondrán") to "poner",
            listOf("saldré", "saldrás", "saldrá", "saldremos", "saldréis", "saldrán") to "salir",
            listOf("tendré", "tendrás", "tendrá", "tendremos", "tendréis", "tendrán") to "tener",
            listOf("vendré", "vendrás", "vendrá", "vendremos", "vendréis", "vendrán") to "venir",
        )) {
            for (form in forms) {
                add(suffixInflection(form, infinitive, setOf("v"), setOf("v")))
            }
        }

        // Present subjunctive
        add(stemChangingRule(Regex("ie[a-z]*(e|es|e|en)$"), "ie", "e", Regex("(e|es|e|en)$"), "ar", setOf("v_ar"), setOf("v_ar")))
        add(stemChangingRule(Regex("ie[a-z]*(a|as|a|an)$"), "ie", "e", Regex("(a|as|a|an)$"), "er", setOf("v_er"), setOf("v_er")))
        add(stemChangingRule(Regex("ie[a-z]*(a|as|a|an)$"), "ie", "e", Regex("(a|as|a|an)$"), "ir", setOf("v_ir"), setOf("v_ir")))
        add(stemChangingRule(Regex("ue[a-z]*(e|es|e|en)$"), "ue", "o", Regex("(e|es|e|en)$"), "ar", setOf("v_ar"), setOf("v_ar"), mapOf("jue" to "u")))
        add(stemChangingRule(Regex("ue[a-z]*(a|as|a|an)$"), "ue", "o", Regex("(a|as|a|an)$"), "er", setOf("v_er"), setOf("v_er"), mapOf("hue" to "o")))
        add(stemChangingRule(Regex("ue[a-z]*(a|as|a|an)$"), "ue", "o", Regex("(a|as|a|an)$"), "ir", setOf("v_ir"), setOf("v_ir")))
        add(stemChangingRule(Regex("i[a-z]*(a|as|a|an)$"), "i", "e", Regex("(a|as|a|an)$"), "ir", setOf("v_ir"), setOf("v_ir")))

        add(suffixInflection("e", "ar", setOf("v_ar"), setOf("v_ar")))
        add(suffixInflection("es", "ar", setOf("v_ar"), setOf("v_ar")))
        add(suffixInflection("emos", "ar", setOf("v_ar"), setOf("v_ar")))
        add(suffixInflection("éis", "ar", setOf("v_ar"), setOf("v_ar")))
        add(suffixInflection("en", "ar", setOf("v_ar"), setOf("v_ar")))
        add(suffixInflection("a", "er", setOf("v_er"), setOf("v_er")))
        add(suffixInflection("as", "er", setOf("v_er"), setOf("v_er")))
        add(suffixInflection("amos", "er", setOf("v_er"), setOf("v_er")))
        add(suffixInflection("áis", "er", setOf("v_er"), setOf("v_er")))
        add(suffixInflection("an", "er", setOf("v_er"), setOf("v_er")))
        add(suffixInflection("a", "ir", setOf("v_ir"), setOf("v_ir")))
        add(suffixInflection("as", "ir", setOf("v_ir"), setOf("v_ir")))
        add(suffixInflection("amos", "ir", setOf("v_ir"), setOf("v_ir")))
        add(suffixInflection("áis", "ir", setOf("v_ir"), setOf("v_ir")))
        add(suffixInflection("an", "ir", setOf("v_ir"), setOf("v_ir")))

        // Irregular present subjunctive
        add(wholeWordInflection("dé", "dar", setOf("v"), setOf("v")))
        add(wholeWordInflection("des", "dar", setOf("v"), setOf("v")))
        add(wholeWordInflection("demos", "dar", setOf("v"), setOf("v")))
        add(wholeWordInflection("deis", "dar", setOf("v"), setOf("v")))
        add(wholeWordInflection("den", "dar", setOf("v"), setOf("v")))
        add(wholeWordInflection("esté", "estar", setOf("v"), setOf("v")))
        add(wholeWordInflection("estés", "estar", setOf("v"), setOf("v")))
        add(wholeWordInflection("estemos", "estar", setOf("v"), setOf("v")))
        add(wholeWordInflection("estéis", "estar", setOf("v"), setOf("v")))
        add(wholeWordInflection("estén", "estar", setOf("v"), setOf("v")))
        add(wholeWordInflection("sea", "ser", setOf("v"), setOf("v")))
        add(wholeWordInflection("seas", "ser", setOf("v"), setOf("v")))
        add(wholeWordInflection("seamos", "ser", setOf("v"), setOf("v")))
        add(wholeWordInflection("seáis", "ser", setOf("v"), setOf("v")))
        add(wholeWordInflection("sean", "ser", setOf("v"), setOf("v")))
        add(wholeWordInflection("vaya", "ir", setOf("v"), setOf("v")))
        add(wholeWordInflection("vayas", "ir", setOf("v"), setOf("v")))
        add(wholeWordInflection("vayamos", "ir", setOf("v"), setOf("v")))
        add(wholeWordInflection("vayáis", "ir", setOf("v"), setOf("v")))
        add(wholeWordInflection("vayan", "ir", setOf("v"), setOf("v")))
        add(wholeWordInflection("haya", "haber", setOf("v"), setOf("v")))
        add(wholeWordInflection("hayas", "haber", setOf("v"), setOf("v")))
        add(wholeWordInflection("hayamos", "haber", setOf("v"), setOf("v")))
        add(wholeWordInflection("hayáis", "haber", setOf("v"), setOf("v")))
        add(wholeWordInflection("hayan", "haber", setOf("v"), setOf("v")))
        add(wholeWordInflection("sepa", "saber", setOf("v"), setOf("v")))
        add(wholeWordInflection("sepas", "saber", setOf("v"), setOf("v")))
        add(wholeWordInflection("sepamos", "saber", setOf("v"), setOf("v")))
        add(wholeWordInflection("sepáis", "saber", setOf("v"), setOf("v")))
        add(wholeWordInflection("sepan", "saber", setOf("v"), setOf("v")))

        // Imperfect subjunctive
        for (conj in listOf("ar", "er", "ir")) {
            val cond = setOf("v_$conj")
            add(suffixInflection("ara", conj, cond, cond))
            add(suffixInflection("ase", conj, cond, cond))
            add(suffixInflection("aras", conj, cond, cond))
            add(suffixInflection("ases", conj, cond, cond))
            add(suffixInflection("áramos", conj, cond, cond))
            add(suffixInflection("ásemos", conj, cond, cond))
            add(suffixInflection("arais", conj, cond, cond))
            add(suffixInflection("aseis", conj, cond, cond))
            add(suffixInflection("aran", conj, cond, cond))
            add(suffixInflection("asen", conj, cond, cond))
        }

        // Irregular imperfect subjunctive (ser/ir share)
        for (infinitive in listOf("ser", "ir")) {
            for (form in listOf("fuera", "fuese", "fueras", "fueses", "fuéramos", "fuésemos", "fuerais", "fueseis", "fueran", "fuesen")) {
                add(wholeWordInflection(form, infinitive, setOf("v"), setOf("v")))
            }
        }

        // Participle
        add(suffixInflection("ado", "ar", setOf("adj"), setOf("v_ar")))
        add(suffixInflection("ido", "er", setOf("adj"), setOf("v_er")))
        add(suffixInflection("ido", "ir", setOf("adj"), setOf("v_ir")))
        add(suffixInflection("oído", "oír", setOf("adj"), setOf("v")))
        add(wholeWordInflection("dicho", "decir", setOf("adj"), setOf("v")))
        add(wholeWordInflection("escrito", "escribir", setOf("adj"), setOf("v")))
        add(wholeWordInflection("hecho", "hacer", setOf("adj"), setOf("v")))
        add(wholeWordInflection("muerto", "morir", setOf("adj"), setOf("v")))
        add(wholeWordInflection("puesto", "poner", setOf("adj"), setOf("v")))
        add(wholeWordInflection("roto", "romper", setOf("adj"), setOf("v")))
        add(wholeWordInflection("visto", "ver", setOf("adj"), setOf("v")))
        add(wholeWordInflection("vuelto", "volver", setOf("adj"), setOf("v")))

        // Reflexive
        add(suffixInflection("arse", "ar", setOf("v_ar"), setOf("v_ar")))
        add(suffixInflection("erse", "er", setOf("v_er"), setOf("v_er")))
        add(suffixInflection("irse", "ir", setOf("v_ir"), setOf("v_ir")))

        // Pronoun substitution
        add(suffixInflection("arme", "arse", setOf("v_ar"), setOf("v_ar")))
        add(suffixInflection("arte", "arse", setOf("v_ar"), setOf("v_ar")))
        add(suffixInflection("arnos", "arse", setOf("v_er"), setOf("v_er")))
        add(suffixInflection("erme", "erse", setOf("v_er"), setOf("v_er")))
        add(suffixInflection("erte", "erse", setOf("v_er"), setOf("v_er")))
        add(suffixInflection("ernos", "erse", setOf("v_er"), setOf("v_er")))
        add(suffixInflection("irme", "irse", setOf("v_ir"), setOf("v_ir")))
        add(suffixInflection("irte", "irse", setOf("v_ir"), setOf("v_ir")))
        add(suffixInflection("irnos", "irse", setOf("v_ir"), setOf("v_ir")))

        // Pronominal (me + verbo -> verbo + se)
        add(Rule.Custom(
            conditionsIn = setOf("v"),
            conditionsOut = setOf("v"),
            isInflected = Regex("\\b(me|te|se|nos|os)\\s+([a-z]+)(ar|er|ir)\\b"),
            deinflectFn = { term -> term.replace(Regex("\\b(?:me|te|se|nos|os)\\s+([a-z]+)(ar|er|ir)\\b")) { match -> "${match.groupValues[1]}${match.groupValues[2]}se" } },
        ))
    }
}
