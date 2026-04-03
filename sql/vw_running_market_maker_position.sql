-- public.vw_running_market_maker_position source

CREATE OR REPLACE VIEW public.vw_running_market_maker_position
AS SELECT id,
    created,
    clordid,
    securityid,
    symbol,
    side,
    ordtype,
    exectype,
    ordstatus,
    orderqty,
    orderqtyscale,
    price,
    pricescale,
        CASE
            WHEN side::text = 'BUY'::text THEN orderqty::numeric::double precision / power(10::double precision, orderqtyscale::double precision)
            WHEN side::text = 'SELL'::text THEN - (orderqty::numeric::double precision / power(10::double precision, orderqtyscale::double precision))
            ELSE 0::double precision
        END AS signed_qty,
    sum(
        CASE
            WHEN side::text = 'BUY'::text THEN orderqty::numeric::double precision / power(10::double precision, orderqtyscale::double precision)
            WHEN side::text = 'SELL'::text THEN - (orderqty::numeric::double precision / power(10::double precision, orderqtyscale::double precision))
            ELSE 0::double precision
        END) OVER (PARTITION BY symbol ORDER BY id ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS running_position,
    price::numeric::double precision / power(10::double precision, pricescale::double precision) AS scaled_price
   FROM execution_report
  WHERE ordstatus::text = 'CALCULATED'::text AND exectype::text = 'TRADE'::text AND targetstrategy = 196
  ORDER BY symbol, id;