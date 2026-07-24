package chimahon.dictionary.english

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class EnglishDeinflectorTest {

    @ParameterizedTest
    @MethodSource("validDeinflections")
    fun `valid deinflections produce expected term`(testCase: TestCase) {
        val results = EnglishDeinflector.deinflect(testCase.source, "en")
        Assertions.assertTrue(
            results.any { it.text == testCase.term },
            "Expected '${testCase.source}' to deinflect to '${testCase.term}', but got: ${results.map { it.text }}",
        )
    }

    @ParameterizedTest
    @MethodSource("invalidDeinflections")
    fun `invalid deinflections do not produce term`(testCase: TestCase) {
        val results = EnglishDeinflector.deinflect(testCase.source, "en")
        Assertions.assertFalse(
            results.any { it.text == testCase.term },
            "Expected '${testCase.source}' to NOT deinflect to '${testCase.term}', but got: ${results.map { it.text }}",
        )
    }

    @Test
    fun `decapitalize preprocessing`() {
        val preprocessed = EnglishDeinflector.preProcess("Running")
        Assertions.assertTrue("Running" in preprocessed)
        Assertions.assertTrue("running" in preprocessed)

        val lowerPreprocessed = EnglishDeinflector.preProcess("running")
        Assertions.assertEquals(1, lowerPreprocessed.size)
        Assertions.assertEquals("running", lowerPreprocessed.single())
    }

    @Test
    fun phrasal_verb_with_interposed_object() {
        val results = EnglishDeinflector.deinflect("look something up", "en")
        Assertions.assertTrue(
            results.any { it.text == "look up" },
            "Expected 'look something up' to deinflect to 'look up', got: ${results.map { it.text }}",
        )
    }

    @Test
    fun phrasal_verb_with_pronoun() {
        val results = EnglishDeinflector.deinflect("look it up", "en")
        Assertions.assertTrue(
            results.any { it.text == "look up" },
            "Expected 'look it up' to deinflect to 'look up', got: ${results.map { it.text }}",
        )
    }

    @Test
    fun phrasal_verb_past_with_interposed_object() {
        val results = EnglishDeinflector.deinflect("looked something up", "en")
        Assertions.assertTrue(
            results.any { it.text == "look up" },
            "Expected 'looked something up' to deinflect to 'look up', got: ${results.map { it.text }}",
        )
    }

    @Test
    fun multi_step_deinflection() {
        val results = EnglishDeinflector.deinflect("unforgettable", "en")
        Assertions.assertTrue(
            results.any { it.text == "forget" },
            "Expected 'unforgettable' to deinflect to 'forget', got: ${results.map { it.text }}",
        )
    }

    data class TestCase(val term: String, val source: String)

    companion object {
        @JvmStatic
        fun validDeinflections() = listOf(
            // Nouns
            TestCase("cat", "cats"),
            TestCase("cat", "cat's"),
            TestCase("cat", "cats'"),
            TestCase("dirt", "dirty"),
            TestCase("haze", "hazy"),
            TestCase("bag", "baggy"),
            TestCase("scum", "scummy"),
            TestCase("run", "runny"),
            TestCase("slip", "slippy"),
            TestCase("star", "starry"),
            TestCase("gas", "gassy"),
            TestCase("wit", "witty"),

            // Verbs - past tense
            TestCase("walk", "walked"),
            TestCase("walk", "going to walk"),
            TestCase("walk", "will walk"),
            TestCase("walk", "don't walk"),
            TestCase("walk", "do not walk"),
            TestCase("hope", "hoped"),
            TestCase("try", "tried"),
            TestCase("frolic", "frolicked"),
            TestCase("rub", "rubbed"),
            TestCase("bid", "bidded"),
            TestCase("rig", "rigged"),
            TestCase("yak", "yakked"),
            TestCase("dial", "dialled"),
            TestCase("skim", "skimmed"),
            TestCase("bin", "binned"),
            TestCase("rip", "ripped"),
            TestCase("star", "starred"),
            TestCase("bus", "bussed"),
            TestCase("pit", "pitted"),
            TestCase("quiz", "quizzed"),
            TestCase("lay", "laid"),
            TestCase("pay", "paid"),
            TestCase("say", "said"),
            TestCase("adorn", "adorn'd"),

            // Verbs - present participle
            TestCase("walk", "walking"),
            TestCase("drive", "driving"),
            TestCase("lie", "lying"),
            TestCase("panic", "panicking"),
            TestCase("rub", "rubbing"),
            TestCase("bid", "bidding"),
            TestCase("rig", "rigging"),
            TestCase("yak", "yakking"),
            TestCase("dial", "dialling"),
            TestCase("skim", "skimming"),
            TestCase("bin", "binning"),
            TestCase("rip", "ripping"),
            TestCase("star", "starring"),
            TestCase("bus", "bussing"),
            TestCase("pit", "pitting"),
            TestCase("quiz", "quizzing"),
            TestCase("run", "runnin'"),

            // Verbs - 3rd person singular present
            TestCase("walk", "walks"),
            TestCase("teach", "teaches"),
            TestCase("try", "tries"),

            // Verbs - -y
            TestCase("push", "pushy"),
            TestCase("groove", "groovy"),
            TestCase("sag", "saggy"),
            TestCase("swim", "swimmy"),
            TestCase("slip", "slippy"),
            TestCase("blur", "blurry"),
            TestCase("chat", "chatty"),

            // Verbs - un-
            TestCase("learn", "unlearn"),

            // Phrasal verbs
            TestCase("look up", "looking up"),
            TestCase("look up", "looked up"),
            TestCase("look up", "looks up"),

            // Adjectives - un-
            TestCase("funny", "unfunny"),

            // Adjectives - comparative
            TestCase("cool", "cooler"),
            TestCase("subtle", "subtler"),
            TestCase("funny", "funnier"),
            TestCase("drab", "drabber"),
            TestCase("mad", "madder"),
            TestCase("big", "bigger"),
            TestCase("dim", "dimmer"),
            TestCase("tan", "tanner"),
            TestCase("hot", "hotter"),

            // Adjectives - superlative
            TestCase("cool", "coolest"),
            TestCase("subtle", "subtlest"),
            TestCase("funny", "funniest"),
            TestCase("drab", "drabbest"),
            TestCase("mad", "maddest"),
            TestCase("big", "biggest"),
            TestCase("dim", "dimmest"),
            TestCase("tan", "tannest"),
            TestCase("hot", "hottest"),

            // Adverbs
            TestCase("quick", "quickly"),
            TestCase("happy", "happily"),
            TestCase("humble", "humbly"),

            // -able
            TestCase("forget", "forgettable"),
            TestCase("like", "likeable"),
            TestCase("do", "doable"),
            TestCase("desire", "desirable"),
            TestCase("rely", "reliable"),
            TestCase("move", "movable"),
            TestCase("adore", "adorable"),
            TestCase("carry", "carriable"),
        )

        @JvmStatic
        fun invalidDeinflections() = listOf(
            TestCase("boss", "bo"),
            TestCase("sta", "stable"),
        )
    }
}
