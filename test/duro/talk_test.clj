(ns duro.talk-test
  (:require
   [clojure.test :refer [testing is are deftest]]
   [duro.core :as core :refer [with-module]]
   [duro.io]
   [duro.vcd]
   [duro.verilator :as verilator]))

(deftest not-test
  (with-module module "nao.v"
    {:trace? true
     :trace-path "nao.vcd"}
    (let [{:keys [:top]} module]
      (duro.core/dump-values top 0)
      (duro.io/eval top {:nao.i/x 0})
      (duro.core/dump-values top 10)
      (duro.io/eval top {:nao.i/x 1})
      (duro.core/dump-values top 20)
      (duro.core/dump-values top 30))))
