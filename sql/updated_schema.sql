-- DROP TABLE address_state;
-- DROP TABLE address_state_log;
-- DROP TABLE addresses;
-- DROP TABLE asset_state;
-- DROP TABLE balance_log;
-- DROP TABLE balance_state;
-- DROP TABLE chain_transaction_log;
-- DROP TABLE deribit_last_trade;
-- DROP TABLE deribit_pair;
-- DROP TABLE email_log;
-- DROP TABLE execution_report;
-- DROP TABLE fee_log;
-- DROP TABLE fund;
-- DROP TABLE fund_balance_log;
-- DROP TABLE funding_rate_history;
-- DROP TABLE geo_ip;
-- DROP TABLE message_log;
-- DROP TABLE order_book_state;
-- DROP TABLE position_report;
-- DROP TABLE position_report_balance;
-- DROP TABLE security_definition_log;
-- DROP TABLE trade_history_log;
-- DROP TABLE upload_file;
-- DROP TABLE user_log;
-- DROP TABLE user_role;
-- DROP TABLE user_stat_fee_log;
-- DROP TABLE user_stat_log;
-- DROP TABLE user_state;
-- DROP TABLE user_whitelist_address;
-- DROP TABLE user_whitelist_ip;
-- DROP TABLE withdraw_request;

CREATE TABLE address_state (
  id bigserial NOT NULL,
  sequence_number int8 NULL,
  insert_time varchar(32) NULL DEFAULT NULL::character varying,
  address varchar(64) NULL,
  updatetype int2 NULL,
  userid int8 NULL,
  assetid int4 NULL,
  symbol varchar(16) NULL,
  balance int8 NULL,
  balance_scale int4 NULL,
  confirms int4 NULL,
  "source" varchar(256) NULL,
  active int2 NULL,
  updateby varchar(64) NULL,
  signature varchar(256) NULL,
  status int4 NULL,
  manageruserid int4 NULL,
  CONSTRAINT address_state_pkey PRIMARY KEY (id)
);
CREATE INDEX address_state1 ON address_state USING btree (userid);
CREATE INDEX address_state2 ON address_state USING btree (assetid);
CREATE INDEX address_state3 ON address_state USING btree (active);
CREATE INDEX address_state4 ON address_state USING btree (manageruserid);

CREATE TABLE address_state_log (
  id bigserial NOT NULL,
  sequence_number int8 NULL,
  insert_time varchar(32) NULL DEFAULT NULL::character varying,
  address varchar(64) NULL,
  updatetype int2 NULL,
  userid int8 NULL,
  assetid int4 NULL,
  symbol varchar(16) NULL,
  balance_change int8 NULL,
  balance_change_scale int4 NULL,
  balance int8 NULL,
  balance_scale int4 NULL,
  confirms int4 NULL,
  "source" varchar(256) NULL,
  updateby varchar(64) NULL,
  signature varchar(256) NULL,
  manageruserid int4 NULL,
  CONSTRAINT address_state_log_pkey PRIMARY KEY (id)
);
CREATE INDEX address_state_log1 ON address_state_log USING btree (manageruserid);

CREATE TABLE addresses (
  id bigserial NOT NULL,
  address varchar(64) NULL,
  userid int8 NULL,
  assetid int4 NULL,
  symbol varchar(16) NULL,
  signature varchar(256) NULL,
  CONSTRAINT addresses_pkey PRIMARY KEY (id)
);

CREATE TABLE asset_state(
    id BIGSERIAL PRIMARY KEY NOT NULL,
    sequence_number INT,
    assetId BIGINT,
    tokeniId INT,
    securityId INT,
    assetType VARCHAR(32),
    assetStatus VARCHAR(32),
    venueId INT,
    ticketId INT,
    artistId INT,
    seriesId INT,
    idHex VARCHAR(256),
    publicAddress VARCHAR(256),
    contractAddress VARCHAR(256),
    contractTokenId VARCHAR(256),
    chainType VARCHAR(64),
    parentId BIGINT,
    numOfKind INT,
    ownerUserId INT,
    externalId VARCHAR(32),
    updateType VARCHAR(16),
    created TIMESTAMP default now(),
    updated TIMESTAMP default now(),
    kafkaRecordOffset BIGINT,
    insert_time VARCHAR(32),
    collectionId INT,
    redeemStatus INT,
    name VARCHAR(64),
    description VARCHAR(1280),
    url VARCHAR(256),
    imageUrl VARCHAR(256),
    imagethumburl VARCHAR(256),
    mediaurl VARCHAR(256),
    etherscanUrl VARCHAR(128),
    openseaUrl VARCHAR(128),
    ipfsUrl VARCHAR(128),
    redeemfile varchar(256) NULL,
    redeemfilehq varchar(256) NULL,
    isphysical bool NULL,
    isdigital bool NULL,
    category VARCHAR(64),
    fundTransferAccount VARCHAR(256),
    eventId INT,
    ticketSyncStatus VARCHAR(32),
    includeMerch bool NULL,
    seatNo VARCHAR(32),
    isRegister bool NULL,
    assetPayeeAccountAddress varchar(256),
    payeeEnabled bool default false,
    assetAttributeId BIGINT,
    region VARCHAR(64),
    assetSize VARCHAR(64),
    registerTime BIGINT,
    firstDivTime BIGINT,
    divFrequencyTime BIGINT,
    estNav double precision,
    estROI double precision,
    metadataUrl varchar(128) NULL,
    redeemimagethumburl varchar(256) NULL,
    supplylimit int4 null,
    royaltypercentage float8 null,
    royaltyreceiveraddress varchar(256) null,
    transferlimit int4 NULL DEFAULT 0,
    resalelimit int4 NULL DEFAULT 0,
    albumname varchar(64) null,
    taxfeewalletaddress varchar(256) null,
    ccfeewalletaddress varchar(256) null,
    "label" varchar(16) null,
    exchangeFee double precision
);
CREATE INDEX ASSET_STATE1 ON ASSET_STATE USING btree (ownerUserId);
CREATE INDEX ASSET_STATE2 ON ASSET_STATE USING btree (assetid);
CREATE INDEX ASSET_STATE3 ON ASSET_STATE USING btree (tokeniId);
CREATE INDEX ASSET_STATE4 ON ASSET_STATE USING btree (securityId);

CREATE TABLE balance_log (
    id bigserial NOT NULL,
    sequence_number int8 NULL,
    insert_time varchar(32) NULL DEFAULT NULL::character varying,
    updatetype int2 NULL,
    userid int8 NULL,
    firmid int4 NULL,
    feetier int4 NULL,
    assetid int4 NULL,
    balance int8 NULL,
    balance_scale int4 NULL,
    balance_change int8 NULL,
    balance_change_scale int4 NULL,
    event_type int4 NULL,
    orderid int8 NULL,
    execid int8 NULL,
    unrealizedusd float4 NULL,
    realizedusd float4 NULL,
    avgcostbasisusd float4 NULL,
    baseusdmark float4 NULL,
    settlecoinusdmark float4 NULL,
    settlecoinunrealized float4 NULL,
    settlecoinrealized float4 NULL,
    CONSTRAINT balance_log_pkey PRIMARY KEY (id)
);
CREATE INDEX balance_log1 ON balance_log USING btree (userid);
CREATE INDEX balance_log2 ON balance_log USING btree (assetid);

CREATE TABLE balance_state (
  id bigserial NOT NULL,
  sequence_number int8 NULL,
  insert_time varchar(32) NULL DEFAULT NULL::character varying,
  updatetype int2 NULL,
  userid int8 NULL,
  firmid int4 NULL,
  feetier int4 NULL,
  assetid int4 NULL,
  balance int8 NULL,
  balance_scale int4 NULL,
  unrealizedusd float4 NULL,
  realizedusd float4 NULL,
  avgcostbasisusd float4 NULL,
  baseusdmark float4 NULL,
  settlecoinusdmark float4 NULL,
  settlecoinunrealized float4 NULL,
  settlecoinrealized float4 NULL,
  CONSTRAINT balance_state_pkey PRIMARY KEY (id)
);
CREATE INDEX balance_state1 ON balance_state USING btree (userid);
CREATE INDEX balance_state2 ON balance_state USING btree (assetid);

