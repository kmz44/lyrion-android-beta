package io.orabel.orabelandroid.prism4j

import io.noties.prism4j.GrammarLocator
import io.noties.prism4j.Prism4j
import io.noties.prism4j.Prism4j.Grammar

class OrabelPrismGrammarLocator : GrammarLocator {
    override fun grammar(prism4j: Prism4j, language: String): Grammar? {
        return when (language) {
            "brainfuck" -> Prism_brainfuck.create(prism4j)
            "c" -> Prism_c.create(prism4j)
            "clike" -> Prism_clike.create(prism4j)
            "clojure" -> Prism_clojure.create(prism4j)
            "cpp" -> Prism_cpp.create(prism4j)
            "csharp" -> Prism_csharp.create(prism4j)
            "css" -> Prism_css.create(prism4j)
            "css-extras" -> Prism_css_extras.create(prism4j)
            "dart" -> Prism_dart.create(prism4j)
            "git" -> Prism_git.create(prism4j)
            "go" -> Prism_go.create(prism4j)
            "groovy" -> Prism_groovy.create(prism4j)
            "java" -> Prism_java.create(prism4j)
            "javascript" -> Prism_javascript.create(prism4j)
            "json" -> Prism_json.create(prism4j)
            "kotlin" -> Prism_kotlin.create(prism4j)
            "latex" -> Prism_latex.create(prism4j)
            "makefile" -> Prism_makefile.create(prism4j)
            "markdown" -> Prism_markdown.create(prism4j)
            "markup" -> Prism_markup.create(prism4j)
            "python" -> Prism_python.create(prism4j)
            "scala" -> Prism_scala.create(prism4j)
            "sql" -> Prism_sql.create(prism4j)
            "swift" -> Prism_swift.create(prism4j)
            "yaml" -> Prism_yaml.create(prism4j)
            else -> null
        }
    }

    override fun languages(): Set<String> {
        return setOf(
            "brainfuck", "c", "clike", "clojure", "cpp", "csharp",
            "css", "css-extras", "dart", "git", "go", "groovy",
            "java", "javascript", "json", "kotlin", "latex",
            "makefile", "markdown", "markup", "python", "scala",
            "sql", "swift", "yaml"
        )
    }
}
