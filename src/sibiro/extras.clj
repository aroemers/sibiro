(ns sibiro.extras
  "Clojure extras for sibiro."
  (:require [sibiro.core :as sc]))

(defn wrap-routes
  "Wrap a handler with middleware that merges the result of
  `match-uri` using the given routes on the incoming request."
  [handler routes]
  (let [compiled (if (sc/compiled? routes) routes (sc/compile-routes routes))]
    (fn [request]
      (if-not (:route-handler request)
        (let [match (sc/match-uri compiled (:uri request) (:request-method request))]
          (handler (merge request match)))
        (handler request)))))

;;---TODO Add ring.util.response to this handler?
(defn route-handler
  "A handler that calls the :route-handler of a request that went
  through `wrap-routes`."
  [request]
  (if-let [handler (:route-handler request)]
    (handler request)
    (throw (IllegalStateException. "The wrapped-handler is not wrapped with wrap-routes."))))

(defn make-handler
  "Returns a request handler that is the combination of
  `route-handler` with `wrap-routes` applied to it."
  [routes]
  (wrap-routes route-handler routes))


(comment
  ;; Above is just an easy default. The routing namespaces also
  ;; supports stuff like that of https://github.com/xsc/ronda-routing.
  ;; For example:

  ;; Let the handler value be a pair: a function and a set
  ;; of "behaviours".
  (def routes (sc/compile-routes
               [[:get  "/login"     [login-page     #{:public}]]
                [:post "/login"     [login-handler  #{:public}]]
                [:get  "/dashboard" [dashboard-page           ]]
                [:get  "/admin"     [admin-page      #{:admin}]]
                [:any  "/rest"      [liberator        #{:json}]]]))

  ;; The base handler simply takes the first value of the matched
  ;; route handler and calls it.
  (defn my-route-handler [request]
    ((first (:route-handler request)) request))

  ;; This middleware wraps the given handler with those middlewares
  ;; for which its predicate (a function that receives the behaviours
  ;; set of the matched route) returns a truthy value, in the order
  ;; the are defined.
  (defn wrap-conditional-middleware [handler middlewares]
    (fn [request]
      (let [wrapped (reduce (fn [h [pred wrapper]]
                              (if (pred (second (:route-handler request)))
                                (wrapper h)
                                h))
                            handler
                            middlewares)]
        (wrapped request))))

  ;; Simply wrap the base handler up, and voila, conditional
  ;; middleware per route.
  (-> my-route-handler
      (wrap-conditional-middleware [[:json                wrap-json]
                                    [:admin               wrap-admin?]
                                    [(complement :public) wrap-logged-in?]])
      (wrap-routes routes)))
