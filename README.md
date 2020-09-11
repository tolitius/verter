# ро́бот ве́ртер

![verter](doc/img/verter.png)

[verter](https://ru.wikipedia.org/wiki/%D0%A0%D0%BE%D0%B1%D0%BE%D1%82_%D0%92%D0%B5%D1%80%D1%82%D0%B5%D1%80) is a curator of institute of time.</br>
his most famous adventure takes place in the year of 2084..

[![<! release](https://img.shields.io/badge/dynamic/json.svg?label=release&url=https%3A%2F%2Fclojars.org%2Ftolitius%2Fverter%2Flatest-version.json&query=version&colorB=blue)](https://github.com/tolitius/verter/releases)
[![<! clojars](https://img.shields.io/clojars/v/tolitius/verter.svg)](https://clojars.org/tolitius/verter)

- [what and why](#what-and-why)
- [how to play](#how-to-play)
    - [create a datasource](#create-a-datasource)
    - [allow verter in](#allow-verter-in)
    - [create institute of time](#create-institute-of-time)
- [adding facts](#adding-facts)
  - [don't record what is already known](#dont-record-what-is-already-known)
- [looking at facts](#looking-at-facts)
  - [facts upto](#facts-upto)
  - [identity now](#identity-now)
  - [identity "as of"](#identity-as-of)
- [useless benchmarks](#useless-benchmarks)
  - [writes](#writes)
  - [reads](#reads)
- [license](#license)

# what and why

verter is _not_ a database, it is _not_ a data store<br/>
it _relies_ on one though to manage records about identities in space and time.

in contrast to [crux](https://github.com/juxt/crux), [datomic](https://github.com/Datomic) and other temporal databases
the idea behind verter is not to be your database, but to live "inside" an _existing_ database to provide audit, history and other bitemporal human needs.

verter is influenced by work that is done by the [crux](https://github.com/juxt/crux) team. if a bitemporal solution is what you are after, where all the data can live in crux, you should just start using crux instead. verter is for use cases where you already have a database / data store.

# how to play

since verter sits inside the existing database all it needs is a datasource, which could be an existing datasource, or a new one to, for example, have a separate connection pool. but do start from the datasource you already have.

### create a datasource

this example is using a datasource to [Postgres](https://www.postgresql.org/):

```clojure
=> (require '[hikari-cp.core :as hk])
=> (def db (hk/make-datasource {:adapter "postgresql" :url "..."})))
```

until now there is nothing verter specific, we just creating a datasource. check out a working example in [dev.clj](dev/dev.clj).

### allow verter in

```clojure
=> (require '[verter.core :as v]
            '[verter.store.postgres :as vp])

=> (def verter (vp/connect db))
```

### create institute of time

in case this is the first time verter is used with this database, the institute of time (verter tables) need to be created:

```clojure
=> (vp/create-institute-of-time db)
```

# adding facts

```clojure
=> (v/add-facts verter [{:id :universe/one :suns 12 :planets #{:one :two :three}}
                        [{:id :universe/two :suns 3 :life? true} #inst "2019-09-09"]
                        {:id :universe/sixty-six :answer 42}])

[{:tx-id 10,
  :at #inst "2020-09-10T17:24:18.697297000-00:00",
  :facts [{:key :universe/one, :hash "8cc82db19a67cc61e8e4144a854c15fd"}
          {:key :universe/two, :hash "0804a4e066f308756dcab8aa1dd35ae4"}
          {:key :universe/sixty-six, :hash "b9bb678c38b5a29917a4a7baafdbf754"}]}]
```

there are a few things to mention here:

* every fact needs to have an `:id`
* fact about `:universe/two` was given a "business time"
* other facts will assume that their business time is the transaction time
* all these facts were recorded in a single transaction
* this transaction id ("tx-id") is `10`
* facts that _were_ recorded came back in keys and content hashes.

## don't record what is already known

let's try this again:

```clojure
=> (v/add-facts verter [{:id :universe/one :suns 12 :planets #{:one :two :three}}
                        [{:id :universe/two :suns 3 :life? true} #inst "2019-09-09"]
                        {:id :universe/sixty-six :answer 42}])

no changes to identities were detected, and hence, these facts

[{:id :universe/one, :suns 12, :planets #{:one :three :two}}
 [{:id :universe/two, :suns 3, :life? true} #inst "2019-09-09T00:00:00.000-00:00"]
 {:id :universe/sixty-six, :answer 42}]

were NOT added at 2020-09-10T17:30:02.975160Z
```

since we already know all these facts.

but.., let's update `:universe/two` business time and number of suns in `universe/one`:

```clojure
=> (v/add-facts verter [{:id :universe/one :suns 42 :planets #{:one :two :three}}
                        [{:id :universe/two :suns 3 :life? true} #inst "2042-09-09"]
                        {:id :universe/sixty-six :answer 42}])

[{:tx-id 11,
  :at #inst "2020-09-10T17:33:13.712498000-00:00",
  :facts [{:key :universe/one, :hash "8657a740c448d4fc46af054f94ed5cec"}
          {:key :universe/two, :hash "999e5216c37fe39510bbb8116b2dc1ca"}]}]
```

as you see no facts about `:universe/sixty-six` were recorded since they did not change.

# looking at facts

```clojure
=> (v/facts verter :universe/sixty-six)

[{:answer 42, :id :universe/sixty-six, :at #inst "2020-09-10T17:24:18.697297000-00:00"}]
```

`facts` will return changes to an identity "attributes":

```clojure
=> (v/facts verter :universe/one)

[{:suns 12,
  :planets #{:one :three :two},
  :id :universe/one,
  :at #inst "2020-09-10T17:24:18.697297000-00:00"}
 {:suns 42,
  :planets #{:one :three :two},
  :id :universe/one,
  :at #inst "2020-09-10T17:33:13.712498000-00:00"}]
```

as well as "business time" changes:

```clojure
=> (v/facts verter :universe/two)

[{:suns 3,
  :life? true,
  :id :universe/two,
  :at #inst "2019-09-09T00:00:00.000000000-00:00"}
 {:suns 3,
  :life? true,
  :id :universe/two,
  :at #inst "2042-09-09T00:00:00.000000000-00:00"}]
```

## facts upto

let's add some more facts about the `:universe/sixty-six`:

```clojure
=> (v/add-facts verter [{:id :universe/sixty-six :suns 42 :planets #{:and-all :earth}, :life? true}
                        {:id :universe/sixty-six :moons 42}
                        {:id :universe/sixty-six :moons nil}]
```

so now it looks like this:

```clojure
=> (v/facts verter :universe/sixty-six)

[{:answer 42,
  :id :universe/sixty-six,
  :at #inst "2020-09-10T21:02:29.570645000-00:00"}
 {:suns 42,
  :planets #{:and-all :earth},
  :life? true,
  :id :universe/sixty-six,
  :at #inst "2020-09-11T00:18:04.151310000-00:00"}
 {:moons 42,
  :id :universe/sixty-six,
  :at #inst "2020-09-11T00:18:04.151310000-00:00"}
 {:moons nil,
  :id :universe/sixty-six,
  :at #inst "2020-09-11T00:18:04.151310000-00:00"}]
```

when looking for facts we can specify a certain time upto which the facts are needed:

```clojure
=> (v/facts verter :universe/sixty-six #inst "2020-09-11T00:18:04")

[{:answer 42,
  :id :universe/sixty-six,
  :at #inst "2020-09-10T21:02:29.570645000-00:00"}]
```

since the other 3 facts were added a bit after "2020-09-11T00:18:04", there is only one fact that "matter" in this case.

## identity now

continuing with `:universe/sixty-six` with currently 4 facts:

```clojure
[{:answer 42,
  :id :universe/sixty-six,
  :at #inst "2020-09-10T21:02:29.570645000-00:00"}
 {:suns 42,
  :planets #{:and-all :earth},
  :life? true,
  :id :universe/sixty-six,
  :at #inst "2020-09-11T00:18:04.151310000-00:00"}
 {:moons 42,
  :id :universe/sixty-six,
  :at #inst "2020-09-11T00:18:04.151310000-00:00"}
 {:moons nil,
  :id :universe/sixty-six,
  :at #inst "2020-09-11T00:18:04.151310000-00:00"}]
```

a quite frequent need is to see the identity "now": i.e. all the facts rolled up over time.

this can be done with `v/rollup`:

```clojure
=> (v/rollup verter :universe/sixty-six)

{:answer 42,
 :id :universe/sixty-six,
 :at #inst "2020-09-11T00:18:04.151310000-00:00",
 :suns 42,
 :planets #{:and-all :earth},
 :life? true}
```

one interesting "fact" about this rollup example: the `moons`.
the last fact about the `moons` is that it is "nil", hence while it still shows up in the list of facts, unless it has a value, it won't show up in a rollup.

## identity "as of"

similarly to "identity rollup", we can look at identity at a given point in time a.k.a. "as of" time:

let's add a fact about the moons right before that transaction with 3 facts above:

```clojure
=> (v/add-facts verter [[{:id :universe/sixty-six :moons 13} #inst "2020-09-11T00:18:03"]])
```

now `:universe/sixty-six` looks like this:

```clojure
[{:answer 42,
  :id :universe/sixty-six,
  :at #inst "2020-09-10T21:02:29.570645000-00:00"}
 {:suns 42,
  :planets #{:and-all :earth},
  :life? true,
  :id :universe/sixty-six,
  :at #inst "2020-09-11T00:18:04.151310000-00:00"}
 {:moons 42,
  :id :universe/sixty-six,
  :at #inst "2020-09-11T00:18:04.151310000-00:00"}
 {:moons nil,
  :id :universe/sixty-six,
  :at #inst "2020-09-11T00:18:04.151310000-00:00"}
 {:moons 13,
  :id :universe/sixty-six,
  :at #inst "2020-09-11T00:18:03.000000000-00:00"}]
```

see that last fact about the moons, but with the earlier business time?

let's look at this identity (`:universe/sixty-six`) upto this business time:

```clojure
=> (v/as-of verter :universe/sixty-six #inst "2020-09-11T00:18:03")

{:answer 42,
 :id :universe/sixty-six,
 :at #inst "2020-09-11T00:18:03.000000000-00:00",
 :moons 13}
```

as of "`2020-09-11T00:18:03`" this universe had 13 moons and the answer was and still is.. 42.

# useless benchmarks

since most of the work is done directly against the database, let's pretend the asking party is collocated with such database:

these are the numbers on top of Postgres:

## writes

we'll create a few new universes to avoid "do nothing" tricks verter does on hash matches:

```clojure
=> (def u40 (mapcat #(vector {:id :universe/forty     :moons % :suns %}) (range 5500)))
=> (def u41 (mapcat #(vector {:id :universe/forty-one :moons % :suns %}) (range 5500)))
=> (def u42 (mapcat #(vector {:id :universe/forty-two :moons % :suns %}) (range 5500)))
```

and add them sequentially in a single thread, not even "pmap":

```clojure
=> (time (do (v/add-facts verter u40)
             (v/add-facts verter u41)
             (v/add-facts verter u42)))

"Elapsed time: 1009.084383 msecs"
```

3 * 5500 = 16,500 a second.<br/>
later we'll play with type hints as well, but the most time will always be in I/O, so this is close.

## reads

let's read facts for `:universe/two` that has for entries and looks like this:

```clojure
=> (v/facts verter :universe/two)
[{:suns 42,
  :moons nil,
  :life? true,
  :id :universe/two,
  :at #inst "2020-09-10T22:33:20.204008000-00:00"}
 {:suns 42,
  :moons 12,
  :life? true,
  :id :universe/two,
  :at #inst "2020-09-10T22:10:36.352157000-00:00"}
 {:suns 42,
  :life? true,
  :id :universe/two,
  :at #inst "2020-09-10T22:10:22.712308000-00:00"}
 {:suns 3,
  :life? true,
  :id :universe/two,
  :at #inst "2019-09-09T00:00:00.000000000-00:00"}]
```

```clojure
=> (time (dotimes [_ 6500] (v/facts verter :universe/two)))
"Elapsed time: 1072.743808 msecs"
```

these benchmarks are "entertainment" hence no "criterium", multiple threads, connection pool tricks, light speed network latency, etc..

# license

Copyright © 2020 tolitius

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
