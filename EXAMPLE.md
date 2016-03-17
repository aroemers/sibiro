## Benefits

The reason why using datastructures to define routes are beneficial and prefered is made perfectly clear in [this presentation](https://www.youtube.com/watch?v=3oQTSP4FngY).
In short, for a routing library using datastructures (instead of macros), it means that URI generating (i.e. the _bi_-directional support) is easy, and routes can be shared, transformed and programmatically generated.

The fact that the routes are not bound to a handler per se is also beneficial, as it decomplects two concerns: that of route matching and that of request handling.
This is very well explained in the [sales pitch](https://github.com/xsc/ronda-routing#official-sales-pitch) of the ronda-routing library.
What's more, with sibiro you only need to know core Clojure to add interesting behaviour to your routes and route handling; there are no extension points or type systems to learn.
The example below shows this as well.

### Example of more intricate use of sibiro basics

With above sales pitch in mind, let's have an example of how sibiro can be used as well: **static conditional middleware and external regular expressions for route parameters**.
Because the routes are just data, they can easily be preprocessed, before compiling them.
A nice helper function for this, is the following:

```clj
(defn process-route-handlers
  "Wrap the handler of the given routes by the given wrapper functions."
  [routes & wrappers]
  (map (fn [[method path handler tag]]
         [method path (reduce #(%2 %1) handler wrappers) tag])
       routes))
```

As said before, the handler in the route can be any object.
So it can also be a map: a map with a handler function, a map of route parameter regular expressions, and a set of "behaviours".

```clj
(def routes
  [[:get  "/login"          {:handler login-page     :behaviours #{:public}                      }]
   [:post "/login"          {:handler login-handler  :behaviours #{:public}                      }]
   [:get  "/dashboard"      {:handler dashboard-page                                             }]
   [:get  "/admin"          {:handler admin-page     :behaviours #{:admin}                       }]
   [:post "/admin/user/:id" {:handler admin-user     :behaviours #{:admin}  :regexes {:id #"\d+"}}]
   [:any  "/rest/*"         {:handler liberator      :behaviours #{:json}                        }]
   [:any  "/rest/admin/*"   {:handler liberadmin     :behaviours #{:admin :json}                 }]])
```

Now lets write the conditional middleware preprocessor.

```clj
(defn add-conditional-middleware [middlewares]
  "The middlewares argument is a sequence of pairs, containing a predicate function
  and a middleware wrapper function. The predicate receives the :behaviours set of
  a route. Returns a pre-process wrapper that applies the given middlewares to
  the route handler when the predicate returns true."
  (fn [{:keys [handler behaviours] :as route}]
    (let [wrapped (reduce (fn [h [pred wrapper]]
                            (cond-> h (pred behaviours) wrapper))
                          handler middlewares)]
      (assoc route :handler wrapped))))
```

For our example, we define some middlewares for our behaviours:

```clj
(def middlewares
  [[:json                wrap-json]
   [:admin               wrap-admin?]
   [(complement :public) wrap-logged-in?]]
```

Now lets write the regular expression guards preprocessor.

```clj
(defn add-regex-params [{:keys [handler regexes] :as route}]
  "Pre-processes a route, by wrapping the handler, by only calling it if all the
  :regexes of that route match the incoming route parameters."
  (let [wrapped (fn [{:keys [route-params] :as request}]
                  (when (every? (fn [[k v]]
                                  (if-let [re (get regexes k)]
                                    (re-matches re v)
                                    true))
                                route-params)
                    (handler request)))]
    (assoc route :handler wrapped)))
```

We also need a base handler, that simply takes the `:handler` value of the matched route handler and calls it.

```clj
(defn my-route-handler [request]
  (let [handler (-> request :route-handler :handler)]
    (handler request)))
```

We can now wrap things up (literally), to conclude our more intricate route handling scenario.

```clj
(def compiled
  (-> routes
      (process-route-handlers (add-conditional-middleware middlewares) add-regex-params)
      compile-routes))

(def handler
  (-> my-route-handler wrap-try-alts (wrap-routes compiled)))
```

Note that our example added static behaviour.
Dynamic behaviour is of course also possible, by wrapping the handler with some middleware based on the incoming request.
Also note that this is just an example. You don't have to use sibiro in this more involved way, and there are many other interesting ways of combining the basics that sibiro offers.
