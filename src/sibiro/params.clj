(ns sibiro.params
  "Parameter binding request handling.")


;;; Private helpers.

(defn- name-with-attrs [name [arg1 arg2 & argx :as args]]
  (let [[attrs args] (cond (and (string? arg1) (map? arg2)) [(assoc arg2 :doc arg1) argx]
                           (string? arg1)                   [{:doc arg1} (cons arg2 argx)]
                           (map? arg1)                      [arg1 (cons arg2 argx)]
                           :otherwise                       [{} args])]
    [(with-meta name (merge (meta name) attrs)) args]))

(defn- with-params-lookup [reqsym ormap asserts sym]
  `(or ~@(remove nil?
                 [`(some-> ~reqsym :route-params ~(keyword sym))
                  `(some-> ~reqsym :params ~(keyword sym))
                  `(some-> ~reqsym :params (get ~(str sym)))
                  (or (get ormap sym)
                      (when (asserts sym)
                        `(throw (ex-info ~(str "with-params assertion not met: " sym " not found")
                                         {:binding '~sym}))))])))

;;; Main macro

(defmacro with-params
  "Macro binding the given symbols around the body to the
  corresponding values in :route-params or in :params (both keyword as
  string lookup) in the request. There are also some special keywords
  available:

  - Prepending a symbol with :as will bind that symbol with the entire
  request. Instead of a symbol, a destructuring expression can also be
  specified.

  - Prepending a map with :or defines default values.

  - Prepending a sequence with :assert will assert that the symbols in
  the sequence are found. Instead of a sequence, you can also set it
  to :all.

  For example:

  (fn [req]
    (with-params [name email password
                  :or {name \"Anonymous\"}
                  :assert (email password)
                  :as request] req
      ...))"
  [bindings reqsym & body]
  (let [[syms specials] (reduce (fn [[syms specials keyw] binding]
                                  (if keyw
                                    [syms (assoc specials keyw binding) nil]
                                    (if (keyword? binding)
                                      [syms specials binding]
                                      [(conj syms binding) specials nil])))
                                [nil nil nil]
                                bindings)
        assym (:as specials (gensym "request-"))
        ormap (:or specials)
        asserts (if (= (:assert specials) :all)
                  (set syms)
                  (set (:assert specials)))]
    (assert (every? symbol? syms)            "bindings can only be symbols or special keywords")
    (assert (every? #{:as :or :assert}
                    (keys specials))         "supported binding keywords are :or, :as and :assert")
    (assert (or (nil? ormap) (map? ormap))   "binding for :or must be a map")
    (assert (every? (set syms) (keys ormap)) "keys in :or map must be subset of binding symbols")
    (assert (every? symbol? asserts)         "assert list can only hold symbols")
    (assert (every? (set syms) asserts)      "symbols in :assert list must be subset of bindings")
    `(let [~assym ~reqsym
           ~@(for [sym syms
                   form [sym (with-params-lookup reqsym ormap asserts sym)]]
               form)]
       ~@body)))


;;; Convenience macros

(defmacro fnp
  "Same as `(fn [req] (with-params bindings req ...))`"
  [bindings & body]
  `(fn [reqsym#]
     (with-params ~bindings reqsym# ~@body)))

(defmacro defnp
  "Same as `(def name (fnp bindings body))`. Supports docstring,
  attribute map, and metadata on the name symbol."
  [name & body]
  (let [[name [bindings & body]] (name-with-attrs name body)]
    `(def ~name (fnp ~bindings ~@body))))

(defmacro defnp-
  "Same as `defnp`, but private."
  [name & body]
  `(defnp ~(with-meta name (merge (meta name) {:private true}))
     ~@body))