CREATE TABLE chain_transaction_log (
  id bigserial NOT NULL,
  userid int8 NULL,
  instrumentid int4 NULL,
  symbol varchar(64) NULL,
  address varchar(256) NULL,
  transactionid varchar(256) NULL,
  balance float4 NULL,
  balance_change float4 NULL,
  confirmations int4 NULL,
  transaction_time varchar(64) NULL,
  created timestamp NULL DEFAULT now(),
  ip varchar(64) NULL,
  "source" varchar(64) NULL,
  status int4 NULL,
  sent float4 NULL,
  received float4 NULL,
  tx_count int4 NULL,
  unconfirmed_tx_count int4 NULL,
  unspent_tx_count int4 NULL,
  unconfirmed_received float4 NULL,
  unconfirmed_sent float4 NULL,
  first_tx varchar(256) NULL,
  last_tx varchar(256) NULL,
  watcher_timestamp varchar(64) NULL,
  transactiontype varchar(32) NULL,
  note varchar(256) NULL,
  CONSTRAINT chain_transaction_log_pkey PRIMARY KEY (id)
);
CREATE INDEX chain_transaction_log1 ON chain_transaction_log USING btree (userid);
CREATE INDEX chain_transaction_log2 ON chain_transaction_log USING btree (instrumentid);
CREATE INDEX chain_transaction_log3 ON chain_transaction_log USING btree (address);

CREATE TABLE chain_transaction2_log (
    id BIGSERIAL PRIMARY KEY NOT NULL,
    userid int8 NULL,
    instrumentId int4 NULL,
    symbol varchar(64) NULL,
    chain VARCHAR(64),
    blockNumber VARCHAR(64),
    address VARCHAR(256),
    transactionHash VARCHAR(256),
    contractType VARCHAR(64),
    contractAddress VARCHAR(256),
    tokenId VARCHAR(256),
    fromAddress VARCHAR(256),
    toAddress VARCHAR(256),
    amount VARCHAR(64),
    status INT,-- 0-Pending, 1-Successful, 2-Rejected
    transactionTime VARCHAR(32),
    created TIMESTAMP default now(),
    transactionType VARCHAR(64),
    note VARCHAR(256)
);
CREATE INDEX chain_transaction2_log1 ON chain_transaction2_log USING btree (userid);
CREATE INDEX chain_transaction2_log2 ON chain_transaction2_log USING btree (instrumentId);
CREATE INDEX chain_transaction2_log3 ON chain_transaction2_log USING btree (address);

CREATE TABLE deribit_last_trade (
    id bigserial NOT NULL,
    pairid int4 NULL,
    instrumentid int4 NULL,
    "timestamp" int8 NULL,
    symbol varchar(32) NULL DEFAULT NULL::character varying,
    direction varchar(32) NULL DEFAULT NULL::character varying,
    trade_id varchar(32) NULL DEFAULT NULL::character varying,
    trade_seq int8 NULL,
    tick_direction int4 NULL,
    price float4 NULL,
    index_price float4 NULL,
    amount float4 NULL,
    iv float4 NULL,
    created timestamp NULL DEFAULT now(),
    CONSTRAINT deribit_last_trade_pkey1 PRIMARY KEY (id)
);
CREATE INDEX deribit_last_trade11 ON deribit_last_trade USING btree (instrumentid);
CREATE INDEX deribit_last_trade21 ON deribit_last_trade USING btree (symbol);
CREATE INDEX deribit_last_trade31 ON deribit_last_trade USING btree (pairid);
CREATE INDEX deribit_last_trade41 ON deribit_last_trade USING btree (trade_seq);

CREATE TABLE deribit_pair (
 id bigserial NOT NULL,
 instrumentid int4 NULL,
 "type" int4 NULL,
 "timestamp" int8 NULL,
 symbol varchar(32) NULL DEFAULT NULL::character varying,
 kind varchar(32) NULL DEFAULT NULL::character varying,
 quote_currency varchar(32) NULL DEFAULT NULL::character varying,
 base_currency varchar(32) NULL DEFAULT NULL::character varying,
 option_type varchar(32) NULL DEFAULT NULL::character varying,
 settlement_period varchar(32) NULL DEFAULT NULL::character varying,
 expiration_timestamp int8 NULL,
 creation_timestamp int8 NULL,
 is_active bool NULL,
 contract_size int4 NULL,
 taker_commission float4 NULL,
 maker_commission float4 NULL,
 tick_size float4 NULL,
 created timestamp NULL DEFAULT now(),
 CONSTRAINT deribit_pair_pkey1 PRIMARY KEY (id)
);
CREATE INDEX deribit_pair1 ON deribit_pair USING btree (instrumentid);
CREATE INDEX deribit_pair2 ON deribit_pair USING btree (symbol);

CREATE TABLE email_log (
  id serial NOT NULL,
  email varchar(128) NULL DEFAULT NULL::character varying,
  insert_time varchar(32) NULL DEFAULT NULL::character varying,
  ip varchar(128) NULL DEFAULT NULL::character varying,
  CONSTRAINT email_log_pkey PRIMARY KEY (id)
);

CREATE TABLE execution_report (
 id bigserial NOT NULL,
 created timestamp NULL DEFAULT now(),
 securityid int4 NULL,
 userid int4 NULL,
 clordid varchar(32) NULL DEFAULT NULL::character varying,
 symbol varchar(32) NULL,
 side varchar(32) NULL,
 ordtype varchar(32) NULL,
 exectype varchar(32) NULL,
 ordstatus varchar(32) NULL,
 orderid int8 NULL,
 secondaryorderid int8 NULL,
 origorderid int8 NULL,
 execid int8 NULL,
 secondaryexecid int8 NULL,
 counterpartyid int4 NULL,
 ispositionsidecrossed bool NULL,
 targetstrategy int4 NULL,
 orderqty int8 NULL,
 orderqtyscale int4 NULL,
 leavesqty int8 NULL,
 leavesqtyscale int4 NULL,
 cumqty int8 NULL,
 cumqtyscale int4 NULL,
 cumquoteqty int8 NULL,
 price int8 NULL,
 pricescale int4 NULL,
 avgpx int8 NULL,
 avgpxscale int4 NULL,
 lastpx int8 NULL,
 lastpxscale int4 NULL,
 lastqty int8 NULL,
 lastqtyscale int4 NULL,
 stoppx int8 NULL,
 stoppxscale int4 NULL,
 timeinforce varchar(32) NULL,
 expiretime int8 NULL,
 timestampmillis int8 NULL,
 expiretimemillis int8 NULL,
 aggressorside varchar(32) NULL,
 price2 int8 NULL,
 price2scale int4 NULL,
 execrestatementreason varchar(32) NULL,
 sourceseqnum int8 NULL,
 sourcesendtime int8 NULL,
 snapid int8 NULL,
 kafkarecordoffset int8 NULL,
 transactionid int8 NULL,
 islastmessageintransaction bool NULL,
 decodedtime int8 NULL,
 matchtime int8 NULL,
 publishtime int8 NULL,
 notional float4 NULL,
 feepositionid int4 NULL,
 feepositionquantitychange int8 NULL,
 feepositionquantity int8 NULL,
 settlepositionid int4 NULL,
 settlepositionquantitychange int8 NULL,
 settlepositionquantity int8 NULL,
 ispaidtoinsurance bool NULL,
 ishidden bool NULL,
 isliquidation bool NULL,
 submitterid int4 NULL DEFAULT 0,
 assetId int8 NULL DEFAULT 0,
 tokenId int4 NULL DEFAULT 0,
 groupAssetId int8 NULL DEFAULT 0,
 selectId int8 NULL DEFAULT 0,
 quoteType varchar(32) NULL,
 quoteTargetUserId int8 NULL DEFAULT 0,
 CONSTRAINT execution_report_pkey PRIMARY KEY (id)
);
CREATE INDEX execution_report1 ON execution_report USING btree (userid);
CREATE INDEX execution_report2 ON execution_report USING btree (securityid);
CREATE INDEX execution_report3 ON execution_report USING btree (orderid);

