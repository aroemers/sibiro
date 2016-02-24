# sibiro

Bidirectional routing using datastructures for Clojure and ClojureScript.

## Motivation

I found the available data-driven, bidirectional routing libraries too hard to use or bloated.
This library comes close to [tripod](https://github.com/frankiesardo/tripod), but sibiro is a little smaller and does support request-methods.
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

### `wrap-routes`

If you want your handler to be unaware of the available routes, but do want the matched route information, you can wrap your handler with `wrap-routes`.
This middleware takes compiled and uncompiled routes as an argument.
On an incoming request, it merges the result of `match-uri` of the request with the request, and calls the wrapped handler.
This way, the wrapped handler receives both `:route-handler` and `:route-params`.

### `route-handler`

A basic handler that uses the information from above `wrap-routes` is the function `route-handler`.
It assumes the `:route-handler` value is a request handler, and calls that with the request.

### `make-handler`

If all you need is the above basic `route-handler`, you can call `make-handler` with the (compiled or uncompiled) routes, and you get the `route-handler` wrapped with `wrap-routes` back.
A voila, a basic request handler using sibiro.

## Benefits

TODO - Story about separating concerns, the possibilities like [ronda-routing](https://github.com/xsc/ronda-routing) and all that jazz.
See comment section in `sibiro.extras` namespace for now.

## Contributing

Patches and ideas welcome.

Master branch: [![Circle CI](https://circleci.com/gh/aroemers/sibiro/tree/master.svg?style=svg&circle-token=8a32ad3de9f753d371e8f464031f6f128d465469)](https://circleci.com/gh/aroemers/sibiro/tree/master)

## License

Copyright © 2016 Functional Bytes

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
