# Scatter Plot Matrix function for Incanter

A Clojure/Incanter library designed to display a scatter plot matrix chart.

## Usage

* Install leiningen
** Download [Leiningen](https://raw.github.com/technomancy/leiningen/stable/bin/lein)
** Place it on your $PATH (eg. ~/bin)
** Set it to be executable. (chmod a+x ~/bin/lein)

* Clone scatter-plot-matrix
** git clone git://github.com/loganisarn/scatter-plot-matrix.git
** cd scatter-plot-matrix

* Run demo
** lein run

* Compile to jar then run
** lein uberjar
** java -jar target/spm-0.1.0-standalone.jar

* Evaulate examples in Emacs (using Slime or nREPL)
** emacs src/spm/core.clj
** M-x clojure-jack-in
** M-x nrepl-jack-in
** Compile core.clj buffer using C-c C-k, or
*** Slime: M-x slime-compile-and-load-file
*** nREPL: M-x nrepl-load-current-buffer
** Find examples in core.clj using C-s examples
** Evaulate examples using C-x C-e right after a closing closure
*** Slime: M-x slime-eval-last-expression
*** nREPL: M-x nrepl-eval-last-expression
** (comment section after (defn -main contains detailed usage

* For more info on usage visit [Loganis blog](http://loganis-data-science.blogspot.com)

## License

Copyright © 2013 iWebMa Ltd.(Loganis)

Distributed under the Eclipse Public License, the same as Clojure.
See LICENSE file.
