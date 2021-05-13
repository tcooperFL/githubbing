(defproject githubbing "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [cheshire "5.10.0"]
                 [org.clojure/tools.logging "1.1.0"]
                 [clj-http "3.12.1"]
                 [dk.ative/docjure "1.13.0"]]
  :main ^:skip-aot githubbing.core
  :target-path "target/%s"
  :plugins [[lein-ancient "0.6.10"]
            [lein-kibit "0.1.5"]]
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}
             :dev     {:resource-paths ["resources"]
                       :global-vars {*print-level* 5 *print-length* 20}}})
