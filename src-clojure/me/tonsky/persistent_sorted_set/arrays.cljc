(ns ^:no-doc me.tonsky.persistent-sorted-set.arrays
  (:require
   [clojure.string :as str])
  (:refer-clojure :exclude [make-array into-array array amap aget aset alength array? aclone])
  #?(:cljs (:require-macros me.tonsky.persistent-sorted-set.arrays))
  #?@(:cljd []
      :clj
      [(:import [java.util Arrays])]))


(defn- if-cljs [env then else]
  (if (:ns env) then else))


#?(:cljd
   (defn ^List make-array [size]
     ;; TODO: fixed length or not?
     (.filled #/(List dynamic) size nil))
   :cljs
   (defn ^array make-array [size] (js/Array. size))
   :clj
   (defn make-array ^{:tag "[[Ljava.lang.Object;"} [size]
     (clojure.core/make-array java.lang.Object size)))


#?(:cljd
   (defn ^List into-array [aseq]
     (let [arr (.empty #/(List dynamic) .growable true)]
       (doseq [x aseq]
         (.add arr x))
       arr))
   :cljs
   (defn ^array into-array [aseq]
     (reduce (fn [a x] (.push a x) a) (js/Array.) aseq))
   :clj
   (defn into-array ^{:tag "[[Ljava.lang.Object;"} [aseq]
     (clojure.core/into-array java.lang.Object aseq)))


#?(:cljd
   (defmacro aget [arr i]
     `(. ~arr "[]" ~i))
   :clj
   (defmacro aget [arr i]
     (if-cljs &env
       (list 'js* "(~{}[~{}])" arr i)
       `(clojure.lang.RT/aget ~(vary-meta arr assoc :tag "[[Ljava.lang.Object;") (int ~i)))))


#?(:cljd
   (defmacro alength [arr]
     `(.-length ~arr))
   :clj
   (defmacro alength [arr]
     (if-cljs &env
       (-> (list 'js* "~{}.length" arr)
           (vary-meta assoc :tag 'number))
       `(clojure.lang.RT/alength ~(vary-meta arr assoc :tag "[[Ljava.lang.Object;")))))


#?(:cljd
   (defmacro aset [arr i v]
     `(. ~arr "[]=" ~i ~v))
   :clj
   (defmacro aset [arr i v]
     (if-cljs &env
       (list 'js* "(~{}[~{}] = ~{})" arr i v)
       `(clojure.lang.RT/aset ~(vary-meta arr assoc :tag "[[Ljava.lang.Object;") (int ~i) ~v))))


#?(:cljd
   (defmacro array [& args]
     `(doto (.filled #/(List dynamic) ~(count args) nil)
        ~@(map-indexed
           (fn [i arg]
             `(. "[]=" ~i ~arg))
           args)))
   :clj
   (defmacro array [& args]
     (if-cljs &env
       (->
        (list* 'js* (str "[" (str/join "," (repeat (count args) "~{}")) "]") args)
        (vary-meta assoc :tag 'array))
       (let [len (count args)]
         (if (zero? len)
           'clojure.lang.RT/EMPTY_ARRAY
           `(let [arr# (clojure.core/make-array java.lang.Object ~len)]
              (doto ^{:tag "[[Ljava.lang.Object;"} arr#
                ~@(map #(list 'aset % (nth args %)) (range len)))))))))


#?(:cljd
   (defmacro acopy [from from-start from-end to to-start]
     `(let [l# (- ~from-end ~from-start)]
        (dotimes [i# l#]
          (aset ~to (+ i# ~to-start) (aget ~from (+ i# ~from-start))))))
   :clj
   (defmacro acopy [from from-start from-end to to-start]
     (if-cljs &env
       `(let [l# (- ~from-end ~from-start)]
          (dotimes [i# l#]
            (aset ~to (+ i# ~to-start) (aget ~from (+ i# ~from-start)))))
       `(let [l# (- ~from-end ~from-start)]
          (when (pos? l#)
            (System/arraycopy ~from ~from-start ~to ~to-start l#))))))


#?(:cljd
   (def aclone cljd.core/aclone)
   :default
   (defn aclone [from]
     #?(:clj  (Arrays/copyOf ^{:tag "[[Ljava.lang.Object;"} from (alength from))
        :cljs (.slice from 0))))


(defn aconcat [a b]
  #?(:cljd (let [combined (.from #/(List dynamic) a)]
             (.addAll combined b)
             combined)
     :cljs (.concat a b)
     :clj  (let [al  (alength a)
                 bl  (alength b)
                 res (Arrays/copyOf ^{:tag "[[Ljava.lang.Object;"} a (+ al bl))]
             (System/arraycopy ^{:tag "[[Ljava.lang.Object;"} b 0 res al bl)
             res)))


#?(:cljd
   (defn ^List amap
     [f ^List arr]
     ;; TODO: should I use .map and .asList ? is that better?
     (let [len (cljd.core/alength arr)
           res (cljd.core/aclone arr)]
       (loop [idx 0]
         (if (< idx len)
           (let []
             (cljd.core/aset res idx (f (cljd.core/aget arr idx)))
             (recur (inc idx)))
           res))))
   :cljs
   (defn amap [f arr]
     (.map arr f))
   :clj
   (defn amap 
     ([f arr]
      (amap f Object arr))
     ([f type arr] ;; TODO check if faster in Java
      (let [res (clojure.core/make-array type (alength arr))]
        (dotimes [i (alength arr)]
          (aset res i (f (aget arr i))))
        res))))


(defn asort [arr cmp]
  #?(:cljd (let [^List arr arr] (doto arr (.sort (fn ^int [a b] (cmp a b)))))
     :cljs (.sort arr cmp)
     :clj  (doto arr (Arrays/parallelSort cmp))))


#?(:cljd
   (defn array? [x]
     (dart/is? x List))
   :cljs
   (defn ^boolean array? [x]
     (if (identical? *target* "nodejs")
       (.isArray js/Array x)
       (instance? js/Array x)))
   :clj
   (defn array? [^Object x]
     (some-> x .getClass .isArray)))


#?(:clj
   (defmacro alast [arr]
     `(let [arr# ~arr]
        (aget arr# (dec (alength arr#))))))

#?(:cljd
   (defn equiv-sequential
     "Assumes x is sequential. Returns true if x equals y, otherwise
  returns false."
     [x y]
     (boolean
      (when (sequential? y)
        (if (and (counted? x) (counted? y)
                 (not (== (count x) (count y))))
          false
          (loop [xs (seq x) ys (seq y)]
            (cond (nil? xs) (nil? ys)
                  (nil? ys) false
                  (= (first xs) (first ys)) (recur (next xs) (next ys))
                  :else false)))))))

#?(:cljd
   (defmacro caching-hash [coll hash-fn hash-key]
     `(let [h# ~hash-key]
        (if-not (nil? h#)
          h#
          (let [h# (~hash-fn ~coll)]
            (set! ~hash-key h#)
            h#)))))

#?(:cljd
   ;; cljd doesn't have it's own unsigned bit shift yet
   ;; TODO: 32 bit is probbly not good enough
   (defn ^int unsigned-bit-shift-right
     [^int x n] (bit-shift-right (bit-and x 0xffffffff) n)))

#?(:clj
   (defmacro half [x]
     `(unsigned-bit-shift-right ~x 1)))


#?(:clj
  (def array-type
    (memoize
      (fn [type]
        (.getClass ^Object (java.lang.reflect.Array/newInstance ^Class type 0))))))
