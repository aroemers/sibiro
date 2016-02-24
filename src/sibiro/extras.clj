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

;;---TODO Add ring.util.response to this handler?
(defn route-handler
  "A handler that calls the :route-handler of a request that went
  through `wrap-routes`."
  [request]
  (if-let [handler (:route-handler request)]
    (handler request)
    {:status 404 :body "Resource not found."}))

(defn make-handler
  "Returns a request handler that is the combination of
  `route-handler` with `wrap-routes` applied to it. Routes argument
  can be compiled or uncompiled."
  [routes]
  (wrap-routes route-handler routes))
