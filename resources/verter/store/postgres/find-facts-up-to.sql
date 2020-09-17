select f.key,
       f.value,
       t.business_time,
       t.at as tx_time,
       t.id as tx_id
from :schema.facts f
join :schema.transactions t
     on f.hash = t.hash
where f.key = :key
        and
      t.business_time <= ?
order by t.business_time;
