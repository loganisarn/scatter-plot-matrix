(defproject spm "0.1.0"
  :description "Scatter Plot Matrix Function for Incanter"
  :url "http://loganis-data-science.blogspot.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :scm {:name "git" :url "https://github.com/loganisarn/scatter-plot-matrix"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [incanter "1.3.0" ]]
  :main spm.core
  :aot :all

)
