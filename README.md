![logo](doc/logo.png)

Bidirectional routing using datastructures for Clojure and ClojureScript.

## Motivation

The data-driven, bidirectional, macro-less routing libraries that I could find were too hard to use or bloated for my personal taste.
This library comes close to [tripod](https://github.com/frankiesardo/tripod), but sibiro is a little smaller and does support request methods.
Having a routing library based on datastructures and decomplecting route matching and request handling has several [benefits](#benefits).

## Usage ([API](https://aroemers.github.io/sibiro/))

Add `[functionalbytes/sibiro "0.1"]` to your dependencies and make sure you have Clojars as one of your repositories.

### Defining routes

A routes structure is a sequence of sequences, with three or four elements.
The request-method, a [clout](https://github.com/weavejester/clout)-like path, a handler (or any object really), and optionally a tag.
For example:

```clj
(def routes
  #{[:get  "/admin/user/"          user-list             ]
    [:get  "/admin/user/:id{\\d+}" user-get    :user-page]
    [:post "/admin/user/:id{\\d+}" user-update           ]
    [:any  "/:*"                   handle-404            ]})
```

> NOTE: In clout paths, a `*` can be used to define a catch-all path. In sibiro, this can also be `:*`. Inline regular expressions after the route parameter keywords are also supported.

**The order in which the routes are specified does not matter.**
Paths with more parts take precedence over shorter paths, exact URI parts take precedence over route parameters, catch-all parameter (`*`) is tried last, and specific request methods take precedence over `:any`.

To use the routes with the other API functions, they need to be "compiled" using `compile-routes`.
This transforms above datastructure into faster structures and functions.
For example:

```clj
(require '[sibiro.core :refer (compile-routes match-uri uri-for)])

(def compiled (compile-routes routes))
;=> #sibiro.core.CompiledRoutes@5728466
```

### Matching an URI

Given compiled routes, an URI and a request-method, the function `match-uri` returns a map with `:route-handler`, `:route-params` and a lazy `:alternatives`, or nil if no match was found.
Note that for a route to match, its inline regular expressions for route parameters need to match as well.
For example:

```clj
(match-uri compiled "/admin/user/42" :post)
;=> {:route-handler update-user,
     :route-params {:id "42"},
     :alternatives ({:route-handler handle-404, :route-params {:* "admin/user/42"}})}
```

The values in `:route-params` are URL decoded for you.
The value of `:alternatives` is lazy, so it won't search for alternatives if not requested.

### Generating an URI

Given compiled routes, a handler (or tag), and optionally parameters, the function `uri-for` returns a map with `:uri` and `:query-string`, or nil if the route could not be found.
For example.

```clj
(uri-for compiled update-user {:id 42 :name "alice bob"})
;=> {:uri "/admin/user/42", :query-string "?name=alice%20bob"}
```

An exception is thrown if parameters for the URI are missing in the data map, or are not matching the inline regular expressions.
The values in the data map are URL encoded for you.

> NOTE: There is also a convenience function called `path-for` that concatenates the :uri and :query-string from `uri-for`.

_That's all there is to the core of this library. Read on for some provided extras and possibilities._

## Provided extras for basic defaults

The `sibiro.extras` namespace contains some extra functions that can be used to get you up to speed quickly.
Note that these extras are Clojure only for now. Below are some of them.

##### `wrap-routes`

This middleware takes compiled and uncompiled routes as an argument.
On an incoming request, it merges the result of `match-uri` of the request with the request, and calls the wrapped handler.
This way, the wrapped handler receives both `:route-handler` and `:route-params`, if a match was found.

##### `wrap-try-alts`

This wraps the handler by merging in each entry from the `:alternatives` in the request one by one, until a non-nil response is given from the handler.
The first time the request is passed as is to the handler.

##### `route-handler`

A basic handler that uses the information from above `wrap-routes`.
It assumes the `:route-handler` value is a request handler, and calls that with the request.
If no `:route-handler` is available, a 404 response is returned.

##### `make-handler`

If all you need is the above basic `route-handler`, you can call `make-handler` with the (compiled or uncompiled) routes, and you get the `route-handler` wrapped with `wrap-try-alts` and `wrap-routes` back.
A voila, a basic request handler using sibiro.


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

_As always, have fun!_

## Contributing

Patches and ideas welcome.

Master branch: [![Circle CI](https://circleci.com/gh/aroemers/sibiro/tree/master.svg?style=svg&circle-token=8a32ad3de9f753d371e8f464031f6f128d465469)](https://circleci.com/gh/aroemers/sibiro/tree/master)

## License

Copyright © 2016 Functional Bytes

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
