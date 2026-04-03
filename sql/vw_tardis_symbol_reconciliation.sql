-- public.vw_tardis_symbol_reconciliation source

CREATE OR REPLACE VIEW public.vw_tardis_symbol_reconciliation
AS SELECT a.exchange,
    a.instrument_type,
    a.id AS symbol,
    b.externalreference,
    b.instrumenttype,
    a.base_currency,
    a.quote_currency,
    b.base,
    b.quote,
    b.status
   FROM tardis_instruments a
     LEFT JOIN ( SELECT a_1.id,
            b_1.externalreference AS exchange,
            a_1.instrumenttype,
            a_1.externalreference,
            a_1.*::liquidity_exchange_pair_state AS a,
            a_1.base,
            a_1.quote,
            a_1.status
           FROM liquidity_exchange_pair_state a_1
             JOIN liquidity_exchange_state b_1 ON a_1.exchange = b_1.id) b ON a.exchange::text = lower(b.exchange::text) AND a.instrument_type = b.instrumenttype AND a.id::text = b.externalreference::text
     JOIN liquidity_pair_state c ON c.alias::text = a.base_currency::text
  WHERE a.quote_currency::text = ANY (ARRAY['USD'::character varying, 'USDC'::character varying, 'USDT'::character varying]::text[])
  ORDER BY a.exchange, a.instrument_type, a.id;