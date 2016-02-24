# sibiro

Bidirectional routing using datastructures for Clojure and ClojureScript.

## Motivation

I found the available data-driven, bidirectional, macro-less routing libraries too hard to use or bloated.
This library comes close to [tripod](https://github.com/frankiesardo/tripod), but sibiro is a little smaller and does support request methods.
Having a routing library based on datastructures and decomplecting route matching and request handling has several [benefits](#benefits).

## Usage ([API](#coming-soon))

Add `[functionalbytes/sibiro "0.1"]` to your dependencies and make sure you have Clojars as one of your repositories.

### Defining routes

A routes structure is a sequence of sequences, with three or four elements.
The request-method, a [clout](https://github.com/weavejester/clout)-like path, a handler (or any object really), and optionally a tag.
For example:

```clj
(def routes
  [[:get  "/admin/user/"    user-list             ]
   [:get  "/admin/user/:id" user-get    :user-page]
   [:post "/admin/user/:id" user-update           ]
   [:any  "/:*"             handle-404            ]]
```

> NOTE: In clout paths, a `*` can be used to define a catch-all path. In sibiro, this can also be `:*`.

The order in which the routes are specified does not matter.
Paths with more parts take precedence over shorter paths, exact URI parts take precedence over route parameters, catch-all parameter (*) is tried last, and specific request methods take precedence over `:any`.

To use the routes with the other API functions, they need to be "compiled" using `compile-routes`.
This transforms above datastructure into faster structures and functions.
For example:

```clj
(require '[sibiro.core :refer (compile-routes match-uri uri-for)])

(def compiled (compile-routes routes))
```

### Matching an URI

Given compiled routes, an URI and a request-method, the function `match-uri` returns a map with `:route-handler` and `:route-params`, or nil if no match was found.
For example:

```clj
(match-uri compiled "/admin/user/42" :post)
;=> {:route-handler update-user, :route-params {:id "42"}
```

The values in `:route-params` are URL decoded for you.

### Generating an URI

Given compiled routes a handler (or tag), and optionally parameters, the function `uri-for` returns a map with `:uri` and `:query-string`, or nil if the route could not be found.
For example.

```clj
(uri-for compiled update-user {:id 42 :name "alice bob"})
;=> {:uri "/admin/user/42", :query-string "?name=alice%20bob"}
```

An exception is thrown if parameters for the URI are missing in the data map.
The values in the data map are URL encoded for you.

_That's all there is to the core of this library. Read on for some provided extras and possibilities._

## Provided extras for basic defaults

The `sibiro.extras` namespace contains some extra functions that can be used to get you up to speed quickly.

##### `wrap-routes`

If you want your handler to be unaware of the available routes, but do want the matched route information, you can wrap your handler with `wrap-routes`.
This middleware takes compiled and uncompiled routes as an argument.
On an incoming request, it merges the result of `match-uri` of the request with the request, and calls the wrapped handler.
This way, the wrapped handler receives both `:route-handler` and `:route-params`.

##### `route-handler`

A basic handler that uses the information from above `wrap-routes` is the function `route-handler`.
It assumes the `:route-handler` value is a request handler, and calls that with the request.

##### `make-handler`

If all you need is the above basic `route-handler`, you can call `make-handler` with the (compiled or uncompiled) routes, and you get the `route-handler` wrapped with `wrap-routes` back.
A voila, a basic request handler using sibiro.

## Benefits

The reason why using datastructures to define routes are benificial and prefered is made perfectly clear in [this presentation](https://www.youtube.com/watch?v=3oQTSP4FngY).
Having datastructures makes URI generating (i.e. the _bi_directional support) fairly easy.

The reason why the routes are not bound to a handler per se is also benificial, as it decomplects two concerns: that of route matching and that of request handling.
This is very well explained in the [sales pitch](https://github.com/xsc/ronda-routing#official-sales-pitch) of the ronda-routing library.
With that in mind, let's have an example of how sibiro can be used as well: conditional middleware.

As said before, the handler in the route can be any object.
So it can also be a pair: a function and a set of "behaviours".

```clj
(def routes (sc/compile-routes
             [[:get  "/login"     [login-page     #{:public}]]
              [:post "/login"     [login-handler  #{:public}]]
              [:get  "/dashboard" [dashboard-page           ]]
              [:get  "/admin"     [admin-page      #{:admin}]]
              [:any  "/rest"      [liberator        #{:json}]]]))
```

Now we write a base handler, that simply takes the first value of the matched route handler and calls it.
We later wrap this base handler with `sibiro.extras/wrap-routes`, which is why the `:route-handler` key is available in the request.

```clj
(defn my-route-handler [request]
  ((first (:route-handler request)) request))
```

To have conditional middleware, we use middleware that takes a sequence of predicate-wrapper pairs.
This middleware wraps the given handler with those wrappers for which its predicate (a function that receives the behaviours set of the matched route) returns a truthy value, in the order the are defined.

```clj
(defn wrap-conditional-middleware [handler middlewares]
  (fn [request]
    (let [wrapped (reduce (fn [h [pred wrapper]]
                            (if (pred (second (:route-handler request)))
                              (wrapper h)
                              h))
                          handler
                          middlewares)]
      (wrapped request))))
```

Now we simply wrap the base handler up, and voila, conditional middleware per route.

```clj
(-> my-route-handler
    (wrap-conditional-middleware [[:json                wrap-json]
                                  [:admin               wrap-admin?]
                                  [(complement :public) wrap-logged-in?]])
    (wrap-routes routes))
```

> NOTE: The downside of this approach is that it wraps the handler with the conditional middleware on each request.
> Above just serves as an example, more sophisticated approaches are possible (like pre-processing the routes datastructure before compiling).

_As always, have fun!_

## Contributing

Patches and ideas welcome.

Master branch: [![Circle CI](https://circleci.com/gh/aroemers/sibiro/tree/master.svg?style=svg&circle-token=8a32ad3de9f753d371e8f464031f6f128d465469)](https://circleci.com/gh/aroemers/sibiro/tree/master)

## License

Copyright © 2016 Functional Bytes

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
