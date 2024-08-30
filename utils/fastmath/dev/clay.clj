(ns fastmath.dev.clay
  (:require [clojure.string :as str]
            [fastmath.core :as m]
            [fastmath.stats :as stats]
            [fastmath.random :as r]
            [scicloj.kindly.v4.kind :as kind]
            [scicloj.kindly.v4.api :as kindly]
            [fastmath.dev.ggplot :as gg]))

(def ^:const size 200)

(defn valid? [^double thr ^double v]
  (and (m/valid-double? v)
       (<= (m/abs v) thr)))

(defn invalid? [^double thr ^double v]
  (or (m/invalid-double? v)
      (> (m/abs v) thr)))

(defmacro callout
  [what title & forms]
  `(kind/fragment
    [(kind/md (str "::: {.callout-" ~what " title=\"" ~title "\"}"))
     ~@forms
     (kind/md (str ":::"))]))

(defmacro examples [& forms]
  `(->> (map (fn [form# value#] (str form# " ;; => " (pr-str value#)))
             ~(mapv (fn [form#] `'~form#) forms)
             ~(vec forms))
        (str/join "\n")
        kind/code
        kindly/hide-code))


(defmacro examples-note
  [& forms]
  `(callout "note" "Examples" (examples ~@forms)))


(defmacro table2
  [rows]
  `(kind/table {:column-names ["Symbol" "Info"]
                :row-vectors [~@(for [[s i] rows]
                                  `[(kind/code (pr-str (quote ~s))) (or ~i "-")])]}))


(let [dist (r/distribution :anderson-darling {:n 1})
      rnge :domain
      f (partial r/pdf dist)
      [dx dy :as dom] [-3.3 3.3]
      md (m/make-norm dx dy 5.0 (- size 5))
      xsys (m/sample f dx dy (* 2 size) true)
      [frx fry] (stats/extent (map second (filter (comp m/valid-double? second) xsys)))
      [rx ry] (case rnge
                :domain dom
                :zero [(min 0.0 frx)
                       (max 0.0 fry)]
                rnge)
      rx (or rx frx)
         ry (or ry fry)
         mr (m/make-norm rx ry (- size 5.0) 5.0)         
         r0 (mr 0)
         d0 (md 0)
         dticks (map md (remove zero? (range (m/qfloor dx) (m/qceil dy))))
         rticks (map mr (remove zero? (range (m/qfloor rx) (m/qceil ry))))] 
  {:r0 r0 :d0 d0 :dticks dticks :rticks rticks})

(defn- split-at-invalid-double
  [xs ^double thr]
  (loop [s xs
         buff []]
    (if-not (seq s)
      buff
      (let [[v inv] (split-with (comp (partial valid? thr) second) s)]
        (recur (drop-while (comp (partial invalid? thr) second) inv)
               (if (seq v) (conj buff v) buff))))))

(defn fgraph-int
  ([f] (fgraph-int f nil))
  ([f domain] (fgraph-int f domain :domain))
  ([f domain rnge] (fgraph-int f domain rnge size))
  ([f domain rnge size] (fgraph-int f domain rnge size {:title "Foo"}))
  ([f domain rnge size {:keys [title]}]
   (let [[dx dy :as dom] (or domain [-3.3 3.3])
         md (m/make-norm dx dy 5.0 (- size 5))
         xsys (m/sample f dx dy (* 2 size) true)
         [frx fry] (stats/extent (map second (filter (comp m/valid-double? second) xsys)))
         [rx ry] (case rnge
                   :domain dom
                   :zero [(min 0.0 frx)
                          (max 0.0 fry)]
                   rnge)
         rx (or rx frx)
         ry (or ry fry)
         mr (m/make-norm rx ry 5.0 (- size 5.0))         
         r0 (mr 0)
         d0 (md 0)
         dticks (map md (remove zero? (range (m/qfloor dx) (m/qceil dy))))
         rticks (map mr (remove zero? (range (m/qfloor rx) (m/qceil ry))))
         tt (let [ps (split-at-invalid-double xsys (* 1.1 (m/max (m/abs rx) (m/abs ry))))]
              (mapcat #(mapv (fn [[x y]] [(md x) (mr y)]) %) ps))
         [xs ys] (reduce (fn [acc [x fx]]
                           (-> acc (update 0 conj x) (update 1 conj fx)))
                         [[] []]
                         tt)]
     (gg/line-points2 xs ys title))))


(defmacro fgraph
  ([f] `(fgraph-int (fn [x#] (~f x#))))
  ([f domain] `(fgraph-int (fn [x#] (~f x#)) ~domain))
  ([f domain rnge] `(fgraph-int (fn [x#] (~f x#)) ~domain ~rnge))
  ([f domain rnge size] `(fgraph-int (fn [x#] (~f x#)) ~domain ~rnge ~size)))


(defn fgraph1
  ([f] (gg/->image (fgraph f)))
  ([f domain] (gg/->image (fgraph f domain)))
  ([f domain rnge] (gg/->image (fgraph f domain rnge)))
  ([f domain rnge size] (gg/->image (fgraph f domain rnge size))))
  

(defn dgraph-cont
  ([pdf-graph distr] (dgraph-cont pdf-graph distr nil))
  ([pdf-graph distr {:keys [pdf icdf data]
                     :or {pdf [0 1] icdf [0 0.999]}}]
   (let [pdf-plot (pdf-graph (if data data (partial r/pdf distr)) pdf [0 nil] 100 {:title "PDF"})
         cdf-plot (fgraph-int (partial r/cdf distr) pdf [0 1] 100 {:title "CDF"})
         icdf-plot (fgraph-int (partial r/icdf distr) icdf :zero 100 {:title "ICDF"})]
     (gg/->image (gg/dgraph pdf-plot cdf-plot icdf-plot)))))

(def dgraph (partial dgraph-cont fgraph-int))