CREATE TABLE fee_log (
id bigserial NOT NULL,
sequence_number int8 NULL,
insert_time varchar(32) NULL DEFAULT NULL::character varying,
updatetype int2 NULL,
assetid int4 NULL,
feeinstrumentid int4 NULL,
fee int8 NULL,
feetype int2 NULL,
makertaker int2 NULL,
tier int4 NULL,
CONSTRAINT fee_log_pkey PRIMARY KEY (id)
);

CREATE TABLE fund (
 id bigserial NOT NULL,
 user_id int8 NULL,
 user_manager_id int8 NULL,
 title varchar(128) NULL,
 description varchar(2048) NULL,
 keywords varchar(2048) NULL,
 image_url varchar(256) NULL,
 overview varchar(2048) NULL,
 manager_description varchar(2048) NULL,
 manager_fee float4 NULL,
 solfini_fee float4 NULL,
 manager_stake float4 NULL,
 created timestamp NULL DEFAULT now(),
 last_updated timestamp NULL DEFAULT now(),
 volatility float4 NULL,
 target_return float4 NULL,
 target_interest float4 NULL,
 nav float4 NULL,
 outstanding float4 NULL,
 inception_return float4 NULL,
 d1_return float4 NULL,
 d30_return float4 NULL,
 holding1 int4 NULL,
 holding1_percent float4 NULL,
 holding1_name varchar(64) NULL,
 holding2 int4 NULL,
 holding2_percent float4 NULL,
 holding2_name varchar(64) NULL,
 holding3 int4 NULL,
 holding3_percent float4 NULL,
 holding3_name varchar(64) NULL,
 holding4 int4 NULL,
 holding4_percent float4 NULL,
 holding4_name varchar(64) NULL,
 holding5 int4 NULL,
 holding5_percent float4 NULL,
 holding5_name varchar(64) NULL,
 status int4 NULL,
 strategy varchar(256) NULL,
 yr1return float4 NULL,
 valuation float4 NULL,
 divyield float4 NULL,
 benchmark float4 NULL,
 account_id int8 NULL,
 CONSTRAINT fund_pkey PRIMARY KEY (id)
);
CREATE INDEX fund1 ON fund USING btree (user_id);
CREATE INDEX fund2 ON fund USING btree (user_manager_id);
CREATE INDEX fund3 ON fund USING btree (holding1);

CREATE TABLE fund_balance_log (
 id bigserial NOT NULL,
 sequence_number int8 NULL,
 insert_time varchar(32) NULL DEFAULT NULL::character varying,
 updatetype int2 NULL,
 userid int8 NULL,
 account_id int8 NULL,
 firmid int4 NULL,
 feetier int4 NULL,
 assetid int4 NULL,
 balance int8 NULL,
 balance_scale int4 NULL,
 balance_change int8 NULL,
 balance_change_scale int4 NULL,
 quantity int8 NULL,
 quantity_scale int4 NULL,
 price int8 NULL,
 price_scale int4 NULL,
 event_type int4 NULL,
 orderid int8 NULL,
 execid int8 NULL,
 unrealizedusd float4 NULL,
 realizedusd float4 NULL,
 avgcostbasisusd float4 NULL,
 baseusdmark float4 NULL,
 settlecoinusdmark float4 NULL,
 settlecoinunrealized float4 NULL,
 settlecoinrealized float4 NULL,
 fund_outstanding int8 NULL,
 fund_outstanding_scale int4 NULL,
 side bpchar(3) NULL,
 usdtotalfundvalue float4 NULL,
 CONSTRAINT fund_balance_log_pkey PRIMARY KEY (id)
);
CREATE INDEX fund_balance_log1 ON fund_balance_log USING btree (userid);
CREATE INDEX fund_balance_log2 ON fund_balance_log USING btree (assetid);

CREATE TABLE funding_rate_history (
 id bigserial NOT NULL,
 created timestamp NULL DEFAULT now(),
 pairid int4 NULL,
 symbol varchar(32) NULL,
 fundingrate float4 NULL,
 markinsettlecoin float4 NULL,
 txid int8 NULL DEFAULT 0,
 "timestamp" int8 NULL DEFAULT 0,
 vwap float4 NULL,
 "last" float4 NULL,
 usdmark float4 NULL,
 timeperiodinterest float4 NULL,
 CONSTRAINT funding_rate_history_pkey PRIMARY KEY (id)
);
CREATE INDEX funding_rate_history1 ON funding_rate_history USING btree (pairid);
CREATE INDEX funding_rate_history2 ON funding_rate_history USING btree ("timestamp");

CREATE TABLE geo_ip (
id bigserial NOT NULL,
ip varchar(64) NULL,
continentname varchar(64) NULL,
countrycode varchar(64) NULL,
countryname varchar(64) NULL,
regioncode varchar(64) NULL,
regionname varchar(64) NULL,
city varchar(64) NULL,
zip varchar(64) NULL,
latitude varchar(64) NULL,
longitude varchar(64) NULL,
geonameid varchar(64) NULL,
status int4 NULL,
created timestamp NULL DEFAULT now(),
CONSTRAINT geo_ip_pkey PRIMARY KEY (id)
);
CREATE INDEX geo_ip1 ON geo_ip USING btree (ip);
CREATE INDEX geo_ip2 ON geo_ip USING btree (countryname);
CREATE INDEX geo_ip3 ON geo_ip USING btree (status);

CREATE TABLE message_log (
id bigserial NOT NULL,
sequence_number int8 NULL,
insert_time varchar(32) NULL DEFAULT NULL::character varying,
messagetype bpchar(3) NULL,
orderid int8 NULL,
execid int8 NULL,
clordid varchar(32) NULL DEFAULT NULL::character varying,
securityid int4 NULL,
symbol varchar(32) NULL,
side bpchar(3) NULL,
ordtype bpchar(3) NULL,
exectype bpchar(3) NULL,
execrestatementreason bpchar(3) NULL,
ordstatus bpchar(3) NULL,
account int4 NULL,
timeinforce bpchar(1) NULL,
expiretime varchar(32) NULL DEFAULT NULL::character varying,
orderqty int8 NULL,
orderqty_scale int2 NULL,
leavesqty int8 NULL,
leavesqty_scale int2 NULL,
cumqty int8 NULL,
cumqty_scale int2 NULL,
price int8 NULL,
price_scale int2 NULL,
avgpx int8 NULL,
avgpx_scale int2 NULL,
lastpx int8 NULL,
lastpx_scale int2 NULL,
lastqty int8 NULL,
lastqty_scale int2 NULL,
CONSTRAINT message_log_pkey PRIMARY KEY (id)
);
CREATE INDEX message_log1 ON message_log USING btree (orderid);
CREATE INDEX message_log2 ON message_log USING btree (account);
CREATE INDEX message_log3 ON message_log USING btree (sequence_number);

CREATE TABLE order_book_state (
 id bigserial NOT NULL,
 sequence_number int8 NULL,
 active int2 NULL,
 insert_time varchar(32) NULL DEFAULT NULL::character varying,
 messagetype bpchar(3) NULL,
 orderid int8 NULL,
 execid int8 NULL,
 clordid varchar(32) NULL DEFAULT NULL::character varying,
 securityid int4 NULL,
 symbol varchar(32) NULL,
 side bpchar(3) NULL,
 ordtype bpchar(3) NULL,
 exectype bpchar(3) NULL,
 execrestatementreason bpchar(3) NULL,
 ordstatus bpchar(3) NULL,
 account int8 NULL,
 timeinforce bpchar(1) NULL,
 expiretime varchar(32) NULL DEFAULT NULL::character varying,
 "timestamp" varchar(32) NULL DEFAULT NULL::character varying,
 orderqty int8 NULL,
 orderqty_scale int4 NULL,
 leavesqty int8 NULL,
 leavesqty_scale int2 NULL,
 cumqty int8 NULL,
 cumqty_scale int2 NULL,
 price int8 NULL,
 price_scale int2 NULL,
 avgpx int8 NULL,
 avgpx_scale int2 NULL,
 lastpx int8 NULL,
 lastpx_scale int2 NULL,
 lastqty int8 NULL,
 lastqty_scale int2 NULL,
 stoppx int8 NULL,
 stoppx_scale int2 NULL,
 sendercompid varchar(64) NULL DEFAULT NULL::character varying,
 feepositionid int8 NULL,
 feepositionquantity int8 NULL,
 feepositionquantitychange int8 NULL,
 CONSTRAINT order_book_state_pkey PRIMARY KEY (id)
);
CREATE INDEX order_book_state1 ON order_book_state USING btree (orderid);
CREATE INDEX order_book_state2 ON order_book_state USING btree (securityid);
CREATE INDEX order_book_state3 ON order_book_state USING btree (active);
CREATE INDEX order_book_state4 ON order_book_state USING btree (account);

