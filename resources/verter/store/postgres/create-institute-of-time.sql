create table if not exists :schema.facts (

    -- db sequence (pk) to handle order and facts with the same timestamp
    id serial primary key,

    -- fact key "foo/bar/baz"
    key varchar not null,

    -- nippied byte array of the fact
    value bytea not null,

    -- murmur3 hash of the fact itself ({:id :foo/bar/baz :a 42} => "fcce6a444f9699b445440a5dd59b028f5ee47184")
    hash varchar not null
    );

-- unique constraint on "key and hash" to ease murmur3 collisions
alter table :schema.facts add constraint fact_uq unique (key, hash);

create index if not exists key_idx on :schema.facts (key);
create index if not exists hash_idx on :schema.facts (hash);

create table if not exists :schema.transactions (

    -- sequential UUID: https://github.com/clojure-cookbook/clojure-cookbook/blob/1b3754a7f4aab51cc9b254ea102870e7ce478aa0/01_primitive-data/1-24_uuids.asciidoc
    id uuid not null,

    -- murmur3 hash of the fact itself ({:id :foo/bar/baz :a 42} => "fcce6a444f9699b445440a5dd59b028f5ee47184")
    hash varchar not null,

    -- business time which by default is transaction time, but can be set to a desired business time
    business_time timestamp not null,

    -- transaction time
    at timestamp default current_timestamp not null
);

create index if not exists tx_time_idx on :schema.transactions (at desc);
create index if not exists business_time_idx on :schema.transactions (business_time desc);
create index if not exists hash_idx on :schema.transactions (hash);

-- unique constraint on "hash and business time" to avoid duplicates in transaction blocks as well same facts with same business time
alter table :schema.transactions add constraint business_hash_uq unique (hash, business_time);
