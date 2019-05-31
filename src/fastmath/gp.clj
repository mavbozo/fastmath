(ns fastmath.gp
  "Gaussian Process

  Based on: https://www.cs.ubc.ca/~nando/540-2013/lectures/gp.py"
  (:require [fastmath.kernel :as k]
            [fastmath.core :as m]
            [fastmath.stats :as stats]
            [fastmath.random :as r]
            [fastmath.vector :as v])
  (:import [org.apache.commons.math3.linear MatrixUtils CholeskyDecomposition Array2DRowRealMatrix ArrayRealVector RealMatrix]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)
(m/use-primitive-operators)


;; Gaussian process

(defn- kernel-cov-matrix
  (^Array2DRowRealMatrix [kernel ^double scale xss xss*]
   (MatrixUtils/createRealMatrix
    (m/seq->double-double-array (for [x xss]
                                  (map #(* scale ^double (kernel x %)) xss*)))))
  (^Array2DRowRealMatrix [kernel xss xss*] (kernel-cov-matrix kernel 1.0 xss xss*)))

(defn- ensure-vectors
  [xs]
  (if (sequential? (first xs)) xs (mapv vector xs)))

(defprotocol GPProto
  (prior-samples [gp X] "Draw samples from prior for given X")
  (predict-all [gp X] [gp X stddev?] "Predict means and stddev for given vector of X")
  (predict [gp X] [gp X stddev?] "Predict means and stddev for given x value")
  (posterior-samples [gp X] [gp X stddev?] "Draw samples from posterior for given X"))

(defn gaussian-process
  "Create Gaussian Process."
  ([] (gaussian-process [0] [0]))
  ([xs y] (gaussian-process xs y {}))
  ([xs y {:keys [^double kscale kernel noise normalize?]
          :or {kscale 1.0 kernel (k/kernel :gaussian 1.0) normalize? false}}]
   (let [xs (ensure-vectors xs)
         ymean (if normalize? (stats/mean y) 0.0)
         ^ArrayRealVector ys (MatrixUtils/createRealVector (m/seq->double-array (map #(- ^double % ymean) y)))
         ^RealMatrix data-cov (kernel-cov-matrix kernel kscale xs xs)
         noise-fn #(if (sequential? noise)
                     (take (count %) (cycle noise))
                     (repeat (count %) (or noise 1.0e-6)))
         ^RealMatrix diag (MatrixUtils/createRealDiagonalMatrix (m/seq->double-array (noise-fn xs)))
         ^CholeskyDecomposition chol (CholeskyDecomposition. (.add data-cov diag))
         L (.getL chol)
         alpha (.solve (.getSolver chol) ys)]
     
     (MatrixUtils/solveLowerTriangularSystem L ys)
     
     (reify GPProto
       (prior-samples [_ xs]
         (let [xs (ensure-vectors xs)
               ^RealMatrix cov (kernel-cov-matrix kernel kscale xs xs)
               ^RealMatrix diag (MatrixUtils/createRealDiagonalMatrix (m/seq->double-array (noise-fn xs)))
               ^RealMatrix Lp (.getL ^CholeskyDecomposition (CholeskyDecomposition. (.add cov diag)))]
           (seq (.operate Lp (m/seq->double-array (repeatedly (count xs) r/grand))))))
       
       (predict-all [gp xtest] (predict-all gp xtest false))
       (predict-all [_ xtest stddev?]
         (let [xtest (ensure-vectors xtest)
               ^RealMatrix cov (kernel-cov-matrix kernel kscale xs xtest)
               cov-v (mapv #(.getColumnVector cov %) (range (.getColumnDimension cov)))]
           (let [mu (mapv #(+ ymean (.dotProduct alpha %)) cov-v)]
             (if-not stddev?
               mu
               (do
                 (run! #(MatrixUtils/solveLowerTriangularSystem L %) cov-v)
                 (let [^RealMatrix k2 (kernel-cov-matrix kernel kscale xtest xtest)
                       stddev (map (fn [^long id ^double v] (m/safe-sqrt (- ^double (.getEntry k2 id id) v)))
                                   (range (count xtest))
                                   (map #(.dotProduct ^ArrayRealVector % ^ArrayRealVector %) cov-v))]
                   [mu stddev]))))))
       
       (predict [gp xval] (predict gp xval false))
       (predict [_ xval stddev?]
         (let [xtest (if (sequential? xval) xval [xval])
               cov-vector (double-array (map #(* kscale ^double (kernel xtest %)) xs))]
           (let [mu (+ ymean ^double (v/dot cov-vector (.getDataRef ^ArrayRealVector alpha)))]
             (if-not stddev?
               mu
               (let [cov-v (MatrixUtils/createRealVector cov-vector)]
                 (MatrixUtils/solveLowerTriangularSystem L cov-v)
                 [mu (m/safe-sqrt (- 1.0 (.dotProduct cov-v cov-v)))])))))

       (posterior-samples [gp xtest] (posterior-samples gp xtest false))
       (posterior-samples [_ xtest stddev?]
         (let [xtest (ensure-vectors xtest)
               ^RealMatrix cov (kernel-cov-matrix kernel kscale xs xtest)
               cov-v (mapv #(.getColumnVector cov %) (range (.getColumnDimension cov)))]
           (run! #(MatrixUtils/solveLowerTriangularSystem L %) cov-v)
           (let [Lk (MatrixUtils/createRealMatrix (m/seq->double-double-array (map #(.getDataRef ^ArrayRealVector %) cov-v)))
                 diag (MatrixUtils/createRealDiagonalMatrix (m/seq->double-array (noise-fn xtest)))
                 ^RealMatrix k2 (kernel-cov-matrix kernel kscale xtest xtest)
                 ^RealMatrix Lp (.getL ^CholeskyDecomposition (CholeskyDecomposition. (.add k2 (.subtract ^RealMatrix diag (.multiply ^RealMatrix Lk (.transpose Lk)))))) ;; opposite than in source
                 mu (map (fn [v ^double n]
                           (+ ymean (.dotProduct ys v) n)) cov-v (seq (.operate Lp (m/seq->double-array (repeatedly (count xtest) r/grand)))))]
             (if-not stddev?
               mu
               (let [stddev (map (fn [^long id ^double v] (m/sqrt (- ^double (.getEntry k2 id id) v)))
                                 (range (count xtest))
                                 (map #(.dotProduct ^ArrayRealVector % ^ArrayRealVector %) cov-v))]
                 [mu stddev])))))))))


#_(time (let [N 20
              gp (gaussian-process (repeatedly 10 (partial r/drand -10 10))
                                   (repeatedly 10 (partial r/grand 0.2)))]
          (posterior-samples gp (map #(m/norm % 0 (dec N) -5.0 5.0) (range N)) true)))

;; bayesian optimization with GP

;;