CREATE TABLE position_report (
id bigserial NOT NULL,
reportid int8 NULL,
created timestamp NULL DEFAULT now(),
userid int4 NULL,
usdvalue float4 NULL,
usdnotionalpositionvalue float4 NULL,
usdmaxexposurepositionandopenordersvalue float4 NULL,
usdopenordersrequiredvalue float4 NULL,
usdmarginvalue float4 NULL,
usdmarginrequiredvalue float4 NULL,
usdmarginmaintvalue float4 NULL,
leverageratio float4 NULL,
usdunrealized float4 NULL,
sourceseqnum int8 NULL,
sourcesendtime int8 NULL,
snapid int8 NULL,
kafkarecordoffset int8 NULL,
transactionid int8 NULL,
islastmessageintransaction bool NULL,
decodedtime int8 NULL,
matchtime int8 NULL,
publishtime int8 NULL,
txntype int4 NULL DEFAULT 0,
execid int8 NULL DEFAULT 0,
orderid int8 NULL DEFAULT 0,
instrumentid1 int4 NULL DEFAULT 0,
qty1 int8 NULL DEFAULT 0,
change1 int8 NULL DEFAULT 0,
instrumentid2 int4 NULL DEFAULT 0,
qty2 int8 NULL DEFAULT 0,
change2 int8 NULL DEFAULT 0,
instrumentid3 int4 NULL DEFAULT 0,
qty3 int8 NULL DEFAULT 0,
change3 int8 NULL DEFAULT 0,
instrumentid4 int4 NULL DEFAULT 0,
qty4 int8 NULL DEFAULT 0,
change4 int8 NULL DEFAULT 0,
txnid int8 NULL,
usdmarginablevalue float4 NULL,
CONSTRAINT position_report_pkey PRIMARY KEY (id)
);
CREATE INDEX position_report1 ON position_report USING btree (userid);
CREATE INDEX position_report2 ON position_report USING btree (reportid);
CREATE INDEX position_report3 ON position_report USING btree (created);
CREATE INDEX position_report4 ON position_report USING btree (txntype);
CREATE INDEX position_report5 ON position_report USING btree (publishtime);

CREATE TABLE position_report_balance (
id bigserial NOT NULL,
reportid int8 NULL,
instrumentid int4 NULL,
assettype bpchar(8) NULL,
quantity int8 NULL,
quantityscale int4 NULL,
availablequantity int8 NULL,
availablequantityscale int4 NULL,
usdcostbasis int8 NULL,
usdcostbasisscale int4 NULL,
usdavgcostbasis int8 NULL,
usdavgcostbasisscale int4 NULL,
usdvalue float4 NULL,
usdunrealized float4 NULL,
usdrealized float4 NULL,
quotedusdmark float4 NULL,
settlecoinusdmark float4 NULL,
settlecoinunrealized float4 NULL,
settlecoinrealized float4 NULL,
bankruptpriceint int8 NULL,
CONSTRAINT position_report_balance_pkey PRIMARY KEY (id)
);
CREATE INDEX position_report_balance1 ON position_report_balance USING btree (reportid);

CREATE TABLE security_definition_log (
id serial NOT NULL,
sequence_number int8 NULL,
insert_time varchar(32) NULL,
updatetype int2 NULL,
securityid int4 NULL,
symbol varchar(32) NULL,
"name" varchar(128) NULL,
assettype int2 NULL,
baseid int4 NULL,
quotedid int4 NULL,
pricescale int4 NULL,
quantityscale int4 NULL,
orderbookstrategy int2 NULL,
preordercheckstrategy int2 NULL,
settletype int4 NULL,
maintmarginpercent int4 NULL,
requiredmarginpercent int4 NULL,
usdmark float4 NULL,
status int4 NULL,
estimatedusercount int4 NULL,
estimatedvolatility float4 NULL,
daysfeedisactive int4 NULL,
estimatedvar float4 NULL,
sortorder int4 NULL DEFAULT 0,
tenure varchar(8) NULL,
symbolrollcount int4 NULL DEFAULT 0,
expiretimemillis int8 NULL DEFAULT 0,
expirerolltimemillis int8 NULL DEFAULT 0,
minpriceincrement float4 NULL DEFAULT 0.01,
minpriceincrementamount float4 NULL DEFAULT 0.01,
minimumfillsize int4 NULL DEFAULT 0,
qtytype int4 NULL DEFAULT 0,
contractmultiplier float4 NULL DEFAULT 0.01,
issuedate int4 NULL DEFAULT 0,
strikecurrencyid int4 NULL DEFAULT 1,
description varchar(256) NULL,
cficode varchar(64) NULL,
miccode varchar(64) NULL,
CONSTRAINT security_definition_log_pkey PRIMARY KEY (id)
);
CREATE INDEX security_definition_log1 ON security_definition_log USING btree (sequence_number);

CREATE TABLE trade_history_log (
  id bigserial NOT NULL,
  timemillis varchar(18) NULL,
  securityid int4 NULL,
  price int8 NULL,
  quantity int8 NULL,
  side bpchar(3) NULL,
  CONSTRAINT trade_history_log_pkey PRIMARY KEY (id)
);

CREATE TABLE upload_file (
id bigserial NOT NULL,
userid int8 NULL,
fileKey varchar(128) NULL,
filename varchar(128) NULL,
contenttype varchar(32) NULL,
proxylocation varchar(128) NULL,
created timestamp NULL DEFAULT now(),
referenceId int8 NULL,
uploadedBy int8 NULL,
CONSTRAINT upload_file_pkey PRIMARY KEY (id)
);
CREATE INDEX upload_file1 ON upload_file USING btree (filekey);

CREATE TABLE user_log (
 id bigserial NOT NULL,
 sequence_number int8 NULL,
 insert_time varchar(32) NULL DEFAULT NULL::character varying,
 userid int8 NULL,
 username varchar(128) NULL,
 "password" varchar(128) NULL,
 firmid int4 NULL,
 feetier int4 NULL,
 requeststatus int2 NULL,
 lmm int2 NULL,
 registeredip varchar(32) NULL,
 lastip varchar(32) NULL,
 verification int4 NULL,
 referral_code varchar(256) NULL,
 referred_by_code varchar(256) NULL,
 CONSTRAINT user_log_pkey PRIMARY KEY (id)
);

CREATE TABLE user_role (
  id bigserial NOT NULL,
  userid int4 NULL,
  account int4 NULL,
  "role" int4 NULL,
  status int4 NULL,
  created_by int4 NULL,
  created timestamp NULL DEFAULT now(),
  CONSTRAINT user_role_pkey PRIMARY KEY (id)
);
CREATE INDEX user_role1 ON user_role USING btree (userid);
CREATE INDEX user_role2 ON user_role USING btree (account);
CREATE INDEX user_role3 ON user_role USING btree (role);
CREATE INDEX user_role4 ON user_role USING btree (created);

CREATE TABLE user_stat_fee_log (
  id bigserial NOT NULL,
  "timestamp" timestamp NULL,
  userid int4 NULL,
  feeinstrumentid int4 NULL,
  feescale int4 NULL,
  feeamount int8 NULL,
  grantuserid int4 NULL,
  granttimestamp timestamp NULL,
  grantamount int8 NULL,
  grantkafkaoffset int8 NULL,
  discountuserid int4 NULL,
  discounttimestamp timestamp NULL,
  discountamount int8 NULL,
  discountkafkaoffset int8 NULL,
  processed bool NULL DEFAULT false,
  CONSTRAINT user_stat_fee_log_pkey PRIMARY KEY (id)
);

