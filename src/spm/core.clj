;;; scatter-plot-matrix chart function for Incanter and Clojure built on JFreeChart

;; by Arnold Matyasi and John Erdos at http://www.loganis.com
;; Februar 26, 2013

;; Copyright (c) 2013 iWebMa Ltd.(Loganis) All rights reserved. The use
;; and distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file LICENSE at the root of this
;; distribution. By using this software in any fashion, you are
;; agreeing to be bound by the terms of this license. You must not
;; remove this notice, or any other, from this software.


(ns ^{:doc "Scatter Plot Matrix chart function for Incanter. See scatter-plot-chart fn doc and comment examples for usage."
      :author "Arnold Matyasi and John Erdos"}
  spm.core
  (:gen-class)
  (:use (incanter core stats charts io datasets pdf))
  (:refer-clojure)
  (:import
   (org.jfree.chart JFreeChart LegendItem LegendItemCollection StandardChartTheme)
   (org.jfree.chart.axis AxisSpace NumberAxis AxisLocation)
   (org.jfree.chart.plot Plot XYPlot)
   (org.jfree.chart.renderer.xy XYLineAndShapeRenderer XYBarRenderer XYSplineRenderer StandardXYBarPainter)
   (org.jfree.data DomainOrder)
   (org.jfree.data.category DefaultCategoryDataset)
   (org.jfree.data.statistics HistogramDataset)
   (org.jfree.data.xy AbstractXYDataset)
   (org.jfree.ui RectangleInsets RectangleEdge)
   (org.jfree.chart.title TextTitle)
   (java.awt Color Shape Rectangle Graphics2D BasicStroke Font)
   (java.awt.geom Ellipse2D$Float Rectangle2D)))

