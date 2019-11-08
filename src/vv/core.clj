(ns vv.core
  (:require [tech.jna :as jna]))

(jna/def-jna-fn "c" memset
        "Set byte memory to a value"
        com.sun.jna.Pointer ;;void* return value
        [data identity]     ;;Each argument has a coercer-fn. Pointers can be lots of types.
        [value int]         ;;read docs for memset
  [n-bytes jna/size-t])

(def test-ary (float-array [1 2 3 4]))

(vec test-ary)

(memset test-ary 0 (* 4 Float/BYTES))