CREATE TABLE user_stat_log (
  id bigserial NOT NULL,
  "timestamp" timestamp NULL,
  userid int4 NULL,
  feetier int4 NULL,
  makervolume float4 NULL,
  takervolume float4 NULL,
  makerfillcount int8 NULL,
  takerfillcount int8 NULL,
  ordercount int8 NULL,
  cancelcount int8 NULL,
  openordercount int8 NULL,
  bestordercount0bps int8 NULL,
  bestordercount20bps int8 NULL,
  bestordercount50bps int8 NULL,
  realizedpnl float4 NULL,
  unrealizedpnl float4 NULL,
  effectivevolume float4 NULL,
  CONSTRAINT user_stat_log_pkey PRIMARY KEY (id)
);

CREATE TABLE user_state (
id serial NOT NULL,
sequence_number int8 NULL,
insert_time varchar(32) NULL DEFAULT NULL::character varying,
username varchar(128) NULL,
"password" varchar(128) NULL,
requesttoken varchar(128) NULL,
requestsecret varchar(128) NULL,
email varchar(128) NULL,
phone varchar(64) NULL,
firmid int4 NULL,
feetier int4 NULL,
requeststatus int2 NULL,
lmm int2 NULL,
sendercompid varchar(128) NULL,
targetcompid varchar(128) NULL,
registeredip varchar(32) NULL,
lastip varchar(32) NULL,
walletrequesttoken varchar(128) NULL,
walletrequestsecret varchar(128) NULL,
verification int4 NULL,
referral_code varchar(128) NULL,
referred_by_code varchar(128) NULL,
use2auth int2 NULL,
authtoken varchar(128) NULL,
authurl varchar(512) NULL,
companyname varchar(256) NULL,
firstname varchar(256) NULL,
middlename varchar(256) NULL,
lastname varchar(256) NULL,
address1 varchar(256) NULL,
address2 varchar(256) NULL,
address3 varchar(256) NULL,
city varchar(128) NULL,
state varchar(128) NULL,
zip varchar(64) NULL,
country varchar(64) NULL,
birthdate varchar(64) NULL,
taxid varchar(64) NULL,
requestcount int4 NULL,
ordercount int4 NULL,
linkedin varchar(128) NULL,
facebook varchar(128) NULL,
skype varchar(128) NULL,
photo_url varchar(128) NULL,
icon_url varchar(128) NULL,
id_url1 varchar(128) NULL,
id_url2 varchar(128) NULL,
id_url3 varchar(128) NULL,
id_url4 varchar(128) NULL,
id_url5 varchar(128) NULL,
security_question1 varchar(256) NULL,
security_answer1 varchar(256) NULL,
security_question2 varchar(256) NULL,
security_answer2 varchar(256) NULL,
security_question3 varchar(256) NULL,
security_answer3 varchar(256) NULL,
security_question4 varchar(256) NULL,
security_answer4 varchar(256) NULL,
security_question5 varchar(256) NULL,
security_answer5 varchar(256) NULL,
emergency_contact_name varchar(128) NULL,
emergency_contact_email varchar(128) NULL,
emergency_contact_phone varchar(128) NULL,
emergency_btc_address varchar(128) NULL,
"type" varchar(64) NULL,
registercode varchar(128) NULL DEFAULT NULL::character varying,
lastwithdrawcode varchar(128) NULL DEFAULT NULL::character varying,
usecodevalidaton int4 NULL DEFAULT 1,
minimumfeetier int4 NULL,
upgradefeetier int4 NULL,
sso_key varchar(64) NULL,
frozen int4 NULL,
alias_username varchar(128) NULL,
updated timestamp NULL,
cancel_on_disconnect int4 NULL,
consent_marketing int4 NULL,
consent_terms int4 NULL,
holdusdeinstrumentid int4 NULL,
isfirm int2 NULL,
isexchangestaff int2 NULL DEFAULT 0,
uithemeid int2 NULL DEFAULT 0,
updated_2fa timestamp NULL DEFAULT now(),
updated_password timestamp NULL DEFAULT now(),
last_login_time timestamp NULL DEFAULT now(),
isindividual int2 NULL DEFAULT 0,
isfuturesenabled int2 NULL DEFAULT 1,
isoptionsenabled int2 NULL DEFAULT 1,
kyctier int2 NULL DEFAULT 1,
twitter varchar(128) NULL,
iscommoditiesenabled int2 NULL DEFAULT 1,
isequitiesenabled int2 NULL DEFAULT 1,
isleaderboardexcluded int2 NULL DEFAULT 0,
istoasterenabled int2 NULL DEFAULT 1,
depositkycrestriction float4 NULL DEFAULT 0,
withdrawkycrestriction float4 NULL DEFAULT 0,
displaycurrency varchar(8) NULL DEFAULT 'USD'::character varying,
notionalpositioncap float4 NULL,
leveragecap float4 NULL,
isauctionsenabled int2 NULL DEFAULT 1,
isdatedfuturesenabled int2 NULL DEFAULT 1,
margincurveidoverride int4 NULL DEFAULT 0,
localauth int4 NULL DEFAULT 0,
CONSTRAINT user_state_pkey1 PRIMARY KEY (id)
);
CREATE INDEX user_state1 ON user_state USING btree (email);
CREATE INDEX user_state2 ON user_state USING btree (requesttoken);

CREATE TABLE user_whitelist_address (
   id bigserial NOT NULL,
   userid int8 NULL,
   instrumentid int4 NULL,
   address varchar(64) NULL DEFAULT NULL::character varying,
   insert_time varchar(32) NULL DEFAULT NULL::character varying,
   created timestamp NULL DEFAULT now(),
   status int4 NULL,
   "name" varchar(128) NULL,
   memo varchar(256) NULL,
   CONSTRAINT user_whitelist_address_pkey PRIMARY KEY (id)
);

CREATE TABLE user_whitelist_ip (
  id bigserial NOT NULL,
  userid int8 NULL,
  ip varchar(32) NULL DEFAULT NULL::character varying,
  insert_time varchar(32) NULL DEFAULT NULL::character varying,
  created timestamp NULL DEFAULT now(),
  status int4 NULL,
  CONSTRAINT user_whitelist_ip_pkey PRIMARY KEY (id)
);

CREATE TABLE withdraw_request (
 id bigserial NOT NULL,
 sequence_number int8 NULL,
 insert_time varchar(32) NULL DEFAULT NULL::character varying,
 address varchar(64) NULL,
 updatetype int2 NULL,
 userid int8 NULL,
 securityid int4 NULL,
 symbol varchar(16) NULL,
 balance_change int8 NULL,
 balance_change_scale int4 NULL,
 balance int8 NULL,
 balance_scale int4 NULL,
 confirms int4 NULL,
 "source" varchar(256) NULL,
 updateby varchar(64) NULL,
 signature varchar(256) NULL,
 status int4 NULL,
 ip varchar(32) NULL,
 assetid int8 NULL,
 tokenid int8 NULL,
 groupid int8 NULL,
 CONSTRAINT withdraw_request_pkey PRIMARY KEY (id)
);
CREATE INDEX withdraw_request1 ON withdraw_request USING btree (userid);
CREATE INDEX withdraw_request2 ON withdraw_request USING btree (securityid);
CREATE INDEX withdraw_request3 ON withdraw_request USING btree (status);
CREATE INDEX withdraw_request4 ON withdraw_request USING btree (assetid);




