(ns duro.talk-test
  (:require
   [clojure.test :refer [testing is are deftest]]
   [duro.core :as core :refer [with-module]]
   [duro.io]
   [duro.vcd]
   [duro.verilator :as verilator]

   [clojure.math.combinatorics :as combo]
   [com.billpiel.sayid.core :as sd]
   [com.billpiel.sayid.trace :as tr]
   [clojure.string :as str]
   [clojure.set :as set]))

(comment

  (sd/ws-clear-log!)
  (-> @sd/workspace :traced tr/audit-traces)
  (sd/ws-add-trace-ns! duro.verilator)
  (sd/ws-enable-all-traces!)
  (sd/ws-deref!)

  ())

(deftest not-test
  (with-module module "nao.v"
    {:trace? true
     :trace-path "naso.vcd"}
    (let [{:keys [:top]} module]
      (duro.core/dump-values top 0)
      (duro.io/eval top {:nao.i/x 0})
      (duro.core/dump-values top 10)
      (duro.io/eval top {:nao.i/x 1})
      (duro.core/dump-values top 20)
      (duro.core/dump-values top 30))))


(comment
  ;; 2
  (def x "gnwj gfw eaddagf ewjuzsflk sfv ugmflafy")

  (char (+ 97 (mod (- (+ 97 5) 97) 26)))

  (int \ )

  (mapv
   (fn [i]
     (->> x
          (mapv int)
          (mapv #(if (not= % 32)
                   (+ (mod (- (- % i) 97) 26) 97)
                   32))
          (mapv char)
          (str/join "")))
   (range 30))

  ())

(comment
  ;; 3
  (def x
    [[:1 [:head :iphone :ps4 :apple-tv :roku]]
     [:2 [:iphone :switch :airpods]]
     [:3 [:iphone :switch :airpods]]
     [:4 [:iphone :airpods]]
     [:5 [:head :ps4 :switch :chromecast :roku]]
     [:6 [:iphone :switch :chromecast :airpods]]
     [:7 [:head :ps4 :airpods :roku :chromecast]]])

  (def items [:head :iphone :ps4 :apple-tv :roku])

  (->> (for [y (combo/permutations x)]
         (reduce (fn [{:keys [:dist :acc]} [p items]]
                   (if-let [item
                            (some #(when (not (contains? acc (last %)))
                                     %)
                                  (map-indexed vector items))]
                     {:dist (assoc dist p item)
                      :acc (conj acc (last item))}
                     {:dist dist
                      :acc acc}))
                 {:dist {}
                  :acc #{}}
                 y))
       (remove nil?)
       (mapv :dist)
       (filter #(= (count %) 7))
       (sort-by #(->> %
                      (mapv val)
                      (mapv first)
                      (reduce + 0)))
       (take 10))

  #_(->> (reduce (fn [{:keys [:dist :acc]} [p items]]
                 (let [item
                       (some #(when (not (contains? acc (last %)))
                                %)
                             (map-indexed vector items))]
                   {:dist (assoc dist p item)
                    :acc (conj acc (last item))}))
               {:dist {}
                :acc #{}}
               x)
       :dist
       (mapv val)
       (mapv first)
       (reduce + 0))

  (def acc #{:head})

  ())

(comment
  ;; 4
  (def x
    {:1993 [7032 5023 12024 4362 3540 5932 9620]
     :2003 [14862 12654 25463 2753 4452 4976 4062]
     :2008 [29086 25433 36963 8072 9833 7749 5183]
     :2019 [94072 20745 21945 21308 19723 15257 7243]})

  (/ (reduce + 0.0 (:2003 x))
     (reduce + 0.0 (:1993 x)))

  (/ (reduce + 0.0 (:2019 x))
     (reduce + 0.0 (:2008 x)))

  ())
