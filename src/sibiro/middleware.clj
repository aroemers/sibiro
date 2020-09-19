(ns sibiro.middleware
  "Middleware for the various parts of sibiro routing. These can be
  combined in several ways to work with a routes datastructure you
  want."
  (:require [sibiro.route-matching :as route]
            [sibiro.tag-matching :as tag]))

(defn wrap-handle-route
  "Wrap the given handler by calling the request's :routes value as the
  request handler, if non-nil. Otherwise the wrapped handler is
  called."
  [handler]
  (fn [request]
    (if-let [route-handler (:routes request)]
      (route-handler request)
      (handler request))))

(defn wrap-match-method
  "Wrap the given handler by updating the :routes key to the match on
  the request's :request-method or :any. If no match is found, :routes
  will be nil."
  [handler]
  (fn [request]
    (handler (route/match-method-request request))))

(defn wrap-assoc-tag
  "Wrap the given handler by adding an :route-tag entry to the request,
  set to the :tag value of the current :routes value."
  [handler]
  (fn [request]
    (handler (assoc request :route-tag (some-> request :routes :tag)))))

(defn wrap-match-uri
  "Wrap the given handler by updating the :routes key to the match on
  the requests's :uri. If no match is found, :routes will be nil."
  [handler]
  (fn [request]
    (handler (route/match-uri-request request))))

(defn wrap-routes
  "Wrap the given handler by adding the given routes to the request
  under the :routes key."
  [handler routes]
  (fn [request]
    (handler (assoc request :routes @routes))))
