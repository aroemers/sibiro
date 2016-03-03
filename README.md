# sibiro

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
  [[:get  "/admin/user/"          user-list             ]
   [:get  "/admin/user/:id{\\d+}" user-get    :user-page]
   [:post "/admin/user/:id{\\d+}" user-update           ]
   [:any  "/:*"                   handle-404            ]]
```

> NOTE: In clout paths, a `*` can be used to define a catch-all path. In sibiro, this can also be `:*`. Inline regular expressions after the route parameter keywords are also supported.

The order in which the routes are specified does not matter.
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

Given compiled routes, an URI and a request-method, the function `match-uri` returns a map with `:route-handler` and `:route-params`, or nil if no match was found.
Note that for a route to match, its inline regular expressions for route parameters need to match as well.
For example:

```clj
(match-uri compiled "/admin/user/42" :post)
;=> {:route-handler update-user, :route-params {:id "42"}
```

The values in `:route-params` are URL decoded for you.

If you require more than one match, you can use `match-uris`. It yields the same result, with the addition of the `:alternatives` key.
The value of that key holds a sequence of maps (the same structure as `match-uri`), beginning with the next best match, and so on.

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

If you want your handler to be unaware of the available routes, but do want the matched route information, you can wrap your handler with `wrap-routes`.
This middleware takes compiled and uncompiled routes as an argument.
On an incoming request, it merges the result of `match-uri` of the request with the request, and calls the wrapped handler.
This way, the wrapped handler receives both `:route-handler` and `:route-params`.

> NOTE: A `wrap-routes-alts` also exists.
> It works the same as `wrap-routes`, but uses `match-uris` instead of `match-uri`.
> This can very well be combined with `wrap-try-alts`, trying each alternative until a non-nil response is returned.

##### `route-handler`

A basic handler that uses the information from above `wrap-routes` is the function `route-handler`.
It assumes the `:route-handler` value is a request handler, and calls that with the request.

##### `make-handler`

If all you need is the above basic `route-handler`, you can call `make-handler` with the (compiled or uncompiled) routes, and you get the `route-handler` wrapped with `wrap-routes` back.
A voila, a basic request handler using sibiro.

> NOTE: A `make-handler-alts` also exists.
> It wraps `route-handler` with `wrap-try-alts` and `wrap-routes-alts`.


## Benefits

The reason why using datastructures to define routes are beneficial and prefered is made perfectly clear in [this presentation](https://www.youtube.com/watch?v=3oQTSP4FngY).
In short, for a routing library using datastructures (instead of macros), it means that URI generating (i.e. the _bi_-directional support) is easy, and routes can be shared, transformed and programmatically generated.

The reason why the routes are not bound to a handler per se is also beneficial, as it decomplects two concerns: that of route matching and that of request handling.
This is very well explained in the [sales pitch](https://github.com/xsc/ronda-routing#official-sales-pitch) of the ronda-routing library.

### Example: conditional middleware

With above sales pitch in mind, let's have an example of how sibiro can be used as well: conditional middleware and external regular expressions for route parameters.
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
   [:any  "/rest/*"         {:handler liberator      :behaviours #{:json}                       }]]))
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
                            (cond-> h (pred behaviours) (wrapper h)))
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

We can now wrap things up (literally), to conclude our more complex route handling scenario.
Note that we use `wrap-routes-alts` and `wrap-try-alts` here (explained in the [extras](#provided-extras-for-basic-defaults) section), in order for the regular expression guards to work.

```clj
(def compiled
  (-> routes
      (process-route-handlers (add-conditional-middleware middlewares) add-regex-params)
      (compile-routes))

(def handler
  (-> my-route-handler
      (wrap-try-alts)
      (wrap-routes-alts compiled)))
```

Note that our example added static behaviour.
Dynamic behaviour is of course also possible, by wrapping the handler with some middleware based on the incoming request.
Also note that this is just an example. You don't have to use sibiro in this more complex way, and there are many other interesting ways of combining the basics that sibiro offers.

_As always, have fun!_

## Contributing

Patches and ideas welcome.

Master branch: [![Circle CI](https://circleci.com/gh/aroemers/sibiro/tree/master.svg?style=svg&circle-token=8a32ad3de9f753d371e8f464031f6f128d465469)](https://circleci.com/gh/aroemers/sibiro/tree/master)

## License

Copyright © 2016 Functional Bytes

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
