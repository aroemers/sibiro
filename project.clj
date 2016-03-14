(defproject functionalbytes/sibiro "0.1.0"
  :description "Simple bidirectional data-driven request routing for Clojure and ClojureScript"
  :url "https://github.com/aroemers/sibiro"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[lein-cljsbuild "1.1.2"]
            [lein-doo "0.1.6"]
            [lein-codox "0.9.4"]]
  :profiles {:test {:dependencies [[org.clojure/clojure "1.8.0"]
                                   [org.clojure/clojurescript "1.7.228"]
                                   [doo "0.1.6"]]}}
  :cljsbuild {:builds {:test {:source-paths ["src" "test"]
                              :compiler {:output-to "target/sibiro.js"
                                         :main 'sibiro.test
                                         :optimizations :whitespace}}}}
  :codox {:output-path "doc"}
  :aliases {"test-cljc" ["with-profile" "+test" "do" "test," "doo" "nashorn" "test" "once"]})