(defn scatter-plot-matrix*
  [ &{:keys [data group-by title bins only-first only-triangle]
      :or {data $data
           group-by nil
           title "Scatter Plot Matrix"
           bins 10 ; number of bars in the histogram
           only-first 6 ; nr of most correlating metrics shown
           only-triangle false }}]
  (let [margin 32
        xmarg 8
        ymarg 16
        group-by-list (if (coll? group-by) group-by [group-by])
        col-names (remove (set group-by-list) (:column-names data))
        col-count (count col-names)
        data-grouped (if (nil? group-by) {:x data} ($group-by group-by-list data))
        rectangles (apply merge (for [xn col-names yn col-names]  {[xn yn] (Rectangle.)}  ))
        xyplot (doto (XYPlot.)
                 (.setRenderer (doto (XYLineAndShapeRenderer. false true)
                                 (.setDrawOutlines true)
                                 (.setBaseFillPaint (Color. 0 0 0 0))
                                 (.setUseFillPaint true)
                                 (.setSeriesPaint 0 (Color/BLUE))
                                 (.setSeriesPaint 1 (Color/RED))
                                 (.setSeriesPaint 2 (Color/GREEN))
                                 (->>
                                  (vector)
                                  (apply (fn [x]
                                           (dotimes [i col-count]
                                             (let [c (.lookupSeriesPaint x i)
                                                   c2 (Color. (.getRed c) (.getGreen c) (.getBlue c) 48 )]
                                               (.setSeriesFillPaint x i c2))))))))
                 (.setRangeGridlinesVisible false)
                 (.setDomainGridlinesVisible false))
        histoplot (doto (XYPlot.)
                    (.setRenderer 1 (doto (XYBarRenderer.)
                                      (.setShadowVisible false)
                                      (.setSeriesPaint 0 (Color. 210 210 210))
                                      (.setBarPainter (StandardXYBarPainter.))))
                    (.setRenderer 0 (doto (XYSplineRenderer.)
                                      (.setShapesVisible false)
                                      (.setSeriesPaint 0 (Color. 170 170 170))
                                      (.setSeriesStroke 0 (BasicStroke. 3))))
                    (.setRangeGridlinesVisible false) ;; these lines do not fit to other range lines
                    (.setDomainGridlinesVisible false)) ; plots for the diagonal
        dataset-impl (fn [x-name y-name] (proxy [AbstractXYDataset] []
                                          ( getDomainOrder [] (DomainOrder/ASCENDING))
                                          ( getXValue [series item] (sel (nth (vals data-grouped) series) :rows item :cols x-name))
                                          ( getYValue [series item] (sel (nth (vals data-grouped) series) :rows item :cols y-name))
                                          ( getItemCount [series] (count (:rows (nth (vals data-grouped) series))))
                                          ( getSeriesKey [series] (str (nth (keys data-grouped) series)))
                                          ( getSeriesCount [] (count data-grouped))))
        histogram-dataset-impl (fn [name]
                                 (doto (HistogramDataset.)
                                   (.addSeries (str name) (double-array ($ name data)) (int bins))))
        color-for (fn [k] (-> xyplot .getRenderer (.lookupSeriesPaint k)))
        shape-for (fn [k] (-> xyplot .getRenderer (.lookupLegendShape k)))
        font-normal (.getBaseItemLabelFont (.getRenderer xyplot))
        font-bold (.deriveFont font-normal (Font/BOLD))
        legend (let [coll (LegendItemCollection.)]
                 (do
                   (doseq [[k v] (map-indexed vector (keys data-grouped))]
                     (.add coll (doto (LegendItem.
                                       (cond
                                        (map? v) (str (first (nfirst v)))
                                        :else (str v))
                                       "" "" ""
                                       (shape-for k)
                                       (color-for k))))))
                 (identity coll))
        draw-string-left (fn [g2 str x y]
                           (do
                             (let [metr (.getFontMetrics g2)
                                   w    (.stringWidth metr str)
                                   h    (.getHeight metr )]
                               (doto g2
                                 (.setPaint (Color. 255 255 255 128))
                                 (.fillRect x (- y (* h 0.75)) w h)
                                 (.setPaint (Color. 0 0 0))
                                 (.drawString str x y)))))
        draw-string-centered (fn [g2 str x y]
                               (let [metr (.getFontMetrics g2)
                                     w (.stringWidth metr str)
                                     h (.getHeight metr)
                                     xx (int (- x (/ w 2)))
                                     yy (int (+ y (/ h 2)))]
                                 (draw-string-left g2 str xx yy)))
        correlations  (memoize (fn [xn yn] (get (apply merge (for [x col-names y col-names]
                                                              { [x y] (correlation (sel data :cols x) (sel data :cols y)) }))
                                               (sort [xn yn]))))
        variances  (fn [xn] (get (apply merge (for [x col-names] {x (variance (sel data :cols x) ) })) xn ))
        col-names-ordered (take only-first (sort-by (fn [x] (- 0 (reduce + (map (fn [y] (abs (correlations x y))) col-names))))
                                                    col-names))
        key-matrix-all (identity (for [ [yk yv] (map-indexed vector col-names-ordered)
                                        [xk xv] (map-indexed vector col-names-ordered) ]
                                   (vector xk xv yk yv) ))
        key-matrix (if only-triangle (filter (fn [[a b c d]] (>= a c)) key-matrix-all) key-matrix-all)]
    (doto
        (JFreeChart.
         (proxy [Plot] []
           (getLegendItems [] (if (nil? group-by) nil legend))
           (getPlotType [] "Scatter-Plot-Matrix")
           (draw  [g2 area anchor parentState info]
             (let [rect (.createInsetRectangle (.getInsets this) area)
                   axis-space (AxisSpace.)
                   w  ($= ((.getWidth rect) - 2 * margin ) / col-count)
                   h  ($= ((.getHeight rect) - 2 * margin ) / col-count)]
               (do
                 (.drawBackground this g2 rect)
                 (.drawOutline this g2 rect)
                 (doto axis-space (.setLeft 0) (.setTop 0) (.setBottom 0) (.setRight 0))
                 (doseq [x [xyplot histoplot]]
                   (doto x
                     (.setInsets    (RectangleInsets. 1 1 1 1))
                     (.setDomainAxis (doto (NumberAxis. " ") (.setAutoRange true) (.setAutoRangeIncludesZero false)))
                     (.setRangeAxis  (doto (NumberAxis. "  ") (.setAutoRange true) (.setAutoRangeIncludesZero false)))
                     (.setFixedDomainAxisSpace axis-space)
                     (.setFixedRangeAxisSpace  axis-space)))
                 (dorun (map
                         (fn [ [ x-ind x-name y-ind y-name]]
                           (let [x (+ margin (* w x-ind) (.getX rect))
                                 y (+ margin (* h y-ind) (.getY rect))
                                 rect (doto (get rectangles [x-name y-name]) (.setBounds x y w h))
                                 plot (cond
                                       (== x-ind y-ind) (doto histoplot
                                                          (.setDataset 1 (histogram-dataset-impl x-name))
                                                          (.setDataset 0 (histogram-dataset-impl x-name)))
                                       :else (doto xyplot
                                               (.setDataset (dataset-impl x-name y-name))))]
                             (do
                               (cond
                                (== y-ind 0) (do
                                               (.setTickLabelsVisible (.getDomainAxis plot) (or (odd? x-ind) only-triangle))
                                               (.setDomainAxisLocation plot (AxisLocation/TOP_OR_LEFT))
                                               (.setTickMarksVisible (.getDomainAxis plot) true))
                                (== y-ind (- col-count 1)) (do
                                                             (.setTickLabelsVisible (.getDomainAxis plot) (even? x-ind))
                                                             (.setDomainAxisLocation plot (AxisLocation/BOTTOM_OR_RIGHT))
                                                             (.setTickMarksVisible (.getDomainAxis plot) true))
                                :else (do
                                        (.setTickLabelsVisible (.getDomainAxis plot) false)
                                        (.setTickMarksVisible (.getDomainAxis plot) false)))
                               (cond
                                (== x-ind 0) (do
                                               (.setTickLabelsVisible (.getRangeAxis plot) (odd? y-ind))
                                               (.setRangeAxisLocation plot (AxisLocation/TOP_OR_LEFT))
                                               (.setTickMarksVisible (.getRangeAxis plot) true))
                                (== x-ind (- col-count 1)) (do
                                                             (.setTickLabelsVisible (.getRangeAxis plot) (or (even? y-ind) only-triangle))
                                                             (.setRangeAxisLocation plot (AxisLocation/BOTTOM_OR_RIGHT))
                                                             (.setTickMarksVisible (.getRangeAxis plot) true))
                                :else (do
                                        (.setTickLabelsVisible (.getRangeAxis plot) false)
                                        (.setTickMarksVisible (.getRangeAxis plot) false)))
                                        ; we do have to handle the bottom right element - in case it has axes displayed.
                               (if (and (== x-ind y-ind (- col-count 1)))
                                 (do
                                   (.setVisible (.getRangeAxis histoplot) false)
                                   (.setDataset xyplot (dataset-impl x-name y-name))
                                   (.setTickLabelsVisible (.getRangeAxis xyplot) (odd? col-count))
                                   (.setRangeAxisLocation xyplot (AxisLocation/BOTTOM_OR_RIGHT))
                                   (.setTickMarksVisible (.getRangeAxis xyplot) true)
                                   (.setVisible (.getRangeAxis xyplot) true)
                                   (.draw (.getRangeAxis xyplot) g2 (- (.getMaxX rect) 1) rect rect RectangleEdge/RIGHT info)))
                               (identity (.draw plot g2 rect anchor parentState info))
                               (if (== x-ind y-ind)
                                 (let [str-name (str x-name)
                                       str-var (format "var %.3f\n" (variances x-name ))]
                                   (doto g2
                                     (.setPaint (Color/BLACK))
                                     (.setFont font-normal)
                                     (draw-string-left str-var (int (+ x xmarg)) (int (+ y ymarg)))
                                     (.setFont font-bold)
                                     (draw-string-centered str-name (int (+ x (/ w 2))) (int (+ y (/ h 2))))))
                                 (let [str-cor (format "corr %.3f" (correlations x-name y-name ))]
                                   (doto g2
                                     (.setPaint (Color/BLACK))
                                     (.setFont font-normal)
                                     (draw-string-left str-cor (int (+ x xmarg)) (int (+ y ymarg)))))
                                 ))))
                         key-matrix)))))))
      (.setTitle title)
      (.addSubtitle (doto
                        (TextTitle. (str group-by))
                      (.setPosition (RectangleEdge/BOTTOM)))))))

