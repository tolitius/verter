## ро́бот ве́ртер

![verter](doc/img/verter.png)

[verter](https://ru.wikipedia.org/wiki/%D0%A0%D0%BE%D0%B1%D0%BE%D1%82_%D0%92%D0%B5%D1%80%D1%82%D0%B5%D1%80) is a curator of institute of time.</br>
his most famous adventure takes place in the year of 2084..

## what and why

verter is _not_ a database, it is _not_ a data store<br/>
it _relies_ on one though to manage records about identities on space and time.

in contrast to [crux](https://github.com/juxt/crux), [datomic](https://github.com/Datomic) and other temporal databases
the idea behind verter is not to be your database, but to live "inside" an _existing_ database to provide audit, history and other bitemporal human needs.

verter is influenced by work that is done by [crux](https://github.com/juxt/crux) team. if a bitemporal solution is what you are after where all the data can live in crux, you should just start using crux instead. verter is for use cases where you already have a database / data store.

## show me

since verter sits inside the existing database all it needs is a datasource, which could be an existing datasource, or the new one to, for example, have a separate connection pool. but do start from the datasource you already have.

### create a datasource

this example is using a datasource to [Postgres](https://www.postgresql.org/):

```clojure
(require '[hikari-cp.core :as hk])
(def db (hk/make-datasource {:adapter "postgresql" :url "..."})))
```

until now there is nothing verter specific, we just creating a datasource. check out a working example in [dev.clj](dev/dev.clj).

### allow verter in

```clojure
(require '[verter.core :as v]
         '[verter.store.postgres :as vp])

(def verter (vp/connect db))
```

### add facts

```clojure
(v/add-facts verter [{:id :universe/one :suns 12 :planets #{:one :two :three}}
                     [{:id :universe/two :suns 3 :life? true} #inst "2019-09-09"]
                     {:id :universe/sixty-six :answer 42}])
```
```clojure
[{:tx-id 10,
  :at #inst "2020-09-10T17:24:18.697297000-00:00",
  :facts [{:key :universe/one, :hash "8cc82db19a67cc61e8e4144a854c15fd"}
          {:key :universe/two, :hash "0804a4e066f308756dcab8aa1dd35ae4"}
          {:key :universe/sixty-six, :hash "b9bb678c38b5a29917a4a7baafdbf754"}]}]
```

there are a few things to mention here:

* every fact needs to have an `:id`
* fact about `:universe/two` was given a "business time"
* other facts will assume that their business time is transaction time
* all these facts were recorded in a single transaction
* this transaction id ("tx-id") is `10`
* facts that _were_ recorded came back in keys and content hashes.

#### don't record what is already known

let's try this again:

```clojure
(v/add-facts verter [{:id :universe/one :suns 12 :planets #{:one :two :three}}
                     [{:id :universe/two :suns 3 :life? true} #inst "2019-09-09"]
                     {:id :universe/sixty-six :answer 42}])
```
```clojure
no changes to identities were detected, and hence, these facts

[{:id :universe/one, :suns 12, :planets #{:one :three :two}}
 [{:id :universe/two, :suns 3, :life? true} #inst "2019-09-09T00:00:00.000-00:00"]
 {:id :universe/sixty-six, :answer 42}]

were NOT added at 2020-09-10T17:30:02.975160Z
```

since we already know all these facts.

but.., let's update `:universe/two` business time and number of suns in `universe/one`:

```clojure
(v/add-facts verter [{:id :universe/one :suns 42 :planets #{:one :two :three}}
                     [{:id :universe/two :suns 3 :life? true} #inst "2042-09-09"]
                     {:id :universe/sixty-six :answer 42}])
```
```clojure
[{:tx-id 11,
  :at #inst "2020-09-10T17:33:13.712498000-00:00",
  :facts [{:key :universe/one, :hash "8657a740c448d4fc46af054f94ed5cec"}
          {:key :universe/two, :hash "999e5216c37fe39510bbb8116b2dc1ca"}]}]
```

as you see no facts about `:universe/sixty-six` were recorded since they did not change.

### find facts

```clojure
(v/facts verter :universe/sixty-six)
```
```clojure
[{:answer 42, :id :universe/sixty-six, :at #inst "2020-09-10T17:24:18.697297000-00:00"}]
```

`facts` will return changes to fact "attributes":

```clojure
(v/facts verter :universe/one)
```
```clojure
[{:suns 12,
  :planets #{:one :three :two},
  :id :universe/one,
  :at #inst "2020-09-10T17:24:18.697297000-00:00"}
 {:suns 42,
  :planets #{:one :three :two},
  :id :universe/one,
  :at #inst "2020-09-10T17:33:13.712498000-00:00"}]
```

as well as business time changes:

```clojure
(v/facts verter :universe/two)
```
```clojure
[{:suns 3,
  :life? true,
  :id :universe/two,
  :at #inst "2019-09-09T00:00:00.000000000-00:00"}
 {:suns 3,
  :life? true,
  :id :universe/two,
  :at #inst "2042-09-09T00:00:00.000000000-00:00"}]
```
