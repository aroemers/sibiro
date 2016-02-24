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

TODO - See `sibiro.extras` namespace for now.

## Benefits

TODO - Story about separating concerns, the possibilities like [ronda-routing](https://github.com/xsc/ronda-routing) and all that jazz. See comment section in `sibiro.extras` namespace for now.

## Contributing

Patches and ideas welcome.

Master branch: [![Circle CI](https://circleci.com/gh/aroemers/sibiro/tree/master.svg?style=svg&circle-token=8a32ad3de9f753d371e8f464031f6f128d465469)](https://circleci.com/gh/aroemers/sibiro/tree/master)

## License

Copyright © 2016 Functional Bytes

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
