(ns sibiro.extras
  "Clojure extras for sibiro."
  (:require [sibiro.core :as sc]))

(defn wrap-routes
  "Wrap a handler with middleware that merges the result of
  `match-uri` using the given routes on the incoming request. Routes
  argument can be compiled or uncompiled."
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
  (-> route-handler wrap-try-alts (wrap-routes-alts routes)))