CREATE TABLE asset_details_state(
    id BIGSERIAL PRIMARY KEY NOT NULL,
    sequence_number INT,
    assetId BIGINT,
    tokeniId INT,
    securityId INT,
    assetType VARCHAR(32),
    assetStatus VARCHAR(32),
    venueId INT,
    ticketId INT,
    artistId INT,
    seriesId INT,
    idHex VARCHAR(256),
    publicAddress VARCHAR(256),
    contractAddress VARCHAR(256),
    contractTokenId VARCHAR(256),
    chainType VARCHAR(64),
    parentId BIGINT,
    numOfKind INT,
    ownerUserId INT,
    externalId VARCHAR(32),
    updateType VARCHAR(16),
    created TIMESTAMP default now(),
    updated TIMESTAMP default now(),
    kafkaRecordOffset BIGINT,
    insert_time VARCHAR(32),
    collectionId INT,
    redeemStatus INT,
    name VARCHAR(64),
    description VARCHAR(1280),
    url VARCHAR(256),
    imageUrl VARCHAR(256),
    imagethumburl VARCHAR(256),
    mediaurl VARCHAR(256),
    etherscanUrl VARCHAR(128),
    openseaUrl VARCHAR(128),
    ipfsUrl VARCHAR(128),
    redeemfile varchar(256) NULL,
    redeemfilehq varchar(256) NULL,
    isphysical bool NULL,
    isdigital bool NULL,
    category VARCHAR(64),
    fundTransferAccount VARCHAR(256),
    eventId INT,
    ticketSyncStatus VARCHAR(32),
    includeMerch bool NULL,
    seatNo VARCHAR(32),
    isRegister bool NULL,
    assetPayeeAccountAddress varchar(256),
    payeeEnabled bool default false,
    assetAttributeId BIGINT,
    region VARCHAR(64),
    assetSize VARCHAR(64),
    registerTime BIGINT,
    firstDivTime BIGINT,
    divFrequencyTime BIGINT,
    estNav double precision,
    estROI double precision,
    metadataUrl varchar(128) NULL,
    redeemimagethumburl varchar(256) NULL,
    supplylimit int4 null,
    royaltypercentage float8 null,
    royaltyreceiveraddress varchar(256) null,
    transferlimit int4 NULL DEFAULT 0,
    resalelimit int4 NULL DEFAULT 0,
    albumname varchar(64) null,
    taxfeewalletaddress varchar(256) null,
    ccfeewalletaddress varchar(256) null,
    "label" varchar(16) null,
    exchangeFee double precision,
    
    keywords varchar(256) null,
    overview varchar(1024) null,
    managerDescription varchar(1024) null,
    managerStake double precision,
    volatility double precision,
    targetReturn double precision,
    targetInterest double precision,
    outstanding double precision,
    inceptionReturn double precision,
    d1Return double precision,
    d30Return double precision,
    holding1 INT,
    holding1Percent double precision,
    holding1Name varchar(128) null,
    holding2 INT,
    holding2Percent double precision,
    holding2Name varchar(128) null,
    holding3 INT,
    holding3Percent double precision,
    holding3Name varchar(128) null,
    holding4 INT,
    holding4Percent double precision,
    holding4Name varchar(128) null,
    holding5 INT,
    holding5Percent double precision,
    holding5Name varchar(128) null,
    createDate BIGINT,
    updatedDate BIGINT,
    status INT,
    strategy varchar(128) null,
    auditor varchar(128) null,
    developer varchar(128) null,
    yr1Return double precision,
    valuation double precision,
    divYield double precision,
    benchmark double precision,
    privatePlaceMemoUrl varchar(256) null,
    operatingAgreementUrl varchar(256) null,
    subscriptionAgreementUrl varchar(256) null,
    pitchDeckUrl varchar(256) null,
    prospectusUrl varchar(256) null,
    supportDocUrl varchar(256) null,
    supportDoc2Url varchar(256) null,
    supportDoc3Url varchar(256) null,
    supportDoc4Url varchar(256) null,
    supportDoc5Url varchar(256) null,
    longitude double precision,
    latitude double precision,
   -- marketType INT DEFAULT 2,
    assetLogoUrl varchar(256) null,
    
    aboutOrg varchar(1024) null,
    sdgId varchar(32) null,
    standardsVersion varchar(64) null,
    methodology varchar(256) null,
    projectScale varchar(32) null,
    annualEstCredits BIGINT,
    marketType varchar(32) null,
    sdgImpactList varchar(256) null,
    projectFromTime BIGINT,
    projectToTime BIGINT,
    floorPrice double precision,
    ceilingPrice double precision
    
);
CREATE INDEX ASSET_DETAILS_STATE1 ON ASSET_DETAILS_STATE USING btree (ownerUserId);
CREATE INDEX ASSET_DETAILS_STATE2 ON ASSET_DETAILS_STATE USING btree (assetid);
CREATE INDEX ASSET_DETAILS_STATE3 ON ASSET_DETAILS_STATE USING btree (tokeniId);
CREATE INDEX ASSET_DETAILS_STATE4 ON ASSET_DETAILS_STATE USING btree (securityId);


CREATE TABLE AML_LOG(
	id BIGSERIAL PRIMARY KEY NOT NULL,
	userId INT,
	direction VARCHAR(16),
	chainType VARCHAR(16),
	score double precision,
	publicAddress VARCHAR(256),
	txnId VARCHAR(256),
	response VARCHAR(2048),
	created TIMESTAMP default now()
);

alter table USER_STATE add subscribeRFQ SMALLINT DEFAULT 0;

alter table asset_state add tokenType SMALLINT DEFAULT 0;

ALTER TABLE chain_transaction2_log ADD decimals int NULL DEFAULT 18;

CREATE TABLE contract_state (
   id bigserial NOT NULL,
   "chain" varchar(64) NULL,
   contractaddress varchar(256) NULL,
   verificationstatus int4 NULL,
   created timestamp NULL DEFAULT now(),
   name varchar(256) NULL,
   symbol varchar(256) NULL,
   contracttype varchar(64) NULL,
   instrumentid int4 NULL,
   decimals varchar(16) NULL,
   CONSTRAINT contract_state_pkey PRIMARY KEY (id)
);

ALTER TABLE contract_state ADD depositallowed bool NULL DEFAULT false;
ALTER TABLE contract_state ADD retireallowed bool NULL DEFAULT false;

CREATE TABLE INDEX_MANAGER (
    id BIGSERIAL PRIMARY KEY NOT NULL,
    managerUserId INT,
    instrumentId INT
);
ALTER TABLE asset_state ADD symbol varchar(32) NULL;

ALTER TABLE withdraw_request ADD withdrawType int default 0;

CREATE TABLE retire_event (
    id bigserial PRIMARY KEY NOT NULL,

                        userId INT,
                        direction VARCHAR(16),
                        chainType VARCHAR(16),
                        score double precision,
                        publicAddress VARCHAR(256),
                        txnId VARCHAR(256),
                        response VARCHAR(2048),
                        created TIMESTAMP default now()
);

ALTER TABLE user_role ADD updated_by int4 NULL;
ALTER TABLE user_role ADD updated timestamp NULL;

ALTER TABLE withdraw_request ADD transactionhash varchar(256) NULL;
ALTER TABLE withdraw_request ADD "chain" varchar(16) NULL;
ALTER TABLE withdraw_request ADD updated timestamp NOT NULL DEFAULT now();
ALTER TABLE withdraw_request ADD "bank" varchar(64) NULL;
ALTER TABLE withdraw_request ADD "bankAccount" varchar(32) NULL;
ALTER TABLE withdraw_request ADD "swiftCode" varchar(16) NULL;
ALTER TABLE asset_state ADD isupdatedimageurl bool DEFAULT false;
ALTER TABLE asset_state ADD isupdatedmetadatajson bool DEFAULT false;
ALTER TABLE asset_state ADD detailId int8 DEFAULT 0;
ALTER TABLE asset_state ADD vintage int8 null;
ALTER TABLE withdraw_request ADD "serials" text NULL;
ALTER TABLE withdraw_request ADD "certificateName" varchar(128) null;
ALTER TABLE asset_state ADD retiredContractAddress varchar(64) null;
ALTER TABLE asset_state ADD toVintage int8 null;
ALTER TABLE withdraw_request ADD "registryName" varchar(128) null;
ALTER TABLE withdraw_request ADD "accountNumber" varchar(128) null;
ALTER TABLE withdraw_request ADD memo varchar(256) NULL;
ALTER TABLE asset_state ADD certificateName varchar(64) null;
ALTER TABLE contract_state ADD contractVersion varchar(16) NULL DEFAULT 'V1';
ALTER TABLE user_state ADD isInfluencer bool NULL DEFAULT FALSE;





CREATE TABLE subscription_state (
    id bigserial PRIMARY KEY NOT NULL,
    userId INT,
    platform VARCHAR(128),
    accountId VARCHAR(128),
    exchange VARCHAR(64),
    apiUser VARCHAR(128),
    apiSecret VARCHAR(128),
    percentage int,
    maxAmount int8,
    status int,
    created int8,
    expires int8
);
ALTER TABLE user_state ADD platform varchar(64) NULL;
ALTER TABLE user_state ADD accountId varchar(64) NULL;
ALTER TABLE subscription_state ADD apiKey varchar(128) NULL;
ALTER TABLE subscription_state ADD updated int8 default 0;

