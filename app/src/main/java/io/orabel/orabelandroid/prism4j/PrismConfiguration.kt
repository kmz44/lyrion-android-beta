package io.orabel.orabelandroid.prism4j

import io.noties.prism4j.annotations.PrismBundle

@PrismBundle(
    include = [
        "brainfuck",
        "c", 
        "clike",
        "clojure",
        "cpp",
        "csharp", 
        "css",
        "css-extras",
        "dart",
        "git",
        "go",
        "groovy",
        "java",
        "javascript",
        "json",
        "kotlin",
        "latex",
        "makefile",
        "markdown",
        "markup",
        "python",
        "scala",
        "sql",
        "swift",
        "yaml"
    ],
    grammarLocatorClassName = ".OrabelPrismGrammarLocator"
)
class PrismConfiguration
