-- public.vw_average_cost_calculation source

CREATE OR REPLACE VIEW public.vw_average_cost_calculation
AS WITH position_calculations AS (
         SELECT execution_report.id,
            execution_report.userid,
            execution_report.securityid,
            execution_report.clordid,
            execution_report.symbol,
            execution_report.side,
            execution_report.orderqty,
            execution_report.orderqtyscale,
            execution_report.price,
            execution_report.pricescale,
            execution_report.avgpx,
            execution_report.avgpxscale,
            execution_report.notional,
            execution_report.created,
            sum(
                CASE
                    WHEN execution_report.side::text = 'BUY'::text THEN execution_report.orderqty::double precision / power(10::double precision, execution_report.orderqtyscale::double precision)
                    ELSE (- execution_report.orderqty)::double precision / power(10::double precision, execution_report.orderqtyscale::double precision)
                END) OVER (PARTITION BY execution_report.userid, execution_report.securityid ORDER BY execution_report.id ROWS UNBOUNDED PRECEDING) AS running_position,
            sum(
                CASE
                    WHEN execution_report.side::text = 'BUY'::text THEN execution_report.notional
                    ELSE 0::real
                END) OVER (PARTITION BY execution_report.userid, execution_report.securityid ORDER BY execution_report.id ROWS UNBOUNDED PRECEDING) AS running_cost_basis
           FROM execution_report
          WHERE execution_report.ordstatus::text = 'FILLED'::text AND (execution_report.securityid <> ALL (ARRAY[2, 12, 14, 925]))
        ), position_with_lag AS (
         SELECT position_calculations.id,
            position_calculations.userid,
            position_calculations.securityid,
            position_calculations.clordid,
            position_calculations.symbol,
            position_calculations.side,
            position_calculations.orderqty,
            position_calculations.orderqtyscale,
            position_calculations.price,
            position_calculations.pricescale,
            position_calculations.avgpx,
            position_calculations.avgpxscale,
            position_calculations.notional,
            position_calculations.created,
            position_calculations.running_position,
            position_calculations.running_cost_basis,
            lag(position_calculations.running_position, 1, 0::double precision) OVER (PARTITION BY position_calculations.userid, position_calculations.securityid ORDER BY position_calculations.id) AS prev_position
           FROM position_calculations
        ), reset_points AS (
         SELECT position_with_lag.id,
            position_with_lag.userid,
            position_with_lag.securityid,
            position_with_lag.clordid,
            position_with_lag.symbol,
            position_with_lag.side,
            position_with_lag.orderqty,
            position_with_lag.orderqtyscale,
            position_with_lag.price,
            position_with_lag.pricescale,
            position_with_lag.avgpx,
            position_with_lag.avgpxscale,
            position_with_lag.notional,
            position_with_lag.created,
            position_with_lag.running_position,
            position_with_lag.running_cost_basis,
            position_with_lag.prev_position,
            sum(
                CASE
                    WHEN position_with_lag.prev_position <> 0::double precision AND position_with_lag.running_position = 0::double precision THEN 1
                    ELSE 0
                END) OVER (PARTITION BY position_with_lag.userid, position_with_lag.securityid ORDER BY position_with_lag.id ROWS UNBOUNDED PRECEDING) AS reset_group
           FROM position_with_lag
        ), cost_basis_reset AS (
         SELECT reset_points.id,
            reset_points.userid,
            reset_points.securityid,
            reset_points.clordid,
            reset_points.symbol,
            reset_points.side,
            reset_points.orderqty,
            reset_points.orderqtyscale,
            reset_points.price,
            reset_points.pricescale,
            reset_points.avgpx,
            reset_points.avgpxscale,
            reset_points.notional,
            reset_points.created,
            reset_points.running_position,
            reset_points.running_cost_basis,
            reset_points.prev_position,
            reset_points.reset_group,
            sum(
                CASE
                    WHEN reset_points.side::text = 'BUY'::text THEN reset_points.notional
                    ELSE 0::real
                END) OVER (PARTITION BY reset_points.userid, reset_points.securityid, reset_points.reset_group ORDER BY reset_points.id ROWS UNBOUNDED PRECEDING) AS adjusted_cost_basis,
            sum(
                CASE
                    WHEN reset_points.side::text = 'BUY'::text THEN reset_points.orderqty::double precision / power(10::double precision, reset_points.orderqtyscale::double precision)
                    ELSE 0::double precision
                END) OVER (PARTITION BY reset_points.userid, reset_points.securityid, reset_points.reset_group ORDER BY reset_points.id ROWS UNBOUNDED PRECEDING) AS adjusted_buy_qty
           FROM reset_points
        )
 SELECT id,
    userid,
    securityid,
    clordid,
    symbol,
    side,
    orderqty,
    orderqtyscale,
    price,
    pricescale,
    avgpx,
    avgpxscale,
    notional,
    created,
    running_position,
        CASE
            WHEN running_position = 0::double precision THEN 0::numeric
            WHEN adjusted_buy_qty = 0::double precision THEN 0::numeric
            ELSE adjusted_cost_basis::numeric / adjusted_buy_qty::numeric
        END AS average_cost
   FROM cost_basis_reset;