CREATE TABLE copy_trade_state (
    id bigserial PRIMARY KEY NOT NULL,
    userId INT,
    securityId INT,
    clOrdId VARCHAR(32),
    platform VARCHAR(128),
    accountId VARCHAR(128),
    exchange VARCHAR(64),
    created int8,
    side VARCHAR(32),
    ordType VARCHAR(32),
    timeInForce VARCHAR(32),
    orderQty int8,
    orderQtyScale int2,
    price int8,
    priceScale int2,
    result VARCHAR(256),
    kafkarecordoffset int8,
    basesymbol VARCHAR(32),
    quotedsymbol VARCHAR(32),
    subscriptionId int8,
    externalId VARCHAR(256),
    originalAmount double precision,
    cumulativeAmount double precision,
    status VARCHAR(32)
    );
ALTER TABLE user_state ADD fiatwireenabled int default 0;
ALTER TABLE user_state ADD stablecoinenabled int default 0;
--ALTER TABLE security_definition_log ADD markettype int default 2;

CREATE TABLE external_instrument_state (
    id bigserial PRIMARY KEY NOT NULL,
    exchange VARCHAR(32),
    base VARCHAR(32),
    quoted VARCHAR(32),
    tradable bool,
    updated int8
    );

ALTER TABLE subscription_state ADD preferredQuoteCurrency varchar(16) NULL;

CREATE TABLE external_exchange_state (
    id serial PRIMARY KEY NOT NULL,
    code VARCHAR(32),
    quoteCurrencies VARCHAR(256),
    created int8,
    updated int8,
    status int
    );

ALTER TABLE subscription_state ADD preferredCurrencies varchar(256) NULL;

CREATE TABLE subscription_payment_state (
    id bigserial PRIMARY KEY NOT NULL,
    userId int,
    subscriptionId int8,
    noOfDays int,
    amount double precision,
    chain VARCHAR(32),
    paymentCurrency VARCHAR(32),
    created int8,
    updated int8,
    status int,
    percentage int,
    maxAmount int8
    );

ALTER TABLE subscription_payment_state ADD paymentMethod int null;

ALTER TABLE user_state ADD channel varchar(32) DEFAULT 'CARBON_CREDIT' NOT NULL;

CREATE TABLE billing_info_state (
    id bigserial PRIMARY KEY NOT NULL,
    userId int,
    billingName VARCHAR(64),
    nickname VARCHAR(64),
    preferred int2,
    billingType VARCHAR(32),
    name VARCHAR(64),
    address1 VARCHAR(64),
    address2 VARCHAR(64),
    city VARCHAR(64),
    state VARCHAR(64),
    country VARCHAR(64),
    postalCode VARCHAR(64),
    status int2,
    email VARCHAR(64),
    externalToken VARCHAR(64),
    externalCustomerId VARCHAR(64),
    cardBrand VARCHAR(64),
    errorTxt VARCHAR(256),
    cardAndCvcHash VARCHAR(128),
    paymentMethod VARCHAR(128),
    channel VARCHAR(32)
    );

CREATE TABLE twitter_user_state (
    id bigserial PRIMARY KEY NOT NULL,
    twitterId VARCHAR(64),
    name VARCHAR(128),
    username VARCHAR(64),
    profileImage VARCHAR(128),
    verified bool,
    followersCount int8,
    followingCount int8,
    tweetCount int8,
    listedCount int8,
    likeCount int8,
    lastUpdated int8,
    createdAt VARCHAR(32),
    insertedAt int8,
    status int
    );

CREATE TABLE public.twitter_username_backfill_state (
	username varchar(32) PRIMARY KEY NOT NULL,
	backfilled bool NOT NULL DEFAULT false
);

CREATE TABLE subscription_fee_status (
    id serial PRIMARY KEY NOT NULL,
    fromValue int8,
    toValue int8,
    percentage int8,
    flatAmount int8,
    createdAt int8,
    updatedAt int8,
    status int
    );

ALTER TABLE subscription_state ADD inverseTrade int default 0;

ALTER TABLE copy_trade_state ADD origClOrdId VARCHAR(32);
ALTER TABLE copy_trade_state ADD signalPercentage int8;
ALTER TABLE copy_trade_state ADD signalpercentagescale int;
ALTER TABLE copy_trade_state ADD signalprice int8;
ALTER TABLE copy_trade_state ADD signalpricescale int;
ALTER TABLE copy_trade_state ADD xQuantity VARCHAR(32);
ALTER TABLE copy_trade_state ADD xPrice VARCHAR(32);
ALTER TABLE copy_trade_state ADD isToClose bool;

ALTER TABLE subscription_state ADD amountWithLeverage int8 default 0;
ALTER TABLE subscription_payment_state ADD amountWithLeverage int8 default 0;
ALTER TABLE copy_trade_state ADD isToClose bool default FALSE;

ALTER TABLE copy_trade_state ADD closeClOrdId VARCHAR(32);
ALTER TABLE copy_trade_state ADD closed bool default false;
ALTER TABLE subscription_state ADD hasPendingClose bool default false;

ALTER TABLE external_instrument_state ADD closePricePercentage int default 50000;--50000 => 5%

ALTER TABLE copy_trade_state ADD borrowedAmount double precision default 0;
ALTER TABLE copy_trade_state ADD repaid bool default false;
ALTER TABLE subscription_payment_state ADD txnFee double precision default 0;


--applied to production
ALTER TABLE copy_trade_state ADD futuresEnabled bool default false;
ALTER TABLE subscription_state ADD futuresEnabled bool default false;
ALTER TABLE subscription_state ADD lastUsedProxy varchar(32) default null;


CREATE TABLE public.infulencer_symbol_state (
	username varchar(32) PRIMARY KEY NOT NULL,
	symbols varchar(1024),
	lastUpdated int8 NOT NULL DEFAULT 0
);

ALTER TABLE copy_trade_state ADD tradeValue double precision default 0;

CREATE TABLE public.promo_code_state (
    id bigserial PRIMARY KEY NOT NULL,
    type int not null default 0,
	code varchar(32),
	status int not null default 0,
	startTime int8 not null default 0,
	endTime int8 not null default 0,
	updated int8 not null default 0
);

ALTER TABLE user_state ADD copyTradeEnabled bool not NULL DEFAULT FALSE;

ALTER TABLE notification_state ADD channel varchar(2) not NULL DEFAULT 'CC';

ALTER TABLE subscription_state ADD availableMaxAmount int8 not NULL DEFAULT 0;

ALTER TABLE subscription_state ADD activationToken varchar(64);
ALTER TABLE subscription_state ADD tokenGeneratedTime int8 not NULL DEFAULT 0;


CREATE TABLE public.channel_configuration_state (
    channel varchar(32) PRIMARY KEY NOT NULL,
	emailHost varchar(32),
	emailPort int not null default 0,
    emailUsername varchar(64),
    emailPassword varchar(128),
    emailSender varchar(64),
    emailSenderName varchar(32),
    emailTemplateFolder varchar(32),
    status int not null default 0
);

ALTER TABLE channel_configuration_state ADD linkedDomain varchar(128);
ALTER TABLE channel_configuration_state ADD projectName varchar(32);
ALTER TABLE channel_configuration_state ADD stripeKey varchar(256);
ALTER TABLE channel_configuration_state ADD stripeSecret varchar(256);
ALTER TABLE notification_state ALTER COLUMN channel TYPE varchar(32) USING channel::varchar(32);

ALTER TABLE external_instrument_state ADD isFutures bool default false;

CREATE TABLE public.exchange_default_quote (
	exchange varchar(32) NOT NULL,
	base_symbol varchar(32) NOT NULL,
	quote_symbol varchar(32),
	PRIMARY KEY(exchange, base_symbol)
);


ALTER TABLE external_instrument_state ADD priceScale int default 2;
ALTER TABLE external_instrument_state ADD qtyScale int default 2;
ALTER TABLE external_instrument_state ADD openPricePercentage int default 100000;--2000 => 20%

ALTER TABLE subscription_state ADD subscriptionType int DEFAULT 1;

