create table if not exists :schema.facts (

    -- db sequence (pk) to handle order and facts with the same timestamp
    id serial primary key,

    -- fact key "foo/bar/baz"
    key varchar not null,

    -- nippied byte array of the fact
    value bytea not null,

    -- murmur3 hash of the fact itself ({:id :foo/bar/baz :a 42} => "fcce6a444f9699b445440a5dd59b028f5ee47184")
    hash varchar not null,

    -- business time which by default is transaction time, but can be set to a desired business time
    at timestamp default current_timestamp not null
    );

-- unique constraint on "key and hash" to ease murmur3 collisions
alter table :schema.facts add constraint fact_uq unique (key, hash);

create index if not exists business_time_idx on :schema.facts (at desc);
create index if not exists key_idx on :schema.facts (key desc);


create table if not exists :schema.transactions (

    -- db sequence (pk) to handle order and transactions with the same timestamp
    id serial primary key,

    -- transaction time
    at timestamp default current_timestamp not null,

    -- nippied byte array of the fact hashes in this transaction
    facts bytea not null
);

create index if not exists tx_time_idx on :schema.transactions (at desc);
