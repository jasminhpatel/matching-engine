select
--E.id, A.exchange, A.instrument_type, A.id as symbol, B.externalreference,B.instrumenttype, A.base_currency, C.id,  A."quote_currency", B.base, B."quote", B.status
'INSERT INTO public.liquidity_exchange_pair_state (id, exchange, symbol, instrumenttype, base, "quote", externalreference, status, alias, symbolid) VALUES(nextval(''liquidity_exchange_pair_state_id_seq''::regclass), ' || E.id || ', ''' ||  A.id || ''', ' || A.instrument_type || ', ''' ||  A.base_currency || ''', ''' ||  A."quote_currency" ||  ''', ''' ||  A.id ||  ''', 1, ''' || A.base_currency || ''', ' || C.id || ');'
from tardis_instruments A
left join (
SELECT A.id, b."externalreference" as exchange, A.instrumenttype, A.externalreference, A,base, A."quote", A.status
FROM public.liquidity_exchange_pair_state A
join liquidity_exchange_state B on A.exchange = B.id
) B on A.exchange = lower(B.exchange) and A.instrument_type = B.instrumenttype and A.id = B.externalreference
join liquidity_pair_state C on C.alias = A.base_currency
join liquidity_exchange_state E on lower(E.externalreference) = A.exchange and E.instrumenttype = A.instrument_type
where A."quote_currency" in ('USD', 'USDC', 'USDT')
and B.externalreference is null
order by A.exchange, A.instrument_type, A.id ASC