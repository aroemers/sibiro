(ns sibiro.params
   "ERPERIMENTAL! EVERYTHING IS SUBJECT TO CHANGE IN THIS NAMESPACE!")

;;; Private helpers.

(defn- name-with-attrs [name [arg1 arg2 & argx :as args]]
  (let [[attrs args] (cond (and (string? arg1) (map? arg2)) [(assoc arg2 :doc arg1) argx]
                           (string? arg1)                   [{:doc arg1} (cons arg2 argx)]
                           (map? arg1)                      [arg1 (cons arg2 argx)]
                           :otherwise                       [{} args])]
    [(with-meta name (merge (meta name) attrs)) args]))


;;; Main macro

(defmacro with-params
  "Macro binding the given symbols around the body to the
  corresponding values in :route-params or :params (both keyword as
  string lookup) from the request. There are also some special keywords
  available:

  - Prepending a symbol with :as will bind that symbol with the entire
  request. Instead of a symbol, a destructuring expression can also be
  specified.

  - Prepending a map with :or defines default values. If no default
  value is specified, and the binding cannot be found in the request
  parameters, an exception is thrown. It basically asserts for you
  that all parameters are found or at least have a value.

  For example:

  (fn [req]
    (with-params [name email password
                  :or {name \"Anonymous\"}
                  :as {:keys [uri]}] req
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
        ormap (:or specials)]
    (assert (every? symbol? syms)               "bindings can only be symbols or special keywords")
    (assert (every? #{:as :or} (keys specials)) "supported binding keywords are :or, :as and :assert")
    (assert (or (nil? ormap) (map? ormap))      "binding for :or must be a map")
    (assert (every? (set syms) (keys ormap))    "keys in :or map must be subset of binding symbols")
    `(let [~assym ~reqsym
           ~@(for [sym syms
                   form [sym `(or (some-> ~reqsym :route-params ~(keyword sym))
                                  (some-> ~reqsym :params ~(keyword sym))
                                  (some-> ~reqsym :params (get ~(str sym)))
                                  (throw (ex-info ~(str "param not found: " sym " not found")
                                                  {:binding '~sym})))]]
               form)]
       ~@body)))


;;; Convenience macros

(defmacro fnp
  "Same as `(fn [req] (with-params bindings req body))`"
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

(defmacro defmethodp
  "Same as `(defmethod name dispatch [req]
  (with-params bindings req body))`."
  [name dispatch bindings & body]
  `(defmethod ~name ~dispatch
     [request#]
     (with-params ~bindings request#
       ~@body)))
