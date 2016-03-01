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

(defn wrap-routes-alts
  "Same as wrap-routes, but uses `match-uris` instead of `match-uri`
  internally."
  [handler routes]
  (let [compiled (if (sc/compiled? routes) routes (sc/compile-routes routes))]
    (fn [request]
      (if-not (:route-handler request)
        (handler (merge request (sc/match-uris compiled (:uri request) (:request-method request))))
        (handler request)))))

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

(defn wrap-alternatives
  "Wraps the handler by merging in each entry from the `:alternatives`
  in the request one by one, until a non-nil response is given from
  the handler. On the first try, nothing is merged in the request."
  [handler]
  (fn [request]
    (loop [alts (cons {} (:alternatives request))]
      (when-let [alt (first alts)]
        (if-let [response (handler (merge request alt))]
          response
          (recur (rest alts)))))))
