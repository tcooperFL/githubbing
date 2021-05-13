(ns githubbing.config)

(def config
  "Provides the default GitHub Organization context for most GitHub APIs.
    :name - the github name (slug) used in URLs to access organization repos
    :products - words that, if appearing in repo names, indicate the repo is likely client-facing code
    :languages - The primary development languages used in this organization."

  {:organization "mongodb"
   :products     (clojure.string/split
                   "mongo
                   atlas
                   jasper
                   motor
                   redalert
                   slogger
                   snooty
                   stitch
                   winkerberos
                   sdk"
                   #"\n\s*")
   :languages    (clojure.string/split
                   "C++
                    C
                    JavaScript
                    C#
                    Java
                    Go
                    Python
                    Ruby
                    PHP
                    Swift
                    CSS
                    Scala
                    TypeScript
                    Groovy
                    Rust"
                   #"\n\s*")})