(defn scatter-plot-matrix
  "Returns a JFreeChart object displaying a scatter plot matrix for the given data.
   Use the 'view' function to display the chart or 'save' to write it to a file.

   Use:
   (scatter-plot-matrix & options)
   (scatter-plot-matrix data & options)

   Options:
   :data data (default $data) the data set for the plot.
   :title s (default \"Scatter Plot Matrix\").
   :bins n (default 10) number of bins (ie. bars) in histogram.
   :group-by grp (default nil) name of column or columns for grouping data.
   :only-first n (default 12) show only the first n most correlating columns of the data set.
   :only-triangle b (default false) shows only the upper triangle of the plot matix.

   Examples:

   (view (scatter-plot-matrix (get-dataset :iris) :bins 20 :group-by :Species ))
   (with-data (get-dataset :iris) (view (scatter-plot-matrix :bins 20 :group-by :Species )))
   (view (scatter-plot-matrix (get-dataset :chick-weight) :group-by :Diet :bins 20))
"

  ([& opts] (cond
             (even? (count opts)) (apply scatter-plot-matrix* opts)
             :else (apply scatter-plot-matrix* (apply merge  [:data (first opts)]  (rest opts))))))

(defn -main [& args]
     (view (scatter-plot-matrix (get-dataset :iris) :bins 20 :group-by :Species ) :width 800 :height 600)
  )


(comment
  ;;;Input examples for iris
  ;; Input dataset examples: Incanter data repo, local file, remote file (url)
  (def iris (get-dataset :iris))
  (def iris (read-dataset "data/iris.dat" :delim \space :header true)) ; relative to project home
  (def iris (read-dataset "https://raw.github.com/liebke/incanter/master/data/iris.dat" :delim \space :header true))
  ;; Filter dataset to specific columns only
  (def iris ($ [:Sepal.Length :Sepal.Width :Petal.Length :Petal.Width :Species] (get-dataset :iris)))
  (def iris (sel (get-dataset :iris) :cols [:Sepal.Length :Sepal.Width :Petal.Length :Petal.Width :Species] ))

  ;;; Scatter plot matrix examples
  ;; Using default options
  (def iris-spm (scatter-plot-matrix iris :group-by :Species))
  ;; filter to metrics only, no categorical dimension for grouping
  (def iris-spm (scatter-plot-matrix :data ($ [:Sepal.Length :Sepal.Width :Petal.Length :Petal.Width] iris)))

  ;; Using more options
  (def iris-spm (scatter-plot-matrix iris
                                     :title "Iris scatter plot matrix"
                                     :bins 20 ; number of histogram bars
                                     :group-by :Species
                                     :only-first 4 ; most correlating columns
                                     :only-triangle false))


  ;;;Output examples
  ;; View on Display
  (view iris-spm :width 1280 :height 800)
  ;; Save as PDF
  (save-pdf  iris-spm "out/iris-spm.pdf" :width 2560 :height 1600)
  ;; Save as PNG
  (save iris-spm "out/iris-spm.png" :width 2560 :height 1600)

  ;;
  (def airline ($ [:year :passengers :month] (read-dataset "https://raw.github.com/liebke/incanter/master/data/airline_passengers.csv" :header true)))
  (def airline-spm (scatter-plot-matrix airline  :group-by :month :bins 20 ))
  (view airline-spm)
  ;;; End of Comment
  )
