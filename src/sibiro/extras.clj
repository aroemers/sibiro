(ns sibiro.extras
  "Clojure extras for sibiro."
  (:require [sibiro.core :as sc]))


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
  "A handler that calls the :route-handler of a request map that went
  through `wrap-routes`. Optionally, one can specify another function
  that gets the actual handler function from/for the `:route-handler`
  value, by creating a handler using `(partial route-handler f)`."
  ([request]
   (route-handler identity request))
  ([transf request]
   (if-let [route-handler (:route-handler request)]
     (if-let [handler (transf route-handler)]
       (handler request)
       {:status 500 :body "Not implemented."})
     {:status 404 :body "Not found."})))

(defn make-handler
  "Returns a request handler that is the combination of
  `route-handler` with `wrap-try-alts` and `wrap-routes` applied to
  it. Routes argument can be compiled or uncompiled."
  [routes]
  (-> route-handler wrap-try-alts (wrap-routes routes)))
