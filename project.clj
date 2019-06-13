(defproject generateme/fastmath "1.4.0-SNAPSHOT"
  :description "Fast and primitive math library"
  :url "https://github.com/generateme/fastmath"
  :license {:name "The Unlicence"
            :url "http://unlicense.org"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [net.jafama/jafama "2.3.1"]
                 [org.apache.commons/commons-math3 "3.6.1"]
                 [com.github.haifengl/smile-interpolation "1.5.2" ]
                 [com.github.haifengl/smile-core "1.5.2"]
                 [com.github.haifengl/smile-netlib "1.5.2"]
                 [org.slf4j/slf4j-nop "1.7.25"]
                 [de.sciss/jwave "1.0.3"]
                 [net.mikera/core.matrix "0.62.0"]
                 ;; [clj-boost "0.0.4"]
                 [de.bwaldvogel/liblinear "2.30"]]
  :exclusions [[org.slf4j/slf4j-simple]]
  :resource-path "resources/"
  :java-source-paths ["src"]
  :javac-options ["-target" "1.8" "-source" "1.8"]
  :scm {:name "git"
        :url "https://github.com/generateme/fastmath/"}  
  :profiles {:dev-codox {:codox {:source-uri "https://github.com/generateme/fastmath/blob/master/{filepath}#L{line}"}}
             :uberjar {:aot :all}})
