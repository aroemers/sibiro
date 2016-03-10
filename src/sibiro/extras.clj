(ns sibiro.extras
  "Clojure extras for sibiro."
  (:require [sibiro.core :as sc]))


;;; Route handling extras.

(defn wrap-routes
  "Wrap a handler with middleware that merges the result of
  `match-uri` using the given routes on the incoming request. Routes
  argument can be compiled or uncompiled. If request already contains
  a `:route-handler` key, matching is skipped, and the request is
  passed to the wrapped handler unchanged."
  [handler routes]
  (let [compiled (if (sc/compiled? routes) routes (sc/compile-routes routes))]
    (fn [request]
      (if-not (:route-handler request)
        (handler (merge request (sc/match-uri compiled (:uri request) (:request-method request))))
        (handler request)))))

(defn wrap-try-alts
  "Wraps the handler by merging in each entry from the `:alternatives`
  in the request one by one, until a non-nil response is given from
  the handler. The first time the request is passed as is."
  [handler]
  (fn [request]
    (loop [req request]
      (or (handler req)
          (when-let [alt (first (:alternatives req))]
            (recur (merge req alt {:alternatives (rest (:alternatives req))})))))))

(defn route-handler
  "A handler that calls the :route-handler of a request that went
  through `wrap-routes`."
  [request]
  (if-let [handler (:route-handler request)]
    (handler request)
    {:status 404 :body "Resource not found."}))

(defn make-handler
  "Returns a request handler that is the combination of
  `route-handler` with `wrap-try-alts` and `wrap-routes` applied to
  it. Routes argument can be compiled or uncompiled."
  [routes]
  (-> route-handler wrap-try-alts (wrap-routes routes)))


;;; Parameter binding request handler.

(defn- with-params-lookup [assym ormap asserts sym]
  `(or ~@(remove nil?
                 [`(some-> ~assym :route-params ~(keyword sym))
                  `(some-> ~assym :params ~(keyword sym))
                  `(some-> ~assym :params (get ~(str sym)))
                  (or (get ormap sym)
                      (when (asserts sym)
                        `(throw (ex-info ~(str "with-params assertion not met: " sym " not found")
                                         {:binding '~sym}))))])))

(defmacro with-params
  "Macro yielding a function that takes a ring request, and binds the
  given binding symbols to the corresponding value in :route-params or
  in :params (both keyword as string lookup) in the request. There are
  also some special keywords available:

  - Prepending a symbol with :as will bind that symbol with
  the entire request.

  - Prepending a map with :or defines default values.

  - Prepending a sequence with :assert will assert that the symbols in
  the sequence are found. Instead of a sequence, you can also set it
  to :all.

  For example:

  (def create-user
    (with-params [name email password
                  :or {name \"Anonymous\"}
                  :assert (email password)
                  :as request]
      ...))"
  [bindings & body]
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
    (assert (symbol? assym)                  "binding for :as must be a symbol")
    (assert (or (nil? ormap) (map? ormap))   "binding for :or must be a map")
    (assert (every? (set syms) (keys ormap)) "keys in :or map must be subset of binding symbols")
    (assert (every? symbol? asserts)         "assert list can only hold symbols")
    (assert (every? (set syms) asserts)      "symbols in :assert list must be subset of bindings")
    `(fn [~assym]
       (let [~@(for [sym syms
                     form [sym (with-params-lookup assym ormap asserts sym)]]
                 form)]
         ~@body))))