ALTER TABLE deposit_wallet_state ADD expireTime int8 DEFAULT 0 NOT NULL;
ALTER TABLE subscription_payment_state ADD address varchar(256) default null;
ALTER TABLE deposit_wallet_state ADD updated int8 DEFAULT 0 NOT NULL;
ALTER TABLE transfer_state ADD type int DEFAULT 0 NOT NULL;
ALTER TABLE deposit_wallet_state ADD lastFetchedBlock int8 DEFAULT 0 NOT NULL;

ALTER TABLE subscription_state ADD amount double precision default 0;
ALTER TABLE subscription_state ADD paidAmount double precision default 0;
ALTER TABLE subscription_payment_state ADD marketPrice double precision default 0;
ALTER TABLE subscription_payment_state ADD paidAmount double precision default 0;

ALTER TABLE public.subscription_payment_state RENAME TO subscription_payment_state_bk;

CREATE TABLE public.subscription_payment_state (
	id bigserial NOT NULL,
	userId int4 NULL,
	subscriptionId int8 NULL,
	paymentIntentId int8 NULL,
	txnFee float8 DEFAULT 0 NULL,
	paidTxnFee float8 DEFAULT 0 NULL,
	paidAmount float8 DEFAULT 0 NULL,
	"chain" varchar(32) NULL,
	address varchar(256) DEFAULT NULL,
	paymentMethod int4 NULL,
	paymentCurrency varchar(32) NULL,
	fxRate float8 DEFAULT 0 NULL,
	status int4 NULL,
	created int8 NULL,
    updated int8 NULL,
	PRIMARY KEY(id)
);

CREATE TABLE public.subscription_payment_intent_state (
	id bigserial NOT NULL,
	userId int4 NULL,
	subscriptionId int8 NULL,
	noOfDays int4 NULL,
	amount float8 DEFAULT 0,
	status int4 NULL,
	created int8 NULL,
    updated int8 NULL,
    percentage int4 DEFAULT 0,
    maxAmount int8 DEFAULT 0,
    amountWithLeverage int8 DEFAULT 0,
	paidAmount float8 DEFAULT 0,
	PRIMARY KEY(id)
);

CREATE TABLE public.liquidity_exchange_state(
    id serial NOT NULL,
    name varchar(32),
    externalReference varchar(32),
    instrumentType integer,
    status int4,
    PRIMARY KEY(id)
);

CREATE TABLE public.liquidity_pair_state(
    id serial NOT NULL,
    symbol varchar(32),
    instrumentType integer,
    status int4,
    PRIMARY KEY(id)
);

CREATE TABLE public.liquidity_exchange_pair_state(
    id serial NOT NULL,
    exchange int4,
    symbol varchar(32),
    instrumentType integer,
    base varchar(16),
    quote varchar(16),
    externalReference varchar(32),
    status int4,
    PRIMARY KEY(id)
);

ALTER TABLE liquidity_pair_state ADD alias varchar(16) default null;
ALTER TABLE liquidity_pair_state ADD cmc_slug varchar(32) default null;
ALTER TABLE liquidity_pair_state ADD name varchar(32) default null;
ALTER TABLE liquidity_pair_state ADD address varchar(64) default null;

ALTER TABLE contract_state ADD lastfetchedblocknumberLiq varchar(16) DEFAULT '0';

CREATE TABLE liquidity_subscription_state (
    id serial PRIMARY KEY NOT NULL,
    exchange VARCHAR(64),
    apiUser VARCHAR(128),
    apiKey VARCHAR(128),
    apiSecret VARCHAR(128),
    status int,
    created int8,
    expires int8,
    updated int8,
    futuresEnabled bool default false,
    hasLeverage bool default false,
    lastUsedProxy VARCHAR(64)
);

CREATE TABLE blockchain_notional_state (
    userId int PRIMARY KEY NOT NULL,
    notional int8,
    updated int8,
    snapshotId int8
);

ALTER TABLE user_state ADD isRewardClaimed bool not NULL DEFAULT FALSE;
ALTER TABLE external_instrument_state ADD symbol varchar(64) NULL;
ALTER TABLE liquidity_subscription_state ADD apikey2 varchar(1024) NULL;
ALTER TABLE liquidity_subscription_state ADD apisecret2 varchar(1024) NULL;


ALTER TABLE subscription_state ADD influencer_userId int NULL;

--update subscription_state set influencer_userId =142 where accountid='Peaceful-Green-Hair';
--update subscription_state set influencer_userId =2 where accountid='Crowded-Yellow-Wall';
--update subscription_state set influencer_userId =98 where accountid='Lovely-Olden-Magazine';
--update subscription_state set influencer_userId =62 where accountid='Messy-Purple-Lock';
--update subscription_state set influencer_userId =60 where accountid='Silly-Cyan-Dog';
--update subscription_state set influencer_userId =1 where accountid='Obedient-Navy blue-Window';
--update subscription_state set influencer_userId =142 where accountid='0x0581d84dff0c3bca7c951dde76b516e89eb460a1';


CREATE TABLE copy_trade_pnl_state (
    created            BIGINT       NOT NULL, -- epoch millis
    userid             INT       NOT NULL,
    subscriptionid     INT       NOT NULL,
    securityid         INT       NOT NULL,
    influencerid       INT       NOT NULL,
    symbol             VARCHAR(32)  NOT NULL,
    realizedpnl        NUMERIC(20,8) NOT NULL,
    paidpnl        NUMERIC(20,8) NOT NULL,
    runningposition    NUMERIC(20,8) NOT NULL,
    averagecost        NUMERIC(20,8) NOT NULL,
    lastexecid         BIGINT        NOT NULL,
    CONSTRAINT pk_copy_trade_pnl_State
        PRIMARY KEY (userid,subscriptionid,securityid,created)
);
CREATE INDEX CONCURRENTLY idx_copy_trade_pnl_State_latest
ON copy_trade_pnl_State (userid,subscriptionid,securityid,created DESC);

CREATE TABLE referral_pnl_state (
    created            BIGINT       NOT NULL, -- epoch millis
    userid             INT       NOT NULL,
    referredby         INT       NOT NULL,
    pnl        NUMERIC(20,8) NOT NULL,
    earnings    NUMERIC(20,8) NOT NULL,
    lastexecid         BIGINT        NOT NULL,
    CONSTRAINT pk_referral_pnl_State
        PRIMARY KEY (userid,created)
);

CREATE INDEX CONCURRENTLY idx_referral_pnl_State_latest
ON referral_pnl_State (userid,created DESC);

(created,userid,referredBy,pnl,earnings,lastExecId)

ALTER TABLE public.liquidity_exchange_pair_state ALTER COLUMN alias TYPE varchar(32) USING alias::varchar(32);
ALTER TABLE public.liquidity_exchange_pair_state ALTER COLUMN base TYPE varchar(32) USING base::varchar(32);
ALTER TABLE public.liquidity_pair_state ALTER COLUMN alias TYPE varchar(32) USING alias::varchar(32);


ALTER TABLE subscription_state ADD connectionType int DEFAULT 0;

ALTER TABLE external_instrument_state ADD instrumentType int default 0;
UPDATE external_instrument_state set instrumentType = 1 where isFutures is true;  --perp
UPDATE external_instrument_state set instrumentType = 2 where isFutures is false; --spot

ALTER TABLE subscription_state ADD restOnly bool DEFAULT true;
ALTER TABLE subscription_state ADD passphrase varchar(128);
ALTER TABLE subscription_state ADD forceToUseProxy bool DEFAULT false;

ALTER TABLE withdraw_request ADD "contractAddress" varchar(64) NULL;

ALTER TABLE blockchain_notional_state ADD "contractKey" varchar(32) default 'MAINNET_V1';
ALTER TABLE blockchain_notional_state DROP CONSTRAINT blockchain_notional_state_pkey;
ALTER TABLE blockchain_notional_state ADD PRIMARY KEY (userid, contractkey);

CREATE TABLE blockchan_snap_mapping (
    id        BIGSERIAL PRIMARY KEY,
    snapshot_id BIGINT NOT NULL UNIQUE
);

CREATE TABLE blockchain_user_state (
    id         INT          NOT NULL,
    address    VARCHAR(255) NOT NULL,
    createdAt  BIGINT       NOT NULL,
    network    VARCHAR(50)  NOT NULL,
    PRIMARY KEY (id, network)
);